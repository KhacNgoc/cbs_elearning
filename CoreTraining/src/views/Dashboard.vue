<template>
  <main class="learning-home animate-fade-in">
    <header class="welcome">
      <div>
        <span class="eyebrow">HỌC TẬP MỖI NGÀY</span>
        <h1>Xin chào, {{ user.fullName || user.username || "bạn" }}</h1>
        <p>Tiếp tục hành trình học tập của bạn.</p>
      </div>
    </header>
    <Transition name="content" mode="out-in">
      <PageLoading
        v-if="learning.dashboardLoading && !learning.dashboard"
        key="loading"
        label="Đang tải thông tin học tập..."
      />
      <div v-else-if="learning.dashboardError" class="card empty" role="alert">
        <p>{{ learning.dashboardError }}</p>
        <button class="btn btn-primary" @click="refresh">Thử lại</button>
      </div>
      <div
        v-else-if="learning.dashboard"
        key="loaded"
        class="learning-home-content"
      >
        <section v-if="learning.dashboard.resume" class="resume-banner">
          <BookOpen :size="28" />
          <div>
            <span class="eyebrow">{{
              learning.dashboard.resume.lastAccessedAt
                ? "ĐANG HỌC GẦN NHẤT"
                : "SẴN SÀNG BẮT ĐẦU"
            }}</span>
            <h2>{{ learning.dashboard.resume.lessonTitle }}</h2>
            <p>
              {{ learning.dashboard.resume.courseName }} ·
              {{ resumeDescription }}
            </p>
          </div>
          <button
            class="btn btn-primary"
            :disabled="!!opening"
            @click="openLearning(learning.dashboard.resume, !!learning.dashboard.resume.lastAccessedAt)"
          >
            {{
              learning.dashboard.resume.lastAccessedAt
                ? "Tiếp tục học"
                : "Bắt đầu học"
            }}
            <ArrowRight :size="18" />
          </button>
        </section>
        <section v-else-if="!summary.joined" class="resume-banner">
          <BookOpen :size="28" />
          <div>
            <h2>Bắt đầu hành trình học tập</h2>
            <p>Khám phá và đăng ký khóa học phù hợp.</p>
          </div>
          <button class="btn btn-primary" @click="changeTab('discover')">
            Khám phá khóa học
          </button>
        </section>
        <section class="progress-overview" aria-label="Tiến độ học tập">
          <div class="overall">
            <div>
              <span>Tiến độ học tập của tôi</span
              ><strong>{{
                summary.percent === null
                  ? "Chưa có dữ liệu"
                  : `${summary.percent}%`
              }}</strong>
            </div>
            <ProgressBar :progress="summary.percent || 0" height="6px" /><small
              >{{ summary.completed }}/{{ summary.total }} bài hoàn thành ·
              {{ summary.joined }} khóa tham gia</small
            >
          </div>
          <div class="status-counts" role="group" aria-label="Số khóa học theo trạng thái">
          <button
            v-for="stat in statusCounts"
            :key="stat.status"
            type="button"
            class="status-count"
            :class="{ 'is-selected': tab === 'mine' && filters.status === stat.status }"
            :aria-pressed="tab === 'mine' && filters.status === stat.status"
            @click="
              tab = 'mine';
              filters.status = stat.status;
            "
          >
            <strong>{{ stat.count }}</strong
            ><span>{{ learningStatusLabel(stat.status) }}</span>
          </button>
          </div>
        </section>
        <nav
          class="learning-tabs"
          role="tablist"
          aria-label="Danh sách khóa học"
        >
          <button
            v-for="item in catalogTabs"
            :id="`${item.id}-tab`"
            :key="item.id"
            role="tab"
            :aria-selected="tab === item.id"
            aria-controls="course-list-panel"
            :class="{ active: tab === item.id }"
            @click="changeTab(item.id)"
            @keydown="tabKey($event, item.id)"
          >
            {{ item.label }} <span>{{ item.count }}</span>
          </button>
        </nav>
        <section
          id="course-list-panel"
          role="tabpanel"
          :aria-labelledby="`${tab}-tab`"
        >
          <div class="search-toolbar">
            <label class="search-box"
              ><Search :size="19" /><input
                v-model="filters.search"
                type="search"
                placeholder="Tên khóa học, mã hoặc từ khóa..."
                aria-label="Tìm khóa học theo tên, mã hoặc từ khóa" /></label
            ><label class="select-label"
              >Trạng thái<select v-model="filters.status">
                <option value="">Tất cả</option>
                <option
                  v-for="status in availableStatuses"
                  :key="status"
                  :value="status"
                >
                  {{ learningStatusLabel(status) }}
                </option>
              </select></label
            ><button
              class="btn btn-secondary"
              :aria-expanded="advanced"
              aria-controls="advanced-filters"
              @click="advanced = !advanced"
            >
              <SlidersHorizontal :size="17" /> Bộ lọc
              {{ activeFilters ? `(${activeFilters})` : "" }}
            </button>
          </div>
          <MotionCollapse :open="advanced"
            ><div id="advanced-filters" class="advanced-filters card">
              <div class="filter-grid">
                <label v-for="field in filterFields" :key="field.key"
                  >{{ field.label
                  }}<select v-model="filters[field.key]">
                    <option value="">Tất cả</option>
                    <option
                      v-for="value in options(field.key)"
                      :key="value"
                      :value="value"
                    >
                      {{ optionLabel(field.key, value) }}
                    </option>
                  </select></label
                >
              </div>
              <label class="mandatory-toggle"
                ><input v-model="filters.mandatory" type="checkbox" /> Chỉ khóa
                học bắt buộc</label
              ><button class="clear-filters" @click="clearFilters">
                Xóa bộ lọc
              </button>
            </div></MotionCollapse
          >
          <div class="list-meta">
            <span aria-live="polite"
              >{{ resultTotal }} khóa học
              {{ tab === "mine" ? "của tôi" : "trong danh mục" }}</span
            ><label
              >Sắp xếp
              <select v-model="sort">
                <option value="deadline">Ưu tiên hạn hoàn thành</option>
                <option value="recent">Học gần đây</option>
                <option value="name">Tên khóa học</option>
              </select></label
            >
          </div>
          <p v-if="learning.dashboardLoading" role="status">Đang tải khóa học...</p>
          <TransitionGroup
            v-show="!learning.dashboardLoading"
            :key="`${tab}-${page}`"
            name="data-list"
            tag="div"
            class="course-grid"
            appear
          >
            <article
              v-for="(item, index) in paginated"
              :key="item.course.id"
              class="learning-course card"
              :style="{ '--item-delay': `${Math.min(index, 4) * 20}ms` }"
            >
              <CourseThumbnail
                :thumbnail="item.course.thumbnail"
                :name="item.course.name"
              />
              <div class="course-content">
                <div class="badges">
                  <span
                    class="status-badge"
                    :class="item.learningStatus.toLowerCase()"
                    >{{ learningStatusLabel(item.learningStatus) }}</span
                  ><span v-if="item.mandatory" class="mandatory-badge"
                    >Bắt buộc</span
                  >
                </div>
                <h3>{{ item.course.name }}</h3>
                <p class="course-meta">
                  {{
                    [
                      item.course.domainName,
                      item.course.businessModule,
                      item.course.level,
                    ]
                      .filter(Boolean)
                      .join(" · ") || "Chưa phân loại"
                  }}
                </p>
                <p class="course-meta">
                  {{ item.course.programName
                  }}<span v-if="isEnrolled(item)">
                    · {{ sourceLabel(item.source) }}</span
                  >
                </p>
                <template v-if="isEnrolled(item)"
                  ><div class="course-progress">
                    <span
                      >{{ item.completedCount }}/{{ item.totalCount }} bài hoàn
                      thành</span
                    ><strong>{{ item.percent }}%</strong>
                  </div>
                  <ProgressBar :progress="item.percent" height="6px"
                /></template>
                <p v-else class="course-meta">
                  {{ item.totalCount }} bài học · Đăng ký cần phê duyệt
                </p>
                <footer>
                  <span
                    class="deadline"
                    :class="{ urgent: deadlineState(item) }"
                    >{{
                      isEnrolled(item)
                        ? `Hạn: ${formatLearningDate(item.dueDate)}`
                        : `Mã: ${item.course.code || item.course.id}`
                    }}<small v-if="deadlineState(item)">{{
                      deadlineState(item)
                    }}</small></span
                  ><button
                    class="btn"
                    :class="
                      item.learningStatus === 'IN_PROGRESS'
                        ? 'btn-primary'
                        : 'btn-secondary'
                    "
                    :disabled="item.source === 'PENDING' || !!opening"
                    @click="openCourse(item)"
                  >
                    {{ actionLabel(item)
                    }}<ArrowRight v-if="item.source !== 'PENDING'" :size="15" />
                  </button>
                </footer>
              </div>
            </article>
          </TransitionGroup>
          <div v-if="!learning.dashboardLoading && !resultTotal" class="card empty data-reveal">
            <BookOpen :size="32" />
            <h3>
              {{
                tab === "mine" && !summary.joined
                  ? "Chưa có khóa học tham gia"
                  : "Không có khóa học phù hợp"
              }}
            </h3>
            <p>Khám phá khóa học hoặc thay đổi từ khóa và bộ lọc.</p>
            <button class="btn btn-secondary" @click="clearFilters">
              Xóa tìm kiếm & bộ lọc
            </button>
          </div>
          <TablePagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :page-count="pageCount"
            :total="resultTotal"
            :sizes="[6, 9, 12]"
            unit="khóa học"
            unit-label="Khóa học"
          />
        </section>
      </div>
    </Transition>
    <AdminModal
      :open="!!registerTarget"
      title="Đăng ký khóa học"
      submit-label="Gửi yêu cầu đăng ký"
      :width="460"
      :busy="registering"
      @close="registerTarget = null"
      @submit="register"
      ><p>
        Gửi yêu cầu đăng ký khóa “{{ registerTarget?.course.name }}”? Khóa sẽ mở
        sau khi được phê duyệt.
      </p></AdminModal
    >
  </main>
