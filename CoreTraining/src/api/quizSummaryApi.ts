import { axiosClient, isMockApi } from './axiosClient';

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
  courses: { id: string; name: string }[];
}
export async function getQuizSummaries(params: { courseId?: string; search: string; page: number; size: number }): Promise<QuizSummaryPage> {
  if (isMockApi()) {
    const [{ mockDbCourses }, { mockDbQuestions }] = await Promise.all([import('./courseApi'), import('./quizApi')]);
    const rows: QuizSummary[] = [];
    for (const course of mockDbCourses) {
      for (const lesson of [null, ...course.lessons]) {
        const questionCount = mockDbQuestions.filter(q => q.courseId === course.id && (lesson ? q.lessonId === lesson.id : !q.lessonId)).length;
        if (questionCount) rows.push({ courseId: course.id, courseName: course.name, lessonId: lesson?.id || null,
          lessonTitle: lesson?.title || null, questionCount, passingScore: (lesson ? lesson.quizPassingScore : course.quizPassingScore) ?? 80 });
      }
    }
    const filtered = rows.filter(row => (!params.courseId || row.courseId === params.courseId)
      && `${row.lessonTitle || 'Bài kiểm tra tổng hợp'} ${row.courseName} ${row.lessonId || ''}`.toLocaleLowerCase('vi').includes(params.search.trim().toLocaleLowerCase('vi')));
    return { content: filtered.slice(params.page * params.size, (params.page + 1) * params.size), totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / params.size), courses: mockDbCourses.map(c => ({ id: c.id, name: c.name })) };
  }
  return (await axiosClient.get<QuizSummaryPage>('/api/admin/quizzes', { params })).data;
}
