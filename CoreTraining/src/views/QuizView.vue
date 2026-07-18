<template>
  <div class="quiz-view animate-fade-in" v-if="topic">
    <!-- Quiz Header -->
    <div class="quiz-header-bar">
      <div class="header-left">
        <h1 class="quiz-title">Kiểm tra kiến thức</h1>
        <p class="quiz-subtitle">Đánh giá nhanh năng lực nghiệp vụ phân hệ {{ topic.name }}</p>
      </div>
      <div class="header-right card">
        <span class="score-label">ĐIỂM CAO NHẤT</span>
        <span class="score-value">
          {{ highScore ? `${highScore}/${totalQuestions}` : 'Chưa thi' }}
        </span>
      </div>
    </div>

    <!-- Active Quiz Card -->
    <div v-if="!quizStore.isQuizFinished" class="quiz-main-card card">
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
        
        <!-- Answer Choices -->
        <div class="options-list">
          <div 
            v-for="(option, idx) in currentQuestion.options" 
            :key="idx" 
            class="option-item"
            :class="{ selected: isSelected(currentQuestion.id, idx) }"
            @click="selectOption(currentQuestion.id, idx)"
          >
            <div class="radio-indicator">
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
          :disabled="selectedAnswers[currentQuestion?.id || 0] === undefined"
        >
          {{ quizStore.isLastQuestion ? 'Nộp bài' : 'Câu tiếp theo' }}
          <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="arrow-next"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
        </button>
      </div>
    </div>

    <!-- Quiz Result Screen -->
    <div v-else class="quiz-result-card card animate-fade-in">
      <div class="result-celebration">
        <div class="result-badge-icon" :class="getResultColorClass()">
          <svg v-if="quizResult && quizResult.percent >= 80" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="badge-icon"><path d="M12 2l3.09 6.26 6.91 1.01-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>
          <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="badge-icon"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
        </div>
        
        <h2>{{ getResultTitle() }}</h2>
        <p class="result-score-desc">
          Bạn đạt <strong class="score-highlight">{{ quizResult?.score }} / {{ quizResult?.total }}</strong> câu trả lời đúng ({{ quizResult?.percent }}%)
        </p>
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

      <!-- Review Questions Answers details -->
      <div class="result-review-list">
        <h3>CHI TIẾT BÀI LÀM</h3>
        <div 
          v-for="(q, idx) in quizStore.quizQuestions" 
          :key="q.id" 
          class="review-item"
          :class="{ correct: selectedAnswers[q.id] === quizStore.correctAnswers[q.id], incorrect: selectedAnswers[q.id] !== quizStore.correctAnswers[q.id] }"
        >
          <div class="review-status">
            <svg v-if="selectedAnswers[q.id] === quizStore.correctAnswers[q.id]" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="status-icon"><polyline points="20 6 9 17 4 12"/></svg>
            <svg v-else xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" class="status-icon"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </div>
          <div class="review-content">
            <p class="review-question"><strong>Câu {{ idx + 1 }}:</strong> {{ q.text }}</p>
            <p class="review-answer">Đáp án của bạn: <span class="selected-ans">{{ q.options[selectedAnswers[q.id]] }}</span></p>
            <p class="review-correct" v-if="selectedAnswers[q.id] !== quizStore.correctAnswers[q.id]">
              Đáp án đúng: <span class="correct-ans">{{ q.options[quizStore.correctAnswers[q.id]] }}</span>
            </p>
          </div>
        </div>
      </div>

      <div class="result-actions">
        <button class="btn btn-secondary" @click="retakeQuiz">Thi lại</button>
        <router-link to="/" class="btn btn-primary">Về Trang chủ</router-link>
      </div>
    </div>
  </div>
  <div v-else class="loading-state">Đang tải thông tin bài thi...</div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import { useCourseStore } from '../stores/courseStore';
import { useQuizStore } from '../stores/quizStore';
import { useUserStore } from '../stores/userStore';
import { Award, Shield, Medal } from 'lucide-vue-next';

const props = defineProps({
  topicId: {
    type: String,
    required: true
  }
});
const courseStore = useCourseStore();
const quizStore = useQuizStore();
const userStore = useUserStore();

const topic = computed(() => {
  return courseStore.topics.find(t => t.id === props.topicId);
});

const totalQuestions = computed(() => quizStore.quizQuestions.length);
const currentQuestion = computed(() => quizStore.currentQuestion);
const selectedAnswers = computed(() => quizStore.selectedAnswers);
const quizResult = computed(() => quizStore.quizResult);

const highScore = computed(() => {
  return userStore.quizScores[props.topicId] || null;
});

// Capture recently unlocked badge if any
const unlockedBadge = ref<any | null>(null);

onMounted(() => {
  startNewQuiz();
});

watch(() => props.topicId, () => {
  startNewQuiz();
});

const startNewQuiz = () => {
  unlockedBadge.value = null;
  quizStore.startQuiz(props.topicId);
};

const selectOption = (questionId: number, optionIdx: number) => {
  quizStore.selectAnswer(questionId, optionIdx);
};

const isSelected = (questionId: number, optionIdx: number) => {
  return selectedAnswers.value[questionId] === optionIdx;
};

const retakeQuiz = () => {
  startNewQuiz();
};

// Check for unlocked badge after quiz submit completes
watch(() => quizStore.isQuizFinished, (finished) => {
  if (finished && quizResult.value) {
    const percent = quizResult.value.percent;
    let badgeType: 'bronze' | 'silver' | 'gold' | null = null;
    
    if (percent === 100) badgeType = 'gold';
    else if (percent > 90) badgeType = 'silver';
    else if (percent >= 80) badgeType = 'bronze';

    if (badgeType) {
      const badgeId = `${props.topicId}_${badgeType}`;
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
  if (quizResult.value.percent >= 80) return 'bronze';
  return 'fail';
};

const getResultTitle = () => {
  if (!quizResult.value) return '';
  const percent = quizResult.value.percent;
  if (percent === 100) return 'XUẤT SẮC - ĐẠT ĐIỂM TUYỆT ĐỐI!';
  if (percent > 90) return 'QUÁ TỐT - HOÀN THÀNH BÀI THI!';
  if (percent >= 80) return 'CHÚC MỪNG - ĐÃ ĐẠT YÊU CẦU!';
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
  font-family: monospace;
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

/* Review questions list */
.result-review-list {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1rem 0;
}

.result-review-list h3 {
  font-size: 1rem;
  font-weight: 700;
  border-bottom: 1.5px solid var(--border-color);
  padding-bottom: 0.5rem;
  color: var(--text-main);
}

.review-item {
  display: flex;
  gap: 1rem;
  padding: 1.2rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
}

.review-item.correct {
  background-color: #f0fdf4;
  border-color: #bbf7d0;
}

.review-item.incorrect {
  background-color: #fef2f2;
  border-color: #fecaca;
}

.review-status {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
}

.correct .review-status {
  background-color: #10b981;
}

.incorrect .review-status {
  background-color: #ef4444;
}

.status-icon {
  width: 14px;
  height: 14px;
}

.review-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.review-question {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-main);
}

.review-answer, .review-correct {
  font-size: 0.85rem;
}

.selected-ans {
  font-weight: 600;
}

.correct .selected-ans {
  color: #065f46;
}

.incorrect .selected-ans {
  color: #991b1b;
}

.correct-ans {
  color: #065f46;
  font-weight: 600;
}

.result-actions {
  display: flex;
  gap: 1.5rem;
}

.result-actions button, .result-actions a {
  padding: 0.8rem 2rem;
  border-radius: var(--radius-full);
}
</style>
