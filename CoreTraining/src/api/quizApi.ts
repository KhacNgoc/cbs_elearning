import { axiosClient, isMockApi, mockDelay } from "./axiosClient";
import type { Question, QuizQuestion } from "../types";
import { mockQuestions } from "../data/mockData";
import { mockDbCourses } from "./courseApi";

export let mockDbQuestions = JSON.parse(
  JSON.stringify(mockQuestions),
) as Question[];
mockDbCourses.forEach((course) => {
  course.hasQuiz = mockDbQuestions.some(
    (question) => question.courseId === course.id && !question.lessonId,
  );
});
export const setMockDbQuestions = (newQuestions: Question[]) => {
  mockDbQuestions = newQuestions;
};

export interface QuizSubmitRequest {
  selectedAnswers: Record<string, number[]>;
  timeElapsed: number;
}

export interface QuizSubmitResponse {
  score: number;
  total: number;
  percent: number;
  passingScore?: number;
  passed?: boolean;
}

export interface QuizReviewQuestion {
  id: number;
  text: string;
  options: string[];
  selectedAnswers: number[];
  correctAnswers: number[];
  correct: boolean;
}

export interface QuizReview extends QuizSubmitResponse {
  questions: QuizReviewQuestion[];
}

export const quizApi = {
  async getQuizQuestions(
    courseId: string,
    lessonId?: string,
  ): Promise<QuizQuestion[]> {
    if (isMockApi()) {
      await mockDelay(300);
      const filtered = mockDbQuestions.filter((q) =>
        lessonId
          ? q.lessonId === lessonId
          : q.courseId === courseId && !q.lessonId,
      );
      // Hide correctAnswer field from the returned payload to simulate backend anti-cheat logic
      return filtered.map(({ correctAnswer, correctAnswers, ...q }) => q);
    }
    const url = lessonId
      ? `/api/quizzes/lessons/${lessonId}/questions`
      : `/api/quizzes/${courseId}/questions`;
    const response = await axiosClient.get<QuizQuestion[]>(url);
    return response.data;
  },

  async submitQuiz(
    courseId: string,
    data: QuizSubmitRequest,
    lessonId?: string,
  ): Promise<QuizSubmitResponse> {
    if (isMockApi()) {
      await mockDelay(400);

      const filteredOriginals = mockDbQuestions.filter((q) =>
        lessonId
          ? q.lessonId === lessonId
          : q.courseId === courseId && !q.lessonId,
      );
      let score = 0;

      filteredOriginals.forEach((q) => {
        const selected = [...(data.selectedAnswers[q.id] || [])].sort();
        const correct = [...(q.correctAnswers || [q.correctAnswer])].sort();
        if (
          selected.length === correct.length &&
          selected.every((value, index) => value === correct[index])
        ) {
          score++;
        }
      });

      const total = filteredOriginals.length;
      const percent = Math.round((score / total) * 100);

      // Save score & badge to LocalStorage in Mock Mode
      const scoreStorageKey = lessonId
        ? "mockLessonQuizScores"
        : "mockQuizScores";
      const totalStorageKey = lessonId
        ? "mockLessonQuizTotals"
        : "mockQuizTotals";
      const scoreScopeId = lessonId || courseId;
      const currentScores = JSON.parse(
        localStorage.getItem(scoreStorageKey) || "{}",
      );
      const currentTotals = JSON.parse(
        localStorage.getItem(totalStorageKey) || "{}",
      );

      const prevHighScore = currentScores[scoreScopeId] || 0;
      if (score >= prevHighScore) {
        currentScores[scoreScopeId] = score;
        currentTotals[scoreScopeId] = total;
        localStorage.setItem(scoreStorageKey, JSON.stringify(currentScores));
        localStorage.setItem(totalStorageKey, JSON.stringify(currentTotals));
        const answerStorageKey = lessonId
          ? "mockLessonQuizAnswers"
          : "mockQuizAnswers";
        const savedAnswers = JSON.parse(
          localStorage.getItem(answerStorageKey) || "{}",
        );
        savedAnswers[scoreScopeId] = data.selectedAnswers;
        localStorage.setItem(answerStorageKey, JSON.stringify(savedAnswers));
      }

      const lesson = mockDbCourses
        .flatMap((course) => course.lessons)
        .find((item) => item.id === lessonId);
      const currentCourse = mockDbCourses.find(
        (course) => course.id === courseId,
      );
      const passingScore = lessonId
        ? (lesson?.quizPassingScore ?? 80)
        : (currentCourse?.quizPassingScore ?? 80);
      if (percent >= passingScore) {
        const completed = JSON.parse(
          localStorage.getItem("mockCompletedLessons") || "[]",
        ) as string[];
        const completedLessonIds = lessonId
          ? [lessonId]
          : (currentCourse?.lessons.map((item) => item.id) ?? []);
        for (const completedLessonId of completedLessonIds) {
          if (!completed.includes(completedLessonId))
            completed.push(completedLessonId);
        }
        localStorage.setItem("mockCompletedLessons", JSON.stringify(completed));
      }

      // Check badge unlock
      let badgeType: "bronze" | "silver" | "gold" | null = null;
      if (percent === 100) badgeType = "gold";
      else if (percent > 90) badgeType = "silver";
      else if (percent >= 80) badgeType = "bronze";

      if (badgeType) {
        const badgeId = `${courseId}_${badgeType}`;
        const unlockedBadges = JSON.parse(
          localStorage.getItem("mockUnlockedBadges") || "[]",
        );
        const alreadyHas = unlockedBadges.some((b: any) => b.id === badgeId);

        if (!alreadyHas) {
          unlockedBadges.push({
            id: badgeId,
            title:
              badgeType === "gold"
                ? `Huy hiệu Vàng ${courseId}`
                : badgeType === "silver"
                  ? `Huy hiệu Bạc ${courseId}`
                  : `Huy hiệu Đồng ${courseId}`,
            type: badgeType,
            courseId,
            courseName: courseId,
            unlockedAt: new Date().toISOString().split("T")[0],
            icon:
              badgeType === "gold"
                ? "Award"
                : badgeType === "silver"
                  ? "Medal"
                  : "Shield",
          });
          localStorage.setItem(
            "mockUnlockedBadges",
            JSON.stringify(unlockedBadges),
          );
        }
      }

      return {
        score,
        total,
        percent,
        passingScore,
        passed: total > 0 && percent >= passingScore,
      };
    }

    const url = lessonId
      ? `/api/quizzes/lessons/${lessonId}/submit`
      : `/api/quizzes/${courseId}/submit`;
    const response = await axiosClient.post<QuizSubmitResponse>(url, data);
    return response.data;
  },

  async getQuizReview(
    courseId: string,
    lessonId?: string,
  ): Promise<QuizReview> {
    if (isMockApi()) {
      await mockDelay(250);
      const questions = mockDbQuestions.filter((question) =>
        lessonId
          ? question.lessonId === lessonId
          : question.courseId === courseId && !question.lessonId,
      );
      const scoreScopeId = lessonId || courseId;
      const scoreStorageKey = lessonId
        ? "mockLessonQuizScores"
        : "mockQuizScores";
      const totalStorageKey = lessonId
        ? "mockLessonQuizTotals"
        : "mockQuizTotals";
      const answerStorageKey = lessonId
        ? "mockLessonQuizAnswers"
        : "mockQuizAnswers";
      const score = JSON.parse(localStorage.getItem(scoreStorageKey) || "{}")[
        scoreScopeId
      ];
      const total = JSON.parse(localStorage.getItem(totalStorageKey) || "{}")[
        scoreScopeId
      ];
      const selectedAnswers = JSON.parse(
        localStorage.getItem(answerStorageKey) || "{}",
      )[scoreScopeId];
      if (score === undefined || !selectedAnswers)
        throw new Error("Không có dữ liệu bài làm để xem lại");
      const lesson = mockDbCourses
        .flatMap((course) => course.lessons)
        .find((item) => item.id === lessonId);
      const course = mockDbCourses.find((item) => item.id === courseId);
      const passingScore = lessonId
        ? (lesson?.quizPassingScore ?? 80)
        : (course?.quizPassingScore ?? 80);
      const percent = total ? Math.round((score / total) * 100) : 0;
      if (percent < passingScore) throw new Error("Bài kiểm tra chưa đạt");
      return {
        score,
        total,
        percent,
        passingScore,
        passed: true,
        questions: questions.map((question) => ({
          id: question.id,
          text: question.text,
          options: question.options,
          selectedAnswers: selectedAnswers[question.id] || [],
          correctAnswers: question.correctAnswers || [question.correctAnswer],
          correct:
            JSON.stringify([...(selectedAnswers[question.id] || [])].sort()) ===
            JSON.stringify(
              [...(question.correctAnswers || [question.correctAnswer])].sort(),
            ),
        })),
      };
    }
    const url = lessonId
      ? `/api/quizzes/lessons/${lessonId}/review`
      : `/api/quizzes/${courseId}/review`;
    const response = await axiosClient.get<QuizReview>(url);
    return response.data;
  },
};
