<template>
  <main class="course-outline">
    <!-- Admin Review & Publishing Bar -->
    <div v-if="outline && isAdminView" class="admin-review-bar">
      <div class="admin-review-left">
        <button
          type="button"
          class="btn-back-tree"
          @click="backToCurriculumTree"
        >
          <FolderTree :size="16" />
          <span>Về Cây đào tạo</span>
        </button>
        <div class="admin-status-info">
          <span
            class="course-status-badge"
            :class="outline.course.status === 'PUBLISHED' ? 'published' : 'draft'"
          >
            <span class="status-dot"></span>
            {{
              outline.course.status === "PUBLISHED"
                ? "Đã xuất bản"
                : "Bản nháp · Chưa xuất bản"
            }}
          </span>
          <span class="admin-review-hint">
            Chế độ xem trước & duyệt nội dung khóa học
          </span>
        </div>
      </div>

      <div class="admin-review-actions">
        <button
          v-if="outline.course.status !== 'PUBLISHED'"
          type="button"
          class="btn-publish-course"
          :disabled="publishing"
          @click="changeCourseStatus('PUBLISHED')"
        >
          <Loader2 v-if="publishing" :size="16" class="spin-icon" />
          <span>{{ publishing ? "Đang xuất bản..." : "Kiểm tra & Xuất bản khóa học" }}</span>
        </button>
        <button
          v-else
          type="button"
          class="btn-unpublish-course"
          :disabled="publishing"
          @click="changeCourseStatus('DRAFT')"
        >
          <Loader2 v-if="publishing" :size="16" class="spin-icon" />
          <RotateCcw v-else :size="16" />
          <span>{{ publishing ? "Đang xử lý..." : "Chuyển về bản nháp" }}</span>
        </button>
      </div>
    </div>

    <RouterLink to="/" class="back-link"
      ><ArrowLeft :size="18" /> Khóa học</RouterLink
    >
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <template v-if="outline">
      <CourseThumbnail
        v-if="outline.course.thumbnail"
        class="outline-thumbnail"
        :thumbnail="outline.course.thumbnail"
        :name="outline.course.name"
      />
      <header>
        <div>
          <p>
            {{ outline.course.domainName }} / {{ outline.course.programName }}
          </p>
          <h1>{{ outline.course.name }}</h1>
          <p>
            {{ outline.chapters.length }} chương · {{ totalLessons }} bài ·
            {{ completedCount }}/{{ totalLessons }} bài hoàn thành
          </p>
        </div>
        <span v-if="outline.completed" class="complete-tag"
          ><CheckCircle2 :size="18" /> Đã hoàn thành khóa học</span
        ><button
          v-else-if="outline.resumeLessonId"
          class="btn btn-primary"
          @click="continueCourse"
        >
          Tiếp tục học <ArrowRight :size="17" />
        </button>
      </header>
      <p v-if="outline.course.description" class="description">
        {{ outline.course.description }}
      </p>
      <div v-if="outline.completed" class="course-complete card">
        <h2>
          {{ outline.chapters.length }}/{{ outline.chapters.length }} chương đã
          hoàn thành
        </h2>
        <p>
          Tất cả nội dung bài học và các kiểm tra bắt buộc đã hoàn thành. Kiểm
          tra tùy chọn vẫn có thể làm để ôn luyện.
        </p>
      </div>
      <CourseRatingForm v-if="outline.completed&&user.canLearn" :course-id="String(route.params.courseId)"/>
      <section
        v-for="(chapter, index) in outline.chapters"
        :key="chapter.id"
        class="card chapter-card"
      >
        <div class="chapter-heading">
          <button
            class="chapter-title"
            :aria-expanded="expanded.has(chapter.id)"
            :aria-controls="`chapter-${chapter.id}`"
            @click="toggle(chapter.id)"
          >
            <ChevronDown
              v-if="expanded.has(chapter.id)"
              :size="21"
            /><ChevronRight v-else :size="21" /><span
              >Chương {{ index + 1 }} · {{ chapter.title }}</span
            >
          </button>
          <div class="chapter-actions">
            <span class="chapter-status"
              ><CheckCircle2 v-if="chapter.completed" :size="17" /><Lock
                v-else-if="chapter.locked"
                :size="17"
              />{{
                chapter.completed
                  ? "Đã hoàn thành"
                  : chapter.locked
                    ? "Chưa mở khóa"
                    : `${chapterCompleted(chapter)}/${chapter.lessons.length} bài`
              }}</span
            ><button
              class="btn"
              :class="chapter.completed ? 'btn-secondary' : 'btn-primary'"
              :disabled="chapter.locked || !chapter.lessons.length"
              @click="start(chapter)"
            >
              {{
                chapter.locked
                  ? "Hoàn thành chương trước"
                  : chapter.completed
                    ? "Học lại chương"
                    : `Học chương ${index + 1}`
              }}
            </button>
          </div>
        </div>
        <div
          v-if="expanded.has(chapter.id)"
          :id="`chapter-${chapter.id}`"
          class="chapter-lessons"
        >
          <button
            v-for="(lesson, lessonIndex) in chapter.lessons"
            :key="lesson.id"
            class="outline-lesson"
            :disabled="chapter.locked || isLocked(chapter, lessonIndex)"
            @click="learn(lesson.id)"
          >
            <CheckCircle2 v-if="completed(lesson.id)" :size="18" /><Lock
              v-else-if="chapter.locked || isLocked(chapter, lessonIndex)"
              :size="18"
            /><PlayCircle v-else :size="18" /><span
              >{{ lessonIndex + 1 }}. {{ lesson.title }}</span
            >
            <small
              v-if="isAdminView && lesson.status === 'DRAFT'"
              class="draft-lesson-tag"
            >
              Bản nháp
            </small>
            <small
              v-if="lesson.hasQuiz"
              :class="{ required: lesson.quizRequired }"
              >{{
                lesson.quizRequired ? "Kiểm tra bắt buộc" : "Kiểm tra tùy chọn"
              }}
              · {{ passed(lesson) ? "Đã đạt" : "Chưa đạt" }}</small
            >
            <span
              v-if="isAdminView"
              role="button"
              tabindex="0"
              class="btn-quick-edit-lesson"
              title="Chỉnh sửa bài học này"
              @click.stop="router.push(`/admin/lessons/${encodeURIComponent(lesson.id)}/edit`)"
              @keydown.enter.stop="router.push(`/admin/lessons/${encodeURIComponent(lesson.id)}/edit`)"
            >
              <Edit3 :size="14" />
              Sửa bài
            </span>
          </button>
          <p v-if="!chapter.lessons.length" class="empty">
            Chương chưa có bài học.
          </p>
        </div>
      </section>
    </template>
    <p v-else-if="!error">Đang tải khóa học...</p>
  </main>
