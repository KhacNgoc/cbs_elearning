import { axiosClient } from "./axiosClient";

export type NotificationType =
  | "COMMENT_REPLY"
  | "ENROLLMENT_REQUEST"
  | "ENROLLMENT_APPROVED"
  | "ENROLLMENT_REJECTED"
  | "LESSON_REPORT"
  | "COURSE_CONTENT_UPDATED"
  | "FLASHCARD_STUDY_REMINDER";

export interface AppNotification {
  id: number;
  type: NotificationType;
  title: string;
  message: string;
  targetUrl?: string;
  read: boolean;
  createdAt: string;
}

export const notificationApi = {
  async getNotifications(beforeId?: number): Promise<NotificationPage> {
    return (await axiosClient.get<NotificationPage>("/api/notifications", { params: { size: 6, beforeId } }))
      .data;
  },
  async markRead(id: number): Promise<AppNotification> {
    return (
      await axiosClient.patch<AppNotification>(`/api/notifications/${id}/read`)
    ).data;
  },
  async markAllRead(): Promise<void> {
    await axiosClient.patch("/api/notifications/read-all");
  },
};
export interface NotificationPage {
  content: AppNotification[];
  nextBeforeId: number | null;
  hasMore: boolean;
  unreadCount: number;
}
