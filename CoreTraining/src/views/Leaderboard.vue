<template>
  <div class="leaderboard-view animate-fade-in">
    <!-- User Profile Hero Summary -->
    <div class="profile-hero card">
      <div class="user-big-avatar">Q</div>
      <div class="profile-info">
        <span class="user-status-tag">HỌC VIÊN CHÍNH THỨC</span>
        <h1>Quản Trị Hệ Thống</h1>
        <p class="profile-stats-row">
          <span>Điểm tích lũy: <strong>{{ userStore.points }} PTS</strong></span>
          <span class="dot-separator">•</span>
          <span>Bài học hoàn thành: <strong>{{ userStore.completedLessonsCount }} / {{ userStore.totalLessonsCount }}</strong></span>
          <span class="dot-separator">•</span>
          <span>Huy hiệu đạt được: <strong>{{ userStore.unlockedBadges.length }}</strong></span>
        </p>
      </div>
    </div>

    <div class="leaderboard-grid">
      <!-- Left Column: Rank List -->
      <div class="ranks-col card">
        <h2 class="card-title">BẢNG VINH DANH CHI NHÁNH</h2>
        <p class="card-subtitle">Bảng xếp hạng năng lực học tập dựa trên điểm tích lũy (PTS)</p>
        
        <div class="ranks-list">
          <div 
            v-for="user in sortedLeaderboard" 
            :key="user.username" 
            class="rank-row"
            :class="{ 'is-current-user': user.username === 'quantrh' }"
          >
            <div class="rank-num" :class="getRankClass(user.rank)">
              {{ user.rank }}
            </div>
            
            <div class="rank-avatar" :style="{ backgroundColor: user.avatarColor }">
              {{ user.username.charAt(0).toUpperCase() }}
            </div>
            
            <div class="rank-username-box">
              <span class="rank-fullname">{{ user.fullName }}</span>
              <span class="rank-details">
                {{ user.completedLessonsCount }} bài học • {{ user.badgesCount }} huy hiệu
              </span>
            </div>
            
            <div class="rank-points">
              {{ user.points }} <span class="pts-lbl">PTS</span>
            </div>
          </div>
        </div>
      </div>

      <!-- Right Column: Badges Collection Cabinet -->
      <div class="badges-col card">
        <h2 class="card-title">HỘP HUY HIỆU NĂNG LỰC</h2>
        <p class="card-subtitle">Bộ sưu tập huy hiệu nghiệp vụ ngân hàng của bạn</p>
        
        <div class="cabinet-group">
          <h3>Huy hiệu nghiệp vụ đã nhận</h3>
          
          <div class="badge-cabinet" v-if="userStore.unlockedBadges.length > 0">
            <div 
              v-for="badge in userStore.unlockedBadges" 
              :key="badge.id" 
              class="cabinet-badge-card"
              :class="badge.type"
            >
              <div class="cabinet-badge-icon-box">
                <component :is="getBadgeIcon(badge.icon)" class="cabinet-badge-icon" />
              </div>
              <div class="cabinet-badge-text">
                <h4>{{ badge.title }}</h4>
                <p>Nghiệp vụ {{ badge.courseName }}</p>
                <span class="unlocked-date">Mở khóa: {{ badge.unlockedAt }}</span>
              </div>
            </div>
          </div>
          <div class="empty-cabinet" v-else>
            <p>Bạn chưa đạt được huy hiệu nào. Hãy tham gia thi thử trắc nghiệm đạt 80% trở lên!</p>
          </div>
        </div>

        <div class="cabinet-group system-rules">
          <h3>Quy tắc mở khóa huy hiệu</h3>
          <div class="rules-list">
            <div class="rule-item">
              <div class="rule-icon-box gold"><Award class="rule-icon" /></div>
              <div class="rule-text">
                <strong>Huy hiệu Vàng (Gold Badge)</strong>
                <span>Đạt điểm tối đa <strong>100%</strong> trong bài thi thử nghiệp vụ.</span>
              </div>
            </div>
            <div class="rule-item">
              <div class="rule-icon-box silver"><Medal class="rule-icon" /></div>
              <div class="rule-text">
                <strong>Huy hiệu Bạc (Silver Badge)</strong>
                <span>Đạt kết quả thi thử trên <strong>90%</strong> câu hỏi chính xác.</span>
              </div>
            </div>
            <div class="rule-item">
              <div class="rule-icon-box bronze"><Shield class="rule-icon" /></div>
              <div class="rule-text">
                <strong>Huy hiệu Đồng (Bronze Badge)</strong>
                <span>Đạt kết quả thi thử từ <strong>80% trở lên</strong> câu hỏi chính xác.</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue';
