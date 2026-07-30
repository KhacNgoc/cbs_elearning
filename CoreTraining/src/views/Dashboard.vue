<template>
  <div class="dashboard-view animate-fade-in">
    <!-- Welcome Header Banner -->
    <div class="welcome-banner card">
      <div class="welcome-text">
        <span class="banner-tag">TỔNG QUAN HỌC TẬP</span>
        <h1>Xin chào, {{ username }} <span class="user-group-tag" v-if="userStore.groupName">({{ userStore.groupName }})</span></h1>
        <p class="last-lesson-info">
          Bài học xem gần nhất: 
          <span class="lesson-link" v-if="lastLesson" @click="goToLesson(lastLesson.id)">{{ lastLesson.title }}</span>
          <span v-else>Chưa xem bài nào</span>
        </p>
      </div>
      <button class="btn btn-primary btn-learn" @click="resumeLearning">
        TIẾP TỤC HỌC
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="btn-icon"><line x1="5" y1="12" x2="19" y2="12"/><polyline points="12 5 19 12 12 19"/></svg>
      </button>
    </div>

    <!-- Main Content Section: Grid of Topics vs Progress & Analytics -->
    <div class="dashboard-grid">
      
      <!-- Left Column: Topics Grid & Radar Chart -->
      <div class="left-col">
        <h2 class="section-title">CHỦ ĐỀ ĐÀO TẠO</h2>
        
        <!-- Grid of 7 banking topics -->
        <div class="topics-grid">
          <div 
            v-for="topic in courseStore.topics" 
            :key="topic.id" 
            class="topic-card card card-hover"
            :class="{ 'topic-locked': !userStore.isTopicAccessible(topic.id) }"
            @click="handleTopicClick(topic)"
          >
            <div class="topic-icon-box">
              <component :is="getIconComponent(topic.iconName)" class="topic-icon" />
            </div>
            <div class="topic-details">
              <div class="topic-header-row">
                <h3>{{ topic.name }}</h3>
                <span 
                  v-if="userStore.mandatoryTopics.includes(topic.id)" 
                  class="badge badge-mandatory"
                >Bắt buộc</span>
                <span 
                  v-else-if="userStore.registeredTopics.includes(topic.id)" 
                  class="badge badge-optional"
                >Đã đăng ký</span>
                <span
                  v-else-if="userStore.pendingTopics.includes(topic.id)"
                  class="badge badge-pending"
                >Chờ duyệt</span>
                <span 
                  v-else 
                  class="badge badge-locked"
                >Chưa đăng ký</span>
              </div>
              <span class="lesson-count">{{ topic.lessons.length }} bài học</span>
            </div>
            <div class="topic-action">
              <Lock v-if="!userStore.isTopicAccessible(topic.id)" class="lock-icon" />
              <ChevronRight v-else class="arrow-icon" />
            </div>
          </div>
        </div>

        <!-- Competency Analysis Radar Chart -->
        <div class="radar-section card">
          <h3 class="card-title">PHÂN TÍCH NĂNG LỰC</h3>
          <p class="card-subtitle">Biểu đồ đánh giá năng lực nghiệp vụ dựa trên kết quả thi</p>
          <div class="radar-chart-container">
            <RadarChart />
          </div>
        </div>
      </div>

      <!-- Right Column: Overall Progress & Badges Showcase -->
      <div class="right-col">
        <!-- Overall Progress Card -->
        <div class="progress-section card">
          <h3 class="card-title">TIẾN ĐỘ TỔNG THỂ</h3>
          
          <div class="overall-progress-big">
            <span class="progress-pct">{{ userStore.overallProgressPercent }}%</span>
            <span class="progress-ratio">{{ userStore.completedLessonsCount }} / {{ userStore.totalLessonsCount }} ĐÃ HỌC</span>
          </div>
          
          <ProgressBar :progress="userStore.overallProgressPercent" height="12px" class="main-prog-bar" />
          
          <div class="topic-progress-list">
            <div v-for="topic in courseStore.topics" :key="topic.id" class="topic-prog-item">
              <div class="topic-prog-header">
                <span>{{ topic.name }}</span>
                <span>{{ userStore.getTopicProgressPercent(topic.id) }}%</span>
              </div>
              <ProgressBar :progress="userStore.getTopicProgressPercent(topic.id)" height="6px" />
              <div
                class="topic-course-status"
                :class="{ completed: userStore.getTopicProgressPercent(topic.id) === 100 }"
              >
                <CheckCircle2 v-if="userStore.getTopicProgressPercent(topic.id) === 100" :size="15" />
                <CircleDashed v-else :size="15" />
                <span>
                  {{ userStore.getTopicProgressPercent(topic.id) === 100
                    ? 'Đã hoàn thành khóa học'
                    : 'Chưa hoàn thành khóa học' }}
                </span>
              </div>
            </div>
          </div>
        </div>

        <!-- Achievements & Badges Card -->
        <div class="badges-section card">
          <div class="badges-header">
            <h3 class="card-title">HUY HIỆU ĐÃ ĐẠT</h3>
            <span class="points-badge">{{ userStore.points }} PTS</span>
          </div>
          <p class="card-subtitle">Hoàn thành bài thi tối thiểu 80% để mở khóa</p>
          
          <div class="badges-mini-grid" v-if="userStore.unlockedBadges.length > 0">
            <div 
              v-for="badge in userStore.unlockedBadges" 
              :key="badge.id" 
              class="badge-mini-card"
              :class="badge.type"
            >
              <component :is="getBadgeIcon(badge.icon)" class="badge-mini-icon" />
              <div class="badge-mini-tooltip">
                <strong>{{ badge.title }}</strong>
                <span>Nhận ngày {{ badge.unlockedAt }}</span>
              </div>
            </div>
          </div>
          <div class="empty-badges" v-else>
            <Award class="empty-icon" />
            <p>Chưa có huy hiệu nào. Hãy hoàn thành các bài thi thử đạt điểm cao nhé!</p>
          </div>
          <router-link to="/leaderboard" class="view-all-link">Bảng xếp hạng & Huy hiệu -></router-link>
        </div>
      </div>
      
    </div>

    <Teleport to="body">
      <!-- Registration Confirmation Modal -->
      <div class="modal-overlay animate-fade-in" v-if="showRegisterModal" @click.self="showRegisterModal = false">
        <div class="modal-card card animate-scale-up" role="dialog" aria-modal="true" aria-labelledby="register-course-title">
          <div class="modal-header">
            <div class="modal-icon-box">
              <BookOpen class="modal-icon" />
            </div>
            <h2 id="register-course-title">Đăng ký học Nghiệp vụ</h2>
          </div>
          <div class="modal-body">
            <p>Bạn có muốn đăng ký học thêm chủ đề nghiệp vụ <strong>{{ selectedTopicToRegister?.name }}</strong> không?</p>
            <p class="modal-note">Yêu cầu sẽ được gửi đến quản trị viên. Khóa học được mở sau khi được phê duyệt.</p>
          </div>
          <div class="modal-footer">
            <button class="btn btn-secondary" @click="showRegisterModal = false" :disabled="isRegistering">HỦY BỎ</button>
            <button class="btn btn-primary btn-confirm" @click="handleRegisterTopic" :disabled="isRegistering">
              <span v-if="isRegistering" class="spinner"></span>
              <span v-else>GỬI YÊU CẦU ĐĂNG KÝ</span>
            </button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useCourseStore } from '../stores/courseStore';
