<template>
  <div class="profile-page animate-fade-in">
    <div v-if="loading" class="profile-loading card">
      <LoaderCircle class="profile-spinner" :size="28" />
      <span>Đang tải hồ sơ cá nhân...</span>
    </div>

    <template v-else>
      <section class="profile-hero card">
        <div class="profile-avatar" :style="{ backgroundColor: profile?.avatarColor || '#005B41' }">
          {{ avatarLetter }}
        </div>
        <div class="profile-identity">
          <span class="profile-eyebrow">Hồ sơ người dùng</span>
          <h1>{{ profile?.fullName || userStore.fullName }}</h1>
          <div class="profile-tags">
            <span><AtSign :size="14" />{{ profile?.username || userStore.username }}</span>
            <span v-if="profile?.groupName"><UsersRound :size="14" />{{ profile.groupName }}</span>
            <span class="role-tag"><ShieldCheck :size="14" />{{ roleLabel }}</span>
          </div>
        </div>
        <div class="profile-points">
          <span>Điểm tích lũy</span>
          <strong>{{ userStore.points.toLocaleString('vi-VN') }}</strong>
          <small>PTS</small>
        </div>
      </section>

      <form class="card" @submit.prevent="saveProfile" v-if="profile">
        <label>Họ và tên <input v-model="profile.fullName" required /></label>
        <label>Email <input v-model="profile.email" type="email" required /></label>
        <button class="btn btn-primary" :disabled="saving">Lưu hồ sơ</button><p role="status">{{ profileMessage }}</p>
      </form>
      <div class="profile-layout">
        <aside class="profile-sidebar">
          <section class="profile-info card">
            <div class="section-heading">
              <div>
                <span class="profile-eyebrow">Thông tin</span>
                <h2>Thông tin cá nhân</h2>
              </div>
              <UserRound :size="22" />
            </div>

            <dl class="profile-details">
              <div>
                <dt><BadgeCheck :size="17" />Mã nhân viên</dt>
                <dd>{{ profile?.employeeId || 'Chưa cập nhật' }}</dd>
              </div>
              <div>
                <dt><Mail :size="17" />Email</dt>
                <dd>{{ profile?.email || 'Chưa cập nhật' }}</dd>
              </div>
              <div>
                <dt><UsersRound :size="17" />Nhóm nghiệp vụ</dt>
                <dd>{{ profile?.groupName || 'Chưa phân nhóm' }}</dd>
              </div>
              <div>
                <dt><KeyRound :size="17" />Tên đăng nhập</dt>
                <dd>{{ profile?.username || userStore.username }}</dd>
              </div>
            </dl>
          </section>

          <section class="profile-summary card">
            <div class="section-heading compact">
              <div>
                <span class="profile-eyebrow">Tổng quan</span>
                <h2>Kết quả học tập</h2>
              </div>
              <ChartNoAxesColumnIncreasing :size="22" />
            </div>
            <div class="summary-grid">
              <div><strong>{{ joinedCourses.length }}</strong><span>Khóa học tham gia</span></div>
              <div><strong>{{ userStore.completedLessons.length }}</strong><span>Bài học hoàn thành</span></div>
              <div><strong>{{ overallCourseProgress }}%</strong><span>Tiến độ tổng thể</span></div>
              <div><strong>{{ userStore.unlockedBadges.length }}</strong><span>Huy hiệu đạt được</span></div>
            </div>
          </section>
        </aside>

        <section class="enrolled-courses">
          <div class="courses-header">
            <div>
              <span class="profile-eyebrow">Hành trình học tập</span>
              <h2>Khóa học đã tham gia</h2>
              <p>Theo dõi tiến độ từng khóa học nghiệp vụ được phân công hoặc đã đăng ký.</p>
            </div>
            <span class="course-total">{{ joinedCourses.length }} khóa học</span>
          </div>

          <div v-if="joinedCourses.length" class="course-list">
            <article v-for="course in joinedCourses" :key="course.course.id" class="profile-course-card card">
              <div class="course-icon">
                <component :is="getCourseIcon(course.course.iconName)" :size="23" />
              </div>
              <div class="course-content">
                <div class="course-title-row">
                  <div>
                    <span class="course-kind">{{ course.kind }}</span>
                    <h3>{{ course.course.name }}</h3>
                  </div>
                  <span class="course-status" :class="course.statusClass">{{ course.status }}</span>
                </div>

                <div class="course-progress-meta">
                  <span>{{ course.completed }} / {{ course.total }} bài học</span>
                  <strong>{{ course.percent }}%</strong>
                </div>
                <div class="course-progress-track">
                  <span :style="{ width: `${course.percent}%` }"></span>
                </div>

                <div class="course-footer">
                  <span v-if="course.quiz">
                    <ClipboardCheck :size="16" />
                    Điểm đánh giá: <strong>{{ course.quiz.score }}/{{ course.quiz.total }}</strong>
                  </span>
                  <span v-else class="quiz-empty"><ClipboardList :size="16" />Chưa thực hiện đánh giá</span>
                  <button @click="continueCourse(course.course)">
                    {{ course.percent > 0 ? 'Tiếp tục học' : 'Bắt đầu học' }}
                    <ArrowRight :size="16" />
                  </button>
                </div>
              </div>
            </article>
          </div>

          <div v-else class="empty-courses card">
            <BookOpenCheck :size="38" />
            <h3>Chưa tham gia khóa học nào</h3>
            <p>Các khóa học bắt buộc hoặc đã đăng ký sẽ xuất hiện tại đây.</p>
            <router-link to="/" class="browse-courses">Khám phá khóa học</router-link>
          </div>
        </section>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import {roleLabel as describeRoles} from "../utils/permissions";
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  ArrowLeftRight, ArrowRight, AtSign, BadgeCheck, BookOpen, BookOpenCheck,
  ChartNoAxesColumnIncreasing, ClipboardCheck, ClipboardList, Coins, CreditCard,
  KeyRound, LoaderCircle, Mail, PiggyBank, ShieldCheck, UserRound, Users,
  UsersRound, Wallet
} from 'lucide-vue-next';
import { useCourseStore } from '../stores/courseStore';
import { useUserStore } from '../stores/userStore';
import { userApi } from '../api/userApi';
import type { Course, UserProfile } from '../types';

