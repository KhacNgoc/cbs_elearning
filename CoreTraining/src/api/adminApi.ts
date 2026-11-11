import {
  validateVideoUpload,
  MAX_VIDEO_UPLOAD_BYTES,
} from "../utils/videoAccess";
import {
  axiosClient,
  QUIZ_GENERATION_TIMEOUT_MS,
} from "./axiosClient";
import type { Course, Lesson, LessonDocument, Question } from "../types";
import {
  runQuizGenerationJob,
  type QuizGenerationJob,
} from "../utils/quizGenerationJob";

/** A course picker entry: course identity plus the admin actions allowed on it. */
export interface CourseSearchOption {
  id: string;
  code?: string;
  name: string;
  status: string;
  adminActions?: Record<string, string[]>;
}

// Set from QUIZ_MAX_QUESTIONS at build time; the backend enforces the same value.
export const QUIZ_MAX_QUESTIONS =
  Number(import.meta.env.VITE_QUIZ_MAX_QUESTIONS) || 10;

const configuredVideoMaxBytes = Number(
  import.meta.env.VITE_VIDEO_MAX_UPLOAD_BYTES,
);
export const VIDEO_UPLOAD_MAX_BYTES =
  Number.isFinite(configuredVideoMaxBytes) && configuredVideoMaxBytes > 0
    ? configuredVideoMaxBytes
    : MAX_VIDEO_UPLOAD_BYTES;

const normalizeUploadedPath = (value: string): string => {
  const uploadsIndex = value.indexOf("/uploads/");
  return uploadsIndex > 0 ? value.slice(uploadsIndex) : value;
};

export interface LessonKnowledgeSource {
  documentId: string;
  pageFrom?: number | null;
  pageTo?: number | null;
  section?: string | null;
}

export interface DocumentOutline {
  documentId: string;
  pages: number[];
  /** Word sections in document order, named by heading path ("Chương 1 › 1.1 ..."). */
  sections: string[];
  excerpts: { page: number; section: string; heading?: string; excerpt: string }[];
  /** PDF headings with suggested page ranges; absent for Word or older indexes. */
  headings?: { section: string; level: number; pageFrom: number; pageTo: number }[];
}

export interface AdminUser {
  id?: number;
  allowedActions?: string[];
  username: string;
  password?: string;
  fullName: string;
  avatarColor: string;
  role: string;
  roles?: string[];
  roleNames?: Record<string, string>;
  permissions?: string[];
  unitId?: string;
  scopeUnitIds?: string[];
  status?: string;
  learnerType?: string;
  points: number;
  employeeId?: string;
  email?: string;
}

export interface EnrollmentRequest {
  id: number;
  userId: number;
  username: string;
  fullName: string;
  courseId: string;
  courseName: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedAt: string;
  reviewedAt?: string;
  reviewedBy?: string;
  rejectionReason?: string;
}

export interface CourseDocument {
  id: string;
  courseId: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: "PENDING" | "INDEXING" | "INDEXED" | "FAILED";
  indexedChunks?: number;
  errorMessage?: string;
  createdAt: string;
  indexedAt?: string;
}

export interface GeneratedQuizQuestion {
  type: "SINGLE_CHOICE" | "MULTIPLE_CHOICE";
  text: string;
  options: string[];
  correctAnswers: number[];
  explanation: string;
  sourceIds: string[];
}

export interface QuizGenerationSource {
  id: string;
  documentId: string;
  documentName: string;
  page: number;
  section: string;
}

export interface QuizGenerationResponse {
  questions: GeneratedQuizQuestion[];
  sources: QuizGenerationSource[];
  warnings: string[];
}

export interface QuizGenerationRequest {
  courseId: string;
  lessonId?: string;
  singleChoiceCount: number;
  multipleChoiceCount: number;
  optionCount: number;
  /** Unsaved drafts from the previous run, so the next draft does not repeat them. */
  excludeQuestions?: string[];
}

export type QuizGenerationJobSnapshot = QuizGenerationJob<QuizGenerationResponse>;

