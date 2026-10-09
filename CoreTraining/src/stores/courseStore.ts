import { defineStore } from 'pinia';
import type { Course, Lesson } from '../types';
import { courseApi } from '../api/courseApi';

let coursesRequest: Promise<void> | null = null;
let lessonRequestId = 0;

export const useCourseStore = defineStore('course', {
  state: () => ({
    courses: [] as Course[],
    currentLessonId: null as string | null,
    currentLessonData: null as Lesson | null,
    isLoading: false
  }),

  getters: {
    allLessons(): Lesson[] {
      return this.courses.flatMap(course => course.lessons);
    },
    currentLesson(): Lesson | null {
      return this.currentLessonData;
    },
    currentCourse(): Course | null {
      const lesson = this.currentLesson;
      if (!lesson) return null;
      return this.courses.find(course => course.id === lesson.courseId) || null;
    }
  },

  actions: {
    async fetchCourses(force = false) {
      if (this.courses.length > 0 && !force) return;
      if (coursesRequest && !force) return coursesRequest;
      this.isLoading = true;
      coursesRequest = (async () => {
        this.courses = await courseApi.getAllCourses();
      })();
      try { await coursesRequest; }
      catch (error) { console.error('Failed to load courses:', error); }
      finally { coursesRequest = null; this.isLoading = false; }
    },

    async fetchLessonDetail(lessonId: string) {
      const requestId = ++lessonRequestId;
      this.isLoading = true;
      this.currentLessonId = lessonId;
      try {
        const lesson = await courseApi.getLessonById(lessonId);
        if (requestId !== lessonRequestId) return;
        this.currentLessonData = lesson;
        // Keep the course outline in sync with the detailed lesson response.
        // The sidebar reads from `courses`, while the learning page reads from
        // `currentLessonData`; without this merge a newly-created quiz can
        // appear in one place but not the other until a full page refresh.
        if (this.currentLessonData) {
          const course = this.courses.find(item => item.id === this.currentLessonData?.courseId);
          const lessonIndex = course?.lessons.findIndex(item => item.id === lessonId) ?? -1;
          if (course && lessonIndex >= 0) {
            course.lessons[lessonIndex] = {
              ...course.lessons[lessonIndex],
              ...this.currentLessonData
            };
          }
        }
      } catch (error) {
        console.error(`Failed to load lesson detail for ${lessonId}:`, error);
        if (requestId === lessonRequestId) this.currentLessonData = null;
      } finally {
        if (requestId === lessonRequestId) this.isLoading = false;
      }
    },

    async addComment(lessonId: string, rating: number, content: string) {
      try {
        const newComment = await courseApi.addComment(lessonId, rating, content);
        if (this.currentLessonData && this.currentLessonData.id === lessonId) {
          this.currentLessonData.comments.unshift(newComment);
        }
      } catch (error) {
        console.error('Failed to add comment:', error);
      }
    },

    async replyToComment(
      lessonId: string,
      rootCommentId: number,
      replyToCommentId: number,
      content: string
    ) {
      const reply = await courseApi.replyToComment(
        lessonId, rootCommentId, replyToCommentId, content
      );
      if (this.currentLessonData?.id === lessonId) {
        const root = this.currentLessonData.comments.find(comment => comment.id === rootCommentId);
        if (root && !(root.replies || []).some(item => item.id === reply.id)) {
          root.replies = [...(root.replies || []), reply];
        }
      }
    },

    async deleteComment(lessonId: string, commentId: number) {
      await courseApi.deleteComment(lessonId, commentId);
      if (this.currentLessonData?.id !== lessonId) return;

      const isRootComment = this.currentLessonData.comments
        .some(comment => comment.id === commentId);
      if (isRootComment) {
        this.currentLessonData.comments = this.currentLessonData.comments
          .filter(comment => comment.id !== commentId);
        return;
      }
      this.currentLessonData.comments.forEach(comment => {
        comment.replies = (comment.replies || [])
          .filter(reply => reply.id !== commentId);
      });
    }
  }
});
