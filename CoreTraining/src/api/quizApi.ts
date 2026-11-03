import { axiosClient } from "./axiosClient";
import type { QuizQuestion } from "../types";

export interface QuizSubmitRequest {
  selectedAnswers: Record<string, number[]>;
  timeElapsed: number;
  sessionId?: string | null;
}

/** Server-side attempt clock; every field is null for an untimed quiz. */
export interface QuizSession {
  sessionId: string | null;
  timeLimitMinutes: number | null;
  remainingSeconds: number | null;
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
    const url = lessonId
      ? `/api/quizzes/lessons/${lessonId}/questions`
      : `/api/quizzes/${courseId}/questions`;
    const response = await axiosClient.get<QuizQuestion[]>(url);
    return response.data;
  },

  async startLessonQuiz(lessonId: string): Promise<QuizSession> {
    const response = await axiosClient.post<QuizSession>(
      `/api/quizzes/lessons/${lessonId}/start`,
    );
    return response.data;
  },

  async submitQuiz(
    courseId: string,
    data: QuizSubmitRequest,
    lessonId?: string,
  ): Promise<QuizSubmitResponse> {

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
    const url = lessonId
      ? `/api/quizzes/lessons/${lessonId}/review`
      : `/api/quizzes/${courseId}/review`;
    const response = await axiosClient.get<QuizReview>(url);
    return response.data;
  },
};
