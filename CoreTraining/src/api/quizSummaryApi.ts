import { axiosClient } from './axiosClient';

export interface QuizSummary {
  courseId: string;
  courseName: string;
  lessonId: string | null;
  lessonTitle: string | null;
  questionCount: number;
  passingScore: number;
}
export interface QuizSummaryPage {
  content: QuizSummary[];
  totalElements: number;
  totalPages: number;
}
export async function getQuizSummaries(params: { courseId?: string; search: string; page: number; size: number }): Promise<QuizSummaryPage> {
  return (await axiosClient.get<QuizSummaryPage>('/api/admin/quizzes', { params })).data;
}