</template>
<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from "vue";
import { useRouter } from "vue-router";
import {
  BookOpen,
  Search,
  ArrowRight,
  SlidersHorizontal,
} from "lucide-vue-next";
import { useUserStore } from "../stores/userStore";
import { useLearningStore } from "../stores/learningStore";
import type { LearnerCourse, ResumeTarget } from "../types";
import {
  deadlineState,
  formatLearningDate,
  isEnrolled,
  learningStatusLabel,
  resumeRoute,
} from "../utils/learning";
import { getDashboardResume } from "../api/learnerDashboardApi";
import CourseThumbnail from "../components/CourseThumbnail.vue";
import ProgressBar from "../components/ProgressBar.vue";
import TablePagination from "../components/TablePagination.vue";
import AdminModal from "../components/AdminModal.vue";
import MotionCollapse from "../components/MotionCollapse.vue";
import PageLoading from "../components/PageLoading.vue";
import { showToast } from "../utils/toast";
import { courseRegistrationError } from "../utils/courseRegistrationError";
const user = useUserStore(),
  learning = useLearningStore(),
  router = useRouter();
const tab = ref<"mine" | "discover">("mine"),
  advanced = ref(false),
  sort = ref("deadline"),
  page = ref(1),
  pageSize = ref(6),
  opening = ref("");
