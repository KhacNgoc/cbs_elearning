import { axiosClient } from './axiosClient';

export type NotificationType =
  | 'COMMENT_REPLY'
  | 'ENROLLMENT_REQUEST'
  | 'ENROLLMENT_APPROVED'
  | 'ENROLLMENT_REJECTED'
  | 'LESSON_REPORT';

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
  async getNotifications(): Promise<AppNotification[]> {
    return (await axiosClient.get<AppNotification[]>('/api/notifications')).data;
  },
  async markRead(id: number): Promise<AppNotification> {
    return (await axiosClient.patch<AppNotification>(`/api/notifications/${id}/read`)).data;
  },
  async markAllRead(): Promise<void> {
    await axiosClient.patch('/api/notifications/read-all');
  }
};
