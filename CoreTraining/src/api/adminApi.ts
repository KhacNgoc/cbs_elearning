import { validateVideoUpload, MAX_VIDEO_UPLOAD_BYTES } from "../utils/videoAccess";
import { axiosClient, isMockApi, mockDelay, QUIZ_GENERATION_TIMEOUT_MS } from "./axiosClient";
import type { Course, Lesson, LessonDocument, Question } from "../types";
import { mockDbCourses, setMockDbCourses } from "./courseApi";
import { mockDbQuestions, setMockDbQuestions } from "./quizApi";

const configuredVideoMaxBytes = Number(import.meta.env.VITE_VIDEO_MAX_UPLOAD_BYTES);
export const VIDEO_UPLOAD_MAX_BYTES = Number.isFinite(configuredVideoMaxBytes) && configuredVideoMaxBytes > 0
  ? configuredVideoMaxBytes : MAX_VIDEO_UPLOAD_BYTES;

const normalizeUploadedPath = (value: string): string => {
  const uploadsIndex = value.indexOf("/uploads/");
  return uploadsIndex > 0 ? value.slice(uploadsIndex) : value;
};

const getDocumentType = (fileName: string): LessonDocument["documentType"] => {
  const extension = fileName.split(".").pop()?.toLowerCase();
  if (extension === "pdf") return "PDF";
  if (extension === "ppt" || extension === "pptx") return "POWERPOINT";
  return "WORD";
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
  sections: string[];
  excerpts: { page: number; section: string; excerpt: string }[];
}

