import { axiosClient, isMockApi, mockDelay } from './axiosClient';
import type { Topic, Lesson, Comment, UserProgress, VideoNote } from '../types';
import { mockTopics } from '../data/mockData';

// We maintain an in-memory database in mock mode so updates are reflected dynamically!
export let mockDbTopics = JSON.parse(JSON.stringify(mockTopics)) as Topic[];
export const setMockDbTopics = (newTopics: Topic[]) => {
  mockDbTopics = newTopics;
};
let mockDbCompleted: string[] = [];
let mockDbPoints = 1000;
const mockVideoNotes: Record<string, VideoNote[]> = {};
let mockVideoNoteId = 1;

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

  async getVideoNotes(lessonId: string): Promise<VideoNote[]> {
    if (isMockApi()) {
      await mockDelay(150);
      return [...(mockVideoNotes[lessonId] || [])];
    }
    const response = await axiosClient.get<VideoNote[]>(`/api/lessons/${lessonId}/notes`);
    return response.data;
  },

  async createVideoNote(
    lessonId: string,
    timestampSeconds: number,
    content: string
  ): Promise<VideoNote> {
    if (isMockApi()) {
      await mockDelay(200);
      const now = new Date().toISOString();
      const note: VideoNote = {
        id: mockVideoNoteId++,
        lessonId,
        timestampSeconds,
        content,
        createdAt: now,
        updatedAt: now
      };
      mockVideoNotes[lessonId] = [...(mockVideoNotes[lessonId] || []), note]
        .sort((a, b) => a.timestampSeconds - b.timestampSeconds);
      return note;
    }
    const response = await axiosClient.post<VideoNote>(`/api/lessons/${lessonId}/notes`, {
      timestampSeconds,
      content
    });
    return response.data;
  },

  async updateVideoNote(
    lessonId: string,
    noteId: number,
    timestampSeconds: number,
    content: string
  ): Promise<VideoNote> {
    if (isMockApi()) {
      await mockDelay(200);
      const note = (mockVideoNotes[lessonId] || []).find(item => item.id === noteId);
      if (!note) throw new Error(`Video note not found with id: ${noteId}`);
      note.timestampSeconds = timestampSeconds;
      note.content = content;
      note.updatedAt = new Date().toISOString();
      return { ...note };
    }
    const response = await axiosClient.put<VideoNote>(
      `/api/lessons/${lessonId}/notes/${noteId}`,
      { timestampSeconds, content }
    );
    return response.data;
  },

  async deleteVideoNote(lessonId: string, noteId: number): Promise<void> {
    if (isMockApi()) {
      await mockDelay(150);
      mockVideoNotes[lessonId] = (mockVideoNotes[lessonId] || [])
        .filter(item => item.id !== noteId);
      return;
    }
    await axiosClient.delete(`/api/lessons/${lessonId}/notes/${noteId}`);
  },

  async getMyProgress(): Promise<UserProgress> {
    if (isMockApi()) {
      await mockDelay(200);
      // Construct in-memory progress
      const mockRegistered: string[] = JSON.parse(localStorage.getItem('mockRegisteredTopics') || '[]');
      return {
        completedLessons: mockDbCompleted,
        quizScores: JSON.parse(localStorage.getItem('mockQuizScores') || '{}'),
        quizTotals: JSON.parse(localStorage.getItem('mockQuizTotals') || '{}'),
        points: mockDbPoints,
        unlockedBadges: JSON.parse(localStorage.getItem('mockUnlockedBadges') || '[]'),
        groupId: 'CIF',
        groupName: 'Nhóm nghiệp vụ CIF',
        mandatoryTopics: ['CIF'],
        registeredTopics: mockRegistered
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
  },

  async registerTopic(topicId: string): Promise<UserProgress> {
    if (isMockApi()) {
      await mockDelay(300);
      const mockRegistered: string[] = JSON.parse(localStorage.getItem('mockRegisteredTopics') || '[]');
      if (!mockRegistered.includes(topicId)) {
        mockRegistered.push(topicId);
        localStorage.setItem('mockRegisteredTopics', JSON.stringify(mockRegistered));
      }
      return this.getMyProgress();
    }
    const response = await axiosClient.post<UserProgress>(`/api/users/me/register-topic/${topicId}`);
    return response.data;
  },

  async changePassword(newPassword: string): Promise<string> {
    if (isMockApi()) {
      await mockDelay(300);
      return 'Đổi mật khẩu thành công!';
    }
    const response = await axiosClient.post<string>('/api/users/me/change-password', { newPassword });
    return response.data;
  }
};
