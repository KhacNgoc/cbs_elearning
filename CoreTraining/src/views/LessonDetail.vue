<template>
  <div class="lesson-detail-view animate-fade-in" v-if="lesson">
    <!-- Navigation back & topic header -->
    <div class="lesson-header-wrapper">
      <router-link to="/" class="btn-back">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="back-icon"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>
        Quay lại Trang chủ
      </router-link>
      
      <div class="lesson-meta-bar">
        <div class="meta-tags">
          <span class="topic-tag">{{ topic?.name }}</span>
          <span class="system-tag">WEBCSR</span>
        </div>
        <div class="complete-status-box">
          <span class="status-indicator" :class="{ completed: isCompleted }">
            {{ isCompleted ? 'Đã hoàn thành' : 'Đang học' }}
          </span>
          <button 
            v-if="!isCompleted"
            class="btn btn-secondary btn-sm" 
            @click="markAsCompleted"
          >
            Đánh dấu Hoàn thành
          </button>
        </div>
      </div>
      
      <h1 class="lesson-title">{{ lesson.title }}</h1>
    </div>

    <!-- WebCSR System Path Hint Box -->
    <div class="system-hint-box card">
      <div class="hint-icon-box">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="key-icon"><path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4"/></svg>
      </div>
      <div class="hint-content">
        <p class="hint-text">{{ lesson.hint }}</p>
      </div>
    </div>

    <!-- Main Layout with Lesson Media Content & Sidebar -->
    <div class="lesson-main-grid">
      <!-- Media Content Area -->
      <div class="media-column">
        <!-- Tabs Header Bar -->
        <div class="tabs-header-bar">
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'document' }"
            @click="activeTab = 'document'"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="tab-icon"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><polyline points="10 9 9 9 8 9"/></svg>
            Tài Liệu
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'video' }"
            @click="activeTab = 'video'"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="tab-icon"><polygon points="23 7 16 12 23 17 23 7"/><rect x="1" y="5" width="15" height="14" rx="2" ry="2"/></svg>
            Video Hướng Dẫn
          </button>
          <button 
            class="tab-btn" 
            :class="{ active: activeTab === 'procedure' }"
            @click="activeTab = 'procedure'"
          >
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="tab-icon"><line x1="9" y1="6" x2="20" y2="6"/><line x1="9" y1="12" x2="20" y2="12"/><line x1="9" y1="18" x2="20" y2="18"/><line x1="5" y1="6" x2="5.01" y2="6"/><line x1="5" y1="12" x2="5.01" y2="12"/><line x1="5" y1="18" x2="5.01" y2="18"/></svg>
            Quy trình
          </button>
        </div>

        <!-- Dynamic Tab Panel -->
        <div class="tab-content-panel">
          <!-- Document Tab -->
          <div v-if="activeTab === 'document'" class="tab-pane">
            <SlideViewer :slides="lesson.documentSlides" :topicName="lesson.title" />
          </div>
          
          <!-- Video Tab -->
          <div v-if="activeTab === 'video'" class="tab-pane">
            <VideoPlayer :videoUrl="lesson.videoUrl" :title="lesson.title" />
          </div>
          
          <!-- Procedure Tab -->
          <div v-if="activeTab === 'procedure'" class="tab-pane animate-fade-in">
            <div class="procedure-card card">
              <div v-for="(section, idx) in lesson.procedureSteps" :key="idx" class="proc-section">
                <h3 class="proc-sec-title">{{ section.title }}</h3>
                <ul class="proc-steps-list">
                  <li v-for="(step, sIdx) in section.steps" :key="sIdx" class="proc-step-item">
                    <span v-html="highlightKeywords(step)"></span>
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </div>

        <!-- Discussion and Comments Feedback -->
        <div class="discussion-section card">
          <h2 class="discussion-title">
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="msg-icon"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            Thảo luận & Góp ý
          </h2>
          
          <!-- Add Comment Section -->
          <div class="add-feedback-box">
            <h4>THÊM GÓP Ý CỦA BẠN</h4>
            <div class="rating-input-row">
              <span class="rating-label">Đánh giá:</span>
              <StarRating v-model="userRating" />
              <span class="rating-value-desc" v-if="userRating > 0">({{ userRating }}/5 sao)</span>
            </div>
            <div class="comment-textarea-wrapper">
              <textarea 
                v-model="commentText" 
                placeholder="Nhập nội dung góp ý hoặc câu hỏi..." 
                rows="4"
                class="comment-input"
              ></textarea>
            </div>
            <div class="feedback-submit-row">
              <button class="btn btn-primary" @click="submitFeedback" :disabled="!commentText.trim()">
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="send-icon"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>
                Gửi góp ý
              </button>
            </div>
          </div>

          <!-- Existing Comments List -->
          <div class="comments-list">
            <div v-if="lesson.comments.length === 0" class="no-comments">
              <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="empty-msg-icon"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
              <p>Chưa có góp ý. Hãy là người đầu tiên!</p>
            </div>
            
            <div 
              v-else 
              v-for="comment in lesson.comments" 
              :key="comment.id" 
              class="comment-card"
            >
              <div class="comment-user-avatar">{{ comment.avatar }}</div>
              <div class="comment-body">
                <div class="comment-header">
                  <span class="comment-username">{{ comment.user }}</span>
                  <span class="comment-date">{{ comment.date }}</span>
                </div>
                <div class="comment-stars">
                  <StarRating :modelValue="comment.rating" readonly />
                </div>
                <p class="comment-text-content">{{ comment.content }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Navigation Sidebar for other lessons of topic -->
      <div class="sidebar-column">
        <!-- Topic Test CTA -->
        <div class="quiz-cta-card card">
          <h3>ĐÁNH GIÁ NĂNG LỰC</h3>
          <p>Kiểm tra và củng cố kiến thức phân hệ {{ topic?.name }} ngay sau khi hoàn thành bài học.</p>
          <button class="btn btn-outline w-100" @click="startQuiz">LÀM BÀI KIỂM TRA</button>
        </div>

        <!-- Lessons List -->
        <div class="lessons-nav-card card">
          <h3 class="nav-title">NỘI DUNG CHỦ ĐỀ</h3>
          <span class="nav-subtitle">{{ topic?.lessons.length }} bài học trong phân hệ</span>
          
          <div class="lessons-list-group">
            <div 
              v-for="item in topic?.lessons" 
              :key="item.id" 
              class="lesson-nav-item"
              :class="{ active: item.id === lesson.id, completed: userStore.completedLessons.includes(item.id) }"
              @click="switchLesson(item.id)"
            >
              <div class="nav-item-icon">
                <svg v-if="userStore.completedLessons.includes(item.id)" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="check-icon"><polyline points="20 6 9 17 4 12"/></svg>
                <span v-else class="bullet"></span>
              </div>
              <span class="nav-item-title">{{ item.title }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="loading-state">Đang tải bài học...</div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import { useCourseStore } from '../stores/courseStore';
import { useUserStore } from '../stores/userStore';
import SlideViewer from '../components/SlideViewer.vue';
import VideoPlayer from '../components/VideoPlayer.vue';
import StarRating from '../components/StarRating.vue';

const props = defineProps({
  id: {
    type: String,
    required: true
  }
});

const router = useRouter();
const courseStore = useCourseStore();
const userStore = useUserStore();

const activeTab = ref('document');
const userRating = ref(5);
const commentText = ref('');

const lesson = computed(() => courseStore.currentLesson);
const topic = computed(() => courseStore.currentTopic);

const isCompleted = computed(() => {
  return lesson.value ? userStore.completedLessons.includes(lesson.value.id) : false;
});

const loadLessonData = async (lessonId: string) => {
  await courseStore.fetchLessonDetail(lessonId);
  // Auto complete lesson when viewed
  if (lesson.value) {
    await userStore.completeLesson(lesson.value.id);
  }
};

onMounted(async () => {
  if (courseStore.topics.length === 0) {
    await courseStore.fetchTopics();
  }
  await loadLessonData(props.id);
});

watch(() => props.id, async (newId) => {
  await loadLessonData(newId);
  activeTab.value = 'document'; // reset tab
});

const switchLesson = (lessonId: string) => {
  router.push(`/lesson/${lessonId}`);
};

const markAsCompleted = async () => {
  if (lesson.value) {
    await userStore.completeLesson(lesson.value.id);
  }
};

const startQuiz = () => {
  if (topic.value) {
    router.push(`/quiz/${topic.value.id}`);
  }
};

const submitFeedback = async () => {
  if (!commentText.value.trim() || !lesson.value) return;
  
  await courseStore.addComment(
    lesson.value.id,
    userRating.value,
    commentText.value
  );
  
  // reset input
  commentText.value = '';
  userRating.value = 5;
};

// Highlights Maker / Checker and other system keywords in Vietnamese procedure text
const highlightKeywords = (text: string) => {
  let res = text;
  // Highlight MAKER / Maker
  res = res.replace(/(Maker|MAKER|CÁN BỘ QTTD|Giao dịch viên)/gi, '<strong style="color: #005B41;">$1</strong>');
  // Highlight Checker / CHECKER
  res = res.replace(/(Checker|CHECKER|Kiểm soát viên)/gi, '<strong style="color: #CA8A04;">$1</strong>');
  // Highlight WebCSR
  res = res.replace(/(WebCSR)/g, '<span style="background-color: var(--primary-light); color: var(--primary-color); padding: 2px 6px; border-radius: 4px; font-weight: 600;">$1</span>');
  return res;
};
</script>

<style scoped>
.lesson-detail-view {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.lesson-header-wrapper {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.btn-back {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: var(--text-muted);
  font-weight: 600;
  transition: var(--transition);
  align-self: flex-start;
  margin-bottom: 0.5rem;
}

.btn-back:hover {
  color: var(--primary-color);
  transform: translateX(-4px);
}

.back-icon {
  width: 16px;
  height: 16px;
}

.lesson-meta-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.meta-tags {
  display: flex;
  gap: 0.5rem;
}

.topic-tag {
  background-color: var(--primary-color);
  color: white;
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  font-weight: 700;
}

.system-tag {
  background-color: var(--accent-color);
  color: #000;
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  font-weight: 700;
}

.complete-status-box {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.status-indicator {
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-muted);
  background-color: var(--border-color);
  padding: 0.25rem 0.75rem;
  border-radius: var(--radius-full);
}

.status-indicator.completed {
  color: #059669;
  background-color: #d1fae5;
}

.btn-sm {
  padding: 0.35rem 0.75rem;
  font-size: 0.8rem;
  border-radius: var(--radius-sm);
}

.lesson-title {
  font-size: 1.75rem;
  font-weight: 700;
  color: var(--text-main);
}

/* System Hint Box */
.system-hint-box {
  background: linear-gradient(to right, #005b410d, transparent);
  border-left: 4px solid var(--primary-color);
  display: flex;
  gap: 1rem;
  padding: 1rem 1.25rem;
  align-items: center;
}

.hint-icon-box {
  background-color: var(--primary-light);
  color: var(--primary-color);
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.key-icon {
  width: 18px;
  height: 18px;
}

.hint-text {
  font-size: 0.9rem;
  font-weight: 500;
  color: var(--primary-hover);
}

/* Main Grid Layout */
.lesson-main-grid {
  display: grid;
  grid-template-columns: 2.5fr 1fr;
  gap: 1.5rem;
}

.media-column, .sidebar-column {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

/* Tabs styles */
.tabs-header-bar {
  display: flex;
  border-bottom: 1px solid var(--border-color);
  background-color: var(--bg-card);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  overflow: hidden;
}

.tab-btn {
  flex: 1;
  background: none;
  border: none;
  border-bottom: 3px solid transparent;
  padding: 1rem;
  font-weight: 600;
  color: var(--text-muted);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  transition: var(--transition);
  font-size: 0.95rem;
}

.tab-btn:hover {
  background-color: var(--bg-main);
  color: var(--primary-color);
}

.tab-btn.active {
  border-bottom-color: var(--primary-color);
  color: var(--primary-color);
  background-color: var(--primary-light);
}

.tab-icon {
  width: 18px;
  height: 18px;
}

/* Procedure tab styles */
.procedure-card {
  padding: 2rem;
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.proc-section {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  border-bottom: 1px dashed var(--border-color);
  padding-bottom: 1.5rem;
}

.proc-section:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.proc-sec-title {
  font-size: 1.1rem;
  font-weight: 700;
  color: var(--text-main);
  background-color: var(--bg-main);
  padding: 0.5rem 1rem;
  border-radius: var(--radius-sm);
  display: inline-block;
}

.proc-steps-list {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  list-style: none;
  padding-left: 0.5rem;
}

.proc-step-item {
  position: relative;
  padding-left: 1.5rem;
  font-size: 0.95rem;
  color: #334155;
  line-height: 1.6;
}

.proc-step-item::before {
  content: "•";
  color: var(--accent-secondary);
  font-weight: bold;
  font-size: 1.25rem;
  position: absolute;
  left: 0.25rem;
  top: -0.15rem;
}

/* Discussion Styles */
.discussion-section {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
}

.discussion-title {
  font-size: 1.2rem;
  font-weight: 700;
  color: var(--text-main);
  display: flex;
  align-items: center;
  gap: 0.5rem;
  border-bottom: 1px solid var(--border-color);
  padding-bottom: 0.75rem;
}

.msg-icon {
  width: 20px;
  height: 20px;
  color: var(--primary-color);
}

.add-feedback-box {
  background-color: var(--bg-main);
  padding: 1.2rem;
  border-radius: var(--radius-md);
  display: flex;
  flex-direction: column;
  gap: 1rem;
  border: 1px solid var(--border-color);
}

.add-feedback-box h4 {
  font-size: 0.85rem;
  font-weight: 700;
  letter-spacing: 0.5px;
  color: var(--text-muted);
}

.rating-input-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.rating-label {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--text-main);
}

.rating-value-desc {
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--text-muted);
}

.comment-textarea-wrapper {
  width: 100%;
}

.comment-input {
  width: 100%;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-color);
  padding: 0.75rem;
  font-size: 0.9rem;
  outline: none;
  transition: var(--transition);
  resize: vertical;
}

.comment-input:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 2px var(--primary-light);
}

.feedback-submit-row {
  display: flex;
  justify-content: flex-end;
}

.feedback-submit-row button {
  padding: 0.6rem 1.25rem;
  font-size: 0.9rem;
}

.send-icon {
  width: 14px;
  height: 14px;
}

.comments-list {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.no-comments {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--text-muted);
  padding: 2rem;
  text-align: center;
  font-size: 0.9rem;
}

.empty-msg-icon {
  width: 32px;
  height: 32px;
  color: var(--border-color);
  margin-bottom: 0.5rem;
}

.comment-card {
  display: flex;
  gap: 1rem;
  padding: 1rem 0;
  border-bottom: 1px solid var(--border-color);
}

.comment-card:last-child {
  border-bottom: none;
}

.comment-user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background-color: var(--primary-light);
  color: var(--primary-color);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 0.9rem;
}

.comment-body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.comment-header {
  display: flex;
  justify-content: space-between;
}

.comment-username {
  font-size: 0.9rem;
  font-weight: 700;
  color: var(--text-main);
}

.comment-date {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.comment-stars {
  margin-top: -0.1rem;
}

.comment-text-content {
  font-size: 0.9rem;
  color: #334155;
  margin-top: 0.25rem;
}

/* Sidebar Column Styles */
.w-100 {
  width: 100%;
}

.quiz-cta-card {
  background: linear-gradient(135deg, var(--bg-card) 0%, var(--primary-light) 100%);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  border: 1px dashed var(--primary-color);
}

.quiz-cta-card h3 {
  font-size: 1rem;
  font-weight: 700;
  color: var(--primary-color);
}

.quiz-cta-card p {
  font-size: 0.85rem;
  color: var(--text-muted);
}

.lessons-nav-card {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.nav-title {
  font-size: 1rem;
  font-weight: 700;
}

.nav-subtitle {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-bottom: 0.75rem;
}

.lessons-list-group {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.lesson-nav-item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.65rem 0.85rem;
  border-radius: var(--radius-sm);
  cursor: pointer;
  transition: var(--transition);
  border: 1px solid transparent;
}

.lesson-nav-item:hover {
  background-color: var(--bg-main);
  color: var(--primary-color);
}

.lesson-nav-item.active {
  background-color: var(--primary-light);
  color: var(--primary-color);
  font-weight: 600;
  border-color: rgba(0, 91, 65, 0.15);
}

.nav-item-icon {
  width: 18px;
  height: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check-icon {
  width: 16px;
  height: 16px;
  color: #10b981;
}

.bullet {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background-color: var(--text-muted);
}

.lesson-nav-item.active .bullet {
  background-color: var(--primary-color);
}

.nav-item-title {
  font-size: 0.85rem;
  line-height: 1.3;
}

.loading-state {
  text-align: center;
  padding: 3rem;
  color: var(--text-muted);
}

@media (max-width: 768px) {
  .lesson-main-grid {
    grid-template-columns: 1fr;
  }
}
</style>
