import { defineStore } from "pinia";
import type { QuizQuestion } from "../types";
import { quizApi } from "../api/quizApi";
import type { QuizSubmitResponse } from "../api/quizApi";
import { useUserStore } from "./userStore";

let quizRequestId = 0;

/**
 * Unsubmitted answers live in localStorage so a reload or crash does not lose them.
 * Keys are scoped per user and quiz; the user store removes every key with this prefix on logout.
 */
export const QUIZ_DRAFT_PREFIX = "quiz-draft:";
const DRAFT_MAX_AGE_MS = 24 * 60 * 60 * 1000;
interface QuizDraft {
  sessionId: string | null;
  answers: Record<number, number[]>;
  currentIndex: number;
  savedAt: number;
}
const draftKey = (courseId: string, lessonId: string | null) =>
  `${QUIZ_DRAFT_PREFIX}${useUserStore().username ?? ""}:${courseId}:${lessonId ?? ""}`;
function readDraft(key: string): QuizDraft | null {
  try {
    const draft = JSON.parse(localStorage.getItem(key) || "null") as QuizDraft | null;
    if (!draft || typeof draft.answers !== "object" || Date.now() - draft.savedAt > DRAFT_MAX_AGE_MS) return null;
    return draft;
  } catch {
    return null;
  }
}
function writeDraft(key: string, draft: QuizDraft | null) {
  try {
    if (draft) localStorage.setItem(key, JSON.stringify(draft));
    else localStorage.removeItem(key);
  } catch {
    // Storage full or blocked: the attempt still works, it just cannot survive a reload.
  }
}

const formatSeconds = (total: number) =>
  `${Math.floor(total / 60).toString().padStart(2, "0")}:${(total % 60).toString().padStart(2, "0")}`;

