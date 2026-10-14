<template>
  <div class="quiz-view animate-fade-in" v-if="course">
    <!-- Quiz Header -->
    <div class="quiz-header-bar">
      <div class="header-left">
        <h1 class="quiz-title">Kiểm tra kiến thức</h1>
        <p class="quiz-subtitle">Đánh giá nhanh năng lực nghiệp vụ phân hệ {{ course.name }}</p>
      </div>
      <div class="header-right card">
        <span class="score-label">ĐIỂM CAO NHẤT</span>
        <span class="score-value">
          {{ highScore !== null ? `${highScore}/${highScoreTotal}` : 'Chưa thi' }}
        </span>
      </div>
    </div>

    <!-- Active Quiz Card -->
    <div v-if="quizStore.isLoading" class="quiz-main-card card" role="status">Đang tải bài kiểm tra...</div>
    <div v-else-if="!isReviewMode && !totalQuestions" class="quiz-main-card card">
      <h2>Bài học chưa có bài kiểm tra</h2>
      <p>Chưa có câu hỏi để làm bài. Bạn có thể quay lại tiếp tục học.</p>
      <RouterLink class="btn btn-secondary" :to="lessonId ? `/lesson/${lessonId}` : `/course/${courseId}`">Quay lại học</RouterLink>
    </div>
    <div v-else-if="!isReviewMode && !quizStore.isQuizFinished" class="quiz-main-card card">
      <!-- Question progress and timer row -->
      <div class="quiz-status-row">
        <span class="question-progress">Câu hỏi {{ quizStore.currentQuestionIndex + 1 }} / {{ totalQuestions }}</span>
        <div class="quiz-timer">
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="timer-icon"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
          <span>{{ quizStore.formattedTime }}</span>
        </div>
      </div>

      <!-- Question Text -->
      <div class="question-container" v-if="currentQuestion">
        <h2 class="question-text">{{ currentQuestion.text }}</h2>
        <p v-if="currentQuestion.type === 'MULTIPLE_CHOICE'" class="multiple-choice-note">
          Câu hỏi có nhiều đáp án đúng. Hãy chọn tất cả đáp án phù hợp.
        </p>
        
        <!-- Answer Choices -->
        <div class="options-list">
          <div 
            v-for="(option, idx) in currentQuestion.options" 
            :key="idx" 
            class="option-item cursor-pointer"
            :class="{ selected: isSelected(currentQuestion.id, idx) }"
            @click="selectOption(currentQuestion.id, idx)"
          >
            <div class="radio-indicator" :class="{ checkbox: currentQuestion.type === 'MULTIPLE_CHOICE' }">
              <span class="inner-dot" v-if="isSelected(currentQuestion.id, idx)"></span>
            </div>
            <span class="option-text">{{ option }}</span>
          </div>
        </div>
      </div>

      <!-- Footer Buttons -->
      <div class="quiz-footer-actions">
        <button 
          class="btn btn-secondary" 
          @click="quizStore.prevQuestion"
          :disabled="quizStore.currentQuestionIndex === 0"
        >
          Câu trước
        </button>
        <button 
          class="btn btn-primary" 
          @click="quizStore.nextQuestion"
          :disabled="!(selectedAnswers[currentQuestion?.id || 0]?.length)"
        >
          {{ quizStore.isLastQuestion ? 'Nộp bài' : 'Câu tiếp theo' }}
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="arrow-next"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
        </button>
      </div>
    </div>

    <div v-else-if="isReviewMode && review" class="quiz-result-card review-card card animate-fade-in">
      <div class="review-heading">
        <div>
          <span class="review-eyebrow">Bài kiểm tra đã hoàn thành</span>
          <h2>Xem lại chi tiết bài làm</h2>
          <p>
            Điểm cao nhất <strong>{{ review.score }}/{{ review.total }}</strong>
            · {{ review.percent }}% · Ngưỡng đạt {{ review.passingScore }}%
          </p>
        </div>
        <div class="review-passed-badge"><CheckCircle2 :size="20" /> Đã đạt</div>
      </div>

      <div class="review-list">
        <article
          v-for="(question, index) in review.questions"
          :key="question.id"
          class="review-question-card"
          :class="{ correct: question.correct, incorrect: !question.correct }"
        >
          <div class="review-question-status">
            <CheckCircle2 v-if="question.correct" :size="21" />
            <XCircle v-else :size="21" />
          </div>
          <div>
            <h3>Câu {{ index + 1 }}: {{ question.text }}</h3>
            <p>
              Đáp án của bạn:
              <strong>{{ formatReviewAnswers(question.options, question.selectedAnswers) }}</strong>
            </p>
            <p v-if="!question.correct" class="correct-answer">
              Đáp án đúng: <strong>{{ formatReviewAnswers(question.options, question.correctAnswers) }}</strong>
            </p>
          </div>
        </article>
      </div>

      <div class="result-actions">
        <button class="btn btn-secondary" @click="retakeQuiz">Thi lại</button>
        <router-link :to="lessonId ? `/lesson/${lessonId}` : '/'" class="btn btn-primary">
          {{ lessonId ? 'Quay lại bài học' : 'Về Trang chủ' }}
        </router-link>
      </div>
    </div>

    <!-- Quiz Result Screen -->
    <div v-else class="quiz-result-card card animate-fade-in">
      <div class="result-celebration">
        <div class="result-badge-icon" :class="getResultColorClass()">
          <svg v-if="quizResult?.passed" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="badge-icon"><path d="M12 2l3.09 6.26 6.91 1.01-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="badge-icon"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        </div>
        
        <h2>{{ getResultTitle() }}</h2>
        <p class="result-score-desc">
          Bạn đạt <strong class="score-highlight">{{ quizResult?.score }} / {{ quizResult?.total }}</strong> câu trả lời đúng ({{ quizResult?.percent }}%)
        </p>
        <p class="passing-note">Ngưỡng đạt: <strong>{{ quizResult?.passingScore ?? passingScore }}%</strong></p>
        <p class="time-spent-desc">Thời gian hoàn thành: <strong>{{ quizStore.formattedTime }}</strong></p>
      </div>

      <!-- Badge Unlocked Modal/Notification -->
      <div v-if="unlockedBadge" class="unlocked-badge-announcement card">
        <div class="glowing-ring" :class="unlockedBadge.type">
          <component :is="getBadgeIcon(unlockedBadge.icon)" class="unlocked-icon" />
        </div>
        <div class="announcement-text">
          <span class="badge-tag-label">XIN CHÚC MỪNG! BẠN ĐÃ ĐẠT ĐƯỢC:</span>
          <h3>{{ unlockedBadge.title }}</h3>
          <p>Huy hiệu đã được thêm vào hồ sơ năng lực của bạn.</p>
        </div>
      </div>

      <div class="result-actions">
        <button v-if="quizResult?.passed" class="btn btn-secondary" @click="showReview">
          Xem lại chi tiết bài làm
        </button>
        <button v-else class="btn btn-secondary" @click="retakeQuiz">Thi lại</button>
        <router-link :to="lessonId ? `/lesson/${lessonId}` : '/'" class="btn btn-primary">
          {{ lessonId ? 'Quay lại bài học' : 'Về Trang chủ' }}
        </router-link>
      </div>
    </div>
  </div>
  <div v-else class="loading-state">Đang tải thông tin bài thi...</div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useCourseStore } from '../stores/courseStore';
