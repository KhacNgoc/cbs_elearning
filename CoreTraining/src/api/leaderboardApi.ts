import { axiosClient, isMockApi, mockDelay } from './axiosClient';
import type { LeaderboardUser } from '../types';
import { mockLeaderboard } from '../data/mockData';

export const leaderboardApi = {
  async getLeaderboard(): Promise<LeaderboardUser[]> {
    if (isMockApi()) {
      await mockDelay(300);
      
      // Load current user details to position them correctly in mock leaderboard
      const mockCompleted = JSON.parse(localStorage.getItem('mockCompletedLessons') || '[]');
      const mockBadges = JSON.parse(localStorage.getItem('mockUnlockedBadges') || '[]');
      const mockScores = JSON.parse(localStorage.getItem('mockQuizScores') || '{}');
      
      // Calculate current user mock points
      let points = 1000;
      points += mockCompleted.length * 100;
      Object.keys(mockScores).forEach(t => {
        points += mockScores[t] * 50;
      });
      mockBadges.forEach((b: any) => {
        points += b.type === 'gold' ? 300 : (b.type === 'silver' ? 200 : 100);
      });

      const updatedList = mockLeaderboard.map(user => {
        if (user.username === 'quantrh') {
          return {
            ...user,
            points,
            badgesCount: mockBadges.length,
            completedLessonsCount: mockCompleted.length
          };
        }
        return user;
      });

      const sorted = [...updatedList].sort((a, b) => b.points - a.points);
      return sorted.map((user, idx) => ({
        ...user,
        rank: idx + 1
      }));
    }
    const response = await axiosClient.get<LeaderboardUser[]>('/api/leaderboard');
    return response.data;
  }
};