</template>
<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import {
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Edit3,
  FolderTree,
  Loader2,
  Lock,
  PlayCircle,
  RotateCcw,
} from "lucide-vue-next";
import { useCourseStore } from "../stores/courseStore";
import { chapterLearningState, outlineContext } from "../utils/learningStructure";
import { useUserStore } from "../stores/userStore";
import { trainingApi } from "../api/trainingApi";
import { showToast } from "../utils/toast";
import type { CourseOutline, Chapter, Lesson } from "../types";
import CourseThumbnail from "../components/CourseThumbnail.vue";
import { resumeRoute } from "../utils/learning";
import CourseRatingForm from '../components/CourseRatingForm.vue';
const route = useRoute(),
  router = useRouter(),
  user = useUserStore(),
  courses = useCourseStore(),
  outline = ref<CourseOutline | null>(null),
  error = ref(""),
  publishing = ref(false),
  expanded = ref(new Set<string>());
const isAdminView = computed(
  () => user.systemAdmin || user.can("courses") || !!outline.value?.course.canEdit,
);
const totalLessons = computed(
  () => outline.value?.chapters.reduce((n, c) => n + c.lessons.length, 0) || 0,
);
const completedSet = computed(() => new Set(user.completedLessons));
const chapterStates = computed(() => chapterLearningState(outline.value?.chapters || [], completedSet.value));
const completedCount = computed(() =>
  [...chapterStates.value.values()].reduce((sum, chapter) => sum + chapter.completedCount, 0),
);
const completed = (id: string) => completedSet.value.has(id);
const chapterCompleted = (chapter: Chapter) => chapterStates.value.get(chapter.id)?.completedCount || 0;
const passed = (l: Lesson) =>
  (user.lessonQuizTotals[l.id] || 0) > 0 &&
  Math.round(
    ((user.lessonQuizScores[l.id] || 0) / (user.lessonQuizTotals[l.id] || 1)) *
      100,
  ) >= (l.quizPassingScore || 80);