import { useQuizStore } from '../stores/quizStore';
import { useUserStore } from '../stores/userStore';
import { quizApi } from '../api/quizApi';
import type { QuizReview } from '../api/quizApi';
import { Award, CheckCircle2, Shield, Medal, XCircle } from 'lucide-vue-next';

const props = defineProps({
  courseId: {
    type: String,
    required: true
  }
});
const router = useRouter();
const route = useRoute();
const courseStore = useCourseStore();
const quizStore = useQuizStore();
const userStore = useUserStore();

const course = computed(() => {
  return courseStore.courses.find(t => t.id === props.courseId);
});

const totalQuestions = computed(() => quizStore.quizQuestions.length);
const currentQuestion = computed(() => quizStore.currentQuestion);
const selectedAnswers = computed(() => quizStore.selectedAnswers);
const quizResult = computed(() => quizStore.quizResult);
const lessonId = computed(() => typeof route.query.lessonId === 'string' ? route.query.lessonId : undefined);
const activeLesson = computed(() => course.value?.lessons.find(item => item.id === lessonId.value));
const passingScore = computed(() => activeLesson.value?.quizPassingScore ?? course.value?.quizPassingScore ?? 80);
const isReviewMode = ref(false);
const review = ref<QuizReview | null>(null);

const highScore = computed(() => {
  if (lessonId.value) return userStore.lessonQuizScores[lessonId.value] ?? null;
  return userStore.quizScores[props.courseId] ?? null;
});
const highScoreTotal = computed(() => {
  if (lessonId.value) return userStore.lessonQuizTotals[lessonId.value] ?? totalQuestions.value;
  return userStore.quizTotals[props.courseId] ?? totalQuestions.value;
});

