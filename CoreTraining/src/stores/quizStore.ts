import { defineStore } from 'pinia';
import type { Question } from '../types';
import { quizApi } from '../api/quizApi';
import { useUserStore } from './userStore';

export const useQuizStore = defineStore('quiz', {
  state: () => ({
    currentTopicId: null as string | null,
    currentLessonId: null as string | null,
    currentQuestionIndex: 0,
    questions: [] as Omit<Question, 'correctAnswer'>[],
    selectedAnswers: {} as Record<number, number>, // questionId -> optionIndex
    correctAnswers: {} as Record<number, number>, // questionId -> correctAnswerIndex (returned after submit)
    timeElapsed: 0, // seconds
    timerIntervalId: null as any | null,
    isQuizFinished: false,
    quizResult: null as { score: number; total: number; percent: number } | null,
    isLoading: false
  }),

  getters: {
    quizQuestions(): Omit<Question, 'correctAnswer'>[] {
      return this.questions;
    },
    currentQuestion(): Omit<Question, 'correctAnswer'> | null {
      if (this.questions.length === 0 || this.currentQuestionIndex >= this.questions.length) return null;
      return this.questions[this.currentQuestionIndex];
    },
    isLastQuestion(): boolean {
      return this.currentQuestionIndex === this.questions.length - 1;
    },
    formattedTime(): string {
      const minutes = Math.floor(this.timeElapsed / 60);
      const seconds = this.timeElapsed % 60;
      return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
    }
  },

  actions: {
    async startQuiz(topicId: string, lessonId?: string) {
      this.isLoading = true;
      this.currentTopicId = topicId;
      this.currentLessonId = lessonId || null;
      this.currentQuestionIndex = 0;
      this.selectedAnswers = {};
      this.correctAnswers = {};
      this.timeElapsed = 0;
      this.isQuizFinished = false;
      this.quizResult = null;
      
      this.stopTimer();
      
      try {
        this.questions = await quizApi.getQuizQuestions(topicId, lessonId);
        this.startTimer();
      } catch (error) {
        console.error(`Failed to load quiz questions for ${topicId}:`, error);
        this.questions = [];
      } finally {
        this.isLoading = false;
      }
    },
    selectAnswer(questionId: number, answerIndex: number) {
      this.selectedAnswers[questionId] = answerIndex;
    },
    async nextQuestion() {
      if (this.currentQuestionIndex < this.questions.length - 1) {
        this.currentQuestionIndex++;
      } else {
        await this.submitQuiz();
      }
    },
    prevQuestion() {
      if (this.currentQuestionIndex > 0) {
        this.currentQuestionIndex--;
      }
    },
    startTimer() {
      this.timerIntervalId = setInterval(() => {
        this.timeElapsed++;
      }, 1000);
    },
    stopTimer() {
      if (this.timerIntervalId) {
        clearInterval(this.timerIntervalId);
        this.timerIntervalId = null;
      }
    },
    async submitQuiz() {
      this.stopTimer();
      if (!this.currentTopicId) return;

      this.isLoading = true;
      try {
        const res = await quizApi.submitQuiz(this.currentTopicId, {
          selectedAnswers: this.selectedAnswers,
          timeElapsed: this.timeElapsed
        }, this.currentLessonId || undefined);

        this.quizResult = {
          score: res.score,
          total: res.total,
          percent: res.percent
        };
        
        // Save the correct answers map to render the review UI
        this.correctAnswers = res.correctAnswers || {};
        this.isQuizFinished = true;

        // Force reload the user's progress from the API to update badges, points, and radar chart!
        const userStore = useUserStore();
        await userStore.fetchProgress();
      } catch (error) {
        console.error('Failed to submit quiz:', error);
      } finally {
        this.isLoading = false;
      }
    }
  }
});
