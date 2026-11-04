import { axiosClient } from "./axiosClient";
import type {
  Course,
  Lesson,
  Comment,
  ReplyPage,
  UserProgress,
  VideoNote,
  LessonProgress,
  LessonProgressRequest,
} from "../types";

export const courseApi = {
  async getAllCourses(): Promise<Course[]> {
    const response = await axiosClient.get<Course[]>("/api/courses");
    return response.data;
  },

  async getCourseById(id: string): Promise<Course> {
    const response = await axiosClient.get<Course>(`/api/courses/${id}`);
    return response.data;
  },

  async getLessonById(id: string): Promise<Lesson> {
    const response = await axiosClient.get<Lesson>(`/api/lessons/${id}`);
    return response.data;
  },

  async getComments(lessonId: string, page = 0, size = 20): Promise<{
    content: Comment[]; page: number; last: boolean; totalElements: number;
  }> {
    const response = await axiosClient.get(`/api/lessons/${lessonId}/comments`, {
      params: { page, size },
    });
    return response.data;
  },

  async getReplies(lessonId: string, rootCommentId: number, after?: string | null): Promise<ReplyPage> {
    const response = await axiosClient.get<ReplyPage>(`/api/lessons/${lessonId}/comments/${rootCommentId}/replies`, {
      params: after ? { after } : {},
    });
    return response.data;
  },

  async addComment(
    lessonId: string,
    rating: number | null,
    content: string,
  ): Promise<Comment> {
    const response = await axiosClient.post<Comment>(
      `/api/lessons/${lessonId}/comments`,
      {
        rating,
        content,
      },
    );
    return response.data;
  },

  async replyToComment(
    lessonId: string,
    rootCommentId: number,
    replyToCommentId: number,
    content: string,
  ): Promise<Comment> {
    const response = await axiosClient.post<Comment>(
      `/api/lessons/${lessonId}/comments/${rootCommentId}/replies`,
      { content, replyToCommentId },
    );
    return response.data;
  },

  async deleteComment(lessonId: string, commentId: number): Promise<void> {
    await axiosClient.delete(`/api/lessons/${lessonId}/comments/${commentId}`);
  },

  async getVideoNotes(lessonId: string): Promise<VideoNote[]> {
    const response = await axiosClient.get<VideoNote[]>(
      `/api/lessons/${lessonId}/notes`,
    );
    return response.data;
  },

  async createVideoNote(
    lessonId: string,
    timestampSeconds: number,
    content: string,
  ): Promise<VideoNote> {
    const response = await axiosClient.post<VideoNote>(
      `/api/lessons/${lessonId}/notes`,
      {
        timestampSeconds,
        content,
      },
    );
    return response.data;
  },

  async updateVideoNote(
    lessonId: string,
    noteId: number,
    timestampSeconds: number,
    content: string,
  ): Promise<VideoNote> {
    const response = await axiosClient.put<VideoNote>(
      `/api/lessons/${lessonId}/notes/${noteId}`,
      { timestampSeconds, content },
    );
    return response.data;
  },

  async deleteVideoNote(lessonId: string, noteId: number): Promise<void> {
    await axiosClient.delete(`/api/lessons/${lessonId}/notes/${noteId}`);
  },

  async getMyProgress(): Promise<UserProgress> {
    const response = await axiosClient.get<UserProgress>("/api/users/me");
    return response.data;
  },

  async markComplete(lessonId: string): Promise<UserProgress> {
    const response = await axiosClient.post<UserProgress>(
      `/api/users/me/complete-lesson/${lessonId}`,
    );
    return response.data;
  },

  async resetLesson(lessonId: string): Promise<UserProgress> {
    const response = await axiosClient.delete<UserProgress>(
      `/api/users/me/lessons/${lessonId}/completion`,
    );
    return response.data;
  },

  async saveLessonProgress(
    lessonId: string,
    request: LessonProgressRequest,
  ): Promise<LessonProgress> {
    const response = await axiosClient.post<LessonProgress>(
      `/api/users/me/lessons/${lessonId}/progress`,
      request,
    );
    return response.data;
  },

  async registerCourse(courseId: string): Promise<UserProgress> {
    const response = await axiosClient.post<UserProgress>(
      `/api/users/me/register-course/${courseId}`,
    );
    return response.data;
  },

  async changePassword(newPassword: string): Promise<string> {
    const response = await axiosClient.post<string>(
      "/api/users/me/change-password",
      { newPassword },
    );
    return response.data;
  },
};
