import { axiosClient, QUIZ_GENERATION_TIMEOUT_MS } from "./axiosClient";
import type { QuizGenerationSource } from "./adminApi";
import {
  runQuizGenerationJob,
  type QuizGenerationJob,
} from "../utils/quizGenerationJob";

export type FlashcardType = "TERM" | "CLOZE";
export type FlashcardRating = "AGAIN" | "HARD" | "GOOD" | "EASY";
export type FlashcardStatus = "DRAFT" | "PUBLISHED";

export interface FlashcardSource {
  documentId: string;
  documentName: string;
  page?: number | null;
  section?: string | null;
}

export interface FlashcardProgress {
  dueAt: string;
  intervalDays: number;
  repetitions: number;
  lapses: number;
  lastRating: FlashcardRating;
  due: boolean;
  mastered: boolean;
}

export interface Flashcard {
  id: number;
  courseId: string;
  lessonId: string;
  lessonTitle: string;
  type: FlashcardType;
  front: string;
  back: string;
  source: FlashcardSource | null;
  progress: FlashcardProgress | null;
  /** The learner's own personal card; only these carry a version for editing. */
  mine: boolean;
  version: number | null;
}

export interface PersonalFlashcardRequest {
  type: FlashcardType;
  front: string;
  back: string;
  version?: number;
}

export interface FlashcardStats {
  total: number;
  newCount: number;
  dueCount: number;
  learningCount: number;
  masteredCount: number;
}

export interface FlashcardDeck {
  lessonId: string;
  cards: Flashcard[];
  stats: FlashcardStats;
}

export interface FlashcardSummary {
  dueTotal: number;
  courses: { courseId: string; courseName: string; due: number }[];
}

export interface FlashcardReviewQueue {
  courseId: string;
  courseName: string;
  dueTotal: number;
  cards: Flashcard[];
}

export interface AdminFlashcard {
  id: number;
  lessonId: string;
  type: FlashcardType;
  front: string;
  back: string;
  origin: "MANUAL" | "AI";
  status: FlashcardStatus;
  source: FlashcardSource | null;
  cardOrder: number;
  updatedAt: string;
  version: number;
}

export interface FlashcardRequest {
  type: FlashcardType;
  front: string;
  back: string;
  status?: FlashcardStatus;
  origin?: "MANUAL" | "AI";
  sourceDocumentId?: string | null;
  sourcePage?: number | null;
  sourceSection?: string | null;
  version?: number;
}

export interface GeneratedFlashcard {
  type: FlashcardType;
  front: string;
  back: string;
  sourceIds: string[];
}

export interface FlashcardGenerationResult {
  cards: GeneratedFlashcard[];
  sources: QuizGenerationSource[];
  warnings: string[];
}

/** The flashcard job reuses the quiz job shape, with counts in cards. */
export type FlashcardGenerationJob = Omit<
  QuizGenerationJob<FlashcardGenerationResult>,
  "requestedQuestions" | "acceptedQuestions"
> & { requestedCards: number; acceptedCards: number };

export interface FlashcardGenerationRequest {
  count: number;
  types: FlashcardType[];
}

/** Blank marker of a cloze card; the server accepts any run of 3+ underscores. */
export const CLOZE_BLANK = "____";

export const RATING_LABELS: Record<FlashcardRating, string> = {
  AGAIN: "Chưa nhớ",
  HARD: "Khó",
  GOOD: "Nhớ",
  EASY: "Dễ",
};

export const TYPE_LABELS: Record<FlashcardType, string> = {
  TERM: "Thuật ngữ",
  CLOZE: "Điền khuyết",
};

const lessonPath = (lessonId: string) =>
  `/api/admin/lessons/${encodeURIComponent(lessonId)}/flashcards`;

// Accepts both job shapes so the shared poller can drive flashcard jobs.
const asQuizJob = (job: FlashcardGenerationJob) =>
  ({
    ...job,
    requestedQuestions: job.requestedCards,
    acceptedQuestions: job.acceptedCards,
  }) as QuizGenerationJob<FlashcardGenerationResult>;

