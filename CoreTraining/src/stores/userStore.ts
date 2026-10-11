import { defineStore } from 'pinia';
import type { LessonProgressRequest, UserProgress } from '../types';
import { useCourseStore } from './courseStore';
import { authApi } from '../api/authApi';
import type { LoginRequest } from '../api/authApi';
import { courseApi } from '../api/courseApi';
import { userApi } from '../api/userApi';
import router from '../router';
import { isMockApi } from '../api/axiosClient';
import { clearAuthSession, setCachedAuthSession } from '../utils/authSession';
import { showToast } from '../utils/toast';

let progressRequest: Promise<void> | null = null;

export const useUserStore = defineStore('user', {
  state: (): UserProgress & { isAuthenticated: boolean; role: string; username: string; fullName: string; mustChangePassword: boolean } => ({
    completedLessons: [],
    quizScores: {},
    quizTotals: {},
    lessonQuizScores: {},
    lessonQuizTotals: {},
    lessonProgress: {},
    points: 0,
    unlockedBadges: [],
    groupId: '',
    groupName: '',
    mandatoryCourses: [],
    registeredCourses: [],
    pendingCourses: [],
    isAuthenticated: !!localStorage.getItem('username'),
    role: localStorage.getItem('userRole') || '',
    username: localStorage.getItem('username') || '',
    fullName: localStorage.getItem('fullName') || '',
    mustChangePassword: localStorage.getItem('mustChangePassword') === 'true'
  }),

  getters: {
    totalLessonsCount(): number {
      return useCourseStore().courses.reduce((acc, course) => acc + course.lessons.length, 0);
    },
    completedLessonsCount(): number {
      return this.completedLessons.length;
    },
    overallProgressPercent(): number {
      const total = this.totalLessonsCount;
      if (total === 0) return 0;
      return Math.round((this.completedLessons.length / total) * 100);
    },
    getCourseProgressPercent: (state) => (courseId: string): number => {
      const course = useCourseStore().courses.find(t => t.id === courseId);
      if (!course || course.lessons.length === 0) return 0;

      const completedInCourse = course.lessons.filter(lesson =>
        state.completedLessons.includes(lesson.id)
      ).length;

      return Math.round((completedInCourse / course.lessons.length) * 100);
    },
    getCourseQuizScore: (state) => (courseId: string): { score: number, total: number, percent: number } | null => {
      if (state.quizScores[courseId] === undefined) return null;
      const score = state.quizScores[courseId];
      const total = state.quizTotals[courseId] || 5;
      return {
        score,
        total,
        percent: Math.round((score / total) * 100)
      };
    },
    isCourseAccessible: (state) => (courseId: string): boolean => {
      if (state.role === 'ROLE_ADMIN') return true;
      return state.mandatoryCourses.includes(courseId) || state.registeredCourses.includes(courseId);
    }
  },

  actions: {
    async login(credentials: LoginRequest) {
      try {
        const res = await authApi.login(credentials);
        if (isMockApi() && res.accessToken) localStorage.setItem('accessToken', res.accessToken);
        localStorage.setItem('userRole', res.role);
        localStorage.setItem('username', res.username);
        localStorage.setItem('fullName', res.fullName);
        localStorage.setItem('mustChangePassword', res.mustChangePassword ? 'true' : 'false');

        this.isAuthenticated = true;
        this.role = res.role;
        this.username = res.username;
        this.fullName = res.fullName;
        this.mustChangePassword = res.mustChangePassword;
        setCachedAuthSession({
          role: res.role,
          username: res.username,
          fullName: res.fullName,
          mustChangePassword: res.mustChangePassword,
        });

        await this.fetchProgress();
        router.push(this.mustChangePassword ? '/change-password' : '/');
      } catch (error) {
        console.error('Login failed:', error);
        throw error;
      }
    },

    async logout() {
      try {
        await authApi.logout();
      } catch (error) {
        console.error('Server logout failed:', error);
      }
      clearAuthSession();
      localStorage.removeItem('accessToken');
      localStorage.removeItem('userRole');
      localStorage.removeItem('username');
      localStorage.removeItem('fullName');
      localStorage.removeItem('mustChangePassword');

      this.isAuthenticated = false;
      this.role = '';
      this.username = '';
      this.fullName = '';
      this.mustChangePassword = false;
      this.completedLessons = [];
      this.quizScores = {};
      this.quizTotals = {};
      this.lessonQuizScores = {};
      this.lessonQuizTotals = {};
      this.lessonProgress = {};
      this.points = 0;
      this.unlockedBadges = [];
      this.groupId = '';
      this.groupName = '';
      this.mandatoryCourses = [];
      this.registeredCourses = [];
      this.pendingCourses = [];

      useCourseStore().$reset();
      router.push('/login');
    },

    async fetchProgress() {
      if (!this.isAuthenticated) return;
      if (progressRequest) return progressRequest;
      progressRequest = (async () => {
        try {
        const [progress, profile] = await Promise.all([
          courseApi.getMyProgress(),
          userApi.getMyProfile()
        ]);

        this.completedLessons = progress.completedLessons || [];
        this.quizScores = progress.quizScores || {};
        this.quizTotals = progress.quizTotals || {};
        this.lessonQuizScores = progress.lessonQuizScores || {};
        this.lessonQuizTotals = progress.lessonQuizTotals || {};
        this.lessonProgress = progress.lessonProgress || {};
        this.points = progress.points || 0;
        this.unlockedBadges = progress.unlockedBadges || [];
        this.groupId = progress.groupId || '';
        this.groupName = progress.groupName || '';
        this.mandatoryCourses = progress.mandatoryCourses || [];
        this.registeredCourses = progress.registeredCourses || [];
        this.pendingCourses = progress.pendingCourses || [];
        this.mustChangePassword = progress.mustChangePassword || false;

        this.fullName = profile.fullName || '';
        localStorage.setItem('fullName', this.fullName);
        localStorage.setItem('mustChangePassword', this.mustChangePassword ? 'true' : 'false');

        // Save progress to localstorage for mock caching if active
        localStorage.setItem('mockCompletedLessons', JSON.stringify(this.completedLessons));
        } catch (error) {
          console.error('Failed to fetch user progress:', error);
          showToast('Không thể tải thông tin và tiến độ học tập. Vui lòng thử lại.', 'error');
        }
      })();
      try {
        await progressRequest;
      } finally {
        progressRequest = null;
      }
    },

    async completeLesson(lessonId: string) {
      if (!this.isAuthenticated) return;
      try {
        const updatedProgress = await courseApi.markComplete(lessonId);
        this.completedLessons = updatedProgress.completedLessons;
        this.points = updatedProgress.points;

        // Save mock complete to local cache
        localStorage.setItem('mockCompletedLessons', JSON.stringify(this.completedLessons));
      } catch (error) {
        console.error('Failed to mark lesson complete:', error);
        throw error;
      }
    },

    async resetLesson(lessonId: string) {
      if (!this.isAuthenticated) return;
      try {
        const updatedProgress = await courseApi.resetLesson(lessonId);
        this.completedLessons = updatedProgress.completedLessons || [];
        this.lessonProgress = updatedProgress.lessonProgress || {};
        this.points = updatedProgress.points;
        localStorage.setItem('mockCompletedLessons', JSON.stringify(this.completedLessons));
      } catch (error) {
        console.error('Failed to reset lesson:', error);
        throw error;
      }
    },

    async saveLessonProgress(lessonId: string, request: LessonProgressRequest) {
      if (!this.isAuthenticated) return null;
      try {
        const progress = await courseApi.saveLessonProgress(lessonId, request);
        this.lessonProgress = { ...(this.lessonProgress || {}), [lessonId]: progress };
        return progress;
      } catch (error) {
        console.error('Failed to save lesson progress:', error);
        return null;
      }
    },

    async registerCourse(courseId: string) {
      if (!this.isAuthenticated) return;
      try {
        const updatedProgress = await courseApi.registerCourse(courseId);
        this.registeredCourses = updatedProgress.registeredCourses || [];
        this.pendingCourses = updatedProgress.pendingCourses || [];
        this.mandatoryCourses = updatedProgress.mandatoryCourses || [];
        this.groupId = updatedProgress.groupId || '';
        this.groupName = updatedProgress.groupName || '';
      } catch (error) {
        console.error('Failed to register course:', error);
        throw error;
      }
    }
  }
});
