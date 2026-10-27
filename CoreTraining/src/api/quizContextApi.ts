import { axiosClient, isMockApi } from './axiosClient';

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
async function mockCourses(): Promise<QuizCourseContext[]> {
  const { mockDbCourses } = await import('./courseApi');
  return mockDbCourses.map(c => ({ id: c.id, name: c.name, domainName: c.domainName, status: c.status || 'DRAFT',
    quizPassingScore: c.quizPassingScore ?? 80, adminActions: { exams: c.adminActions?.exams || [] },
    lessons: c.lessons.map(l => ({ id: l.id, title: l.title, courseId: c.id, quizPassingScore: l.quizPassingScore ?? 80, quizRequired: l.quizRequired ?? true })) }));
}
export async function getQuizCourses(): Promise<QuizCourseContext[]> {
  if (isMockApi()) return (await mockCourses()).map(c => ({ ...c, lessons: [] }));
  return (await axiosClient.get<QuizCourseContext[]>('/api/admin/quizzes/courses')).data;
}
export async function getQuizContext(params: { courseId?: string; lessonId?: string }): Promise<QuizCourseContext> {
  if (isMockApi()) {
    const course = (await mockCourses()).find(c => (!params.courseId || c.id === params.courseId)
      && (!params.lessonId || c.lessons.some(l => l.id === params.lessonId)));
    if (!course) throw new Error('Không tìm thấy khóa học hoặc bài học');
    return { ...course, lessons: params.lessonId ? course.lessons.filter(l => l.id === params.lessonId) : course.lessons };
  }
  return (await axiosClient.get<QuizCourseContext>('/api/admin/quizzes/context', { params })).data;
}
