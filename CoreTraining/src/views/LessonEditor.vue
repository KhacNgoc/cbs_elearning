<template>
  <main class="lesson-editor-page">
    <template v-if="lesson && chapter">
      <header>
        <div class="heading-with-back">
          <AdminBackButton
            :to="chapterUrl"
            label="Quay lại chương trong khóa học"
          />
          <div class="heading-copy">
            <nav class="breadcrumb">
              <RouterLink to="/admin/courses">{{
                course?.name
              }}</RouterLink>
              / <RouterLink :to="chapterUrl">{{ chapter.title }}</RouterLink> /
              <span>{{ lesson.title }}</span>
            </nav>
            <div class="title-header-container">
              <input
                v-if="isEditingTitle"
                ref="titleInputRef"
                v-model="quickTitle"
                type="text"
                class="header-title-input"
                placeholder="Nhập tiêu đề bài học..."
                @blur="saveQuickTitle"
                @keydown.enter.prevent="saveQuickTitle"
                @keydown.esc="cancelQuickTitle"
              />
              <div
                v-else
                class="header-title-interactive"
                title="Bấm để đổi tên bài học"
                @click="startEditTitle"
              >
                <h1>{{ lesson.title }}</h1>
                <button
                  v-if="canEdit"
                  type="button"
                  class="btn-edit-title"
                  aria-label="Sửa tên bài học"
                >
                  <Pencil :size="16" />
                </button>
              </div>
            </div>
          </div>
        </div>
      </header>
      <p v-if="contentLocked" class="readonly-notice" role="status">
        <Lock :size="16" aria-hidden="true" />
        <span>
          {{ courseLockReason(course) }} nên bài học chỉ xem được: không lưu,
          xóa hay sửa nội dung, bài kiểm tra và flashcard.
          <RouterLink :to="`/admin/courses/${chapter.courseId}`"
            >Mở chi tiết khóa học và bấm “Chuyển về nháp”</RouterLink
          >
          để chỉnh sửa, rồi xuất bản lại khi xong.
        </span>
      </p>
      <div class="editor-tabs" role="tablist">
        <button
          id="content-tab"
          role="tab"
          :aria-selected="tab === 'content'"
          aria-controls="content-panel"
          :class="{ active: tab === 'content' }"
          @click="tab = 'content'"
        >
          Nội dung<span class="tab-label-long"> bài học</span><span v-if="dirty" class="unsaved-dot" aria-label="Có thay đổi chưa lưu" title="Có thay đổi chưa lưu"></span></button
        ><button
          id="flashcard-tab"
          role="tab"
          :aria-selected="tab === 'flashcards'"
          aria-controls="flashcard-panel"
          :class="{ active: tab === 'flashcards' }"
          @click="tab = 'flashcards'"
        >
          Flashcard
        </button
        ><button
          v-if="user.can('exams')"
          id="quiz-tab"
          role="tab"
          :aria-selected="tab === 'quiz'"
          aria-controls="quiz-panel"
          :class="{ active: tab === 'quiz' }"
          @click="tab = 'quiz'"
        >
          <span class="tab-label-long">Bài kiểm tra</span><span class="tab-label-short">Kiểm tra</span>
          <span v-if="lesson.hasQuiz" class="tab-badge">{{
            lesson.quizRequired ? "Bắt buộc" : "Tùy chọn"
          }}</span>
        </button>
      </div>
      <div
        v-if="dirty && canEdit && tab !== 'content'"
        class="unsaved-content-bar"
        role="status"
      >
        <span>Nội dung bài học có thay đổi chưa lưu.</span>
        <button
          type="button"
          class="btn btn-primary"
          :disabled="contentEditor?.saving"
          @click="contentEditor?.save()"
        >
          {{ contentEditor?.saving ? "Đang lưu..." : "Lưu nội dung" }}
        </button>
      </div>
      <fieldset :disabled="false" class="editor-fieldset">
        <section
          id="content-panel"
          v-show="tab === 'content'"
          role="tabpanel"
          aria-labelledby="content-tab"
        >
          <LessonContentEditor
            ref="contentEditor"
            :lesson="lesson"
            :course="course"
            :read-only="!canEdit"
            :can-delete="canDelete"
            @delete="deleteOpen = true"
            @saved="savedContent"
            @dirty="dirty = $event"
            @title-change="onTitleChange"
            @cancel="router.push(chapterUrl)"
          />
        </section>
        <section
          v-if="user.can('exams')"
          id="quiz-panel"
          v-show="tab === 'quiz'"
          role="tabpanel"
          aria-labelledby="quiz-tab"
        >
          <div v-if="!lesson.hasQuiz && !preparingQuiz" class="card empty-quiz">
            <h2>Bài học chưa có bài kiểm tra</h2>
            <p>
              Bài kiểm tra chỉ xuất hiện với học viên sau khi lưu ít nhất một
              câu hỏi.
            </p>
            <button
              v-if="
                !contentLocked &&
                !!course?.adminActions?.exams?.includes('create')
              "
              class="btn btn-primary"
              @click="createQuiz"
            >
              Soạn bài kiểm tra
            </button>
          </div>
          <AdminQuizDetail
            v-else
            :key="lesson.id"
            embedded
            :lesson="lesson"
            :read-only="contentLocked"
            @saved="refreshLesson"
          />
        </section>
        <section
          id="flashcard-panel"
          v-if="tab === 'flashcards'"
          role="tabpanel"
          aria-labelledby="flashcard-tab"
        >
          <LessonFlashcardManager
            :course-id="chapter.courseId"
            :lesson-id="lesson.id"
            :actions="course?.allowedActions || []"
            :read-only="!canEdit"
          />
        </section>
      </fieldset>
      <ConfirmModal
        :open="deleteOpen"
        title="Xác nhận xóa bài học"
        :message="`Xóa bài học “${lesson.title}”? Xóa bài học sẽ đồng thời xóa toàn bộ học liệu và bài kiểm tra bên trong.`"
        confirm-label="Xóa bài học"
        :loading="deleting"
        @cancel="deleteOpen = false"
        @confirm="deleteLesson"
      />
    </template>
    <SkeletonLoader v-else-if="loading" :rows="8" label="Đang tải bài học" />
    <div v-else class="empty-quiz">
      <button class="btn btn-secondary" @click="load">Tải lại bài học</button>
    </div>
  </main>
