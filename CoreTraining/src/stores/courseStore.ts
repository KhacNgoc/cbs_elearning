import { defineStore } from 'pinia';
import type { Topic, Lesson } from '../types';
import { courseApi } from '../api/courseApi';

export const useCourseStore = defineStore('course', {
  state: () => ({
    topics: [] as Topic[],
    currentLessonId: null as string | null,
    currentLessonData: null as Lesson | null,
    isLoading: false
  }),

  getters: {
    allLessons(): Lesson[] {
      return this.topics.flatMap(topic => topic.lessons);
    },
    currentLesson(): Lesson | null {
      return this.currentLessonData;
    },
    currentTopic(): Topic | null {
      const lesson = this.currentLesson;
      if (!lesson) return null;
      return this.topics.find(topic => topic.id === lesson.topicId) || null;
    }
  },

  actions: {
    async fetchTopics() {
      this.isLoading = true;
      try {
        this.topics = await courseApi.getAllTopics();
      } catch (error) {
        console.error('Failed to load topics:', error);
      } finally {
        this.isLoading = false;
      }
    },

    async fetchLessonDetail(lessonId: string) {
      this.isLoading = true;
      this.currentLessonId = lessonId;
      try {
        this.currentLessonData = await courseApi.getLessonById(lessonId);
      } catch (error) {
        console.error(`Failed to load lesson detail for ${lessonId}:`, error);
        this.currentLessonData = null;
      } finally {
        this.isLoading = false;
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
    }
  }
});