const filters = reactive({
  search: "",
  status: "",
  domainId: "",
  businessModule: "",
  programId: "",
  level: "",
  source: "",
  mandatory: false,
});
const filterFields = [
  { key: "domainId", label: "Lĩnh vực" },
  { key: "businessModule", label: "Phân hệ nghiệp vụ" },
  { key: "programId", label: "Chương trình" },
  { key: "level", label: "Mức độ" },
  { key: "source", label: "Nguồn tham gia" },
] as const;
const summary = computed(() => learning.dashboard?.summary || {
  joined: 0, discover: 0, completed: 0, total: 0, percent: null,
  notStarted: 0, inProgress: 0, complete: 0, mandatory: 0, mandatoryComplete: 0,
});
const statusCounts = computed(() => [
  { status: "NOT_STARTED", count: summary.value.notStarted },
  { status: "IN_PROGRESS", count: summary.value.inProgress },
  { status: "COMPLETED", count: summary.value.complete },
]);
const catalogTabs = computed(() => [
  { id: "mine" as const, label: "Khóa học của tôi", count: summary.value.joined },
  { id: "discover" as const, label: "Khám phá & đăng ký", count: summary.value.discover },
]);
const availableStatuses = computed(() => (learning.dashboard?.choices.status || []).map(o => o.value));
const options = (key: (typeof filterFields)[number]["key"]) =>
  (learning.dashboard?.choices[key] || []).map(o => o.value).sort((a,b) => a.localeCompare(b,"vi"));