const router = useRouter();
const courseStore = useCourseStore();
const userStore = useUserStore();
const loading = ref(true);
const saving=ref(false);const profileMessage=ref('');
async function saveProfile(){if(!profile.value)return;saving.value=true;profileMessage.value='';try{profile.value=await userApi.updateProfile({fullName:profile.value.fullName,email:profile.value.email || ''});await userStore.fetchProgress();profileMessage.value='Đã lưu hồ sơ';}catch(e:any){profileMessage.value=e.response?.data?.message || 'Không lưu được hồ sơ';}finally{saving.value=false;}}

const profile = ref<UserProfile | null>(null);

onMounted(async () => {
  loading.value = true;
  try {
    const [profileData] = await Promise.all([
      userApi.getMyProfile(),
      userStore.fetchProgress(),
      courseStore.fetchCourses()
    ]);
    profile.value = profileData;
  } catch (error) {
    console.error('Failed to load user profile:', error);
  } finally {
    loading.value = false;
  }
});

const avatarLetter = computed(() =>
  (profile.value?.fullName || userStore.fullName || userStore.username || 'U').charAt(0).toUpperCase()
);

const roleLabel = computed(() => describeRoles(profile.value?.roles || [profile.value?.role || userStore.role]));

const joinedCourses = computed(() => {
  const explicitCourseIds = new Set([
    ...userStore.mandatoryCourses,
    ...userStore.registeredCourses
  ]);

  return courseStore.courses
    .filter(course => {
      if (userStore.systemAdmin) return true;
      const hasCompletedLesson = course.lessons.some(lesson =>
        userStore.completedLessons.includes(lesson.id)
      );
      return explicitCourseIds.has(course.id)
        || hasCompletedLesson
        || userStore.quizScores[course.id] !== undefined;
    })
    .map(course => {
      const completed = course.lessons.filter(lesson =>
        userStore.completedLessons.includes(lesson.id)
      ).length;
      const total = course.lessons.length;
      const percent = total ? Math.round(completed / total * 100) : 0;
      const quizScore = userStore.quizScores[course.id];
      const quiz = quizScore === undefined ? null : {
        score: quizScore,
        total: userStore.quizTotals[course.id] || 0
      };

      return {
        course,
        completed,
        total,
        percent,
        quiz,
        kind: userStore.systemAdmin
          ? 'Khóa học hệ thống'
          : userStore.mandatoryCourses.includes(course.id)
            ? 'Khóa học bắt buộc'
            : 'Khóa học đã đăng ký',
        status: percent === 100 ? 'Đã hoàn thành' : percent > 0 ? 'Đang học' : 'Chưa bắt đầu',
        statusClass: percent === 100 ? 'completed' : percent > 0 ? 'learning' : 'not-started'
      };
    });
});

