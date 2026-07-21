import { axiosClient, isMockApi, mockDelay } from './axiosClient';
import type { Topic, Lesson, Comment, UserProgress } from '../types';
import { mockTopics } from '../data/mockData';

// We maintain an in-memory database in mock mode so updates are reflected dynamically!
export let mockDbTopics = JSON.parse(JSON.stringify(mockTopics)) as Topic[];
export const setMockDbTopics = (newTopics: Topic[]) => {
  mockDbTopics = newTopics;
};
let mockDbCompleted: string[] = [];
let mockDbPoints = 1000;

export const courseApi = {
  async getAllTopics(): Promise<Topic[]> {
    if (isMockApi()) {
      await mockDelay(300);
      return mockDbTopics;
    }
    const response = await axiosClient.get<Topic[]>('/api/topics');
    return response.data;
  },

  async getTopicById(id: string): Promise<Topic> {
    if (isMockApi()) {
      await mockDelay(200);
      const topic = mockDbTopics.find(t => t.id === id);
      if (!topic) throw new Error(`Topic not found with id: ${id}`);
      return topic;
    }
    const response = await axiosClient.get<Topic>(`/api/topics/${id}`);
    return response.data;
  },

  async getLessonById(id: string): Promise<Lesson> {
    if (isMockApi()) {
      await mockDelay(250);
      const allLessons = mockDbTopics.flatMap(t => t.lessons);
      const lesson = allLessons.find(l => l.id === id);
      if (!lesson) throw new Error(`Lesson not found with id: ${id}`);
      return lesson;
    }
    const response = await axiosClient.get<Lesson>(`/api/lessons/${id}`);
    return response.data;
  },

  async addComment(lessonId: string, rating: number, content: string): Promise<Comment> {
    const username = localStorage.getItem('username') || 'quantrh';
    if (isMockApi()) {
      await mockDelay(300);
      const allLessons = mockDbTopics.flatMap(t => t.lessons);
      const lesson = allLessons.find(l => l.id === lessonId);
      if (!lesson) throw new Error(`Lesson not found with id: ${lessonId}`);

      const newComment: Comment = {
        id: `comment_${Date.now()}_${Math.random().toString(36).substring(2, 6)}`,
        user: username,
        avatar: username.charAt(0).toUpperCase(),
        rating,
        content,
        date: new Date().toISOString().split('T')[0]
      };
      
      lesson.comments.unshift(newComment);
      return newComment;
    }
    const response = await axiosClient.post<Comment>(`/api/lessons/${lessonId}/comments`, {
      rating,
      content
    });
    return response.data;
  },

  async getMyProgress(): Promise<UserProgress> {
    if (isMockApi()) {
      await mockDelay(200);
      // Construct in-memory progress
      return {
        completedLessons: mockDbCompleted,
        quizScores: JSON.parse(localStorage.getItem('mockQuizScores') || '{}'),
        quizTotals: JSON.parse(localStorage.getItem('mockQuizTotals') || '{}'),
        points: mockDbPoints,
        unlockedBadges: JSON.parse(localStorage.getItem('mockUnlockedBadges') || '[]')
      };
    }
    const response = await axiosClient.get<UserProgress>('/api/users/me');
    return response.data;
  },

  async markComplete(lessonId: string): Promise<UserProgress> {
    if (isMockApi()) {
      await mockDelay(300);
      if (!mockDbCompleted.includes(lessonId)) {
        mockDbCompleted.push(lessonId);
        mockDbPoints += 100;
      }
      return this.getMyProgress();
    }
    const response = await axiosClient.post<UserProgress>(`/api/users/me/complete-lesson/${lessonId}`);
    return response.data;
  }
};