import { useUserStore } from '../stores/userStore';
import ProgressBar from '../components/ProgressBar.vue';
import RadarChart from '../components/RadarChart.vue';
import { 
  Coins, 
  ArrowLeftRight, 
  PiggyBank, 
  Users, 
  Wallet, 
  BookOpen, 
  CreditCard, 
  ChevronRight, 
  Award,
  Shield,
  Medal,
  Lock,
  CheckCircle2,
  CircleDashed
} from 'lucide-vue-next';

const router = useRouter();
const courseStore = useCourseStore();
const userStore = useUserStore();

const showRegisterModal = ref(false);
const selectedTopicToRegister = ref<{ id: string, name: string } | null>(null);
const isRegistering = ref(false);

onMounted(async () => {
  await Promise.all([
    userStore.fetchProgress(),
    courseStore.fetchTopics()
  ]);
});

const username = computed(() => userStore.fullName || userStore.username || 'quantrh');

// Grab last lesson in computed, let's use the first completed lesson as mock, or first lesson overall
const lastLesson = computed(() => {
  if (courseStore.currentLessonId) {
    return courseStore.allLessons.find(l => l.id === courseStore.currentLessonId) || null;
  }
  return null;
});

const resumeLearning = () => {
  if (lastLesson.value) {
    router.push(`/lesson/${lastLesson.value.id}`);
  } else {
    // Go to first lesson of first topic that is accessible
    const firstAccessibleTopic = courseStore.topics.find(t => userStore.isTopicAccessible(t.id));
    if (firstAccessibleTopic && firstAccessibleTopic.lessons.length > 0) {
      router.push(`/lesson/${firstAccessibleTopic.lessons[0].id}`);
    } else {
      const firstTopic = courseStore.topics[0];
      if (firstTopic && firstTopic.lessons.length > 0) {
        router.push(`/lesson/${firstTopic.lessons[0].id}`);
      }
    }
  }
};