function optionLabel(key: (typeof filterFields)[number]["key"], value: string) {
  if (key === "source") return sourceLabel(value);
  const choices = learning.dashboard?.choices[key] || [];
  const label = choices.find(o => o.value === value)?.label || value;
  return choices.some(o => o.label === label && o.value !== value) ? `${label} (${value})` : label;
}
const activeFilters = computed(
  () =>
    filterFields.filter((f) => filters[f.key]).length +
    Number(filters.mandatory),
);
const resultTotal = computed(() => learning.dashboardPage?.total || 0);
const pageCount = computed(() => Math.max(1, Math.ceil(resultTotal.value / pageSize.value)));
const paginated = computed(() => learning.dashboardPage?.items || []);
let filterTimer: ReturnType<typeof setTimeout> | undefined;
let lastTab = tab.value;
watch([filters, sort, tab, pageSize], () => {
  learning.invalidateDashboard();
  page.value = 1;
  clearTimeout(filterTimer);
  const refreshSummary = lastTab !== tab.value;
  lastTab = tab.value;
  filterTimer = setTimeout(() => loadDashboard(refreshSummary), 250);
}, { deep: true });
watch(page, () => {
  clearTimeout(filterTimer);
  filterTimer = setTimeout(() => loadDashboard(false), 0);
});
watch(pageCount, (n) => { if (page.value > n) page.value = n; });
function loadDashboard(refreshSummary = true) {
  return learning.loadDashboard({ ...filters, tab: tab.value, sort: sort.value, page: page.value, size: pageSize.value }, refreshSummary);
}
function clearFilters() {
  Object.assign(filters, {
    search: "",
    status: "",
    domainId: "",
    businessModule: "",
    programId: "",
    level: "",
    source: "",
    mandatory: false,
  });
}
function changeTab(value: "mine" | "discover") {
  tab.value = value;
  clearFilters();
}
function tabKey(e: KeyboardEvent, id: "mine" | "discover") {
  if (["ArrowLeft", "ArrowRight", "Home", "End"].includes(e.key)) {
    e.preventDefault();
    const next =
      e.key === "Home"
        ? "mine"
        : e.key === "End"
          ? "discover"
          : id === "mine"
            ? "discover"
            : "mine";
    changeTab(next);
    document.getElementById(`${next}-tab`)?.focus();
  }
}
const sourceLabel = (value: string) =>
  ({
    ASSIGNED: "Được giao",
    REGISTERED: "Đã đăng ký",
    PENDING: "Chờ duyệt",
    AVAILABLE: "Có thể đăng ký",
  })[value] || value;
const actionLabel = (item: LearnerCourse) =>
  item.source === "PENDING"
    ? "Chờ duyệt"
    : item.source === "AVAILABLE"
      ? "Đăng ký học"
      : item.learningStatus === "COMPLETED"
        ? "Xem lại"
        : item.learningStatus === "NOT_STARTED"
          ? "Bắt đầu học"
          : "Tiếp tục học";
const resumeDescription = computed(() => {
  const t = learning.dashboard?.resume;
  if (!t) return "";
  return t.contentType === "DOCUMENT"
    ? `Tài liệu · Trang ${t.documentPage || 1}`
    : t.contentType === "QUIZ"
      ? "Kiểm tra bắt buộc cần hoàn thành"
      : t.contentType === "VIDEO"
        ? `Video · ${Math.floor((t.positionSeconds || 0) / 60)}:${String(Math.floor((t.positionSeconds || 0) % 60)).padStart(2, "0")}`
        : "Nội dung bài học";
});
async function openLearning(target: ResumeTarget, hasStarted = true) {
  await router.push(
    hasStarted
      ? resumeRoute(target)
      : `/courses/${encodeURIComponent(target.courseId)}`,
  );
}
const registerTarget = ref<LearnerCourse | null>(null),
  registering = ref(false);
