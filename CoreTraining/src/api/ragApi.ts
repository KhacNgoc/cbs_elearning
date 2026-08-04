import { axiosClient } from './axiosClient';

export interface RagSource {
  documentId: string;
  documentName: string;
  page: number;
  section: string;
}

export interface RagAnswer {
  answer: string;
  grounded: boolean;
  sources: RagSource[];
}

export interface RagChatHistory extends RagAnswer {
  id: number;
  courseId: string;
  question: string;
  createdAt: string;
}

export const ragApi = {
  async chat(question: string, courseId: string, page?: number): Promise<RagAnswer> {
    const response = await axiosClient.post<RagAnswer>('/api/rag/chat', {
      question,
      courseId,
      page
    });
    return response.data;
  },

  async getHistory(courseId: string): Promise<RagChatHistory[]> {
    const response = await axiosClient.get<RagChatHistory[]>('/api/rag/chat/history', {
      params: { courseId }
    });
    return response.data;
  }
};