</template>
<script setup lang="ts">
import SkeletonLoader from "../components/SkeletonLoader.vue";
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from "vue";
import { useRoute, useRouter, onBeforeRouteLeave } from "vue-router";
import { useUserStore } from "../stores/userStore";
import { trainingApi } from "../api/trainingApi";
import { adminApi } from "../api/adminApi";
import { showToast } from "../utils/toast";
import type { Lesson, Chapter, Course } from "../types";
import LessonContentEditor from "./LessonContentEditor.vue";
import AdminQuizDetail from "./AdminQuizDetail.vue";
import AdminBackButton from "../components/AdminBackButton.vue";
import ConfirmModal from "../components/ConfirmModal.vue";
import LessonFlashcardManager from "../components/flashcards/LessonFlashcardManager.vue";
import { Lock, Pencil } from "lucide-vue-next";
import { isCourseContentLocked, courseLockReason } from "../utils/courseEditLock";

const route = useRoute(),
  router = useRouter(),
  user = useUserStore();
// Published and archived courses are read-only here; the author moves the course back to draft first.
const contentLocked = computed(() => isCourseContentLocked(course.value));
const canEdit = computed(
  () =>
    !contentLocked.value &&
    course.value?.status !== "DELETED" &&
    !!course.value?.allowedActions?.includes("update"),
);
const lesson = ref<Lesson | null>(null),
  chapter = ref<Chapter | null>(null),
  course = ref<Course | null>(null),
  loading = ref(false),
  dirty = ref(false),
  preparingQuiz = ref(false),
  tab = ref("content");
const contentEditor = ref<InstanceType<typeof LessonContentEditor> | null>(null);
const canDelete = computed(
  () => canEdit.value && !!course.value?.allowedActions?.includes("delete"),
);
const deleteOpen = ref(false),
  deleting = ref(false);
async function deleteLesson() {
  if (!lesson.value) return;
  deleting.value = true;
  try {
    await trainingApi.deleteLesson(lesson.value.id);
    const title = lesson.value.title;
    deleteOpen.value = false;
    dirty.value = false; // the lesson is gone; there is nothing left to warn about
    ensureTreeBranchSavedInSession();
    showToast(`Đã xóa bài học "${title}".`, "success");
    await router.push(chapterUrl.value);
  } catch (e) {
    showToast(
      (e as { response?: { data?: { message?: string } } }).response?.data
        ?.message || "Không xóa được bài học.",
      "error",
    );
  } finally {
    deleting.value = false;
  }
}

const isEditingTitle = ref(false);
const quickTitle = ref("");
const titleInputRef = ref<HTMLInputElement | null>(null);

