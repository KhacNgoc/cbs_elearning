import { axiosClient } from "./axiosClient";
import type {
  TrainingDomain,
  TrainingProgram,
  Chapter,
  Course,
  CourseOutline,
  Lesson,
} from "../types";

export const trainingApi = {
  async uploadCourseImage(file: File, courseId?: string, programId?: string) {
    const data = new FormData();
    data.append("file", file);
    return (
      await axiosClient.post<{ thumbnail: string }>(
        "/api/admin/courses/images",
        data,
        {
          params: {
            courseId: courseId || undefined,
            programId: programId || undefined,
          },
          headers: { "Content-Type": "multipart/form-data" },
        },
      )
    ).data;
  },
  async catalog() {
    return (
      await axiosClient.get<{
        domains: TrainingDomain[];
        programs: TrainingProgram[];
      }>("/api/admin/training/catalog")
    ).data;
  },
  async saveDomain(data: Partial<TrainingDomain>) {
    return (
      await axiosClient[data.id ? "put" : "post"]<TrainingDomain>(
        `/api/admin/training-domains${data.id ? `/${data.id}` : ""}`,
        data,
      )
    ).data;
  },
  async saveProgram(data: Partial<TrainingProgram>) {
    return (
      await axiosClient[data.id ? "put" : "post"]<TrainingProgram>(
        `/api/admin/training-programs${data.id ? `/${data.id}` : ""}`,
        data,
      )
    ).data;
  },
  async courses() {
    return (await axiosClient.get<Course[]>("/api/admin/courses")).data;
  },
  async saveCourse(data: Partial<Course>) {
    return (
      await axiosClient[data.id ? "put" : "post"]<Course>(
        `/api/admin/courses${data.id ? `/${data.id}` : ""}`,
        data,
      )
    ).data;
  },
  async chapters(courseId: string) {
    return (
      await axiosClient.get<Chapter[]>(
        `/api/admin/courses/${courseId}/chapters`,
      )
    ).data;
  },
  async chapter(id: string) {
    return (await axiosClient.get<Chapter>(`/api/admin/chapters/${id}`)).data;
  },
  async saveChapter(courseId: string, data: Partial<Chapter>) {
    return (
      await axiosClient[data.id ? "put" : "post"]<Chapter>(
        `/api/admin/courses/${courseId}/chapters${data.id ? `/${data.id}` : ""}`,
        data,
      )
    ).data;
  },
  async deleteDomain(id: string) {
    await axiosClient.delete(`/api/admin/training-domains/${encodeURIComponent(id)}`);
  },
  async deleteProgram(id: string) {
    await axiosClient.delete(`/api/admin/training-programs/${encodeURIComponent(id)}`);
  },
  async deleteChapter(id: string) {
    await axiosClient.delete(`/api/admin/chapters/${encodeURIComponent(id)}`);
  },
  async deleteLesson(id: string) {
    await axiosClient.delete(`/api/admin/lessons/${encodeURIComponent(id)}`);
  },
  async reorderChapters(id: string, ids: string[]) {
    await axiosClient.put(`/api/admin/courses/${id}/chapters/order`, ids);
  },
  async reorderLessons(id: string, ids: string[]) {
    await axiosClient.put(`/api/admin/chapters/${id}/lessons/order`, ids);
  },
  async setStatus(id: string, status: string) {
    return (
      await axiosClient.post<Course>(`/api/admin/courses/${id}/status`, {
        status,
      })
    ).data;
  },
  async outline(id: string) {
    return (await axiosClient.get<CourseOutline>(`/api/courses/${id}/outline`))
      .data;
  },
  async lesson(id: string) {
    return (await axiosClient.get<Lesson>(`/api/lessons/${id}`)).data;
  },
  async domainsPaged(params?: { search?: string; page?: number; size?: number }) {
    return (
      await axiosClient.get<{
        items: (TrainingDomain & { programsCount?: number })[];
        page: number;
        size: number;
        totalElements: number;
        totalPages: number;
        hasNext: boolean;
      }>("/api/admin/domains/paged", { params })
    ).data;
  },
  async programsPaged(params?: { domainId?: string; search?: string; page?: number; size?: number }) {
    return (
      await axiosClient.get<{
        items: (TrainingProgram & { coursesCount?: number })[];
        page: number;
        size: number;
        totalElements: number;
        totalPages: number;
        hasNext: boolean;
      }>("/api/admin/programs/paged", { params })
    ).data;
  },
  async coursesByProgramPaged(programId: string, params?: { search?: string; page?: number; size?: number }) {
    return (
      await axiosClient.get<{
        items: Course[];
        page: number;
        size: number;
        totalElements: number;
        totalPages: number;
        hasNext: boolean;
      }>(`/api/admin/programs/${encodeURIComponent(programId)}/courses`, { params })
    ).data;
  },
};