import { showToast } from '../utils/toast';
import { useUserStore } from '../stores/userStore';
import { leaderboardApi } from '../api/leaderboardApi';
import type { LeaderboardUser } from '../types';
import { Award, Shield, Medal } from 'lucide-vue-next';

const userStore = useUserStore();
const leaderboardUsers = ref<LeaderboardUser[]>([]);

onMounted(async () => {
  await userStore.fetchProgress();
  try {
    leaderboardUsers.value = await leaderboardApi.getLeaderboard();
  } catch {
    showToast('Không thể tải bảng vinh danh. Vui lòng thử lại.', 'error');
  }
});

// Dynamically construct and sort leaderboard based on user points from API
const sortedLeaderboard = computed(() => {
  return leaderboardUsers.value;
});

const getRankClass = (rank: number) => {
  if (rank === 1) return 'rank-first';
  if (rank === 2) return 'rank-second';
  if (rank === 3) return 'rank-third';
  return '';
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
.leaderboard-view {
  display: flex;
  flex-direction: column;
  gap: 2rem;
}

.profile-hero {
  background: linear-gradient(to right, #005B41 0%, #003d2b 100%);
  color: white;
  display: flex;
  align-items: center;
  gap: 2rem;
  padding: 2.2rem;
  border-radius: var(--radius-lg);
  border: none;
  box-shadow: var(--shadow-lg);
}

.user-big-avatar {
  width: 90px;
  height: 90px;
  border-radius: 50%;
  background-color: var(--accent-color);
  color: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 2.5rem;
  font-weight: 800;
  box-shadow: 0 0 20px rgba(212, 175, 55, 0.4);
}

.profile-info {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.user-status-tag {
  font-size: 0.7rem;
  font-weight: 800;
  color: var(--accent-color);
  background-color: rgba(255, 255, 255, 0.1);
  padding: 0.2rem 0.6rem;
  border-radius: var(--radius-sm);
  align-self: flex-start;
  letter-spacing: 1px;
}

.profile-info h1 {
  font-size: 2rem;
  font-weight: 700;
}

.profile-stats-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  font-size: 0.95rem;
  color: rgba(255, 255, 255, 0.85);
  flex-wrap: wrap;
}

.profile-stats-row strong {
  color: var(--accent-color);
  font-weight: 700;
}

.dot-separator {
  color: rgba(255, 255, 255, 0.4);
}

/* Leaderboard layout grid */
.leaderboard-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 2rem;
}

.card-title {
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-main);
}

.card-subtitle {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-bottom: 1.5rem;
}

/* Ranks Column */
.ranks-list {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.rank-row {
  display: flex;
  align-items: center;
  padding: 0.85rem 1.2rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  background-color: var(--bg-card);
  transition: var(--transition);
  gap: 1rem;
}

.rank-row.is-current-user {
  border-color: var(--primary-color);
  background-color: var(--primary-light);
  box-shadow: var(--shadow-sm);
}

.rank-num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 0.95rem;
  color: var(--text-muted);
  background-color: var(--bg-main);
}