export const useQuizStore = defineStore("quiz", {
  state: () => ({
    currentCourseId: null as string | null,
    currentLessonId: null as string | null,
    currentQuestionIndex: 0,
    questions: [] as QuizQuestion[],
    selectedAnswers: {} as Record<number, number[]>, // questionId -> selected option indexes
    timeElapsed: 0, // seconds
    timeLimitSeconds: null as number | null, // null = untimed quiz
    isTimeUp: false,
    sessionId: null as string | null,
    submitError: "",
    timerIntervalId: null as any | null,
    isQuizFinished: false,
    quizResult: null as QuizSubmitResponse | null,
    isLoading: false,
  }),

  getters: {
    quizQuestions(): QuizQuestion[] {
      return this.questions;
    },
    currentQuestion(): QuizQuestion | null {
      if (
        this.questions.length === 0 ||
        this.currentQuestionIndex >= this.questions.length
      )
        return null;
      return this.questions[this.currentQuestionIndex];
    },
    isLastQuestion(): boolean {
      return this.currentQuestionIndex === this.questions.length - 1;
    },
    formattedTime(): string {
      return formatSeconds(this.timeElapsed);
    },
    remainingSeconds(): number | null {
      return this.timeLimitSeconds == null
        ? null
        : Math.max(0, this.timeLimitSeconds - this.timeElapsed);
    },
    formattedRemaining(): string {
      return formatSeconds(this.remainingSeconds ?? 0);
    },
  },

  actions: {
    async startQuiz(courseId: string, lessonId?: string) {
      const current = ++quizRequestId;
      this.isLoading = true;
      this.currentCourseId = courseId;
      this.currentLessonId = lessonId || null;
      this.currentQuestionIndex = 0;
      this.selectedAnswers = {};
      this.timeElapsed = 0;
      this.timeLimitSeconds = null;
      this.isTimeUp = false;
      this.sessionId = null;
      this.submitError = "";
      this.isQuizFinished = false;
      this.quizResult = null;
      this.questions = [];

      this.stopTimer();

      try {
        const [questions, session] = await Promise.all([
          quizApi.getQuizQuestions(courseId, lessonId),
          lessonId ? quizApi.startLessonQuiz(lessonId) : Promise.resolve(null),
        ]);
        if (current !== quizRequestId) return;
        this.questions = questions;
        if (session?.timeLimitMinutes && session.remainingSeconds != null) {
          // A resumed attempt keeps the deadline the server fixed when it started.
          this.sessionId = session.sessionId;
          this.timeLimitSeconds = session.timeLimitMinutes * 60;
          this.timeElapsed = Math.max(0, this.timeLimitSeconds - session.remainingSeconds);
        }
        this.restoreDraft();
        if (this.questions.length > 0) this.startTimer();
      } catch (error) {
        console.error(`Failed to load quiz questions for ${courseId}:`, error);
        if (current === quizRequestId) this.questions = [];
      } finally {
        if (current === quizRequestId) this.isLoading = false;
      }
    },
    restoreDraft() {
      if (!this.currentCourseId) return;
      const key = draftKey(this.currentCourseId, this.currentLessonId);
      const draft = readDraft(key);
      // A timed draft belongs to one server session; answers from an expired attempt are discarded.
      if (!draft || draft.sessionId !== this.sessionId) {
        writeDraft(key, null);
        return;
      }
      const answers: Record<number, number[]> = {};
      for (const question of this.questions) {
        const saved = draft.answers[question.id];
        const valid = Array.isArray(saved)
          ? saved.filter((i) => Number.isInteger(i) && i >= 0 && i < (question.options?.length ?? 0))
          : [];
        if (valid.length) answers[question.id] = valid;
      }
      this.selectedAnswers = answers;
      this.currentQuestionIndex = Math.min(Math.max(0, draft.currentIndex | 0), this.questions.length - 1);
    },
    saveDraft() {
      if (!this.currentCourseId || !this.questions.length || this.isQuizFinished) return;
      writeDraft(draftKey(this.currentCourseId, this.currentLessonId), {
        sessionId: this.sessionId,
        answers: this.selectedAnswers,
        currentIndex: this.currentQuestionIndex,
        savedAt: Date.now(),
      });
    },
    selectAnswer(questionId: number, answerIndex: number) {
      if (this.isTimeUp) return;
      const question = this.questions.find((item) => item.id === questionId);
      if (question?.type === "MULTIPLE_CHOICE") {
        const current = this.selectedAnswers[questionId] || [];
        this.selectedAnswers[questionId] = current.includes(answerIndex)
          ? current.filter((index) => index !== answerIndex)
          : [...current, answerIndex].sort();
      } else {
        this.selectedAnswers[questionId] = [answerIndex];
      }
      this.saveDraft();
    },
    async nextQuestion() {
      if (!this.questions.length) return;
      if (this.currentQuestionIndex < this.questions.length - 1) {
        this.currentQuestionIndex++;
        this.saveDraft();
      } else {
        await this.submitQuiz();
      }
    },
    prevQuestion() {
      if (this.currentQuestionIndex > 0) {
        this.currentQuestionIndex--;
        this.saveDraft();
      }
    },
    goToQuestion(index: number) {
      if (index >= 0 && index < this.questions.length) {
        this.currentQuestionIndex = index;
        this.saveDraft();
      }
    },
    startTimer() {
      // Count from wall-clock time so a throttled background tab cannot stretch the limit.
      const startedAt = Date.now() - this.timeElapsed * 1000;
      this.timerIntervalId = setInterval(() => {
        this.timeElapsed = Math.floor((Date.now() - startedAt) / 1000);
        if (this.timeLimitSeconds != null && this.timeElapsed >= this.timeLimitSeconds) {
          this.timeElapsed = this.timeLimitSeconds;
          this.isTimeUp = true;
          void this.submitQuiz();
        }
      }, 1000);
    },
    stopTimer() {
      if (this.timerIntervalId) {
        clearInterval(this.timerIntervalId);
        this.timerIntervalId = null;
      }
    },
    async submitQuiz() {
      this.stopTimer();
      if (!this.currentCourseId || !this.questions.length || this.isLoading) return;

      this.isLoading = true;
      this.submitError = "";
      try {
        const res = await quizApi.submitQuiz(
          this.currentCourseId,
          {
            selectedAnswers: this.selectedAnswers,
            timeElapsed: this.timeElapsed,
            sessionId: this.sessionId,
          },
          this.currentLessonId || undefined,
        );

        this.quizResult = {
          score: res.score,
          total: res.total,
          percent: res.percent,
          passingScore: res.passingScore,
          passed: res.passed,
        };

        this.isQuizFinished = true;
        writeDraft(draftKey(this.currentCourseId, this.currentLessonId), null);

        // Force reload the user's progress from the API to update badges, points, and radar chart!
        const userStore = useUserStore();
        await userStore.fetchProgress();
      } catch (error) {
        console.error("Failed to submit quiz:", error);
        const response = (error as { response?: { status?: number; data?: { message?: string } } })?.response;
        this.submitError =
          response?.status === 400 && response.data?.message
            ? response.data.message
            : "Nộp bài chưa thành công. Vui lòng thử lại.";
      } finally {
        this.isLoading = false;
      }
    },
  },
});