// Capture recently unlocked badge if any
const unlockedBadge = ref<any | null>(null);

onMounted(async () => {
  if (courseStore.courses.length === 0) {
    await courseStore.fetchCourses();
  }
  if (userStore.mandatoryCourses.length === 0) {
    await userStore.fetchProgress();
  }
  await openQuizOrReview();
});

watch([() => props.courseId, lessonId], async () => {
  await openQuizOrReview();
});

const startNewQuiz = () => {
  if (!userStore.isCourseAccessible(props.courseId)) {
    alert("Bạn cần đăng ký học khóa học nghiệp vụ này trước khi thi!");
    router.push('/');
    return;
  }
  isReviewMode.value = false;
  review.value = null;
  unlockedBadge.value = null;
  quizStore.startQuiz(props.courseId, lessonId.value);
};

const openQuizOrReview = async () => {
  const score = lessonId.value
    ? userStore.lessonQuizScores[lessonId.value]
    : userStore.quizScores[props.courseId];
  const total = lessonId.value
    ? userStore.lessonQuizTotals[lessonId.value]
    : userStore.quizTotals[props.courseId];
  const passed = score !== undefined && total > 0
    && Math.round(score / total * 100) >= passingScore.value;
  if (passed) {
    try {
      review.value = await quizApi.getQuizReview(props.courseId, lessonId.value);
      isReviewMode.value = true;
      quizStore.stopTimer();
      return;
    } catch (error) {
      console.warn('Không thể tải dữ liệu xem lại, chuyển sang làm bài:', error);
    }
  }
  startNewQuiz();
};

const selectOption = (questionId: number, optionIdx: number) => {
  quizStore.selectAnswer(questionId, optionIdx);
};

const isSelected = (questionId: number, optionIdx: number) => {
  return selectedAnswers.value[questionId]?.includes(optionIdx) ?? false;
};

const retakeQuiz = () => {
  startNewQuiz();
};

const showReview = async () => {
  try {
    review.value = await quizApi.getQuizReview(props.courseId, lessonId.value);
    isReviewMode.value = true;
  } catch (error) {
    console.error('Không thể tải chi tiết bài làm:', error);
    alert('Không thể tải chi tiết bài làm. Vui lòng thử lại.');
  }
};

const formatReviewAnswers = (options: string[], answers?: number[]) =>
  !answers?.length
    ? 'Không chọn đáp án'
    : answers.map(answer => `${String.fromCharCode(65 + answer)}. ${options[answer] ?? ''}`).join('; ');

