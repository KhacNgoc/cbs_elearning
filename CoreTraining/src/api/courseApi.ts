import { axiosClient, isMockApi, mockDelay } from './axiosClient';
import type { Topic, Lesson, Comment, UserProgress, VideoNote, LessonProgress, LessonProgressRequest } from '../types';
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
        id: Date.now(),
        user: username,
        avatar: username.charAt(0).toUpperCase(),
        rating,
        content,
        date: new Date().toISOString().split('T')[0],
        replies: []
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

  async replyToComment(
    lessonId: string,
    rootCommentId: number,
    replyToCommentId: number,
    content: string
  ): Promise<Comment> {
    const username = localStorage.getItem('username') || 'quantrh';
    if (isMockApi()) {
      await mockDelay(200);
      const lesson = mockDbTopics.flatMap(topic => topic.lessons).find(item => item.id === lessonId);
      const root = lesson?.comments.find(comment => comment.id === rootCommentId);
      if (!root) throw new Error(`Comment not found with id: ${rootCommentId}`);
      const target = replyToCommentId === rootCommentId
        ? root
        : root.replies?.find(reply => reply.id === replyToCommentId);
      const reply: Comment = {
        id: Date.now(),
        user: username,
        avatar: username.charAt(0).toUpperCase(),
        content,
        date: new Date().toISOString().split('T')[0],
        parentId: rootCommentId,
        replyToUser: target?.user || root.user,
        replies: []
      };
      root.replies = [...(root.replies || []), reply];
      return reply;
    }
    const response = await axiosClient.post<Comment>(
      `/api/lessons/${lessonId}/comments/${rootCommentId}/replies`,
      { content, replyToCommentId }
    );
    return response.data;
  },

  async deleteComment(lessonId: string, commentId: number): Promise<void> {
    if (isMockApi()) {
      await mockDelay(150);
      const lesson = mockDbTopics
        .flatMap(topic => topic.lessons)
        .find(item => item.id === lessonId);
      if (!lesson) throw new Error(`Lesson not found with id: ${lessonId}`);

      const username = localStorage.getItem('username') || '';
      const isAdmin = localStorage.getItem('userRole') === 'ROLE_ADMIN';
      const root = lesson.comments.find(comment => comment.id === commentId);
      const reply = lesson.comments
        .flatMap(comment => comment.replies || [])
        .find(comment => comment.id === commentId);
      const target = root || reply;
      if (!target) throw new Error(`Comment not found with id: ${commentId}`);
      if (!isAdmin && target.user !== username) {
        throw new Error('You do not have permission to delete this comment');
      }

      if (root) {
        lesson.comments = lesson.comments.filter(comment => comment.id !== commentId);
      } else {
        lesson.comments.forEach(comment => {
          comment.replies = (comment.replies || []).filter(item => item.id !== commentId);
        });
      }
      return;
    }
    await axiosClient.delete(`/api/lessons/${lessonId}/comments/${commentId}`);
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
      const storedCompleted: string[] = JSON.parse(localStorage.getItem('mockCompletedLessons') || '[]');
      for (const lessonId of storedCompleted) {
        if (!mockDbCompleted.includes(lessonId)) mockDbCompleted.push(lessonId);
      }
      return {
        completedLessons: mockDbCompleted,
        quizScores: JSON.parse(localStorage.getItem('mockQuizScores') || '{}'),
        quizTotals: JSON.parse(localStorage.getItem('mockQuizTotals') || '{}'),
        lessonQuizScores: JSON.parse(localStorage.getItem('mockLessonQuizScores') || '{}'),
        lessonQuizTotals: JSON.parse(localStorage.getItem('mockLessonQuizTotals') || '{}'),
        lessonProgress: JSON.parse(localStorage.getItem('mockLessonProgress') || '{}'),
        points: mockDbPoints,
        unlockedBadges: JSON.parse(localStorage.getItem('mockUnlockedBadges') || '[]'),
        groupId: 'GDV',
        groupName: 'Giao dịch viên',
        mandatoryTopics: ['CIF', 'TIEN_GUI', 'TELLER', 'THE'],
        registeredTopics: mockRegistered,
        pendingTopics: JSON.parse(localStorage.getItem('mockPendingTopics') || '[]')
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

  async resetLesson(lessonId: string): Promise<UserProgress> {
    if (isMockApi()) {
      await mockDelay(200);
      if (mockDbCompleted.includes(lessonId)) {
        mockDbCompleted = mockDbCompleted.filter(id => id !== lessonId);
        mockDbPoints = Math.max(0, mockDbPoints - 100);
      }
      const allProgress = JSON.parse(localStorage.getItem('mockLessonProgress') || '{}');
      delete allProgress[lessonId];
      localStorage.setItem('mockLessonProgress', JSON.stringify(allProgress));
      localStorage.setItem('mockCompletedLessons', JSON.stringify(mockDbCompleted));
      return this.getMyProgress();
    }
    const response = await axiosClient.delete<UserProgress>(
      `/api/users/me/lessons/${lessonId}/completion`
    );
    return response.data;
  },

  async saveLessonProgress(lessonId: string, request: LessonProgressRequest): Promise<LessonProgress> {
    if (isMockApi()) {
      await mockDelay(100);
      const allProgress: Record<string, LessonProgress> = JSON.parse(
        localStorage.getItem('mockLessonProgress') || '{}'
      );
      const previous = allProgress[lessonId];
      const progress: LessonProgress = {
        lessonId,
        lastPositionSeconds: Math.max(0, request.lastPositionSeconds),
        watchedSeconds: Math.max(previous?.watchedSeconds || 0, request.watchedSeconds),
        durationSeconds: Math.max(previous?.durationSeconds || 0, request.durationSeconds)
      };
      allProgress[lessonId] = progress;
      localStorage.setItem('mockLessonProgress', JSON.stringify(allProgress));
      return progress;
    }
    const response = await axiosClient.post<LessonProgress>(
      `/api/users/me/lessons/${lessonId}/progress`,
      request
    );
    return response.data;
  },

  async registerTopic(topicId: string): Promise<UserProgress> {
    if (isMockApi()) {
      await mockDelay(300);
      const pending: string[] = JSON.parse(localStorage.getItem('mockPendingTopics') || '[]');
      if (!pending.includes(topicId)) {
        pending.push(topicId);
        localStorage.setItem('mockPendingTopics', JSON.stringify(pending));
      }
      return this.getMyProgress();
    }
    const response = await axiosClient.post<UserProgress>(`/api/users/me/register-topic/${topicId}`);
    return response.data;
  },

  async changePassword(newPassword: string, employeeId: string, email: string): Promise<string> {
    if (isMockApi()) {
      await mockDelay(300);
      return 'Đổi mật khẩu thành công!';
    }
    const response = await axiosClient.post<string>('/api/users/me/change-password', { newPassword, employeeId, email });
    return response.data;
  }
};
