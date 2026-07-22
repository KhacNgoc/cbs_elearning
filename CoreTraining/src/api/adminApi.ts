import { axiosClient, isMockApi, mockDelay } from './axiosClient';
import type { Topic, Lesson, LessonDocument, Question } from '../types';
import { mockDbTopics, setMockDbTopics } from './courseApi';
import { mockDbQuestions, setMockDbQuestions } from './quizApi';

const getDocumentType = (fileName: string): LessonDocument['documentType'] => {
  const extension = fileName.split('.').pop()?.toLowerCase();
  if (extension === 'pdf') return 'PDF';
  if (extension === 'ppt' || extension === 'pptx') return 'POWERPOINT';
  return 'WORD';
};

export interface AdminUser {
  id?: number;
  username: string;
  password?: string;
  fullName: string;
  avatarColor: string;
  role: string;
  points: number;
}

// In mock mode, we seed the list of users from the default mock user data
let mockDbUsers: AdminUser[] = [
  { id: 1, username: 'admin', fullName: 'Quản trị hệ thống', avatarColor: '#EF4444', role: 'ROLE_ADMIN', points: 0 },
  { id: 2, username: 'quantrh', fullName: 'Quản Trị Hệ Thống (Bạn)', avatarColor: '#005B41', role: 'ROLE_USER', points: 1850 },
  { id: 3, username: 'nguyenvana', fullName: 'Nguyễn Văn An', avatarColor: '#4F46E5', role: 'ROLE_USER', points: 2850 },
  { id: 4, username: 'tranb', fullName: 'Trần Thị Bình', avatarColor: '#10B981', role: 'ROLE_USER', points: 2600 },
  { id: 5, username: 'lephuc', fullName: 'Lê Hoàng Phúc', avatarColor: '#F59E0B', role: 'ROLE_USER', points: 2450 },
  { id: 6, username: 'phamduc', fullName: 'Phạm Minh Đức', avatarColor: '#EF4444', role: 'ROLE_USER', points: 2100 },
  { id: 7, username: 'hoangyen', fullName: 'Hoàng Kim Yến', avatarColor: '#EC4899', role: 'ROLE_USER', points: 1500 },
  { id: 8, username: 'vuongminh', fullName: 'Vương Tuấn Minh', avatarColor: '#8B5CF6', role: 'ROLE_USER', points: 1200 }
];