function startEditTitle() {
  if (!canEdit.value) return;
  quickTitle.value = lesson.value?.title || "";
  isEditingTitle.value = true;
  nextTick(() => {
    titleInputRef.value?.focus();
    titleInputRef.value?.select();
  });
}

function cancelQuickTitle() {
  isEditingTitle.value = false;
  quickTitle.value = lesson.value?.title || "";
}

async function saveQuickTitle() {
  if (!isEditingTitle.value) return;
  const newTitle = quickTitle.value.trim();
  if (!newTitle) {
    cancelQuickTitle();
    return;
  }
  isEditingTitle.value = false;
  if (lesson.value && lesson.value.title !== newTitle) {
    lesson.value.title = newTitle;
    try {
      const updated = await adminApi.updateLesson(lesson.value.id, {
        ...lesson.value,
        title: newTitle,
        hint: lesson.value.hint || newTitle,
        courseId: chapter.value?.courseId || "",
      });
      lesson.value = updated;
      showToast("Cập nhật tên bài học thành công!", "success");
    } catch (e: any) {
      showToast(e.response?.data?.message || "Lỗi cập nhật tên bài học", "error");
    }
  }
}

function onTitleChange(newTitle: string) {
  if (lesson.value) {
    lesson.value.title = newTitle;
  }
}
const chapterUrl = computed(() => "/admin/courses");

function ensureTreeBranchSavedInSession() {
  try {
    const key = "cbs_curriculum_tree_state_v1";
    const raw = sessionStorage.getItem(key);
    const parsed = raw ? JSON.parse(raw) : {};
    const ids = new Set<string>(Array.isArray(parsed.expandedIds) ? parsed.expandedIds : []);
    if (chapter.value?.id) ids.add(chapter.value.id);
    if (chapter.value?.courseId) ids.add(chapter.value.courseId);
    if (course.value?.programId) ids.add(course.value.programId);
    if ((course.value as any)?.domainId) ids.add((course.value as any).domainId);
    sessionStorage.setItem(
      key,
      JSON.stringify({
        ...parsed,
        expandedIds: Array.from(ids),
        selectedId: chapter.value?.id || parsed.selectedId || null,
      }),
    );
  } catch {
    // ignore storage errors
  }
}