export const flashcardApi = {
  async lessonDeck(lessonId: string): Promise<FlashcardDeck> {
    const response = await axiosClient.get<FlashcardDeck>(
      `/api/lessons/${encodeURIComponent(lessonId)}/flashcards`,
    );
    return response.data;
  },

  async review(id: number, rating: FlashcardRating): Promise<Flashcard> {
    const response = await axiosClient.post<Flashcard>(
      `/api/flashcards/${id}/reviews`,
      { rating },
    );
    return response.data;
  },

  async createMine(lessonId: string, card: PersonalFlashcardRequest): Promise<Flashcard> {
    const response = await axiosClient.post<Flashcard>(
      `/api/lessons/${encodeURIComponent(lessonId)}/flashcards/mine`,
      card,
    );
    return response.data;
  },

  async updateMine(id: number, card: PersonalFlashcardRequest): Promise<Flashcard> {
    const response = await axiosClient.put<Flashcard>(`/api/flashcards/mine/${id}`, card);
    return response.data;
  },

  async removeMine(id: number): Promise<void> {
    await axiosClient.delete(`/api/flashcards/mine/${id}`);
  },

  async summary(): Promise<FlashcardSummary> {
    const response = await axiosClient.get<FlashcardSummary>(
      "/api/users/me/flashcards/summary",
    );
    return response.data;
  },

  async dueQueue(courseId: string, limit = 20): Promise<FlashcardReviewQueue> {
    const response = await axiosClient.get<FlashcardReviewQueue>(
      "/api/users/me/flashcards/due",
      { params: { courseId, limit } },
    );
    return response.data;
  },
};

export const adminFlashcardApi = {
  async list(lessonId: string): Promise<AdminFlashcard[]> {
    const response = await axiosClient.get<AdminFlashcard[]>(lessonPath(lessonId));
    return response.data;
  },

  async create(lessonId: string, card: FlashcardRequest): Promise<AdminFlashcard> {
    const response = await axiosClient.post<AdminFlashcard>(lessonPath(lessonId), card);
    return response.data;
  },

  async createBatch(lessonId: string, cards: FlashcardRequest[]): Promise<AdminFlashcard[]> {
    const response = await axiosClient.post<AdminFlashcard[]>(
      `${lessonPath(lessonId)}/batch`,
      { cards },
    );
    return response.data;
  },

  async update(lessonId: string, id: number, card: FlashcardRequest): Promise<AdminFlashcard> {
    const response = await axiosClient.put<AdminFlashcard>(`${lessonPath(lessonId)}/${id}`, card);
    return response.data;
  },

  async remove(lessonId: string, id: number): Promise<void> {
    await axiosClient.delete(`${lessonPath(lessonId)}/${id}`);
  },

  async updateStatus(lessonId: string, ids: number[], status: FlashcardStatus): Promise<AdminFlashcard[]> {
    const response = await axiosClient.put<AdminFlashcard[]>(`${lessonPath(lessonId)}/status`, { ids, status });
    return response.data;
  },

  generateWithProgress(
    lessonId: string,
    request: FlashcardGenerationRequest,
    options: {
      onProgress?: (job: QuizGenerationJob<FlashcardGenerationResult>) => void;
      signal?: AbortSignal;
    } = {},
  ): Promise<FlashcardGenerationResult> {
    return runQuizGenerationJob(
      {
        start: async (body) =>
          asQuizJob(
            (await axiosClient.post<FlashcardGenerationJob>(`${lessonPath(lessonId)}/generate/jobs`, body)).data,
          ),
        status: async (jobId) =>
          asQuizJob(
            (
              await axiosClient.get<FlashcardGenerationJob>(
                `${lessonPath(lessonId)}/generate/jobs/${encodeURIComponent(jobId)}`,
              )
            ).data,
          ),
      },
      request,
      { ...options, timeoutMs: QUIZ_GENERATION_TIMEOUT_MS },
    );
  },
};

export const apiErrorMessage = (error: unknown, fallback: string): string =>
  (error as { response?: { data?: { message?: string } } })?.response?.data?.message ||
  (error instanceof Error && error.message) ||
  fallback;

/** Short Vietnamese description of when a card comes back. */
export function formatDue(progress: FlashcardProgress | null, now = Date.now()): string {
  if (!progress) return "Thẻ mới";
  const diffMs = new Date(progress.dueAt).getTime() - now;
  if (diffMs <= 0) return "Đến hạn ôn";
  const minutes = Math.round(diffMs / 60_000);
  if (minutes < 60) return `Ôn lại sau ${Math.max(1, minutes)} phút`;
  const days = Math.round(diffMs / 86_400_000);
  if (days < 1) return "Ôn lại trong hôm nay";
  return `Ôn lại sau ${days} ngày`;
}