// Check for unlocked badge after quiz submit completes
watch(() => quizStore.isQuizFinished, (finished) => {
  if (finished && quizResult.value) {
    const percent = quizResult.value.percent;
    let badgeType: 'bronze' | 'silver' | 'gold' | null = null;
    
    if (percent === 100) badgeType = 'gold';
    else if (percent > 90) badgeType = 'silver';
    else if (percent >= 80) badgeType = 'bronze';

    if (badgeType) {
      const badgeId = `${props.courseId}_${badgeType}`;
      // Look up if we just unlocked this badge in userStore
      const badge = userStore.unlockedBadges.find(b => b.id === badgeId);
      if (badge) {
        // Show celebration announcement!
        unlockedBadge.value = badge;
      }
    }
  }
});

const getResultColorClass = () => {
  if (!quizResult.value) return '';
  if (quizResult.value.percent === 100) return 'gold';
  if (quizResult.value.percent > 90) return 'silver';
  if (quizResult.value.passed) return 'bronze';
  return 'fail';
};

const getResultTitle = () => {
  if (!quizResult.value) return '';
  const percent = quizResult.value.percent;
  if (percent === 100) return 'XUẤT SẮC - ĐẠT ĐIỂM TUYỆT ĐỐI!';
  if (percent > 90) return 'QUÁ TỐT - HOÀN THÀNH BÀI THI!';
  if (quizResult.value.passed) return 'CHÚC MỪNG - ĐÃ ĐẠT YÊU CẦU!';
  return 'CHƯA ĐẠT - HÃY ÔN LUYỆN LẠI!';
};

const getBadgeIcon = (icon: string) => {
  switch (icon) {
    case 'Award': return Award;
    case 'Medal': return Medal;
    case 'Shield': return Shield;
    default: return Shield;
  }
};
</script>

<style scoped>
.quiz-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  max-width: 800px;
  margin: 0 auto;
}

.quiz-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.quiz-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--primary-color);
}

.quiz-subtitle {
  font-size: 0.9rem;
  color: var(--text-muted);
}

.header-right {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0.5rem 1.25rem;
  background-color: var(--bg-card);
}

.score-label {
  font-size: 0.65rem;
  font-weight: 700;
  color: var(--text-muted);
  letter-spacing: 1px;
}

.score-value {
  font-size: 1.25rem;
  font-weight: 800;
  color: var(--primary-color);
}

/* Active Quiz Card */
.quiz-main-card {
  padding: 2.2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.quiz-status-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 0.75rem;
}

.question-progress {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--primary-color);
  background-color: var(--primary-light);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-sm);
}

.quiz-timer {
  display: flex;
  align-items: center;
  gap: 0.35rem;
  font-family: var(--font-family);
  font-variant-numeric: tabular-nums;
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-main);
}

.timer-icon {
  width: 18px;
  height: 18px;
  color: var(--text-muted);
}

.question-container {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  margin: 1rem 0;
}

.question-text {
  font-size: 1.35rem;
  font-weight: 700;
  color: var(--text-main);
  line-height: 1.4;
}
.multiple-choice-note { margin: -.3rem 0 1rem; color: var(--text-muted); font-size: .9rem; }

.options-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.option-item {
  display: flex;
  align-items: center;
  padding: 1.1rem 1.5rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: var(--transition);
  gap: 1rem;
}

.option-item:hover {
  background-color: var(--bg-main);
  border-color: var(--primary-color);
}

.option-item.selected {
  background-color: var(--primary-light);
  border-color: var(--primary-color);
}

.radio-indicator {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 2px solid var(--text-muted);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: var(--transition);
}

.option-item.selected .radio-indicator {
  border-color: var(--primary-color);
}

.inner-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background-color: var(--primary-color);
}

.option-text {
  font-size: 1rem;
  font-weight: 500;
  color: var(--text-main);
}

.quiz-footer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 1rem;
}

.arrow-next {
  width: 16px;
  height: 16px;
}

/* Results styles */
.quiz-result-card {
  padding: 2.5rem;
  display: flex;
  flex-direction: column;
  gap: 2rem;
  align-items: center;
}

.result-celebration {
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
}