async function refreshLesson() {
  lesson.value = await trainingApi.lesson(String(route.params.lessonId));
}
// A course the account cannot manage still opens read-only from its outline, as before.
function missingCourse(failure: unknown): null {
  const status = (failure as { response?: { status?: number } }).response?.status;
  if (status === 403 || status === 404) return null;
  throw failure;
}
async function load() {
  loading.value = true;
  try {
    lesson.value = null;
    chapter.value = null;
    await refreshLesson();
    const courseId = lesson.value!.courseId;
    const chapterId = lesson.value!.chapterId!;
    // Only this course and its chapter titles: no course list and no other lesson's content.
    const [courseData, chapterOutlines] = await Promise.all([
      trainingApi.course(courseId).catch(missingCourse),
      trainingApi.chapterOutlines(courseId),
    ]);
    // The outline skips deleted chapters; the single-chapter request still covers that case.
    chapter.value =
      chapterOutlines.find((item) => item.id === chapterId) ||
      (await trainingApi.chapter(chapterId));
    course.value = courseData;
    if (!course.value) {
      try {
        const outlineData = await trainingApi.outline(courseId);
        course.value = outlineData.course;
      } catch {
        // ignore
      }
    }
    ensureTreeBranchSavedInSession();
  } catch (e) {
    showToast(
      (e as { response?: { data?: { message?: string } } }).response?.data
        ?.message || "Không tải được bài học.",
      "error",
    );
  } finally {
    loading.value = false;
  }
}
watch(
  () => route.params.lessonId,
  () => {
    dirty.value = false;
    preparingQuiz.value = false;
    void load();
  },
  { immediate: true },
);
// Saving keeps the author in the editor so they can go on to the quiz or flashcards.
async function savedContent() {
  dirty.value = false;
  ensureTreeBranchSavedInSession();
  await refreshLesson();
}
function createQuiz() {
  preparingQuiz.value = true;
}
onBeforeRouteLeave(
  () =>
    !dirty.value ||
    window.confirm(
      "Nội dung chưa lưu. Bạn có muốn rời trang và bỏ các thay đổi?",
    ),
);
function beforeUnload(e: BeforeUnloadEvent) {
  if (dirty.value) {
    e.preventDefault();
    e.returnValue = "";
  }
}
onMounted(() => window.addEventListener("beforeunload", beforeUnload));
onBeforeUnmount(() => window.removeEventListener("beforeunload", beforeUnload));
</script>
<style scoped>
.editor-fieldset {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
}
.readonly-notice {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  line-height: 1.5;
  padding: 1rem;
  background: var(--surface-fff5de);
  color: var(--ink-805b00);
  border-radius: 8px;
  margin-bottom: 1rem;
}
.readonly-notice a {
  text-decoration: underline;
}
.lesson-editor-page {
  padding: 2rem;
  min-width: 0;
}
.lesson-editor-page header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}
.breadcrumb {
  font-size: 0.85rem;
  color: var(--text-muted);
}
.breadcrumb a {
  color: var(--primary-ink);
}
h1 {
  font-size: 1.5rem;
  margin-top: 0.75rem;
}
.editor-tabs {
  display: flex;
  gap: 2rem;
  border-bottom: 1px solid var(--border-color);
  margin-bottom: 1.5rem;
}
.editor-tabs button {
  flex-shrink: 0;
  white-space: nowrap;
  border: 0;
  border-bottom: 3px solid transparent;
  background: none;
  padding: 1rem 0;
  color: var(--text-muted);
  font-size: 1rem;
  cursor: pointer;
}
.editor-tabs button.active {
  color: var(--primary-ink);
  border-color: var(--primary-color);
  font-weight: 700;
}
.editor-tabs .tab-label-short {
  display: none;
}
.editor-tabs .tab-badge {
  margin-left: 0.5rem;
  padding: 0.1rem 0.45rem;
  border-radius: 999px;
  background: var(--primary-light);
  color: var(--primary-ink);
  font-size: 0.7rem;
  font-weight: 600;
  line-height: 1.4;
  vertical-align: middle;
}
.editor-tabs .unsaved-dot {
  display: inline-block;
  width: 0.5rem;
  height: 0.5rem;
  margin-left: 0.4rem;
  border-radius: 50%;
  background: var(--warning-ink);
  vertical-align: middle;
}
.unsaved-content-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1rem;
  padding: 0.75rem 1rem;
  border: 1px solid var(--warning-border);
  border-radius: 10px;
  background: var(--warning-surface);
  color: var(--warning-ink);
  font-weight: 600;
}
.empty-quiz {
  padding: 2rem;
  text-align: center;
}
.empty-quiz p {
  margin: 1rem;
  color: var(--text-muted);
}
.error {
  color: var(--ink-b91c1c);
}
@media (max-width: 768px) {
  .lesson-editor-page {
    padding: 1rem;
  }
  .lesson-editor-page header {
    flex-direction: column;
    align-items: flex-start;
  }
  /* Short labels keep all three tabs on one line; scrolling is the fallback on very narrow screens. */
  .editor-tabs {
    gap: 1.5rem;
    overflow-x: auto;
    scrollbar-width: none;
  }
  .editor-tabs::-webkit-scrollbar {
    display: none;
  }
  .editor-tabs button {
    padding: 0.85rem 0;
    font-size: 0.95rem;
  }
  .editor-tabs .tab-label-long {
    display: none;
  }
  .editor-tabs .tab-label-short {
    display: inline;
  }
}
.title-header-container {
  margin-top: 0.35rem;
}
.header-title-interactive {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  cursor: pointer;
  border-radius: 8px;
  padding: 0.2rem 0.5rem;
  margin-left: -0.5rem;
  transition: background 0.15s ease;
}
.header-title-interactive:hover {
  background: var(--primary-light);
}
.header-title-interactive h1 {
  margin: 0;
  font-size: 1.5rem;
  font-weight: 700;
  color: var(--text-main);
}
.btn-edit-title {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: var(--text-muted, #94a3b8);
  padding: 0.25rem;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.15s ease;
}
.header-title-interactive:hover .btn-edit-title {
  color: #10b981;
  background: rgba(16, 185, 129, 0.15);
}
.header-title-input {
  font-size: 1.35rem;
  font-weight: 700;
  background: var(--bg-card);
  color: var(--text-main);
  border: 1.5px solid #10b981;
  border-radius: 8px;
  padding: 0.35rem 0.75rem;
  min-width: 340px;
  max-width: 100%;
  outline: none;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
}
.heading-with-back {
  display: flex;
  align-items: flex-start;
  gap: 1rem;
  min-width: 0;
  width: 100%;
}
.heading-copy {
  min-width: 0;
}
.heading-copy h1,
.heading-copy .breadcrumb {
  overflow-wrap: anywhere;
}
@media (max-width: 560px) {
  .heading-with-back {
    gap: 0.75rem;
  }
}
</style>