async function openCourse(item: LearnerCourse) {
  if (item.source === "AVAILABLE") {
    registerTarget.value = item;
    return;
  }
  if (item.source === "PENDING") return;
  opening.value = item.course.id;
  try {
    const target = item.learningStatus === "IN_PROGRESS" ? await getDashboardResume(item.course.id) : null;
    if (target) await openLearning(target);
    else {
      await router.push(`/courses/${encodeURIComponent(item.course.id)}`);
    }
  } catch {
    showToast("Không thể mở khóa học. Vui lòng thử lại.", "error");
  } finally {
    opening.value = "";
  }
}
async function register() {
  if (!registerTarget.value) return;
  registering.value = true;
  try {
    await user.registerCourse(registerTarget.value.course.id);
    registerTarget.value = null;
    await loadDashboard();
    showToast("Đã gửi yêu cầu đăng ký.");
  } catch (error) {
    showToast(courseRegistrationError(error), "error");
  } finally {
    registering.value = false;
  }
}
async function refresh() {
  await loadDashboard();
}
onMounted(refresh);
onUnmounted(() => clearTimeout(filterTimer));
</script>
<style scoped>
.learning-home-content {
  display: flex;
  flex-direction: column;
  gap: 1.3rem;
  min-width: 0;
}
.learning-home {
  width: 100%;
  max-width: 1320px;
  margin: auto;
  display: flex;
  flex-direction: column;
  gap: 1.3rem;
  min-width: 0;
}
.welcome h1 {
  font-size: 1.8rem;
  margin: 0.3rem 0;
}
.welcome p,
.resume-banner p,
.course-meta {
  color: var(--text-muted);
}
.eyebrow {
  font-size: 0.7rem;
  letter-spacing: 1px;
  color: var(--primary-ink);
  font-weight: 700;
}
.resume-banner {
  display: flex;
  align-items: center;
  gap: 1.1rem;
  background: var(--primary-light);
  border-radius: var(--radius-lg);
  padding: 1.5rem;
  color: var(--primary-ink);
}
.resume-banner > div {
  flex: 1;
  min-width: 0;
}
.resume-banner h2 {
  font-size: 1.1rem;
  margin: 0.3rem 0;
}
.resume-banner p {
  font-size: 0.85rem;
}
.resume-banner > .btn {
  flex-shrink: 0;
}
.progress-overview {
  display: flex;
  align-items: center;
  gap: 2rem;
  padding: 0.2rem 0;
}
.overall {
  flex: 1.4;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  min-width: 200px;
}
.overall > div {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  font-size: 0.85rem;
}
.overall strong {
  color: var(--primary-ink);
}
.overall small {
  color: var(--text-muted);
}
.status-counts {
  flex: 3;
  min-width: 0;
  display: flex;
  align-items: stretch;
  padding: 0.9rem 0;
  background: var(--primary-light);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
.status-count {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: var(--text-main);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  padding: 0.35rem 0.75rem;
  cursor: pointer;
}
.status-count + .status-count {
  border-left: 1px solid var(--line-b8d5c8);
}
.status-count:hover,
.status-count.is-selected {
  color: var(--primary-ink);
}
.status-count.is-selected span {
  text-decoration: underline;
  text-underline-offset: 4px;
}
.status-count:focus-visible {
  outline: 2px solid var(--primary-ink);
  outline-offset: -3px;
}
.status-count strong {
  font-size: 2rem;
  font-weight: 750;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  color: inherit;
}
.status-count span {
  font-size: 0.8rem;
  font-weight: 600;
  line-height: 1.4;
  color: inherit;
}
.learning-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 1.5rem;
  border-bottom: 1px solid var(--border-color);
}
.learning-tabs button {
  background: none;
  border: 0;
  border-bottom: 3px solid transparent;
  padding: 0.8rem 0;
  color: var(--text-muted);
  font-weight: 600;
}
.learning-tabs button.active {
  color: var(--primary-ink);
  border-bottom-color: var(--primary-color);
}
.learning-tabs button span {
  padding: 0.15rem 0.5rem;
  background: var(--primary-light);
  border-radius: 20px;
  font-size: 0.7rem;
}
.search-toolbar {
  display: flex;
  gap: 0.8rem;
  align-items: end;
  margin-bottom: 1rem;
}
.search-box {
  flex: 1;
  min-width: 0;
  min-height: 44px;
  display: flex;
  align-items: center;
  gap: 0.7rem;
  background: var(--bg-card);
  padding: 0 1rem;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  color: var(--text-muted);
  font-size: 0.875rem;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.search-box:focus-within {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px var(--primary-light);
}
.search-box > svg {
  flex-shrink: 0;
}
.search-box input {
  appearance: none;
  -webkit-appearance: none;
  border: 0;
  outline: none;
  box-shadow: none;
  background: transparent;
  flex: 1;
  min-width: 0;
  width: 100%;
  min-height: 42px;
  padding: 0.6rem 0;
  color: var(--text-main);
  font: inherit;
  line-height: 1.5;
}
.search-box input::placeholder {
  color: var(--text-muted);
  opacity: 1;
}
.select-label,
.advanced-filters label {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  font-size: 0.8rem;
  color: var(--text-muted);
}
select {
  background: var(--bg-card);
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.6rem;
  color: var(--text-main);
  max-width: 100%;
}
.filter-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin-bottom: 1rem;
}
.advanced-filters {
  padding: 1rem;
  margin-bottom: 1rem;
}
.advanced-filters .mandatory-toggle {
  display: inline-flex;
  flex-direction: row;
  align-items: center;
  margin-right: 1rem;
}
.clear-filters {
  background: none;
  border: 0;
  color: var(--primary-ink);
  padding: 0.5rem;
}
.list-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.8rem;
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 1rem 0;
}
.list-meta select {
  font-size: 0.8rem;
  margin-left: 0.4rem;
}
.course-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1.2rem;
  margin-bottom: 1.5rem;
}
.learning-course {
  padding: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.course-content {
  padding: 1.1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  flex: 1;
  min-width: 0;
}
.course-content h3 {
  font-size: 1.05rem;
  overflow-wrap: anywhere;
}
.badges {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
.status-badge,
.mandatory-badge {
  font-size: 0.7rem;
  padding: 0.2rem 0.5rem;
  border-radius: 6px;
  background: var(--bg-main);
  color: var(--text-muted);
}
.status-badge.in_progress,
.status-badge.completed {
  background: var(--primary-light);
  color: var(--primary-ink);
}
.mandatory-badge {
  background: var(--surface-fff3cf);
  color: var(--ink-785719);
}
.course-meta {
  font-size: 0.75rem;
}
.course-progress {
  display: flex;
  justify-content: space-between;
  gap: 0.6rem;
  font-size: 0.75rem;
  margin-top: 0.5rem;
  color: var(--text-muted);
}
.course-progress strong {
  color: var(--primary-ink);
}
.course-content footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.7rem;
  margin-top: auto;
  padding-top: 1rem;
  flex-wrap: wrap;
}
.course-content footer .btn {
  font-size: 0.75rem;
  padding: 0.5rem 0.7rem;
}
.deadline {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.deadline small {
  display: block;
}
.deadline.urgent {
  color: var(--ink-976718);
}
.empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 0.8rem;
  padding: 2rem;
  text-align: center;
}
.empty p {
  color: var(--text-muted);
}
@media (max-width: 1150px) {
  .course-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 700px) {
  .resume-banner,
  .search-toolbar,
  .progress-overview {
    flex-wrap: wrap;
  }
  .resume-banner .btn {
    margin-left: auto;
  }
  .overall {
    flex-basis: 100%;
  }
  .search-box {
    flex-basis: 100%;
  }
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .progress-overview {
    gap: 0.8rem;
  }
  .status-counts {
    flex-basis: 100%;
  }
  .status-count {
    padding-inline: 0.4rem;
  }
  .welcome h1 {
    font-size: 1.45rem;
  }
}
@media (max-width: 480px) {
  .course-grid,
  .filter-grid {
    grid-template-columns: 1fr;
  }
  .learning-tabs {
    gap: 1rem;
  }
  .learning-tabs button {
    font-size: 0.85rem;
  }
  .search-toolbar > .btn {
    font-size: 0.8rem;
  }
  .resume-banner {
    padding: 1rem;
  }
  .resume-banner > .btn {
    margin-left: 0;
  }
  .search-box input,
  select {
    font-size: 16px;
  }
}
</style>