const overallCourseProgress = computed(() => {
  const totalLessons = joinedCourses.value.reduce((sum, course) => sum + course.total, 0);
  const completedLessons = joinedCourses.value.reduce((sum, course) => sum + course.completed, 0);
  return totalLessons ? Math.round(completedLessons / totalLessons * 100) : 0;
});

const getCourseIcon = (iconName: string) => {
  const icons: Record<string, typeof Users> = {
    Users, Coins, ArrowLeftRight, PiggyBank, Wallet, BookOpen, CreditCard
  };
  return icons[iconName] || BookOpen;
};

const continueCourse = (course: Course) => {
  const nextLesson = course.lessons.find(lesson =>
    !userStore.completedLessons.includes(lesson.id)
  ) || course.lessons[0];
  if (nextLesson) router.push(`/lesson/${nextLesson.id}`);
};
</script>

<style scoped>
.profile-page { max-width: 1320px; margin: 0 auto; }
.profile-loading { min-height: 360px; display: flex; align-items: center; justify-content: center; gap: .7rem; color: var(--text-muted); }
.profile-spinner { color: var(--primary-color); animation: profile-spin .8s linear infinite; }
@keyframes profile-spin { to { transform: rotate(360deg); } }

.profile-hero { min-height: 190px; padding: 2rem 2.25rem; display: flex; align-items: center; gap: 1.5rem; overflow: hidden; position: relative; background: linear-gradient(125deg, #fff 0%, #f4f9f7 66%, #e4f0ec 100%); border-color: rgba(0,91,65,.15); }
.profile-hero::after { content: ''; position: absolute; right: -75px; top: -110px; width: 280px; height: 280px; border: 42px solid rgba(0,91,65,.045); border-radius: 50%; }
.profile-avatar { width: 92px; height: 92px; flex: 0 0 92px; display: grid; place-items: center; border: 5px solid white; border-radius: 50%; box-shadow: 0 8px 22px rgba(10,50,37,.16); color: white; font-size: 2.25rem; font-weight: 800; }
.profile-identity { min-width: 0; flex: 1; }
.profile-eyebrow { color: var(--primary-color); font-size: .72rem; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
.profile-identity h1 { margin: .25rem 0 .6rem; font-size: clamp(1.65rem, 3vw, 2.25rem); line-height: 1.2; }
.profile-tags { display: flex; align-items: center; flex-wrap: wrap; gap: .45rem; }
.profile-tags span { padding: .34rem .6rem; display: inline-flex; align-items: center; gap: .3rem; border-radius: 6px; background: white; border: 1px solid #dae5e1; color: #53655e; font-size: .78rem; }
.profile-tags .role-tag { background: #f5f0dc; border-color: #eadb9e; color: #715b0c; }
.profile-points { position: relative; z-index: 1; min-width: 145px; padding-left: 1.5rem; display: grid; justify-items: end; border-left: 1px solid #cadbd5; }
.profile-points span { color: var(--text-muted); font-size: .75rem; text-transform: uppercase; letter-spacing: .05em; }
.profile-points strong { color: var(--primary-color); font-size: 2rem; line-height: 1.15; }
.profile-points small { color: #9a7a13; font-weight: 800; }

.profile-layout { margin-top: 1.5rem; display: grid; grid-template-columns: 340px minmax(0,1fr); align-items: start; gap: 1.5rem; }
.profile-sidebar { display: grid; gap: 1.25rem; }
.profile-info, .profile-summary { padding: 1.4rem; }
.section-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: .75rem; padding-bottom: 1rem; border-bottom: 1px solid var(--border-color); }
.section-heading > svg { color: var(--primary-color); }
.section-heading h2, .courses-header h2 { margin-top: .2rem; font-size: 1.2rem; }
.profile-details { display: grid; }
.profile-details > div { padding: .9rem 0; border-bottom: 1px solid #edf1ef; }
.profile-details > div:last-child { padding-bottom: 0; border: 0; }
.profile-details dt { display: flex; align-items: center; gap: .45rem; color: var(--text-muted); font-size: .75rem; }
.profile-details dt svg { color: var(--primary-color); }
.profile-details dd { margin: .25rem 0 0 1.45rem; font-size: .9rem; font-weight: 650; overflow-wrap: anywhere; }
.summary-grid { padding-top: 1rem; display: grid; grid-template-columns: 1fr 1fr; gap: .65rem; }
.summary-grid div { min-height: 95px; padding: .75rem; display: flex; flex-direction: column; justify-content: center; border-radius: 8px; background: #f5f8f7; }
.summary-grid strong { color: var(--primary-color); font-size: 1.35rem; }
.summary-grid span { color: var(--text-muted); font-size: .7rem; line-height: 1.35; }

.enrolled-courses { min-width: 0; }
.courses-header { min-height: 78px; display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.courses-header p { margin-top: .3rem; color: var(--text-muted); font-size: .85rem; }
.course-total { padding: .4rem .65rem; border-radius: 6px; background: var(--primary-light); color: var(--primary-color); font-size: .78rem; font-weight: 750; white-space: nowrap; }
.course-list { display: grid; gap: 1rem; }
.profile-course-card { padding: 1.25rem; display: grid; grid-template-columns: 48px minmax(0,1fr); align-items: start; gap: 1rem; }
.profile-course-card:hover { border-color: rgba(0,91,65,.25); box-shadow: var(--shadow-md); }
.course-icon { width: 48px; height: 48px; display: grid; place-items: center; border-radius: 10px; background: var(--primary-light); color: var(--primary-color); }
.course-content { min-width: 0; }
.course-title-row { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.course-kind { color: var(--text-muted); font-size: .68rem; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; }
.course-title-row h3 { margin-top: .12rem; font-size: 1rem; }
.course-status { padding: .27rem .55rem; border-radius: 999px; font-size: .68rem; font-weight: 750; white-space: nowrap; }
.course-status.completed { background: #dcf5e9; color: #087553; }
.course-status.learning { background: #fff4d6; color: #86620b; }
.course-status.not-started { background: #edf1f0; color: #63736d; }
.course-progress-meta { margin-top: .85rem; display: flex; align-items: center; justify-content: space-between; color: var(--text-muted); font-size: .75rem; }
.course-progress-meta strong { color: var(--primary-color); }
.course-progress-track { height: 7px; margin-top: .4rem; overflow: hidden; border-radius: 999px; background: #e6ecea; }
.course-progress-track span { display: block; height: 100%; border-radius: inherit; background: linear-gradient(90deg, var(--primary-color), #19a275); transition: width .5s ease; }
.course-footer { min-height: 35px; margin-top: .85rem; display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.course-footer > span { display: flex; align-items: center; gap: .35rem; color: #52655e; font-size: .75rem; }
.course-footer > span svg { color: var(--primary-color); }
.course-footer .quiz-empty { color: var(--text-muted); }
.course-footer button { padding: .42rem .7rem; display: flex; align-items: center; gap: .35rem; border: 0; border-radius: 6px; background: transparent; color: var(--primary-color); font-weight: 750; cursor: pointer; }
.course-footer button:hover { background: var(--primary-light); }
.empty-courses { min-height: 300px; display: grid; place-content: center; justify-items: center; gap: .5rem; text-align: center; color: var(--text-muted); }
.empty-courses svg { color: #81a79a; }
.empty-courses h3 { color: var(--text-main); }
.browse-courses { margin-top: .5rem; padding: .55rem .8rem; border-radius: 7px; background: var(--primary-color); color: white; font-weight: 700; }

@media (max-width: 900px) {
  .profile-layout { grid-template-columns: 1fr; }
  .profile-sidebar { grid-template-columns: 1fr 1fr; }
}
@media (max-width: 650px) {
  .profile-hero { align-items: flex-start; flex-wrap: wrap; padding: 1.35rem; }
  .profile-avatar { width: 72px; height: 72px; flex-basis: 72px; font-size: 1.7rem; }
  .profile-points { width: 100%; padding: .8rem 0 0; grid-auto-flow: column; align-items: baseline; justify-content: start; gap: .35rem; border-left: 0; border-top: 1px solid #cadbd5; }
  .profile-sidebar { grid-template-columns: 1fr; }
  .courses-header { margin-bottom: 1rem; }
  .profile-course-card { grid-template-columns: 40px minmax(0,1fr); padding: 1rem; }
  .course-icon { width: 40px; height: 40px; }
  .course-title-row, .course-footer { align-items: flex-start; flex-direction: column; }
  .course-footer { gap: .45rem; }
  .course-footer button { padding-left: 0; }
}
</style>