export const adminApi = {
  async reviewEnrollmentRequest(
    id: number,
    approve: boolean,
    rejectionReason?: string,
  ): Promise<EnrollmentRequest> {
    const response = await axiosClient.put<EnrollmentRequest>(
      `/api/admin/enrollment-requests/${id}`,
      { approve, rejectionReason },
    );
    return response.data;
  },

  async uploadCourseDocument(
    courseId: string,
    file: File,
  ): Promise<CourseDocument> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await axiosClient.post<CourseDocument>(
      `/api/admin/courses/${courseId}/documents`,
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
      },
    );
    return response.data;
  },

  async getCourseDocuments(
    courseId: string,
    page?: string,
  ): Promise<CourseDocument[]> {
    const response = await axiosClient.get<CourseDocument[]>(
      `/api/admin/courses/${courseId}/documents`,
      { params: { page } },
    );
    return response.data;
  },

  /** `page` reads the courses under that admin page's permission (as `/api/admin/courses?page=`). */
  async searchCourses(
    search?: string,
    limit = 50,
    page?: "exams" | "documents",
  ): Promise<CourseSearchOption[]> {
    const response = await axiosClient.get<CourseSearchOption[]>(
      "/api/admin/courses/search",
      { params: { search, limit, page } },
    );
    return response.data;
  },

  async getCourseDocumentsPaged(
    courseId: string,
    params?: { search?: string; status?: string; page?: number; size?: number },
  ): Promise<{
    items: CourseDocument[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
    stats: { total: number; indexed: number; indexing: number; failed: number };
  }> {
    const response = await axiosClient.get(
      `/api/admin/courses/${encodeURIComponent(courseId)}/documents/paged`,
      { params },
    );
    return response.data;
  },

  async deleteCourseDocument(
    courseId: string,
    documentId: string,
  ): Promise<void> {
    await axiosClient.delete(
      `/api/admin/courses/${encodeURIComponent(courseId)}/documents/${encodeURIComponent(documentId)}`,
    );
  },

  async getLessonKnowledgeSources(
    lessonId: string,
    page?: string,
  ): Promise<LessonKnowledgeSource[]> {
    return (
      await axiosClient.get<LessonKnowledgeSource[]>(
        `/api/admin/lessons/${encodeURIComponent(lessonId)}/knowledge-sources`,
        { params: { page } },
      )
    ).data;
  },

  async saveLessonKnowledgeSources(
    lessonId: string,
    sources: LessonKnowledgeSource[],
    page?: string,
  ): Promise<LessonKnowledgeSource[]> {
    return (
      await axiosClient.put<LessonKnowledgeSource[]>(
        `/api/admin/lessons/${encodeURIComponent(lessonId)}/knowledge-sources`,
        sources,
        { params: { page } },
      )
    ).data;
  },

  async getDocumentOutline(
    courseId: string,
    documentId: string,
    page?: string,
  ): Promise<DocumentOutline> {
    return (
      await axiosClient.get<DocumentOutline>(
        `/api/admin/courses/${encodeURIComponent(courseId)}/documents/${encodeURIComponent(documentId)}/outline`,
        { params: { page } },
      )
    ).data;
  },

  // ==========================================
  // Course Management
  // ==========================================
  async createCourse(course: Course): Promise<Course> {
    const response = await axiosClient.post<Course>(
      "/api/admin/courses",
      course,
    );
    return response.data;
  },

  async updateCourse(id: string, course: Course): Promise<Course> {
    const response = await axiosClient.put<Course>(
      `/api/admin/courses/${id}`,
      course,
    );
    return response.data;
  },

  async deleteCourse(id: string): Promise<void> {
    await axiosClient.delete(`/api/admin/courses/${id}`);
  },

  // ==========================================
  // Lesson Management
  // ==========================================
  async uploadDocument(
    file: File,
    onProgress?: (percent: number) => void,
  ): Promise<LessonDocument> {

    const formData = new FormData();
    formData.append("file", file);
    const response = await axiosClient.post<LessonDocument>(
      "/api/admin/documents/upload",
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: 120_000,
        onUploadProgress: (event) => {
          const total = event.total || file.size;
          onProgress?.(Math.min(100, Math.round((event.loaded / total) * 100)));
        },
      },
    );
    return {
      ...response.data,
      fileUrl: normalizeUploadedPath(response.data.fileUrl),
    };
  },

  async uploadVideo(
    file: File,
    onProgress?: (percent: number) => void,
  ): Promise<string> {
    const validationError = validateVideoUpload(file, VIDEO_UPLOAD_MAX_BYTES);
    if (validationError) throw new Error(validationError);

    const formData = new FormData();
    formData.append("file", file);
    const response = await axiosClient.post<{ videoUrl: string }>(
      "/api/admin/videos/upload",
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: (() => {
          const configured = Number(
            import.meta.env.VITE_VIDEO_UPLOAD_TIMEOUT_MS,
          );
          return Number.isFinite(configured) && configured > 0
            ? configured
            : 1_800_000;
        })(),
        onUploadProgress: (event) => {
          const total = event.total || file.size;
          onProgress?.(Math.min(100, Math.round((event.loaded / total) * 100)));
        },
      },
    );
    return normalizeUploadedPath(response.data.videoUrl);
  },

  async createLesson(
    lesson: Omit<Lesson, "comments"> & { courseId: string },
  ): Promise<Lesson> {
    const response = await axiosClient.post<Lesson>(
      "/api/admin/lessons",
      lesson,
    );
    return response.data;
  },

  async updateLesson(
    id: string,
    lesson: Omit<Lesson, "comments"> & { courseId: string },
  ): Promise<Lesson> {
    const response = await axiosClient.put<Lesson>(
      `/api/admin/lessons/${id}`,
      lesson,
    );
    return response.data;
  },

  // ==========================================
  // Question Management
  // ==========================================
  async getQuizQuestions(
    courseId: string,
    lessonId?: string,
  ): Promise<Question[]> {
    const response = await axiosClient.get<Question[]>(
      "/api/admin/quizzes/questions",
      {
        params: { courseId, lessonId },
      },
    );
    return response.data;
  },

  async generateQuizQuestions(request: {
    courseId: string;
    lessonId?: string;
    singleChoiceCount: number;
    multipleChoiceCount: number;
    optionCount: number;
  }): Promise<QuizGenerationResponse> {
    const response = await axiosClient.post<QuizGenerationResponse>(
      "/api/admin/quizzes/generate",
      request,
      { timeout: QUIZ_GENERATION_TIMEOUT_MS },
    );
    return response.data;
  },

  async startQuizGenerationJob(
    request: QuizGenerationRequest,
  ): Promise<QuizGenerationJobSnapshot> {
    const response = await axiosClient.post<QuizGenerationJobSnapshot>(
      "/api/admin/quizzes/generate/jobs",
      request,
    );
    return response.data;
  },

  async getQuizGenerationJob(jobId: string): Promise<QuizGenerationJobSnapshot> {
    const response = await axiosClient.get<QuizGenerationJobSnapshot>(
      `/api/admin/quizzes/generate/jobs/${encodeURIComponent(jobId)}`,
    );
    return response.data;
  },

  generateQuizWithProgress(
    request: QuizGenerationRequest,
    options: {
      onProgress?: (job: QuizGenerationJobSnapshot) => void;
      signal?: AbortSignal;
    } = {},
  ): Promise<QuizGenerationResponse> {
    return runQuizGenerationJob(
      {
        start: (body) => adminApi.startQuizGenerationJob(body),
        status: (jobId) => adminApi.getQuizGenerationJob(jobId),
      },
      request,
      { ...options, timeoutMs: QUIZ_GENERATION_TIMEOUT_MS },
    );
  },

  async createQuestion(question: Omit<Question, "id">): Promise<Question> {
    const response = await axiosClient.post<Question>(
      "/api/admin/questions",
      question,
    );
    return response.data;
  },

  async updateQuestion(id: number, question: Question): Promise<Question> {
    const response = await axiosClient.put<Question>(
      `/api/admin/questions/${id}`,
      question,
    );
    return response.data;
  },

  async deleteQuestion(id: number): Promise<void> {
    await axiosClient.delete(`/api/admin/questions/${id}`);
  },

  async updateQuizSettings(
    courseId: string,
    lessonId: string | undefined,
    passingScore: number,
    requiredForProgress = true,
    timeLimitMinutes: number | null = null,
  ): Promise<void> {
    await axiosClient.put("/api/admin/quizzes/settings", {
      courseId,
      lessonId,
      passingScore,
      requiredForProgress,
      timeLimitMinutes,
    });
  },

  async deleteQuiz(courseId: string, lessonId?: string): Promise<void> {
    await axiosClient.delete("/api/admin/quizzes", {
      params: { courseId, lessonId },
    });
  },

  async importUsersExcel(file: File): Promise<string> {
    const formData = new FormData();
    formData.append("file", file);
    const response = await axiosClient.post<string>(
      "/api/admin/users/upload",
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
      },
    );
    return response.data;
  },

  async downloadUserTemplate(): Promise<Blob> {
    const response = await axiosClient.get<Blob>("/api/admin/users/template", {
      responseType: "blob",
    });
    return response.data;
  },
};
