import { axiosClient } from "./axiosClient";
import type { Question } from "../types";
export interface BankQuestion {
  id?: number;
  version?: number;
  code: string;
  courseId: string;
  courseName?: string;
  lessonId?: string;
  lessonTitle?: string;
  domainId?: string;
  domainName?: string;
  text: string;
  type: "SINGLE_CHOICE" | "MULTIPLE_CHOICE";
  options: string[];
  correctAnswers: number[];
  explanation: string;
  difficulty: "EASY" | "MEDIUM" | "HARD";
  status: "DRAFT" | "ACTIVE" | "ARCHIVED";
  allowedActions?: string[];
}
export interface LessonReport {
  id: number;
  version: number;
  lessonId: string;
  lessonTitle: string;
  courseId: string;
  courseName: string;
  reporterName: string;
  content: string;
  images: string[];
  status: "NEW" | "IN_REVIEW" | "RESOLVED" | "DISMISSED";
  resolution: string;
  reviewerName?: string;
  createdAt: string;
  reviewedAt?: string;
  canReview: boolean;
}
export const reportStatus = {
  NEW: "Mới gửi",
  IN_REVIEW: "Đang xem xét",
  RESOLVED: "Đã xử lý",
  DISMISSED: "Không ghi nhận",
};
export const bankStatus = {
  DRAFT: "Bản nháp",
  ACTIVE: "Đang sử dụng",
  ARCHIVED: "Ngừng sử dụng",
};
export const difficultyLabel = {
  EASY: "Dễ",
  MEDIUM: "Trung bình",
  HARD: "Khó",
};
export const contentToolsApi = {
  async bank() {
    return (await axiosClient.get<BankQuestion[]>("/api/admin/question-bank"))
      .data;
  },
  async saveBank(value: BankQuestion) {
    return (
      await (value.id
        ? axiosClient.put<BankQuestion>(
            `/api/admin/question-bank/${value.id}`,
            value,
          )
        : axiosClient.post<BankQuestion>("/api/admin/question-bank", value))
    ).data;
  },
  async capture(id: number) {
    return (
      await axiosClient.post<BankQuestion>(
        `/api/admin/question-bank/capture/${id}`,
      )
    ).data;
  },
  async addToQuiz(lessonId: string, questionIds: number[]) {
    return (
      await axiosClient.post<Question[]>(
        "/api/admin/question-bank/add-to-quiz",
        { lessonId, questionIds },
      )
    ).data;
  },
  async submitReport(lessonId: string, content: string, images: File[]) {
    const data = new FormData();
    data.append("content", content);
    images.forEach((file) => data.append("images", file));
    return (
      await axiosClient.post<LessonReport>(
        `/api/lessons/${encodeURIComponent(lessonId)}/reports`,
        data,
        { headers: { "Content-Type": "multipart/form-data" } },
      )
    ).data;
  },
  async reports() {
    return (await axiosClient.get<LessonReport[]>("/api/admin/content-reports"))
      .data;
  },
  async review(value: LessonReport) {
    return (
      await axiosClient.put<LessonReport>(
        `/api/admin/content-reports/${value.id}`,
        {
          version: value.version,
          status: value.status,
          resolution: value.resolution,
        },
      )
    ).data;
  },
};
export function contentToolsError(error: unknown) {
  return (
    (error as { response?: { data?: { message?: string } } })?.response?.data
      ?.message || "Không thực hiện được. Vui lòng thử lại."
  );
}
