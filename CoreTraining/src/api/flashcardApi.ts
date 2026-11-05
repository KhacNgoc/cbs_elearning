import { axiosClient, QUIZ_GENERATION_TIMEOUT_MS } from "./axiosClient";
import type { QuizGenerationSource } from "./adminApi";
import {
  runQuizGenerationJob,
  type QuizGenerationJob,
} from "../utils/quizGenerationJob";

export type FlashcardType = "TERM" | "CLOZE";
export type FlashcardStatus = "DRAFT" | "PUBLISHED";

export interface FlashcardSource {
  documentId: string;
  documentName: string;
  page?: number | null;
  section?: string | null;
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

export interface FlashcardDeck {
  lessonId: string;
  cards: Flashcard[];
}

export interface FlashcardLibraryLesson {
  id: string;
  title: string;
  total: number;
  mine: number;
}

export interface FlashcardLibraryCourse {
  id: string;
  name: string;
  lessons: FlashcardLibraryLesson[];
}

export interface FlashcardLibrary {
  courses: FlashcardLibraryCourse[];
  nextCursor: string | null;
}

/** cardIds null means the whole lesson, including cards added to it later. */
export interface FlashcardPlanItem {
  lessonId: string;
  cardIds: number[] | null;
}

export interface FlashcardPlan {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  /** ISO weekdays: 1 is Monday, 7 is Sunday. */
  weekdays: number[];
  /** HH:mm in Vietnam time; null means no reminders. */
  reminderTime: string | null;
  active: boolean;
  nextReminderAt: string | null;
  items: FlashcardPlanItem[];
  version: number;
}

export interface FlashcardPlanRequest {
  name: string;
  startDate: string;
  endDate: string;
  weekdays: number[];
  reminderTime: string | null;
  active: boolean;
  /** Left out on an update to keep the plan's cards. */
  items?: FlashcardPlanItem[];
  version?: number;
}

export interface FlashcardPlanSession {
  planId: number;
  name: string;
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

  async library(after?: string): Promise<FlashcardLibrary> {
    const response = await axiosClient.get<FlashcardLibrary>("/api/users/me/flashcards/library", {
      params: after ? { after } : undefined,
    });
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
};

const PLANS_PATH = "/api/users/me/flashcard-plans";

export const flashcardPlanApi = {
  async list(): Promise<FlashcardPlan[]> {
    return (await axiosClient.get<FlashcardPlan[]>(PLANS_PATH)).data;
  },

  async create(plan: FlashcardPlanRequest): Promise<FlashcardPlan> {
    return (await axiosClient.post<FlashcardPlan>(PLANS_PATH, plan)).data;
  },

  async update(id: number, plan: FlashcardPlanRequest): Promise<FlashcardPlan> {
    return (await axiosClient.put<FlashcardPlan>(`${PLANS_PATH}/${id}`, plan)).data;
  },

  async remove(id: number): Promise<void> {
    await axiosClient.delete(`${PLANS_PATH}/${id}`);
  },

  async session(id: number): Promise<FlashcardPlanSession> {
    return (await axiosClient.get<FlashcardPlanSession>(`${PLANS_PATH}/${id}/cards`)).data;
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

