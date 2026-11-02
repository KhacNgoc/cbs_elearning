import { axiosClient, RAG_CHAT_TIMEOUT_MS } from "./axiosClient";

export interface RagSource {
  documentId: string;
  documentName: string;
  page: number;
  section: string;
  courseId?: string | null;
  courseName?: string | null;
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
  courseId: string | null;
  question: string;
  createdAt: string;
}

export const ragApi = {
  // The assistant searches every course the learner can access, so history is
  // per learner: without a conversation id the server returns the latest one.
  async getHistoryPage(conversationId?:string,beforeId?:number) {
    return (await axiosClient.get<{items:RagChatHistory[];nextBeforeId:number|null;hasMore:boolean}>("/api/rag/chat/history/page",{params:{conversationId,beforeId,size:20}})).data;
  },
  /** `contextCourseId` (the course being viewed) is only a ranking hint. */
  async chat(
    question: string,
    contextCourseId?: string,
    page?: number,
    conversationId?: string,
  ): Promise<RagAnswer> {
    const response = await axiosClient.post<RagAnswer>(
      "/api/rag/chat",
      {
        question,
        courseId: contextCourseId,
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
    courseId?: string,
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
