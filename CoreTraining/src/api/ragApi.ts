import { axiosClient } from './axiosClient';

export interface RagSource {
  document_id: string;
  file_name: string;
  page: number;
  excerpt: string;
  score: number;
}

export interface RagAnswer {
  answer: string;
  sources: RagSource[];
}

export const ragApi = {
  async chat(question: string, courseId?: string, page?: number): Promise<RagAnswer> {
    const response = await axiosClient.post<RagAnswer>('/api/rag/chat', {
      question,
      courseId,
      page
    });
    return response.data;
  }
};
