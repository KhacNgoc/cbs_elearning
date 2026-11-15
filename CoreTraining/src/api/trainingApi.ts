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
  /** Editable courses filtered and paged on the server (0-based page); `lessons` is always empty. */
  async coursesPaged(params: { page: number; size: number; search?: string; status?: string; programId?: string }) {
    return (
      await axiosClient.get<{
        items: Course[];
        page: number;
        size: number;
        totalElements: number;
        totalPages: number;
        hasNext: boolean;
      }>("/api/admin/courses/paged", { params })
    ).data;
  },
  /** One editable course with its admin actions; `lessons` is always empty. */
  async course(id: string) {
    return (
      await axiosClient.get<Course>(
        `/api/admin/courses/${encodeURIComponent(id)}`,
      )
    ).data;
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
  /** Chapters with lesson metadata only (no text, video, documents or procedure steps). */
  async chapterOutlines(courseId: string) {
    return (
      await axiosClient.get<Chapter[]>(
        `/api/admin/courses/${encodeURIComponent(courseId)}/chapters`,
        { params: { view: "outline" } },
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
  async domainsPaged(params?: { search?: string; page?: number; size?: number; unallocatedForUnitId?: string }) {
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
  async programsPaged(params?: { domainId?: string; search?: string; page?: number; size?: number; unallocatedForUnitId?: string }) {
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
  /** Active programs of several domains in one request (a page of the curriculum tree). */
  async programsByDomains(domainIds: string[], search?: string) {
    if (!domainIds.length) return [];
    return (
      await axiosClient.get<(TrainingProgram & { coursesCount?: number })[]>(
        "/api/admin/programs/by-domains",
        { params: { domainIds: domainIds.join(","), search: search || undefined } },
      )
    ).data;
  },
  async coursesByProgramPaged(programId: string, params?: { search?: string; page?: number; size?: number; unallocatedForUnitId?: string }) {
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
  async unitAllocatedItems(unitId: string) {
    return (
      await axiosClient.get<{
        categoryType: "domain" | "program" | "course";
        targetId: string;
        grantType: "MANDATORY" | "OPTIONAL";
        code?: string;
        name?: string;
        parentId?: string;
      }[]>(`/api/admin/unit-allocations/${encodeURIComponent(unitId)}/allocated-items`)
    ).data;
  },
  async unitDirectoryPaged(params?: { search?: string; type?: string; status?: string; page?: number; size?: number }) {
    return (
      await axiosClient.get<{
        items: {
          id: string;
          name: string;
          type: string;
          active: boolean;
          parentId?: string;
          domainCount: number;
          programCount: number;
          courseCount: number;
          isAllocated: boolean;
        }[];
        page: number;
        size: number;
        totalElements: number;
        totalPages: number;
        stats: {
          totalCount: number;
          allocatedCount: number;
          unallocatedCount: number;
          branchCount: number;
          departmentCount: number;
        };
      }>("/api/admin/unit-allocations/directory", { params })
    ).data;
  },
};