export const adminApi = {
  // ==========================================
  // User Management
  // ==========================================
  async getAllUsers(): Promise<AdminUser[]> {
    if (isMockApi()) {
      await mockDelay(300);
      return [...mockDbUsers];
    }
    const response = await axiosClient.get<AdminUser[]>('/api/admin/users');
    return response.data;
  },

  async createUser(user: AdminUser): Promise<AdminUser> {
    if (isMockApi()) {
      await mockDelay(300);
      const newUser: AdminUser = {
        ...user,
        id: Math.max(...mockDbUsers.map(u => u.id || 0)) + 1,
        points: user.points || 0
      };
      mockDbUsers.push(newUser);
      return newUser;
    }
    const response = await axiosClient.post<AdminUser>('/api/admin/users', user);
    return response.data;
  },

  async updateUser(id: number, user: AdminUser): Promise<AdminUser> {
    if (isMockApi()) {
      await mockDelay(300);
      const index = mockDbUsers.findIndex(u => u.id === id);
      if (index === -1) throw new Error(`User not found with id: ${id}`);
      
      const updatedUser = {
        ...mockDbUsers[index],
        fullName: user.fullName,
        avatarColor: user.avatarColor,
        role: user.role,
        points: user.points
      };
      if (user.password) {
        updatedUser.password = user.password;
      }
      mockDbUsers[index] = updatedUser;
      return updatedUser;
    }
    const response = await axiosClient.put<AdminUser>(`/api/admin/users/${id}`, user);
    return response.data;
  },

  async deleteUser(id: number): Promise<void> {
    if (isMockApi()) {
      await mockDelay(300);
      mockDbUsers = mockDbUsers.filter(u => u.id !== id);
      return;
    }
    await axiosClient.delete(`/api/admin/users/${id}`);
  },

  // ==========================================
  // Topic Management
  // ==========================================
  async createTopic(topic: Topic): Promise<Topic> {
    if (isMockApi()) {
      await mockDelay(300);
      const newTopic: Topic = { ...topic, lessons: [] };
      mockDbTopics.push(newTopic);
      setMockDbTopics([...mockDbTopics]);
      return newTopic;
    }
    const response = await axiosClient.post<Topic>('/api/admin/topics', topic);
    return response.data;
  },

  async updateTopic(id: string, topic: Topic): Promise<Topic> {
    if (isMockApi()) {
      await mockDelay(300);
      const index = mockDbTopics.findIndex(t => t.id === id);
      if (index === -1) throw new Error(`Topic not found with id: ${id}`);
      
      mockDbTopics[index] = {
        ...mockDbTopics[index],
        name: topic.name,
        iconName: topic.iconName
      };
      setMockDbTopics([...mockDbTopics]);
      return mockDbTopics[index];
    }
    const response = await axiosClient.put<Topic>(`/api/admin/topics/${id}`, topic);
    return response.data;
  },

  async deleteTopic(id: string): Promise<void> {
    if (isMockApi()) {
      await mockDelay(300);
      const filtered = mockDbTopics.filter(t => t.id !== id);
      setMockDbTopics(filtered);
      return;
    }
    await axiosClient.delete(`/api/admin/topics/${id}`);
  },

  // ==========================================
  // Lesson Management
  // ==========================================
  async uploadDocument(file: File): Promise<LessonDocument> {
    if (isMockApi()) {
      return {
        name: file.name.replace(/\.[^.]+$/, ''),
        documentType: getDocumentType(file.name),
        fileUrl: URL.createObjectURL(file),
        originalFileName: file.name,
        fileSize: file.size
      };
    }

    const formData = new FormData();
    formData.append('file', file);
    const response = await axiosClient.post<LessonDocument>('/api/admin/documents/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    });
    const apiBaseUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080';
    return {
      ...response.data,
      fileUrl: response.data.fileUrl.startsWith('http')
        ? response.data.fileUrl
        : `${apiBaseUrl}${response.data.fileUrl}`
    };
  },

  async createLesson(lesson: Omit<Lesson, 'comments'> & { topicId: string }): Promise<Lesson> {
    if (isMockApi()) {
      await mockDelay(300);
      const newLesson: Lesson = {
        ...lesson,
        comments: [],
        procedureSteps: lesson.procedureSteps || []
      };
      const topic = mockDbTopics.find(t => t.id === lesson.topicId);
      if (topic) {
        topic.lessons.push(newLesson);
        setMockDbTopics([...mockDbTopics]);
      }
      return newLesson;
    }
    const response = await axiosClient.post<Lesson>('/api/admin/lessons', lesson);
    return response.data;
  },

  async updateLesson(id: string, lesson: Omit<Lesson, 'comments'> & { topicId: string }): Promise<Lesson> {
    if (isMockApi()) {
      await mockDelay(300);
      let foundLesson: Lesson | null = null;
      
      // Remove from old topic if topicId changed, or just update in place
      for (const t of mockDbTopics) {
        const idx = t.lessons.findIndex(l => l.id === id);
        if (idx !== -1) {
          foundLesson = t.lessons[idx];
          if (t.id !== lesson.topicId) {
            t.lessons.splice(idx, 1); // remove
          }
          break;
        }
      }

      if (!foundLesson) throw new Error(`Lesson not found with id: ${id}`);

      const targetLesson = foundLesson as Lesson;

      const updatedLesson: Lesson = {
        ...targetLesson,
        title: lesson.title,
        topicId: lesson.topicId,
        hint: lesson.hint,
        videoUrl: lesson.videoUrl,
        documents: lesson.documents || [],
        procedureSteps: lesson.procedureSteps || []
      };

      if (targetLesson.topicId !== lesson.topicId) {
        const destTopic = mockDbTopics.find(t => t.id === lesson.topicId);
        if (destTopic) {
          destTopic.lessons.push(updatedLesson);
        }
      } else {
        for (const t of mockDbTopics) {
          const idx = t.lessons.findIndex(l => l.id === id);
          if (idx !== -1) {
            t.lessons[idx] = updatedLesson;
            break;
          }
        }
      }

      setMockDbTopics([...mockDbTopics]);
      return updatedLesson;
    }
    const response = await axiosClient.put<Lesson>(`/api/admin/lessons/${id}`, lesson);
    return response.data;
  },

  async deleteLesson(id: string): Promise<void> {
    if (isMockApi()) {
      await mockDelay(300);
      mockDbTopics.forEach(t => {
        t.lessons = t.lessons.filter(l => l.id !== id);
      });
      setMockDbTopics([...mockDbTopics]);
      return;
    }
    await axiosClient.delete(`/api/admin/lessons/${id}`);
  },

  // ==========================================
  // Question Management
  // ==========================================
  async createQuestion(question: Omit<Question, 'id'>): Promise<Question> {
    if (isMockApi()) {
      await mockDelay(300);
      const newQuestion: Question = {
        ...question,
        id: Math.max(...mockDbQuestions.map(q => q.id || 0)) + 1
      };
      mockDbQuestions.push(newQuestion);
      setMockDbQuestions([...mockDbQuestions]);
      return newQuestion;
    }
    const response = await axiosClient.post<Question>('/api/admin/questions', question);
    return response.data;
  },

  async updateQuestion(id: number, question: Question): Promise<Question> {
    if (isMockApi()) {
      await mockDelay(300);
      const index = mockDbQuestions.findIndex(q => q.id === id);
      if (index === -1) throw new Error(`Question not found with id: ${id}`);

      mockDbQuestions[index] = {
        ...mockDbQuestions[index],
        text: question.text,
        options: question.options,
        correctAnswer: question.correctAnswer,
        topicId: question.topicId
      };
      setMockDbQuestions([...mockDbQuestions]);
      return mockDbQuestions[index];
    }
    const response = await axiosClient.put<Question>(`/api/admin/questions/${id}`, question);
    return response.data;
  },

  async deleteQuestion(id: number): Promise<void> {
    if (isMockApi()) {
      await mockDelay(300);
      const filtered = mockDbQuestions.filter(q => q.id !== id);
      setMockDbQuestions(filtered);
      return;
    }
    await axiosClient.delete(`/api/admin/questions/${id}`);
  }
};
