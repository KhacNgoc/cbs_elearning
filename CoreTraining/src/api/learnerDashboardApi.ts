import { axiosClient } from "./axiosClient";
import type { LearnerCourse, ResumeTarget } from "../types";
export interface DashboardSummary {
  joined: number; discover: number; notStarted: number; inProgress: number; complete: number;
  mandatory: number; mandatoryComplete: number; completed: number; total: number; percent: number | null;
}
export interface DashboardOverview {
  summary: DashboardSummary;
  choices: Record<string, { value: string; label: string }[]>;
  resume: ResumeTarget | null;
}
export interface DashboardPage { items: LearnerCourse[]; total: number; page: number; size: number }
export type DashboardParams = Record<string, string | number | boolean>;
export const getDashboardSummary = async (tab: string) =>
  (await axiosClient.get<DashboardOverview>("/api/users/me/learning/dashboard/summary", { params: { tab } })).data;
export const getDashboardCourses = async (params: DashboardParams) =>
  (await axiosClient.get<DashboardPage>("/api/users/me/learning/dashboard/courses", { params })).data;
export const getDashboardResume = async (courseId: string) =>
  (await axiosClient.get<ResumeTarget | null>(`/api/users/me/learning/dashboard/courses/${encodeURIComponent(courseId)}/resume`)).data;