.result-badge-icon {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 1rem;
  box-shadow: var(--shadow-md);
}

.result-badge-icon.gold {
  background: linear-gradient(135deg, #FFE082 0%, #FFB300 100%);
  color: #FF6F00;
}

.result-badge-icon.silver {
  background: linear-gradient(135deg, #E0E0E0 0%, #9E9E9E 100%);
  color: #424242;
}

.result-badge-icon.bronze {
  background: linear-gradient(135deg, #FFCC80 0%, #CA8A04 100%);
  color: #713F12;
}

.result-badge-icon.fail {
  background-color: #fee2e2;
  color: #ef4444;
}

.badge-icon {
  width: 42px;
  height: 42px;
}

.score-highlight {
  font-size: 1.35rem;
  color: var(--primary-color);
}

/* Badge unlocked panel */
.unlocked-badge-announcement {
  background: radial-gradient(circle at 10% 20%, var(--primary-light) 0%, var(--bg-card) 90%);
  border: 1.5px solid var(--accent-color);
  width: 100%;
  padding: 1.5rem;
  display: flex;
  align-items: center;
  gap: 1.5rem;
  box-shadow: 0 10px 25px rgba(212, 175, 55, 0.15);
  animation: scaleUp 0.5s cubic-bezier(0.34, 1.56, 0.64, 1) forwards;
}

@keyframes scaleUp {
  from { transform: scale(0.9); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

.glowing-ring {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 0 15px rgba(212, 175, 55, 0.4);
}

.glowing-ring.gold {
  background: linear-gradient(135deg, #FFE082 0%, #FFB300 100%);
  color: #FF6F00;
}

.glowing-ring.silver {
  background: linear-gradient(135deg, #E0E0E0 0%, #9E9E9E 100%);
  color: #424242;
}

.glowing-ring.bronze {
  background: linear-gradient(135deg, #FFCC80 0%, #CA8A04 100%);
  color: #713F12;
}

.unlocked-icon {
  width: 30px;
  height: 30px;
}

.badge-tag-label {
  font-size: 0.7rem;
  font-weight: 800;
  color: var(--accent-secondary);
  letter-spacing: 1.5px;
}

.announcement-text h3 {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-main);
  margin-top: 0.15rem;
}

.announcement-text p {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.result-actions {
  display: flex;
  gap: 1.5rem;
}

.result-actions button, .result-actions a {
  padding: 0.8rem 2rem;
  border-radius: var(--radius-full);
}

.review-card { gap: 1.25rem; }
.review-heading { width: 100%; display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; padding-bottom: 1rem; border-bottom: 1px solid var(--border-color); }
.review-eyebrow { color: var(--primary-color); font-size: .72rem; font-weight: 800; letter-spacing: .08em; text-transform: uppercase; }
.review-heading h2 { margin: .3rem 0; color: var(--text-main); }
.review-heading p { color: var(--text-muted); }
.review-passed-badge { flex: 0 0 auto; display: flex; align-items: center; gap: .35rem; padding: .5rem .75rem; border-radius: var(--radius-full); background: #e8f5ef; color: var(--primary-color); font-weight: 800; }
.review-list { width: 100%; display: grid; gap: .75rem; }
.review-question-card { display: grid; grid-template-columns: 28px minmax(0, 1fr); gap: .75rem; padding: 1rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); }
.review-question-card.correct { background: #f0fdf4; border-color: #bbf7d0; }
.review-question-card.incorrect { background: #fef2f2; border-color: #fecaca; }
.review-question-status { color: #10b981; }
.review-question-card.incorrect .review-question-status { color: #ef4444; }
.review-question-card h3 { margin: 0 0 .55rem; font-size: .95rem; line-height: 1.45; }
.review-question-card p { margin: .2rem 0; color: #52645d; font-size: .85rem; }
.review-question-card .correct-answer { color: #067647; }

@media (max-width: 600px) {
  .review-heading { flex-direction: column; }
  .review-passed-badge { align-self: flex-start; }
}
</style>
