import { axiosClient, RAG_CHAT_TIMEOUT_MS } from "./axiosClient";

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
  conversationId: string;
  needsClarification?: boolean;
}

export interface RagChatHistory extends RagAnswer {
  id: number;
  courseId: string;
  question: string;
  createdAt: string;
}

export const ragApi = {
  async courseChoices(search="") {
    return (await axiosClient.get<{id:string;name:string}[]>("/api/users/me/learning/dashboard/choices",{params:{search,size:50}})).data;
  },
  async getHistoryPage(courseId:string,conversationId?:string,beforeId?:number) {
    return (await axiosClient.get<{items:RagChatHistory[];nextBeforeId:number|null;hasMore:boolean}>("/api/rag/chat/history/page",{params:{courseId,conversationId,beforeId,size:20}})).data;
  },
  async chat(
    question: string,
    courseId: string,
    page?: number,
    conversationId?: string,
  ): Promise<RagAnswer> {
    const response = await axiosClient.post<RagAnswer>(
      "/api/rag/chat",
      {
        question,
        courseId,
        page,
        conversationId,
      },
      {
        timeout: RAG_CHAT_TIMEOUT_MS,
      },
    );
    return response.data;
  },

  async getHistory(
    courseId: string,
    conversationId?: string,
  ): Promise<RagChatHistory[]> {
    const response = await axiosClient.get<RagChatHistory[]>(
      "/api/rag/chat/history",
      {
        params: { courseId, conversationId },
      },
    );
    return response.data;
  },
};
