import { axiosClient } from './axiosClient';

export interface QuizLessonContext {
  id: string;
  title: string;
  courseId: string;
  quizPassingScore: number;
  quizRequired: boolean;
}
export interface QuizCourseContext {
  id: string;
  name: string;
  domainName?: string;
  status: string;
  quizPassingScore: number;
  adminActions: { exams: string[] };
  lessons: QuizLessonContext[];
}
export async function getQuizCourses(): Promise<QuizCourseContext[]> {
  return (await axiosClient.get<QuizCourseContext[]>('/api/admin/quizzes/courses')).data;
}
export async function getQuizContext(params: { courseId?: string; lessonId?: string }): Promise<QuizCourseContext> {
  return (await axiosClient.get<QuizCourseContext>('/api/admin/quizzes/context', { params })).data;
}