const goToLesson = (lessonId: string) => {
  router.push(`/lesson/${lessonId}`);
};

const goToTopicFirstLesson = (topic: any) => {
  if (topic.lessons.length > 0) {
    router.push(`/lesson/${topic.lessons[0].id}`);
  }
};
const handleTopicClick = (topic: any) => {
  if (userStore.isTopicAccessible(topic.id)) {
    goToTopicFirstLesson(topic);
  } else if (userStore.pendingTopics.includes(topic.id)) {
    alert('Yêu cầu đăng ký khóa học đang chờ quản trị viên phê duyệt.');
  } else {
    openRegisterConfirm(topic);
  }
};

const openRegisterConfirm = (topic: any) => {
  selectedTopicToRegister.value = topic;
  showRegisterModal.value = true;
};

const handleRegisterTopic = async () => {
  if (!selectedTopicToRegister.value) return;
  isRegistering.value = true;
  try {
    await userStore.registerTopic(selectedTopicToRegister.value.id);
    showRegisterModal.value = false;
    selectedTopicToRegister.value = null;
  } catch (error) {
    alert('Đăng ký không thành công. Vui lòng kiểm tra lại kết nối!');
  } finally {
    isRegistering.value = false;
  }
};

// Map string icon names to Lucide components
const getIconComponent = (iconName: string) => {
  switch (iconName) {
    case 'Coins': return Coins;
    case 'ArrowLeftRight': return ArrowLeftRight;
    case 'PiggyBank': return PiggyBank;
    case 'Users': return Users;
    case 'Wallet': return Wallet;
    case 'BookOpen': return BookOpen;
    case 'CreditCard': return CreditCard;
    default: return BookOpen;
  }
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
.dashboard-view {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.welcome-banner {
  background: linear-gradient(135deg, var(--primary-color) 0%, #004632 100%);
  color: white;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border: none;
  padding: 2.2rem;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-lg);
  position: relative;
  overflow: hidden;
}

.welcome-banner::before {
  content: '';
  position: absolute;
  top: -50%;
  right: -20%;
  width: 350px;
  height: 350px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(212, 175, 55, 0.15) 0%, transparent 70%);
  pointer-events: none;
}

.banner-tag {
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--accent-color);
  letter-spacing: 1.5px;
  background-color: rgba(255, 255, 255, 0.1);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-full);
}

