import { defineStore } from "pinia";
import type { QuizQuestion } from "../types";
import { quizApi } from "../api/quizApi";
import type { QuizSubmitResponse } from "../api/quizApi";
import { useUserStore } from "./userStore";

export const useQuizStore = defineStore("quiz", {
  state: () => ({
    currentCourseId: null as string | null,
    currentLessonId: null as string | null,
    currentQuestionIndex: 0,
    questions: [] as QuizQuestion[],
    selectedAnswers: {} as Record<number, number[]>, // questionId -> selected option indexes
    timeElapsed: 0, // seconds
    timerIntervalId: null as any | null,
    isQuizFinished: false,
    quizResult: null as QuizSubmitResponse | null,
    isLoading: false,
  }),

  getters: {
    quizQuestions(): QuizQuestion[] {
      return this.questions;
    },
    currentQuestion(): QuizQuestion | null {
      if (
        this.questions.length === 0 ||
        this.currentQuestionIndex >= this.questions.length
      )
        return null;
      return this.questions[this.currentQuestionIndex];
    },
    isLastQuestion(): boolean {
      return this.currentQuestionIndex === this.questions.length - 1;
    },
    formattedTime(): string {
      const minutes = Math.floor(this.timeElapsed / 60);
      const seconds = this.timeElapsed % 60;
      return `${minutes.toString().padStart(2, "0")}:${seconds.toString().padStart(2, "0")}`;
    },
  },

  actions: {
    async startQuiz(courseId: string, lessonId?: string) {
      this.isLoading = true;
      this.currentCourseId = courseId;
      this.currentLessonId = lessonId || null;
      this.currentQuestionIndex = 0;
      this.selectedAnswers = {};
      this.timeElapsed = 0;
      this.isQuizFinished = false;
      this.quizResult = null;
      this.questions = [];

      this.stopTimer();

      try {
        this.questions = await quizApi.getQuizQuestions(courseId, lessonId);
        if (this.questions.length > 0) this.startTimer();
      } catch (error) {
        console.error(`Failed to load quiz questions for ${courseId}:`, error);
        this.questions = [];
      } finally {
        this.isLoading = false;
      }
    },
    selectAnswer(questionId: number, answerIndex: number) {
      const question = this.questions.find((item) => item.id === questionId);
      if (question?.type === "MULTIPLE_CHOICE") {
        const current = this.selectedAnswers[questionId] || [];
        this.selectedAnswers[questionId] = current.includes(answerIndex)
          ? current.filter((index) => index !== answerIndex)
          : [...current, answerIndex].sort();
      } else {
        this.selectedAnswers[questionId] = [answerIndex];
      }
    },
    async nextQuestion() {
      if (!this.questions.length) return;
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
      if (!this.currentCourseId || !this.questions.length) return;

      this.isLoading = true;
      try {
        const res = await quizApi.submitQuiz(
          this.currentCourseId,
          {
            selectedAnswers: this.selectedAnswers,
            timeElapsed: this.timeElapsed,
          },
          this.currentLessonId || undefined,
        );

        this.quizResult = {
          score: res.score,
          total: res.total,
          percent: res.percent,
          passingScore: res.passingScore,
          passed: res.passed,
        };

        this.isQuizFinished = true;

        // Force reload the user's progress from the API to update badges, points, and radar chart!
        const userStore = useUserStore();
        await userStore.fetchProgress();
      } catch (error) {
        console.error("Failed to submit quiz:", error);
      } finally {
        this.isLoading = false;
      }
    },
  },
});
