<template>
  <main class="profile-page animate-fade-in">
    <Transition name="content" mode="out-in">
      <PageLoading v-if="loading" key="loading" label="Đang tải hồ sơ..." />
      <div v-else-if="error" class="card empty" role="alert">
        <p>{{ error }}</p>
        <button class="btn btn-primary" @click="load">Thử lại</button>
      </div>
      <div v-else-if="profile" key="loaded" class="profile-content">
        <header class="profile-hero card">
          <div class="profile-avatar">
            {{ (profile.fullName || profile.username).charAt(0).toUpperCase() }}
          </div>
          <div>
            <span class="eyebrow">HỒ SƠ NGƯỜI DÙNG</span>
            <h1>{{ profile.fullName || profile.username }}</h1>
            <p>
              {{ profile.username }} ·
              {{ roleLabel(profile.roles || [profile.role])
              }}<span v-if="profile.groupName"> · {{ profile.groupName }}</span>
            </p>
          </div>
        </header>
        <nav class="profile-tabs" role="tablist" aria-label="Các phần hồ sơ">
          <button
            v-for="tab in tabs"
            :id="`profile-${tab.id}-tab`"
            :key="tab.id"
            role="tab"
            :aria-selected="activeTab === tab.id"
            :aria-controls="`profile-${tab.id}-panel`"
            :class="{ active: activeTab === tab.id }"
            @click="selectTab(tab.id)"
            @keydown="tabKey($event, tab.id)"
          >
            {{ tab.label }}
          </button>
        </nav>
        <section
          id="profile-info-panel"
          v-show="activeTab === 'info'"
          role="tabpanel"
          aria-labelledby="profile-info-tab"
          class="card info-panel"
        >
          <h2>Thông tin cá nhân</h2>
          <form @submit.prevent="saveProfile">
            <div class="info-grid">
              <label
                >Họ và tên<input
                  v-model="profile.fullName"
                  required
                  maxlength="255" /></label
              ><label
                >Email<input
                  v-model="profile.email"
                  type="email"
                  required /></label
              ><label
                >Mã nhân viên<input
                  :value="profile.employeeId || 'Chưa cập nhật'"
                  readonly /></label
              ><label
                >Tên đăng nhập<input
                  :value="profile.username"
                  readonly /></label
              ><label
                >Đơn vị công tác<input
                  :value="profile.unitName || 'Chưa gán đơn vị'"
                  readonly /></label
              ><label
                >Nhóm nghiệp vụ<input
                  :value="profile.groupName || 'Chưa phân nhóm'"
                  readonly /></label
              ><label
                >Vai trò<input
                  :value="roleLabel(profile.roles || [profile.role])"
                  readonly
              /></label>
            </div>
            <footer>
              <button class="btn btn-primary" :disabled="saving">
                {{ saving ? "Đang lưu..." : "Lưu hồ sơ" }}
              </button>
              <p role="status">{{ profileMessage }}</p>
            </footer>
          </form>
        </section>
        <template>
          <section
            id="profile-stats-panel"
            v-if="activeTab === 'stats'"
            role="tabpanel"
            aria-labelledby="profile-stats-tab"
          >
            <p v-if="dataLoading" role="status">Đang tải dữ liệu...</p>
            <div class="summary-grid">
              <article
                v-for="metric in metrics"
                :key="metric.label"
                class="card"
              >
                <span>{{ metric.label }}</span
                ><strong>{{ metric.value }}</strong
                ><small>{{ metric.detail }}</small>
              </article>
            </div>
            <div class="stats-grid">
              <section class="card">
                <h2>Năng lực nghiệp vụ</h2>
                <p class="subtitle">
                  Bình quân điểm cao nhất mỗi bài đánh giá, theo phiên bản đã
                  thi gần nhất.
                </p>
                <RadarChart
                  v-if="activeTab === 'stats'"
                  :competencies="competencies"
                />
              </section>
              <section class="card">
                <h2>Tiến độ theo khóa học</h2>
                <p v-if="!stats.joined" class="subtitle">
                  Chưa có khóa học tham gia.
                </p>
                <article
                  v-for="item in statsCourses"
                  :key="item.course.id"
                  class="course-progress-row"
                >
                  <CourseThumbnail
                    compact
                    :thumbnail="item.course.thumbnail"
                    :name="item.course.name"
                  />
                  <div>
                    <h3>{{ item.course.name }}</h3>
                    <p>
                      {{ learningStatusLabel(item.learningStatus) }} ·
                      {{ item.completedCount }}/{{ item.totalCount }} bài ·
                      {{ item.percent }}%
                    </p>
                    <ProgressBar :progress="item.percent" height="6px" />
                  </div>
                </article>
                <button class="btn btn-secondary" :disabled="statsCoursePage === 0 || dataLoading" @click="statsCoursePage--">Trước</button> <button class="btn btn-secondary" :disabled="(statsCoursePage + 1) * 10 >= statsCourseTotal || dataLoading" @click="statsCoursePage++">Sau</button>
                <RouterLink to="/" class="view-link"
                  >Đến khóa học của tôi →</RouterLink
                >
              </section>
            </div>
          </section>
          <section
            id="profile-results-panel"
            v-if="activeTab === 'results'"
            role="tabpanel"
            aria-labelledby="profile-results-tab"
            class="card"
          >
            <h2>Kết quả kiểm tra</h2>
            <p v-if="dataLoading" role="status">Đang tải kết quả...</p>
            <p class="subtitle">
              Điểm gần nhất và điểm cao nhất được so sánh trong cùng phiên bản
              đề.
            </p>
            <div class="result-toolbar">
              <input
                v-model="resultSearch"
                type="search"
                placeholder="Tìm khóa hoặc bài kiểm tra..."
                aria-label="Tìm kết quả kiểm tra"
              /><span v-if="bestScore !== null"
                >Điểm cao nhất: <strong>{{ bestScore }}/100</strong></span
              >
            </div>
            <div class="table-scroll">
              <table class="results-table">
                <thead>
                  <tr>
                    <th>Bài kiểm tra</th>
                    <th>Lần gần nhất</th>
                    <th>Điểm cao nhất</th>
                    <th>Lịch sử</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="group in paginatedResults"
                    :key="`${group.latest.quizId}-${group.latest.revision}`"
                  >
                    <td>
                      <strong>{{ group.latest.title }}</strong
                      ><small
                        >{{ group.courseName }} · Phiên bản
                        {{ group.latest.revision }} · Ngưỡng
                        {{ group.latest.passingScore }}%</small
                      >
                    </td>
                    <td>
                      <strong
                        >{{ group.latest.score }}/{{
                          group.latest.total
                        }}</strong
                      ><small
                        >{{ formatLearningDate(group.latest.takenAt) }} ·
                        {{ group.latest.passed ? "Đạt" : "Chưa đạt" }}</small
                      >
                    </td>
                    <td>
                      <strong
                        >{{ group.best.score }}/{{ group.best.total }}</strong
                      ><small
                        >{{
                          Math.round(
                            (group.best.score / group.best.total) * 100,
                          )
                        }}%</small
                      >
                    </td>
                    <td>
                      <button
                        class="view-link"
                        @click="openHistory(group)"
                      >
                        {{ group.attemptCount }} lần thi →
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!dataLoading && !results.length" class="empty">
              {{
                resultSearch
                  ? "Không có kết quả phù hợp."
                  : "Chưa có kết quả kiểm tra."
              }}
            </p>
            <TablePagination
              v-model:page="resultPage"
              v-model:page-size="resultPageSize"
              :total="Math.max(resultData?.total || 0,resultData?.legacyTotal || 0)"
              :page-count="resultPageCount"
              unit="bài kiểm tra"
              unit-label="Bài kiểm tra"
            />
          </section>
        </template>
        <section
          v-if="
            activeTab === 'results' && resultData?.legacyScores?.length
          "
          class="card"
          aria-label="Kết quả lưu trước đây"
        >
          <h2>Kết quả lưu trước đây</h2>
          <p class="subtitle">
            Các điểm đã lưu trước khi có lịch sử từng lần thi. Không dùng các
            bản ghi này làm điểm lần thi gần nhất.
          </p>
          <div class="table-scroll">
            <table class="results-table">
              <thead>
                <tr>
                  <th>Bài kiểm tra / khóa học</th>
                  <th>Điểm đã lưu</th>
                  <th>Ngày ghi nhận</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="score in resultData?.legacyScores"
                  :key="`${score.courseId}-${score.lessonId}`"
                >
                  <td>
                    {{ score.title
                    }}<small>{{ score.courseName }}</small>
                  </td>
                  <td>{{ score.score }}/{{ score.total }}</td>
                  <td>{{ formatLearningDate(score.takenAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <div
          v-if="
            dataError &&
            activeTab !== 'info' &&
            activeTab !== 'awards'
          "
          class="card empty data-reveal"
          role="alert"
        >
          <p>{{ dataError }}</p>
          <button class="btn btn-primary" @click="loadTab">
            Thử lại
          </button>
        </div>
        <section
          id="profile-awards-panel"
          v-if="activeTab === 'awards'"
          role="tabpanel"
          aria-labelledby="profile-awards-tab"
          class="card"
        >
          <div v-if="dataError" role="alert">
            <p>{{ dataError }}</p>
            <button class="btn btn-primary" @click="loadTab">
              Thử lại
            </button>
          </div>
          <template v-else
            ><div class="awards-heading">
              <h2>Huy hiệu & điểm tích lũy</h2><p v-if="dataLoading" role="status">Đang tải huy hiệu...</p>
              <strong>{{ (awardsData?.points ?? 0).toLocaleString('vi-VN') }} PTS</strong>
            </div>
            <div class="award-grid">
              <article
                v-for="badge in awardsData?.items || []"
                :key="badge.id"
                class="award"
              >
                <Award :size="34" />
                <h3>{{ badge.title }}</h3>
                <p>{{ badge.courseName }}</p>
                <small
                  >Nhận ngày {{ formatLearningDate(badge.unlockedAt) }}</small
                >
              </article>
            </div>
            <p v-if="awardsData && !dataLoading && !awardsData.items.length" class="empty">
              Chưa có huy hiệu. Thành tích xuất hiện khi bạn hoàn thành điều
              kiện đánh giá.
            </p>
            <TablePagination v-if="awardsData && awardsData.total > 20" v-model:page="awardPage" :page-size="20" :total="awardsData.total" :page-count="Math.max(1,Math.ceil(awardsData.total/20))" :sizes="[20]" unit="huy hiệu"/><RouterLink to="/leaderboard" class="view-link"
              >Bảng vinh danh →</RouterLink
            ></template
          >
        </section>
      </div>
    </Transition>
    <Teleport to="body"
      ><Transition name="modal"
        ><div
          v-if="history"
          class="history-overlay cursor-pointer"
          @click.self="closeHistory"
        >
          <section
            class="history-dialog card"
            role="dialog"
            aria-modal="true"
            aria-labelledby="attempt-history-title"
          >
            <header>
              <h2 id="attempt-history-title">
                Lịch sử · {{ historyTitle }}
              </h2>
              <button class="btn btn-secondary" @click="closeHistory">
                Đóng
              </button>
            </header>
            <p v-if="historyLoading" role="status">Đang tải lịch sử...</p>
            <p v-if="historyError" role="alert">{{ historyError }} <button @click="loadMoreHistory">Thử lại</button></p>
            <button v-if="historyCursor" class="btn btn-secondary" :disabled="historyLoading" @click="loadMoreHistory">Tải thêm lịch sử</button>
            <div class="table-scroll">
              <table class="results-table">
                <thead>
                  <tr>
                    <th>Ngày thi</th>
                    <th>Phiên bản</th>
                    <th>Điểm</th>
                    <th>Kết quả</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="attempt in history" :key="attempt.id">
                    <td>{{ formatLearningDate(attempt.takenAt) }}</td>
                    <td>{{ attempt.revision }}</td>
                    <td>{{ attempt.score }}/{{ attempt.total }}</td>
                    <td>{{ attempt.passed ? "Đạt" : "Chưa đạt" }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div></Transition
      ></Teleport
    >
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { Award } from "lucide-vue-next";
import { useUserStore } from "../stores/userStore";
import { profileReadApi, type AssessmentGroup, type ResultPage, type ProfileStats } from "../api/profileReadApi";
import { userApi } from "../api/userApi";
import type { UserProfile, LearnerAttempt, LearnerCourse } from "../types";
import { roleLabel } from "../utils/permissions";
import {
  formatLearningDate,
  learningStatusLabel,
} from "../utils/learning";
import CourseThumbnail from "../components/CourseThumbnail.vue";
import RadarChart from "../components/RadarChart.vue";
import ProgressBar from "../components/ProgressBar.vue";
import TablePagination from "../components/TablePagination.vue";
import PageLoading from "../components/PageLoading.vue";
const user = useUserStore(),
  route = useRoute(),
  router = useRouter();
const profile = ref<UserProfile | null>(null),
  loading = ref(true),
  error = ref(""),
  saving = ref(false),
  profileMessage = ref("");
const tabs = [
  { id: "info", label: "Thông tin người dùng" },
  { id: "stats", label: "Thống kê học tập" },
  { id: "results", label: "Kết quả kiểm tra" },
  { id: "awards", label: "Thành tích" },
];
const activeTab = ref(
  tabs.some((t) => t.id === route.query.tab) ? String(route.query.tab) : "info",
);
function selectTab(id: string) {
  activeTab.value = id;
  void router.replace({ query: { ...route.query, tab: id } });
}
function tabKey(e: KeyboardEvent, id: string) {
  if (["ArrowLeft", "ArrowRight", "Home", "End"].includes(e.key)) {
    e.preventDefault();
    const i = tabs.findIndex((t) => t.id === id),
      next =
        tabs[
          e.key === "Home"
            ? 0
            : e.key === "End"
              ? tabs.length - 1
              : (i + (e.key === "ArrowRight" ? 1 : -1) + tabs.length) %
                tabs.length
        ]!;
    selectTab(next.id);
    document.getElementById(`profile-${next.id}-tab`)?.focus();
  }
}
watch(
  () => route.query.tab,
  (value) =>
    (activeTab.value = tabs.some((t) => t.id === value)
      ? String(value)
      : "info"),
);
async function load() {
  loading.value = true;
  error.value = "";
  try {
    profile.value = await userApi.getMyProfile();
    await loadTab();
  } catch {
    error.value = "Không thể tải hồ sơ. Vui lòng thử lại.";
  } finally {
    loading.value = false;
  }
}
async function saveProfile() {
  if (!profile.value) return;
  saving.value = true;
  profileMessage.value = "";
  try {
    profile.value = await userApi.updateProfile({
      fullName: profile.value.fullName,
      email: profile.value.email || "",
    });
    user.fullName = profile.value.fullName;
    localStorage.setItem("fullName", user.fullName);
    profileMessage.value = "Đã lưu hồ sơ.";
  } catch (e) {
    profileMessage.value =
      (e as { response?: { data?: { message?: string } } }).response?.data
        ?.message || "Không lưu được hồ sơ.";
  } finally {
    saving.value = false;
  }
}
const emptyStats: ProfileStats = {joined:0,completed:0,total:0,percent:null,notStarted:0,inProgress:0,complete:0,mandatory:0,mandatoryComplete:0,competencies:[]};
const stats = ref<ProfileStats>(emptyStats), statsCourses = ref<LearnerCourse[]>([]), statsCoursePage=ref(0), statsCourseTotal=ref(0);
const summary = stats;
const competencies = computed(() => stats.value.competencies);
const awardPage=ref(1),awardsData=ref<import('../api/profileReadApi').AwardsPage|null>(null);
watch(awardPage,()=>{if(activeTab.value==='awards')void loadTab()});
const resultData = ref<ResultPage|null>(null), dataError=ref(""), dataLoading=ref(false);
let requestVersion=0;
async function loadTab() {
 const version=++requestVersion; dataError.value=""; dataLoading.value=true;
 try {
  if(activeTab.value==='stats') {
   const [data,courses]=await Promise.all([profileReadApi.stats(),profileReadApi.courses(statsCoursePage.value)]);
   if(version!==requestVersion)return;stats.value=data;statsCourses.value=courses.items;statsCourseTotal.value=courses.total;
  } else if(activeTab.value==='results') {
   const data=await profileReadApi.results(resultPage.value-1,resultPageSize.value,resultSearch.value);
   if(version===requestVersion)resultData.value=data;
  } else if(activeTab.value==='awards') {const data=await profileReadApi.awards(awardPage.value-1);if(version===requestVersion)awardsData.value=data;}
 } catch { if(version===requestVersion)dataError.value="Không thể tải dữ liệu. Vui lòng thử lại."; }
 finally {if(version===requestVersion)dataLoading.value=false;}
}
watch(activeTab,loadTab);
watch(statsCoursePage,()=>{if(activeTab.value==='stats')void loadTab()});
const metrics = computed(() => [
  {
    label: "Khóa tham gia",
    value: summary.value.joined,
    detail: `${summary.value.inProgress} đang học · ${summary.value.notStarted} chưa bắt đầu`,
  },
  {
    label: "Hoàn thành khóa",
    value: summary.value.joined
      ? `${Math.round((summary.value.complete / summary.value.joined) * 100)}%`
      : "—",
    detail: `${summary.value.complete}/${summary.value.joined} khóa`,
  },
  {
    label: "Hoàn thành bài",
    value: summary.value.percent === null ? "—" : `${summary.value.percent}%`,
    detail: `${summary.value.completed}/${summary.value.total} bài`,
  },
  {
    label: "Bắt buộc hoàn thành",
    value: `${summary.value.mandatoryComplete}/${summary.value.mandatory}`,
    detail: "khóa học bắt buộc",
  },
]);
const resultSearch = ref(""),
  resultPage = ref(1),
  resultPageSize = ref(10),
  history = ref<LearnerAttempt[] | null>(null);
const paginatedResults=computed(()=>resultData.value?.items || []);
const results=paginatedResults;
const bestScore=computed(()=>resultData.value?.bestScore??null);
const resultPageCount=computed(()=>Math.max(1,Math.ceil(Math.max(resultData.value?.total||0,resultData.value?.legacyTotal||0)/resultPageSize.value)));
let searchTimer: ReturnType<typeof setTimeout> | undefined;
watch(resultSearch,()=>{ clearTimeout(searchTimer);++requestVersion;searchTimer=setTimeout(()=>{if(resultPage.value!==1)resultPage.value=1;else void loadTab()},250); });
watch(resultPageSize,()=>{if(resultPage.value!==1)resultPage.value=1;else void loadTab()});
watch(resultPage,()=>{if(activeTab.value==='results')void loadTab()});
const historyTitle=ref(""),historyCursor=ref<string|null>(null),historyLoading=ref(false),historyError=ref("");
let historyGroup:AssessmentGroup|null=null,historyVersion=0;
async function openHistory(group:AssessmentGroup){historyLoading.value=false;historyTitle.value=group.latest.title;historyGroup=group;history.value=[];historyCursor.value=null;++historyVersion;await loadMoreHistory()}
function closeHistory(){++historyVersion;history.value=null;historyCursor.value=null;}
async function loadMoreHistory(){
 if(!historyGroup || historyLoading.value)return;const version=historyVersion;historyLoading.value=true;historyError.value="";
 try{const data=await profileReadApi.history(historyGroup.latest.quizId,historyGroup.latest.revision,historyCursor.value||undefined);if(version!==historyVersion)return;history.value=[...(history.value||[]),...data.items];historyCursor.value=data.nextCursor;}
 catch{if(version===historyVersion)historyError.value="Không tải được lịch sử. Vui lòng thử lại.";}finally{if(version===historyVersion)historyLoading.value=false;}
}
onMounted(load);
</script>
<style scoped>
.profile-content {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
}
.profile-page {
  max-width: 1320px;
  margin: auto;
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  min-width: 0;
}
.profile-hero {
  display: flex;
  align-items: center;
  gap: 1.2rem;
}
.profile-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: var(--primary-light);
  color: var(--primary-ink);
  display: grid;
  place-items: center;
  font-size: 1.6rem;
  font-weight: 700;
  flex-shrink: 0;
}
.eyebrow {
  font-size: 0.7rem;
  letter-spacing: 1px;
  color: var(--primary-ink);
  font-weight: 700;
}
.profile-hero h1 {
  font-size: 1.6rem;
  margin: 0.3rem 0;
}
.profile-hero p,
.subtitle {
  color: var(--text-muted);
  font-size: 0.8rem;
}
.profile-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  border-bottom: 1px solid var(--border-color);
}
.profile-tabs button {
  padding: 0.8rem 0;
  background: none;
  border: 0;
  border-bottom: 3px solid transparent;
  color: var(--text-muted);
  font-weight: 600;
}
.profile-tabs .active {
  color: var(--primary-ink);
  border-bottom-color: var(--primary-color);
}
h2 {
  font-size: 1.15rem;
  margin-bottom: 0.7rem;
}
.subtitle {
  margin-bottom: 1rem;
}
.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.2rem;
  margin-top: 1.5rem;
}
.info-grid label {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.info-grid input,
.result-toolbar input {
  padding: 0.7rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-main);
  min-width: 0;
}
.info-grid input[readonly] {
  background: var(--bg-main);
}
.info-panel footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  margin-top: 1.5rem;
}
.info-panel footer p {
  font-size: 0.8rem;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 1rem;
  margin-bottom: 1.3rem;
}
.summary-grid article {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.summary-grid strong {
  font-size: 1.8rem;
  color: var(--primary-ink);
}
.summary-grid span,
.summary-grid small {
  font-size: 0.8rem;
  color: var(--text-muted);
}
.stats-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1.3rem;
}
.course-progress-row {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 1rem 0;
  border-bottom: 1px solid var(--border-color);
}
.course-progress-row > div {
  flex: 1;
  min-width: 0;
}
.course-progress-row h3 {
  font-size: 0.9rem;
  margin-bottom: 0.3rem;
}
.course-progress-row p {
  font-size: 0.75rem;
  color: var(--text-muted);
  margin-bottom: 0.5rem;
}
.view-link {
  display: inline-block;
  background: none;
  border: 0;
  color: var(--primary-ink);
  padding: 0.6rem 0;
  font-size: 0.85rem;
}
.table-scroll {
  overflow: auto;
  width: 100%;
}
.results-table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}
.results-table th {
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 600;
}
.results-table td,
.results-table th {
  padding: 1rem 0.8rem;
  border-bottom: 1px solid var(--border-color);
}
.results-table td {
  font-size: 0.9rem;
}
.results-table small {
  display: block;
  color: var(--text-muted);
  font-size: 0.75rem;
  margin-top: 0.4rem;
}
.results-table td:first-child {
  min-width: 180px;
}
.result-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 1rem;
  margin-bottom: 1rem;
}
.result-toolbar input {
  flex: 1;
  max-width: 420px;
}
.result-toolbar span {
  font-size: 0.85rem;
}
.empty {
  padding: 1.5rem;
  text-align: center;
  color: var(--text-muted);
}
.awards-heading {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
}
.awards-heading strong {
  color: var(--primary-ink);
}
.award-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
}
.award {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 0.5rem;
  padding: 1.5rem;
  background: var(--primary-light);
  border-radius: 12px;
  color: var(--primary-ink);
}
.award h3 {
  font-size: 1rem;
}
.award p,
.award small {
  font-size: 0.8rem;
}
.history-overlay {
  position: fixed;
  inset: 0;
  z-index: 1200;
  background: #0007;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}
.history-dialog {
  width: min(800px, 100%);
  max-height: 85vh;
  overflow: auto;
}
.history-dialog header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
.history-dialog header h2 {
  font-size: 1rem;
  margin: 0;
}
@media (max-width: 900px) {
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 550px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
  .profile-tabs {
    gap: 1rem;
  }
  .profile-tabs button {
    font-size: 0.8rem;
  }
  .profile-hero h1 {
    font-size: 1.3rem;
  }
  .summary-grid {
    gap: 0.7rem;
  }
  .summary-grid .card {
    padding: 1rem;
  }
  .result-toolbar input,
  .info-grid input {
    font-size: 16px;
  }
}
</style>