.rank-first {
  background: linear-gradient(135deg, #FFE082 0%, #FFB300 100%);
  color: #FF6F00;
}

.rank-second {
  background: linear-gradient(135deg, #E0E0E0 0%, #9E9E9E 100%);
  color: #424242;
}

.rank-third {
  background: linear-gradient(135deg, #FFCC80 0%, #CA8A04 100%);
  color: #713F12;
}

.rank-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.rank-username-box {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.rank-fullname {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-main);
}

.rank-details {
  font-size: 0.75rem;
  color: var(--text-muted);
}

.rank-points {
  font-size: 1.1rem;
  font-weight: 800;
  color: var(--primary-color);
}

.pts-lbl {
  font-size: 0.75rem;
  font-weight: 600;
  color: var(--text-muted);
}

/* Badges cabinet column */
.cabinet-group {
  margin-bottom: 2rem;
}

.cabinet-group:last-child {
  margin-bottom: 0;
}

.cabinet-group h3 {
  font-size: 0.95rem;
  font-weight: 700;
  text-transform: uppercase;
  color: var(--text-muted);
  border-bottom: 1.5px solid var(--border-color);
  padding-bottom: 0.5rem;
  margin-bottom: 1rem;
  letter-spacing: 0.5px;
}

.badge-cabinet {
  display: grid;
  grid-template-columns: 1fr;
  gap: 0.75rem;
}

.cabinet-badge-card {
  display: flex;
  align-items: center;
  padding: 0.85rem 1.25rem;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-color);
  gap: 1.25rem;
  transition: var(--transition);
}

.cabinet-badge-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-sm);
}

.cabinet-badge-icon-box {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-sm);
}

.cabinet-badge-card.gold {
  background-color: #fffbeb;
  border-color: #fde68a;
}
.cabinet-badge-card.gold .cabinet-badge-icon-box {
  background: linear-gradient(135deg, #FFE082 0%, #FFB300 100%);
  color: #FF6F00;
  border: 2px solid #FF8F00;
}

.cabinet-badge-card.silver {
  background-color: #f9fafb;
  border-color: #e5e7eb;
}
.cabinet-badge-card.silver .cabinet-badge-icon-box {
  background: linear-gradient(135deg, #E0E0E0 0%, #9E9E9E 100%);
  color: #424242;
  border: 2px solid #757575;
}

.cabinet-badge-card.bronze {
  background-color: #fefaf0;
  border-color: #fef08a;
}
.cabinet-badge-card.bronze .cabinet-badge-icon-box {
  background: linear-gradient(135deg, #FFCC80 0%, #CA8A04 100%);
  color: #713F12;
  border: 2px solid #A16207;
}

.cabinet-badge-icon {
  width: 22px;
  height: 22px;
}

.cabinet-badge-text h4 {
  font-size: 0.95rem;
  font-weight: 700;
  color: var(--text-main);
}

.cabinet-badge-text p {
  font-size: 0.8rem;
  color: var(--text-muted);
  font-weight: 500;
}

.unlocked-date {
  font-size: 0.7rem;
  color: var(--text-muted);
}

.empty-cabinet {
  text-align: center;
  padding: 2rem;
  color: var(--text-muted);
  font-size: 0.85rem;
  background-color: var(--bg-main);
  border-radius: var(--radius-md);
  border: 1px dashed var(--border-color);
}

/* Rules list styles */
.rules-list {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
}

.rule-item {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.rule-icon-box {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.rule-icon-box.gold {
  background-color: #fef3c7;
  color: #d97706;
}

.rule-icon-box.silver {
  background-color: #f3f4f6;
  color: #4b5563;
}

.rule-icon-box.bronze {
  background-color: #fef9c3;
  color: #ca8a04;
}

.rule-icon {
  width: 18px;
  height: 18px;
}

.rule-text {
  display: flex;
  flex-direction: column;
}

.rule-text strong {
  font-size: 0.85rem;
  font-weight: 700;
  color: var(--text-main);
}

.rule-text span {
  font-size: 0.8rem;
  color: var(--text-muted);
}

@media (max-width: 1024px) {
  .leaderboard-grid {
    grid-template-columns: 1fr;
  }
}
</style>
