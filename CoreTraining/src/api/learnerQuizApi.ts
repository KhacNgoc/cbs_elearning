import { axiosClient } from "./axiosClient";
export interface LearnerQuizContext {
  id: string; name: string; lessonId: string | null; quizPassingScore: number;
  score: number | null; total: number | null;
}
export async function getLearnerQuizContext(courseId: string, lessonId?: string): Promise<LearnerQuizContext> {
  return (await axiosClient.get<LearnerQuizContext>("/api/users/me/learning/quiz-context", { params: { courseId, lessonId } })).data;
}