export interface AdminUser {
  id?: number;
  username: string;
  password?: string;
  fullName: string;
  avatarColor: string;
  role: string;
  points: number;
  userGroupId?: string;
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

// In mock mode, we seed the list of users from the default mock user data
let mockDbUsers: AdminUser[] = [
  {
    id: 1,
    username: "admin",
    fullName: "Quản trị hệ thống",
    avatarColor: "#EF4444",
    role: "ROLE_ADMIN",
    points: 0,
  },
  {
    id: 2,
    username: "quantrh",
    fullName: "Quản Trị Hệ Thống (Bạn)",
    avatarColor: "#005B41",
    role: "ROLE_USER",
    points: 1850,
  },
  {
    id: 3,
    username: "nguyenvana",
    fullName: "Nguyễn Văn An",
    avatarColor: "#4F46E5",
    role: "ROLE_USER",
    points: 2850,
  },
  {
    id: 4,
    username: "tranb",
    fullName: "Trần Thị Bình",
    avatarColor: "#10B981",
    role: "ROLE_USER",
    points: 2600,
  },
  {
    id: 5,
    username: "lephuc",
    fullName: "Lê Hoàng Phúc",
    avatarColor: "#F59E0B",
    role: "ROLE_USER",
    points: 2450,
  },
  {
    id: 6,
    username: "phamduc",
    fullName: "Phạm Minh Đức",
    avatarColor: "#EF4444",
    role: "ROLE_USER",
    points: 2100,
  },
  {
    id: 7,
    username: "hoangyen",
    fullName: "Hoàng Kim Yến",
    avatarColor: "#EC4899",
    role: "ROLE_USER",
    points: 1500,
  },
  {
    id: 8,
    username: "vuongminh",
    fullName: "Vương Tuấn Minh",
    avatarColor: "#8B5CF6",
    role: "ROLE_USER",
    points: 1200,
  },
];

export const adminApi = {
  async getEnrollmentRequests(): Promise<EnrollmentRequest[]> {
    if (isMockApi()) {
      const pending: string[] = JSON.parse(
        localStorage.getItem("mockPendingCourses") || "[]",
      );
      return pending.map((courseId, index) => ({
        id: index + 1,
        userId: 2,
        username: localStorage.getItem("username") || "quantrh",
        fullName: localStorage.getItem("fullName") || "Học viên",
        courseId,
        courseName:
          mockDbCourses.find((course) => course.id === courseId)?.name || courseId,
        status: "PENDING",
        requestedAt: new Date().toISOString(),
      }));
    }
    const response = await axiosClient.get<EnrollmentRequest[]>(
      "/api/admin/enrollment-requests",
    );
    return response.data;
  },

  async reviewEnrollmentRequest(
    id: number,
    approve: boolean,
    rejectionReason?: string,
  ): Promise<EnrollmentRequest> {
    if (isMockApi()) {
      const requests = await this.getEnrollmentRequests();
      const request = requests.find((item) => item.id === id);
      if (!request) throw new Error("Enrollment request not found");
      const pending: string[] = JSON.parse(
        localStorage.getItem("mockPendingCourses") || "[]",
      );
      localStorage.setItem(
        "mockPendingCourses",
        JSON.stringify(pending.filter((item) => item !== request.courseId)),
      );
      if (approve) {
        const registered: string[] = JSON.parse(
          localStorage.getItem("mockRegisteredCourses") || "[]",
        );
        if (!registered.includes(request.courseId))
          registered.push(request.courseId);
        localStorage.setItem(
          "mockRegisteredCourses",
          JSON.stringify(registered),
        );
      }
      return {
        ...request,
        status: approve ? "APPROVED" : "REJECTED",
        reviewedAt: new Date().toISOString(),
        rejectionReason: approve ? undefined : rejectionReason,
      };
    }
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

  async getCourseDocuments(courseId: string): Promise<CourseDocument[]> {
    const response = await axiosClient.get<CourseDocument[]>(
      `/api/admin/courses/${courseId}/documents`,
    );
    return response.data;
  },

  async deleteCourseDocument(courseId: string, documentId: string): Promise<void> {
    await axiosClient.delete(
      `/api/admin/courses/${encodeURIComponent(courseId)}/documents/${encodeURIComponent(documentId)}`,
    );
  },

  async getLessonKnowledgeSources(lessonId: string): Promise<LessonKnowledgeSource[]> {
    return (await axiosClient.get<LessonKnowledgeSource[]>(`/api/admin/lessons/${encodeURIComponent(lessonId)}/knowledge-sources`)).data;
  },

  async saveLessonKnowledgeSources(lessonId: string, sources: LessonKnowledgeSource[]): Promise<LessonKnowledgeSource[]> {
    return (await axiosClient.put<LessonKnowledgeSource[]>(`/api/admin/lessons/${encodeURIComponent(lessonId)}/knowledge-sources`, sources)).data;
  },

  async getDocumentOutline(courseId: string, documentId: string): Promise<DocumentOutline> {
    return (await axiosClient.get<DocumentOutline>(`/api/admin/courses/${encodeURIComponent(courseId)}/documents/${encodeURIComponent(documentId)}/outline`)).data;
  },

  // ==========================================
  // User Management
  // ==========================================
  async getAllUsers(): Promise<AdminUser[]> {
    if (isMockApi()) {
      await mockDelay(300);
      return [...mockDbUsers];
    }
    const response = await axiosClient.get<AdminUser[]>("/api/admin/users");
    return response.data;
  },

  async createUser(user: AdminUser): Promise<AdminUser> {
    if (isMockApi()) {
      await mockDelay(300);
      const newUser: AdminUser = {
        ...user,
        id: Math.max(...mockDbUsers.map((u) => u.id || 0)) + 1,
        points: user.points || 0,
      };
      mockDbUsers.push(newUser);
      return newUser;
    }
    const response = await axiosClient.post<AdminUser>(
      "/api/admin/users",
      user,
    );
    return response.data;
  },

  async updateUser(id: number, user: AdminUser): Promise<AdminUser> {
    if (isMockApi()) {
      await mockDelay(300);
      const index = mockDbUsers.findIndex((u) => u.id === id);
      if (index === -1) throw new Error(`User not found with id: ${id}`);

      const updatedUser = {
        ...mockDbUsers[index],
        fullName: user.fullName,
        avatarColor: user.avatarColor,
        role: user.role,
        points: user.points,
      };
      if (user.password) {
        updatedUser.password = user.password;
      }
      mockDbUsers[index] = updatedUser;
      return updatedUser;
    }
    const response = await axiosClient.put<AdminUser>(
      `/api/admin/users/${id}`,
      user,
    );
    return response.data;
  },

  async deleteUser(id: number): Promise<void> {
    if (isMockApi()) {
      await mockDelay(300);
      mockDbUsers = mockDbUsers.filter((u) => u.id !== id);
      return;
    }
    await axiosClient.delete(`/api/admin/users/${id}`);
  },

  // ==========================================
  // Course Management
  // ==========================================
  async createCourse(course: Course): Promise<Course> {
    if (isMockApi()) {
      await mockDelay(300);
      const newCourse: Course = { ...course, lessons: [] };
      mockDbCourses.push(newCourse);
      setMockDbCourses([...mockDbCourses]);
      return newCourse;
    }
    const response = await axiosClient.post<Course>("/api/admin/courses", course);
    return response.data;
  },

  async updateCourse(id: string, course: Course): Promise<Course> {
    if (isMockApi()) {
      await mockDelay(300);
      const index = mockDbCourses.findIndex((t) => t.id === id);
      if (index === -1) throw new Error(`Course not found with id: ${id}`);

      mockDbCourses[index] = {
        ...mockDbCourses[index],
        name: course.name,
        iconName: course.iconName,
      };
      setMockDbCourses([...mockDbCourses]);
      return mockDbCourses[index];
    }
    const response = await axiosClient.put<Course>(
      `/api/admin/courses/${id}`,
      course,
    );
    return response.data;
  },

  async deleteCourse(id: string): Promise<void> {
    if (isMockApi()) {
      await mockDelay(300);
      const filtered = mockDbCourses.filter((t) => t.id !== id);
      setMockDbCourses(filtered);
      return;
    }
    await axiosClient.delete(`/api/admin/courses/${id}`);
  },

  // ==========================================
  // Lesson Management
  // ==========================================
  async uploadDocument(file: File): Promise<LessonDocument> {
    if (isMockApi()) {
      return {
        name: file.name.replace(/\.[^.]+$/, ""),
        documentType: getDocumentType(file.name),
        fileUrl: URL.createObjectURL(file),
        originalFileName: file.name,
        fileSize: file.size,
      };
    }

    const formData = new FormData();
    formData.append("file", file);
    const response = await axiosClient.post<LessonDocument>(
      "/api/admin/documents/upload",
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: 120_000,
      },
    );
    return {
      ...response.data,
      fileUrl: normalizeUploadedPath(response.data.fileUrl),
    };
  },

  async uploadVideo(file: File, onProgress?: (percent: number) => void): Promise<string> {
    const validationError = validateVideoUpload(file, VIDEO_UPLOAD_MAX_BYTES);
    if (validationError) throw new Error(validationError);
    if (isMockApi()) {
      onProgress?.(100);
      return URL.createObjectURL(file);
    }

    const formData = new FormData();
    formData.append("file", file);
    const response = await axiosClient.post<{ videoUrl: string }>(
      "/api/admin/videos/upload",
      formData,
      {
        headers: { "Content-Type": "multipart/form-data" },
        timeout: (() => {
          const configured = Number(import.meta.env.VITE_VIDEO_UPLOAD_TIMEOUT_MS);
          return Number.isFinite(configured) && configured > 0 ? configured : 1_800_000;
        })(),
        onUploadProgress: event => {
          const total = event.total || file.size;
          onProgress?.(Math.min(100, Math.round(event.loaded / total * 100)));
        },
      },
    );
    return normalizeUploadedPath(response.data.videoUrl);
  },

  async createLesson(
    lesson: Omit<Lesson, "comments"> & { courseId: string },
  ): Promise<Lesson> {
    if (isMockApi()) {
      await mockDelay(300);
      const newLesson: Lesson = {
        ...lesson,
        comments: [],
        procedureSteps: lesson.procedureSteps || [],
      };
      const course = mockDbCourses.find((t) => t.id === lesson.courseId);
      if (course) {
        course.lessons.push(newLesson);
        setMockDbCourses([...mockDbCourses]);
      }
      return newLesson;
    }
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
    if (isMockApi()) {
      await mockDelay(300);
      let foundLesson: Lesson | null = null;

      // Remove from old course if courseId changed, or just update in place
      for (const t of mockDbCourses) {
        const idx = t.lessons.findIndex((l) => l.id === id);
        if (idx !== -1) {
          foundLesson = t.lessons[idx];
          if (t.id !== lesson.courseId) {
            t.lessons.splice(idx, 1); // remove
          }
          break;
        }
      }

      if (!foundLesson) throw new Error(`Lesson not found with id: ${id}`);

      const targetLesson = foundLesson as Lesson;

      const updatedLesson: Lesson = {
        ...targetLesson,
        title: lesson.title,
        courseId: lesson.courseId,
        hint: lesson.hint,
        videoUrl: lesson.videoUrl,
        videoSource: lesson.videoSource,
        documents: lesson.documents || [],
        procedureSteps: lesson.procedureSteps || [],
      };

      if (targetLesson.courseId !== lesson.courseId) {
        const destCourse = mockDbCourses.find((t) => t.id === lesson.courseId);
        if (destCourse) {
          destCourse.lessons.push(updatedLesson);
        }
      } else {
        for (const t of mockDbCourses) {
          const idx = t.lessons.findIndex((l) => l.id === id);
          if (idx !== -1) {
            t.lessons[idx] = updatedLesson;
            break;
          }
        }
      }

      setMockDbCourses([...mockDbCourses]);
      return updatedLesson;
    }
    const response = await axiosClient.put<Lesson>(
      `/api/admin/lessons/${id}`,
      lesson,
    );
    return response.data;
  },

  async deleteLesson(id: string): Promise<void> {
    if (isMockApi()) {
      await mockDelay(300);
      mockDbCourses.forEach((t) => {
        t.lessons = t.lessons.filter((l) => l.id !== id);
      });
      setMockDbCourses([...mockDbCourses]);
      return;
    }
    await axiosClient.delete(`/api/admin/lessons/${id}`);
  },

  async reorderLessons(
    courseId: string,
    lessonIds: string[],
  ): Promise<Lesson[]> {
    if (isMockApi()) {
      await mockDelay(200);
      const course = mockDbCourses.find((item) => item.id === courseId);
      if (!course) throw new Error(`Course not found with id: ${courseId}`);
      const lessonsById = new Map(
        course.lessons.map((lesson) => [lesson.id, lesson]),
      );
      course.lessons = lessonIds.map((id, index) => ({
        ...lessonsById.get(id)!,
        lessonOrder: index,
      }));
      setMockDbCourses([...mockDbCourses]);
      return course.lessons;
    }
    const response = await axiosClient.put<Lesson[]>(
      `/api/admin/courses/${encodeURIComponent(courseId)}/lessons/order`,
      lessonIds,
    );
    return response.data;
  },

  // ==========================================
  // Question Management
  // ==========================================
  async getQuizQuestions(courseId: string, lessonId?: string): Promise<Question[]> {
    if (isMockApi()) {
      await mockDelay(200);
      return mockDbQuestions.filter(question =>
        lessonId ? question.lessonId === lessonId : question.courseId === courseId && !question.lessonId,
      );
    }
    const response = await axiosClient.get<Question[]>("/api/admin/quizzes/questions", {
      params: { courseId, lessonId },
    });
    return response.data;
  },

  async generateQuizQuestions(request: {
    courseId: string;
    lessonId?: string;
    singleChoiceCount: number;
    multipleChoiceCount: number;
    optionCount: number;
  }): Promise<QuizGenerationResponse> {
    if (isMockApi()) {
      await mockDelay(500);
      const sourceQuestions = mockDbQuestions.filter(question =>
        request.lessonId
          ? question.lessonId === request.lessonId
          : question.courseId === request.courseId && !question.lessonId,
      ).slice(0, request.singleChoiceCount + request.multipleChoiceCount);
      return {
        questions: sourceQuestions.map(question => ({
          type: question.type || "SINGLE_CHOICE",
          text: question.text,
          options: question.options,
          correctAnswers: question.correctAnswers || [question.correctAnswer],
          explanation: "Câu hỏi mẫu trong chế độ mock.",
          sourceIds: [],
        })),
        sources: [],
        warnings: sourceQuestions.length ? [] : ["Không có dữ liệu mẫu phù hợp."],
      };
    }
    const response = await axiosClient.post<QuizGenerationResponse>(
      "/api/admin/quizzes/generate",
      request,
      { timeout: QUIZ_GENERATION_TIMEOUT_MS },
    );
    return response.data;
  },

  async createQuestion(question: Omit<Question, "id">): Promise<Question> {
    if (isMockApi()) {
      await mockDelay(300);
      const newQuestion: Question = {
        ...question,
        id: Math.max(...mockDbQuestions.map((q) => q.id || 0)) + 1,
      };
      mockDbQuestions.push(newQuestion);
      setMockDbQuestions([...mockDbQuestions]);
      if (question.lessonId) {
        const lesson = mockDbCourses
          .flatMap((course) => course.lessons)
          .find((item) => item.id === question.lessonId);
        if (lesson) lesson.hasQuiz = true;
      } else {
        const course = mockDbCourses.find((item) => item.id === question.courseId);
        if (course) course.hasQuiz = true;
      }
      setMockDbCourses([...mockDbCourses]);
      return newQuestion;
    }
    const response = await axiosClient.post<Question>(
      "/api/admin/questions",
      question,
    );
    return response.data;
  },

  async updateQuestion(id: number, question: Question): Promise<Question> {
    if (isMockApi()) {
      await mockDelay(300);
      const index = mockDbQuestions.findIndex((q) => q.id === id);
      if (index === -1) throw new Error(`Question not found with id: ${id}`);

      mockDbQuestions[index] = {
        ...mockDbQuestions[index],
        text: question.text,
        options: question.options,
        correctAnswer: question.correctAnswer,
        correctAnswers: question.correctAnswers,
        type: question.type,
        courseId: question.courseId,
        lessonId: question.lessonId,
      };
      setMockDbQuestions([...mockDbQuestions]);
      return mockDbQuestions[index];
    }
    const response = await axiosClient.put<Question>(
      `/api/admin/questions/${id}`,
      question,
    );
    return response.data;
  },

  async deleteQuestion(id: number): Promise<void> {
    if (isMockApi()) {
      await mockDelay(300);
      const deletedQuestion = mockDbQuestions.find((q) => q.id === id);
      const filtered = mockDbQuestions.filter((q) => q.id !== id);
      setMockDbQuestions(filtered);
      if (deletedQuestion?.lessonId) {
        const lesson = mockDbCourses
          .flatMap((course) => course.lessons)
          .find((item) => item.id === deletedQuestion.lessonId);
        if (lesson)
          lesson.hasQuiz = filtered.some(
            (question) => question.lessonId === deletedQuestion.lessonId,
          );
      } else if (deletedQuestion) {
        const course = mockDbCourses.find(
          (item) => item.id === deletedQuestion.courseId,
        );
        if (course)
          course.hasQuiz = filtered.some(
            (question) =>
              question.courseId === deletedQuestion.courseId &&
              !question.lessonId,
          );
      }
      setMockDbCourses([...mockDbCourses]);
      return;
    }
    await axiosClient.delete(`/api/admin/questions/${id}`);
  },

  async updateQuizSettings(
    courseId: string,
    lessonId: string | undefined,
    passingScore: number,
    requiredForProgress = true,
  ): Promise<void> {
    if (isMockApi()) {
      await mockDelay(200);
      if (lessonId) {
        const lesson = mockDbCourses
          .flatMap((course) => course.lessons)
          .find((item) => item.id === lessonId);
        if (lesson) { lesson.quizPassingScore = passingScore; lesson.quizRequired = requiredForProgress; lesson.hasQuiz = true; }
      } else {
        const course = mockDbCourses.find((item) => item.id === courseId);
        if (course) course.quizPassingScore = passingScore;
      }
      setMockDbCourses([...mockDbCourses]);
      return;
    }
    await axiosClient.put("/api/admin/quizzes/settings", {
      courseId,
      lessonId,
      passingScore,
      requiredForProgress,
    });
  },

  async deleteQuiz(courseId: string, lessonId?: string): Promise<void> {
    if (isMockApi()) {
      await mockDelay(250);
      setMockDbQuestions(
        mockDbQuestions.filter((question) =>
          lessonId
            ? question.lessonId !== lessonId
            : !(question.courseId === courseId && !question.lessonId),
        ),
      );
      if (lessonId) {
        const lesson = mockDbCourses
          .flatMap((course) => course.lessons)
          .find((item) => item.id === lessonId);
        if (lesson) lesson.hasQuiz = false;
      } else {
        const course = mockDbCourses.find((item) => item.id === courseId);
        if (course) course.hasQuiz = false;
      }
      setMockDbCourses([...mockDbCourses]);
      return;
    }
    await axiosClient.delete('/api/admin/quizzes', { params: { courseId, lessonId } });
  },

  async importUsersExcel(file: File): Promise<string> {
    if (isMockApi()) {
      await mockDelay(300);
      return 'Mock Excel upload successful!';
    }
    const formData = new FormData();
    formData.append('file', file);
    const response = await axiosClient.post<string>('/api/admin/users/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    return response.data;
  },

  async downloadUserTemplate(): Promise<Blob> {
    if (isMockApi()) {
      await mockDelay(300);
      return new Blob(['Mock Excel Content'], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
      });
    }
    const response = await axiosClient.get<Blob>('/api/admin/users/template', {
      responseType: 'blob'
    });
    return response.data;
  }
};
