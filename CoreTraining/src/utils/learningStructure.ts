import type { Chapter } from '../types';

export function outlineContext(user: {
  username: string;
  permissions: string[];
  roles?: string[];
  quizTotals?: Record<string, number>;
  lessonQuizTotals?: Record<string, number>;
  systemAdmin: boolean;
  completedLessons: string[];
  quizScores: Record<string, number>;
  lessonQuizScores: Record<string, number>;
  registeredCourses: string[];
  mandatoryCourses: string[];
}) {
  return JSON.stringify([
    user.username, user.permissions, user.systemAdmin, user.completedLessons,
    user.quizScores, user.quizTotals, user.lessonQuizScores, user.lessonQuizTotals,
    user.registeredCourses, user.mandatoryCourses, user.roles,
  ]);
}

/** One pass per chapter; a completed prefix unlocks the following lesson. */
export function chapterLearningState(chapters: Chapter[], completed: Set<string>) {
  return new Map(chapters.map(chapter => {
    let completedCount = 0;
    let firstIncomplete = chapter.lessons.length;
    chapter.lessons.forEach((lesson, index) => {
      if (completed.has(lesson.id)) completedCount++;
      else if (firstIncomplete === chapter.lessons.length) firstIncomplete = index;
    });
    return [chapter.id, { completedCount, firstIncomplete }];
  }));
}