.welcome-text h1 {
  font-size: 2.2rem;
  font-weight: 700;
  margin-top: 0.5rem;
  margin-bottom: 0.25rem;
}

.last-lesson-info {
  color: rgba(255, 255, 255, 0.8);
  font-size: 0.95rem;
}

.lesson-link {
  color: var(--accent-color);
  font-weight: 600;
  cursor: pointer;
  text-decoration: underline;
}

.btn-learn {
  padding: 0.9rem 2.2rem;
  border-radius: var(--radius-full);
  box-shadow: 0 4px 15px rgba(0, 0, 0, 0.15);
  font-size: 1rem;
  background-color: white;
  color: var(--primary-color);
  border: 1.5px solid transparent;
}

.btn-learn:hover {
  background-color: var(--accent-color);
  color: #000;
  transform: translateY(-2px);
}

.btn-icon {
  width: 18px;
  height: 18px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 2rem;
}

.left-col, .right-col {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.section-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: var(--text-main);
  letter-spacing: 0.5px;
  border-left: 4px solid var(--primary-color);
  padding-left: 0.75rem;
  margin-bottom: -0.5rem;
}

.topics-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.85rem;
}

.topic-card {
  display: flex;
  align-items: center;
  padding: 1.1rem 1.5rem;
  gap: 1.25rem;
  cursor: pointer;
}

.topic-icon-box {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background-color: var(--primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
}

.topic-icon {
  width: 24px;
  height: 24px;
}

.topic-details {
  flex: 1;
}

.topic-details h3 {
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text-main);
}

.lesson-count {
  font-size: 0.85rem;
  color: var(--text-muted);
  font-weight: 500;
}

.arrow-icon {
  color: var(--text-muted);
  width: 20px;
  height: 20px;
  transition: var(--transition);
}

.topic-card:hover .arrow-icon {
  color: var(--primary-color);
  transform: translateX(4px);
}

.radar-section {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  min-height: 400px;
}

.card-title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-main);
}

.card-subtitle {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-bottom: 0.5rem;
}

.radar-chart-container {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
}

/* Right Column Styles */
.progress-section {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.overall-progress-big {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  margin-top: 0.5rem;
}

.progress-pct {
  font-size: 3.5rem;
  font-weight: 800;
  color: var(--primary-color);
  line-height: 1;
}

.progress-ratio {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--text-muted);
  letter-spacing: 0.5px;
}

.main-prog-bar {
  margin-bottom: 1.5rem;
}

.topic-progress-list {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.topic-prog-item {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.topic-prog-header {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-main);
}

.topic-course-status {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: #64748b;
  margin-top: 0.15rem;
  background-color: var(--bg-main);
  padding: 0.35rem 0.5rem;
  border-radius: var(--radius-sm);
  font-weight: 600;
}

.topic-course-status.completed {
  background: #e8f5ef;
  color: var(--primary-color);
}


/* Badges section */
.badges-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.points-badge {
  background-color: var(--accent-color);
  color: #000;
  font-weight: 800;
  font-size: 0.8rem;
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-full);
  box-shadow: var(--shadow-sm);
}

.badges-mini-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 0.75rem;
  margin: 1rem 0;
}

.badge-mini-card {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  cursor: pointer;
  box-shadow: var(--shadow-sm);
  transition: var(--transition);
}

.badge-mini-card:hover {
  transform: scale(1.1) rotate(5deg);
  z-index: 10;
}

