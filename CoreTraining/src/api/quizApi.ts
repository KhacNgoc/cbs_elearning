import { axiosClient, isMockApi, mockDelay } from './axiosClient';
import type { Question } from '../types';
import { mockQuestions } from '../data/mockData';

export let mockDbQuestions = JSON.parse(JSON.stringify(mockQuestions)) as Question[];
export const setMockDbQuestions = (newQuestions: Question[]) => {
  mockDbQuestions = newQuestions;
};

export interface QuizSubmitRequest {
  selectedAnswers: Record<string, number>;
  timeElapsed: number;
}

export interface QuizSubmitResponse {
  score: number;
  total: number;
  percent: number;
  correctAnswers?: Record<number, number>; // Added to resolve the frontend review gap
}

export const quizApi = {
  async getQuizQuestions(topicId: string, lessonId?: string): Promise<Omit<Question, 'correctAnswer'>[]> {
    if (isMockApi()) {
      await mockDelay(300);
      const filtered = mockDbQuestions.filter(q =>
        lessonId ? q.lessonId === lessonId : q.topicId === topicId && !q.lessonId
      );
      // Hide correctAnswer field from the returned payload to simulate backend anti-cheat logic
      return filtered.map(({ correctAnswer, ...q }) => q);
    }
    const url = lessonId
      ? `/api/quizzes/lessons/${lessonId}/questions`
      : `/api/quizzes/${topicId}/questions`;
    const response = await axiosClient.get<Omit<Question, 'correctAnswer'>[]>(url);
    return response.data;
  },

  async submitQuiz(topicId: string, data: QuizSubmitRequest, lessonId?: string): Promise<QuizSubmitResponse> {
    if (isMockApi()) {
      await mockDelay(400);
      
      const filteredOriginals = mockDbQuestions.filter(q =>
        lessonId ? q.lessonId === lessonId : q.topicId === topicId && !q.lessonId
      );
      let score = 0;
      
      filteredOriginals.forEach(q => {
        const selected = data.selectedAnswers[q.id];
        if (selected === q.correctAnswer) {
          score++;
        }
      });
      
      const total = filteredOriginals.length;
      const percent = Math.round((score / total) * 100);

      // Create mapping of correct answers for the Review section
      const correctAnswers = filteredOriginals.reduce((acc, q) => {
        acc[q.id] = q.correctAnswer;
        return acc;
      }, {} as Record<number, number>);

      // Save score & badge to LocalStorage in Mock Mode
      const currentScores = JSON.parse(localStorage.getItem('mockQuizScores') || '{}');
      const currentTotals = JSON.parse(localStorage.getItem('mockQuizTotals') || '{}');
      
      const prevHighScore = currentScores[topicId] || 0;
      if (score > prevHighScore) {
        currentScores[topicId] = score;
        currentTotals[topicId] = total;
        localStorage.setItem('mockQuizScores', JSON.stringify(currentScores));
        localStorage.setItem('mockQuizTotals', JSON.stringify(currentTotals));
      }

      if (lessonId && percent >= 80) {
        const completed = JSON.parse(localStorage.getItem('mockCompletedLessons') || '[]') as string[];
        if (!completed.includes(lessonId)) {
          completed.push(lessonId);
          localStorage.setItem('mockCompletedLessons', JSON.stringify(completed));
        }
      }

      // Check badge unlock
      let badgeType: 'bronze' | 'silver' | 'gold' | null = null;
      if (percent === 100) badgeType = 'gold';
      else if (percent > 90) badgeType = 'silver';
      else if (percent >= 80) badgeType = 'bronze';

      if (badgeType) {
        const badgeId = `${topicId}_${badgeType}`;
        const unlockedBadges = JSON.parse(localStorage.getItem('mockUnlockedBadges') || '[]');
        const alreadyHas = unlockedBadges.some((b: any) => b.id === badgeId);
        
        if (!alreadyHas) {
          unlockedBadges.push({
            id: badgeId,
            title: badgeType === 'gold' ? `Huy hiệu Vàng ${topicId}` : (badgeType === 'silver' ? `Huy hiệu Bạc ${topicId}` : `Huy hiệu Đồng ${topicId}`),
            type: badgeType,
            topicId,
            topicName: topicId,
            unlockedAt: new Date().toISOString().split('T')[0],
            icon: badgeType === 'gold' ? 'Award' : (badgeType === 'silver' ? 'Medal' : 'Shield')
          });
          localStorage.setItem('mockUnlockedBadges', JSON.stringify(unlockedBadges));
        }
      }

      return {
        score,
        total,
        percent,
        correctAnswers
      };
    }
    
    /**
     * NOTE FOR BACKEND DEVELOPERS:
     * To support the detailed answers review section after quiz completion,
     * please ensure the payload returned by `POST /api/quizzes/{topicId}/submit`
     * includes the `correctAnswers` map: Record<string, number> representing
     * { [questionId]: correctAnswerIndex }.
     */
    const url = lessonId
      ? `/api/quizzes/lessons/${lessonId}/submit`
      : `/api/quizzes/${topicId}/submit`;
    const response = await axiosClient.post<QuizSubmitResponse>(url, data);
    return response.data;
  }
};