const isLocked = (c: Chapter, index: number) =>
  !outline.value?.course.canEdit &&
  !user.systemAdmin &&
  index > (chapterStates.value.get(c.id)?.firstIncomplete ?? c.lessons.length);
function toggle(id: string) {
  const set = new Set(expanded.value);
  set.has(id) ? set.delete(id) : set.add(id);
  expanded.value = set;
}
function learn(id: string) {
  router.push(`/lesson/${id}`);
}
function continueCourse() {
  if (outline.value?.resume) router.push(resumeRoute(outline.value.resume));
  else if (outline.value?.resumeLessonId) learn(outline.value.resumeLessonId);
}
function start(c: Chapter) {
  const l = c.lessons.find((l) => !completed(l.id)) || c.lessons[0];
  if (l) learn(l.id);
}
function backToCurriculumTree() {
  try {
    const key = "cbs_curriculum_tree_state_v1";
    const raw = sessionStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : {};
    const ids = new Set<string>(Array.isArray(parsed.expandedIds) ? parsed.expandedIds : []);
    const crs = outline.value?.course as any;
    if (crs?.id) ids.add(crs.id);
    if (crs?.programId) ids.add(crs.programId);
    if (crs?.domainId) ids.add(crs.domainId);
    sessionStorage.setItem(
      key,
      JSON.stringify({
        ...parsed,
        expandedIds: Array.from(ids),
        selectedId: parsed.selectedId || crs?.id || null,
      }),
    );
  } catch {
    // ignore storage errors
  }
  router.push("/admin/courses");
}
async function changeCourseStatus(status: "PUBLISHED" | "DRAFT") {
  if (!outline.value || publishing.value) return;
  publishing.value = true;
  error.value = "";
  try {
    const courseId = outline.value.course.id;
    await trainingApi.setStatus(courseId, status);
    outline.value.course.status = status;
    for (const ch of outline.value.chapters) {
      ch.status = status;
      for (const l of ch.lessons) {
        l.status = status;
      }
    }
    await courses.fetchCourses(true);
    showToast(
      status === "PUBLISHED"
        ? "Đã xuất bản khóa học và toàn bộ bài học thành công!"
        : "Đã chuyển khóa học về trạng thái bản nháp.",
      "success",
    );
  } catch (e) {
    const msg =
      (e as { response?: { data?: { message?: string } } }).response?.data
        ?.message || "Không thể thay đổi trạng thái xuất bản khóa học.";
    error.value = msg;
    showToast(msg, "error");
  } finally {
    publishing.value = false;
  }
}
let loadId = 0;
onBeforeUnmount(() => { loadId++; });
watch(
  () => route.params.courseId,
  async courseId => {
    const current = ++loadId;
    error.value = "";
    outline.value = null;
    expanded.value = new Set();
    try {
      // Re-entering this screen refreshes server-calculated locks/resume without a waterfall.
      const [, loaded] = await Promise.all([
        user.fetchProgress(),
        courses.fetchOutline(String(courseId), outlineContext(user), true),
      ]);
      if (current !== loadId) return;
      outline.value = loaded;
      const first = loaded.chapters.find(c => !c.locked && !c.completed) || loaded.chapters[0];
      if (first) expanded.value = new Set([first.id]);
    } catch (e) {
      if (current !== loadId) return;
      error.value =
        (e as { response?: { data?: { message?: string } } }).response?.data
          ?.message || "Không thể tải khóa học.";
    }
  },
  { immediate: true },
);
</script>
<style scoped>
.admin-review-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 0.9rem 1.25rem;
  border-radius: 12px;
  background: var(--bg-card, var(--surface-ffffff));
  border: 1px solid var(--primary-color, #10b981);
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.08);
}
.admin-review-left {
  display: flex;
  align-items: center;
  gap: 0.9rem;
  flex-wrap: wrap;
}
.btn-back-tree {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  padding: 0.5rem 0.9rem;
  border-radius: 8px;
  border: 1px solid var(--border-color);
  background: var(--bg-subtle, rgba(148, 163, 184, 0.12));
  color: var(--text-main);
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}
.btn-back-tree:hover {
  border-color: var(--primary-color);
  color: var(--primary-ink);
}
.admin-status-info {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  flex-wrap: wrap;
}
.course-status-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.3rem 0.75rem;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 700;
}
.course-status-badge .status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}
.course-status-badge.draft {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
  border: 1px solid rgba(245, 158, 11, 0.35);
}
.course-status-badge.draft .status-dot {
  background: #f59e0b;
}
.course-status-badge.published {
  background: rgba(16, 185, 129, 0.15);
  color: #10b981;
  border: 1px solid rgba(16, 185, 129, 0.35);
}
.course-status-badge.published .status-dot {
  background: #10b981;
}
.admin-review-hint {
  font-size: 0.84rem;
  color: var(--text-muted);
}
.admin-review-actions {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}
.btn-publish-course {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.58rem 1.15rem;
  border-radius: 9px;
  border: none;
  background: linear-gradient(135deg, #059669, #10b981);
  color: #ffffff;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.25);
  transition: all 0.15s ease;
}
.btn-publish-course:hover:not(:disabled) {
  filter: brightness(1.08);
  transform: translateY(-1px);
}
.btn-unpublish-course {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 1rem;
  border-radius: 9px;
  border: 1px solid rgba(245, 158, 11, 0.45);
  background: rgba(245, 158, 11, 0.12);
  color: #f59e0b;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.15s ease;
}
.btn-unpublish-course:hover:not(:disabled) {
  background: rgba(245, 158, 11, 0.2);
}
.spin-icon {
  animation: spin 0.9s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.outline-thumbnail {
  width: 100%;
  max-height: 220px;
  aspect-ratio: 4/1;
  border-radius: 12px;
  margin-bottom: 1rem;
}
.course-outline {
  max-width: 1200px;
  margin: auto;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.back-link {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  color: var(--primary-ink);
}
header,
.chapter-heading,
.chapter-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}
header p,
.description {
  color: var(--text-muted);
  font-size: 0.9rem;
}
h1 {
  font-size: 2rem;
  margin: 0.6rem 0;
}
.chapter-card {
  padding: 1.5rem;
}
.chapter-title {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  border: 0;
  background: none;
  text-align: left;
  font-size: 1.15rem;
  font-weight: 700;
  color: var(--text-main);
  cursor: pointer;
}
.chapter-actions {
  flex-shrink: 0;
  font-size: 0.8rem;
}
.chapter-status,
.complete-tag {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  color: var(--primary-ink);
}
.chapter-lessons {
  margin-top: 1.25rem;
  border-top: 1px solid var(--border-color);
  padding-top: 0.5rem;
}
.outline-lesson {
  width: 100%;
  padding: 1rem;
  border: 0;
  border-bottom: 1px solid var(--border-color);
  display: flex;
  align-items: center;
  gap: 0.75rem;
  background: var(--surface-ffffff);
  text-align: left;
  color: var(--text-main);
  cursor: pointer;
}
.outline-lesson > span:first-of-type {
  flex: 1;
}
.outline-lesson small {
  padding: 0.4rem 0.6rem;
  background: var(--primary-light);
  border-radius: 6px;
  color: var(--primary-ink);
}
.outline-lesson small.required {
  background: var(--surface-fff5de);
  color: var(--ink-805b00);
}
.outline-lesson small.draft-lesson-tag {
  background: rgba(245, 158, 11, 0.15);
  color: #f59e0b;
  font-weight: 600;
}
.btn-quick-edit-lesson {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.35rem 0.65rem;
  border-radius: 6px;
  border: 1px solid var(--border-color);
  background: var(--bg-subtle, rgba(148, 163, 184, 0.1));
  color: var(--text-main);
  font-size: 0.78rem;
  font-weight: 600;
  flex: 0 0 auto !important;
  transition: all 0.15s ease;
}
.btn-quick-edit-lesson:hover {
  border-color: var(--primary-color);
  color: var(--primary-ink);
  background: var(--primary-light);
}
.outline-lesson:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.course-complete {
  padding: 1.5rem;
  background: var(--primary-light);
  color: var(--primary-ink);
}
.course-complete p {
  margin-top: 0.75rem;
}
.empty {
  padding: 1rem;
  color: var(--text-muted);
}
.error {
  color: var(--ink-b91c1c);
}
@media (max-width: 900px) {
  header,
  .chapter-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .chapter-actions {
    width: 100%;
    justify-content: space-between;
  }
  .outline-lesson {
    flex-wrap: wrap;
  }
  .outline-lesson small {
    margin-left: 2rem;
  }
}
</style>