.badge-mini-card.gold {
  background: linear-gradient(135deg, #FFE082 0%, #FFB300 100%);
  border: 2.5px solid #FF8F00;
  color: #FF6F00;
}

.badge-mini-card.silver {
  background: linear-gradient(135deg, #E0E0E0 0%, #9E9E9E 100%);
  border: 2.5px solid #757575;
  color: #424242;
}

.badge-mini-card.bronze {
  background: linear-gradient(135deg, #FFCC80 0%, #CA8A04 100%);
  border: 2.5px solid #A16207;
  color: #713F12;
}

.badge-mini-icon {
  width: 22px;
  height: 22px;
}

.badge-mini-tooltip {
  position: absolute;
  bottom: 110%;
  left: 50%;
  transform: translateX(-50%) translateY(10px);
  background-color: #1E293B;
  color: white;
  padding: 0.5rem 0.75rem;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  white-space: nowrap;
  pointer-events: none;
  opacity: 0;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
  z-index: 20;
}

.badge-mini-tooltip::before {
  content: '';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  border-width: 5px;
  border-style: solid;
  border-color: #1E293B transparent transparent transparent;
}

.badge-mini-card:hover .badge-mini-tooltip {
  opacity: 1;
  transform: translateX(-50%) translateY(0);
}

.empty-badges {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 1.5rem;
  text-align: center;
  color: var(--text-muted);
  font-size: 0.85rem;
}

.empty-icon {
  width: 36px;
  height: 36px;
  color: var(--border-color);
  margin-bottom: 0.5rem;
}

.view-all-link {
  font-size: 0.85rem;
  color: var(--primary-color);
  font-weight: 700;
  text-align: right;
  display: block;
  margin-top: 0.5rem;
}

.view-all-link:hover {
  color: var(--accent-secondary);
}

@media (max-width: 1024px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
}

/* User group styles */
.user-group-tag {
  font-size: 1.1rem;
  font-weight: 500;
  color: var(--accent-color);
  margin-left: 0.5rem;
}

/* Badges for topic card */
.topic-header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 0.5rem;
  margin-bottom: 0.25rem;
}

.topic-header-row h3 {
  margin: 0;
}

.badge {
  font-size: 0.7rem;
  font-weight: 700;
  padding: 0.15rem 0.5rem;
  border-radius: var(--radius-sm);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.badge-mandatory {
  background-color: var(--primary-light);
  color: var(--primary-color);
  border: 1px solid var(--primary-color);
}

.badge-optional {
  background-color: rgba(59, 130, 246, 0.1);
  color: #3b82f6;
  border: 1px solid #3b82f6;
}
.badge-pending {
  background: #fff7d6;
  color: #8a6b00;
  border: 1px solid #ead88b;
}

.badge-locked {
  background-color: var(--bg-main);
  color: var(--text-muted);
  border: 1px solid var(--border-color);
}

/* Locked Card Styling */
.topic-card.topic-locked {
  opacity: 0.85;
  background-color: var(--bg-main);
  border-color: var(--border-color);
  cursor: pointer;
  position: relative;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.topic-card.topic-locked:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
  border-color: var(--primary-color);
  opacity: 1;
}

.topic-card.topic-locked:hover .lock-icon {
  color: var(--primary-color);
  transform: scale(1.1);
}

.lock-icon {
  color: var(--text-muted);
  width: 18px;
  height: 18px;
  transition: var(--transition);
}

/* Modal styling */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(15, 23, 42, 0.6);
  backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 999;
}

.modal-card {
  width: 90%;
  max-width: 480px;
  background-color: var(--bg-card);
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.2), 0 10px 10px -5px rgba(0, 0, 0, 0.1);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.modal-header {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.modal-icon-box {
  width: 48px;
  height: 48px;
  border-radius: var(--radius-md);
  background-color: var(--primary-light);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--primary-color);
}

.modal-icon {
  width: 24px;
  height: 24px;
}

.modal-header h2 {
  font-size: 1.3rem;
  font-weight: 700;
  color: var(--primary-color);
  margin: 0;
}

.modal-body {
  font-size: 0.95rem;
  color: var(--text-main);
  line-height: 1.5;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.modal-note {
  font-size: 0.85rem;
  color: var(--text-muted);
  background-color: var(--bg-main);
  padding: 0.75rem;
  border-radius: var(--radius-sm);
  border-left: 3px solid var(--primary-color);
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 0.5rem;
}

.btn-confirm {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  min-width: 140px;
}
</style>
