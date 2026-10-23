<template>
  <div
    v-if="lesson && chapter && !lessonLoading"
    ref="learningPage"
    class="learning-page"
    :class="{ 'sidebar-visible': sidebarOpen }"
  >
    <LessonLearningHeader
      :course-name="`${fullCourse?.name} / ${chapter?.title}`"
      :back-to="`/courses/${fullCourse?.id}`"
      :lesson-title="lesson.title"
      :sidebar-open="sidebarOpen"
      :active-content="activeContentType"
      :has-text="Boolean(lesson.textBody?.trim())"
      :has-video="Boolean(lesson.videoUrl?.trim())"
      @select-content="showPrimaryContent"
      :completed-count="completedCount"
      :total-lessons="course?.lessons.length || 0"
      :completed="isCompleted"
      :can-complete="userStore.canLearn"
      @complete="markAsCompleted"
      @open-content="sidebarOpen = true"
    />
    <div class="learning-stage" :class="{ 'sidebar-hidden': !sidebarOpen }">
      <LessonMediaStage
        ref="mediaStage"
        :lesson="lesson"
        :selected-document="selectedDocument"
        :primary-content="primaryContent"
        :current-index="currentLessonIndex"
        :total-lessons="course?.lessons.length || 0"
        :initial-position-seconds="
          currentLessonProgress?.lastPositionSeconds || 0
        "
        :initial-watched-seconds="currentLessonProgress?.watchedSeconds || 0"
        :initial-document-page="
          currentLessonProgress?.documentId === selectedDocument?.id
            ? currentLessonProgress?.documentPage || 1
            : 1
        "
        :initial-scroll-offset="
          currentLessonProgress?.documentId === selectedDocument?.id
            ? currentLessonProgress?.scrollOffset || 0
            : 0
        "
        @document-position="handleDocumentPosition"
        @adjacent="goToAdjacentLesson"
        @time-update="currentVideoTime = $event"
        @progress-update="handleVideoProgress"
        @video-ended="handleVideoEnded"
        @pause="flushProgress"
        @add-note="openNoteComposer"
      />
      <LessonCourseSidebar
        :course="course"
        :chapters="outline?.chapters || []"
        :active-lesson-id="lesson.id"
        :completed-lessons="userStore.completedLessons"
        :completed-count="completedCount"
        :progress="courseProgress"
        :open="sidebarOpen"
        :selected-document-id="selectedDocument?.id"
        :lesson-quiz-scores="userStore.lessonQuizScores"
        :lesson-quiz-totals="userStore.lessonQuizTotals"
        :course-quiz-scores="userStore.quizScores"
        :course-quiz-totals="userStore.quizTotals"
        @close="sidebarOpen = false"
        @select="openLessonContent"
        @toggle-completion="toggleLessonCompletion"
        @select-document="showDocument"
        @download-document="downloadDocument"
        @quiz="startQuiz"
        @course-quiz="startCourseQuiz"
      />
      <button
        v-if="!sidebarOpen"
        class="sidebar-reopen"
        @click="sidebarOpen = true"
        aria-label="Mở nội dung khóa học"
      >
        <ChevronLeft :size="22" />
      </button>
      <div
        v-if="sidebarOpen"
        class="mobile-overlay cursor-pointer"
        @click="sidebarOpen = false"
      ></div>
    </div>

    <section class="lesson-information">
      <LessonOverview
        :lesson="lesson"
        :course-name="fullCourse?.name"
        :completed="isCompleted"
      >
        <template #actions>
          <LessonReportForm
            v-if="userStore.canLearn"
            :key="lesson.id"
            :lesson-id="lesson.id"
            :lesson-title="lesson.title"
          />
        </template>
        <LessonVideoNotes
          ref="videoNotesPanel"
          :notes="videoNotes"
          :current-time="currentVideoTime"
          :loading="notesLoading"
          :saving="noteSaving"
          @create="createVideoNote"
          @update="updateVideoNote"
          @delete="deleteVideoNote"
          @seek="seekToNote"
        />
        <LessonDiscussion
          :comments="lesson.comments"
          :has-more="(lesson.commentCount ?? lesson.comments.length) > lesson.comments.length"
          :loading-more="commentsLoadingMore"
          @load-more="loadMoreComments"
          :current-username="userStore.username"
          :is-admin="Boolean(fullCourse?.canEdit)"
          v-model:comment="commentText"
          @submit="submitFeedback"
          @reply="submitReply"
          @delete="deleteComment"
        />
      </LessonOverview>
    </section>

    <footer v-if="chapterComplete" class="chapter-completion-footer">
      <span
        ><strong>{{
          nextChapter ? "Đã hoàn thành chương" : "Đã hoàn thành khóa học"
        }}</strong>
        · Tất cả bài học và kiểm tra bắt buộc đã hoàn thành</span
      ><button class="btn btn-primary" @click="advanceChapter">
        {{ nextChapter ? "Sang chương tiếp theo" : "Xem kết quả khóa học" }}
        <ChevronRight :size="17" />
      </button>
    </footer>
    <Transition name="quiz-gate">
      <div
        v-if="quizGateOpen"
        class="quiz-gate-backdrop cursor-pointer"
        role="presentation"
        @click.self="closeQuizPrompt"
      >
        <section
          class="quiz-gate-dialog"
          role="dialog"
          aria-modal="true"
          aria-labelledby="quiz-gate-title"
        >
          <button
            class="quiz-gate-close"
            aria-label="Đóng thông báo"
            @click="closeQuizPrompt"
          >
            <X :size="20" />
          </button>
          <div class="quiz-gate-icon"><AlertTriangle :size="28" /></div>
          <span class="quiz-gate-eyebrow">
            {{
              quizPromptKind === "course"
                ? "Bài kiểm tra tổng kết"
                : "Bài học có bài kiểm tra"
            }}
          </span>
          <h2 id="quiz-gate-title">
            {{
              quizPromptKind === "course"
                ? "Bạn cần làm bài kiểm tra để hoàn thành khóa học"
                : "Bạn có muốn làm bài kiểm tra ngay không?"
            }}
          </h2>
          <p v-if="quizPromptKind === 'course'">
            Đây là bài học cuối cùng chưa hoàn thành của khóa
            <strong>{{ course?.name }}</strong
            >. Bạn cần đạt tối thiểu {{ course?.quizPassingScore ?? 80 }}% bài
            kiểm tra tổng kết để hoàn thành khóa học.
          </p>
          <p v-else>
            Bài <strong>{{ quizPromptLesson?.title }}</strong> có bài kiểm tra.
            Kết quả bài kiểm tra sẽ được dùng để ghi nhận hoàn thành bài học.
          </p>
          <div class="quiz-gate-actions">
            <button class="quiz-gate-secondary" @click="closeQuizPrompt">
              Để sau
            </button>
            <button class="quiz-gate-primary" @click="continueToQuiz">
              {{
                quizPromptKind === "course"
                  ? "Làm bài kiểm tra tổng"
                  : "Làm bài kiểm tra"
              }}
              <ChevronRight :size="17" />
            </button>
          </div>
        </section>
      </div>
    </Transition>
  </div>
  <div v-else-if="lessonError" class="loading-state" role="alert">
    {{ lessonError }}
    <button class="btn btn-primary" @click="loadLessonData(props.id)">Thử lại</button>
  </div>
  <div v-else class="loading-state"><span></span>Đang tải bài học...</div>
</template>

<script setup lang="ts">
import { scrollWithin } from "../utils/scrollWithin";
import {
  ref,
  computed,
  nextTick,
  onMounted,
  onBeforeUnmount,
  watch,
} from "vue";
import { useRoute, useRouter } from "vue-router";
import { AlertTriangle, ChevronLeft, ChevronRight, X } from "lucide-vue-next";
import { useCourseStore } from "../stores/courseStore";
import { useUserStore } from "../stores/userStore";
import { axiosClient } from "../api/axiosClient";
import { outlineContext } from "../utils/learningStructure";
import { courseApi } from "../api/courseApi";
import type {
  CourseOutline,
  LessonDocument,
  LessonProgressRequest,
  VideoNote,
} from "../types";
import LessonLearningHeader from "../components/lesson/LessonLearningHeader.vue";
import LessonCourseSidebar from "../components/lesson/LessonCourseSidebar.vue";
import LessonMediaStage from "../components/lesson/LessonMediaStage.vue";
import LessonOverview from "../components/lesson/LessonOverview.vue";
import LessonReportForm from "../components/lesson/LessonReportForm.vue";
import { initialLessonContent } from "../utils/lessonContent";
import LessonDiscussion from "../components/lesson/LessonDiscussion.vue";
import LessonVideoNotes from "../components/lesson/LessonVideoNotes.vue";

const props = defineProps<{ id: string }>();
const router = useRouter();
const route = useRoute();
const bookmarkReady = ref(false);
const courseStore = useCourseStore();
const userStore = useUserStore();
const mediaStage = ref<InstanceType<typeof LessonMediaStage> | null>(null);
const learningPage = ref<HTMLElement | null>(null);
const videoNotesPanel = ref<InstanceType<typeof LessonVideoNotes> | null>(null);
const selectedDocument = ref<LessonDocument | null>(null);
const primaryContent = ref<"VIDEO" | "TEXT">("TEXT");
const activeContentType = computed(() =>
  selectedDocument.value ? "DOCUMENT" : primaryContent.value,
);
const pendingDocument = ref<{
  lessonId: string;
  document: LessonDocument;
} | null>(null);
const commentText = ref("");
const commentPage = ref(0);
const commentsLoadingMore = ref(false);
const sidebarOpen = ref(window.innerWidth > 900);
const lessonLoading = ref(true);
const lessonError = ref("");
const progressLessonId = ref<string | null>(null);
let lessonLoadId = 0;
let disposed = false;
const currentVideoTime = ref(0);
const videoNotes = ref<VideoNote[]>([]);
const notesLoading = ref(false);
const noteSaving = ref(false);
const quizGateOpen = ref(false);
const quizPromptLessonId = ref<string | null>(null);
const quizPromptKind = ref<"lesson" | "course">("lesson");
const currentProgressSnapshot = ref<LessonProgressRequest>({
  lastPositionSeconds: 0,
  watchedSeconds: 0,
  durationSeconds: 0,
});
let progressSaveTimer: ReturnType<typeof setTimeout> | null = null;

const lesson = computed(() =>
  courseStore.currentLesson?.id === props.id ? courseStore.currentLesson : null,
);
const outline = ref<CourseOutline | null>(null);
const fullCourse = computed(() =>
  outline.value?.course.id === lesson.value?.courseId
    ? outline.value?.course
    : courseStore.currentCourse,
);
const chapter = computed(() =>
  outline.value?.chapters.find((c) => c.id === lesson.value?.chapterId),
);
const course = computed(() =>
  fullCourse.value && outline.value
    ? {
        ...fullCourse.value,
        lessons: outline.value.chapters.flatMap(item => item.lessons),
      }
    : fullCourse.value,
);
const chapterComplete = computed(
  () =>
    !!chapter.value?.lessons.length &&
    chapter.value.lessons.every((l) =>
      userStore.completedLessons.includes(l.id),
    ),
);
const nextChapter = computed(() => {
  const list = outline.value?.chapters || [];
  return list[list.findIndex((c) => c.id === chapter.value?.id) + 1];
});

const quizPromptLesson = computed(
  () =>
    course.value?.lessons.find(
      (item) => item.id === quizPromptLessonId.value,
    ) || lesson.value,
);
const currentLessonProgress = computed(() =>
  lesson.value ? userStore.getLatestLessonProgress(lesson.value.id) : undefined,
);
const isCompleted = computed(() =>
  lesson.value ? userStore.completedLessons.includes(lesson.value.id) : false,
);
const completedCount = computed(
  () =>
    course.value?.lessons.filter((item) =>
      userStore.completedLessons.includes(item.id),
    ).length || 0,
);
const courseProgress = computed(() =>
  course.value?.lessons.length
    ? Math.round((completedCount.value / course.value.lessons.length) * 100)
    : 0,
);
const currentLessonIndex = computed(
  () =>
    course.value?.lessons.findIndex((item) => item.id === lesson.value?.id) ??
    -1,
);

const loadLessonData = async (lessonId: string) => {
  const loadId = ++lessonLoadId;
  commentPage.value = 0;
  commentsLoadingMore.value = false;
  bookmarkReady.value = false;
  lessonLoading.value = true;
  lessonError.value = "";
  currentVideoTime.value = 0;
  videoNotes.value = [];
  // Notes are independent of the content and must not delay the lesson viewer.
  void loadVideoNotes(lessonId, loadId);
  try {
    const [loadedLesson] = await Promise.all([
      courseStore.fetchLessonDetail(lessonId),
      userStore.progressLoaded ? Promise.resolve() : userStore.fetchProgress(),
    ]);
    if (loadId !== lessonLoadId || disposed) return;
    if (!loadedLesson) throw new Error("Không tải được bài học. Vui lòng thử lại.");
    const context = outlineContext(userStore);
    let loadedOutline = await courseStore.fetchOutline(loadedLesson.courseId, context);
    if (loadId !== lessonLoadId || disposed) return;
    // A newly added/moved lesson may not exist in a still-fresh cached structure.
    if (!loadedOutline.chapters.some(chapter =>
      chapter.id === loadedLesson.chapterId && chapter.lessons.some(item => item.id === lessonId),
    )) {
      loadedOutline = await courseStore.fetchOutline(loadedLesson.courseId, context, true);
      if (loadId !== lessonLoadId || disposed) return;
    }
    if (!loadedOutline.course.canEdit && !userStore.isCourseAccessible(loadedLesson.courseId)) {
      alert("Bạn cần đăng ký học khóa học nghiệp vụ này trước!");
      await router.push("/");
      return;
    }
    outline.value = loadedOutline;
    const activeChapter = loadedOutline.chapters.find(c => c.id === loadedLesson.chapterId);
    if (!activeChapter) throw new Error("Không tìm thấy chương của bài học. Vui lòng tải lại.");
    const index = activeChapter.lessons.findIndex(item => item.id === lessonId);
    if (index < 0) throw new Error("Bài học không còn trong cấu trúc khóa. Vui lòng tải lại.");
    activeChapter.lessons[index] = loadedLesson;

    const progress = userStore.getLatestLessonProgress(lessonId);
    currentProgressSnapshot.value = {
      ...progress,
      lastPositionSeconds: progress?.lastPositionSeconds || 0,
      watchedSeconds: progress?.watchedSeconds || 0,
      durationSeconds: progress?.durationSeconds || 0,
    };
    progressLessonId.value = lessonId;
    const requestedDocument = pendingDocument.value?.lessonId === lessonId
      ? pendingDocument.value.document : undefined;
    pendingDocument.value = null;
    selectInitialContent(requestedDocument);
    // Initial content restoration is not a user-initiated content switch.
    await nextTick();
    if (loadId !== lessonLoadId || disposed) return;
    bookmarkReady.value = true;
    lessonLoading.value = false;
    void persistCurrentProgress(true);
  } catch (error) {
    if (loadId !== lessonLoadId || disposed) return;
    lessonError.value = (error as Error).message || "Không thể tải bài học. Vui lòng thử lại.";
    lessonLoading.value = false;
  }
};

const selectInitialContent = (requestedDocument?: LessonDocument) => {
  if (!lesson.value) return;
  const documents = lesson.value.documents || [];
  const saved = userStore.getLatestLessonProgress(lesson.value.id);
  const requestedId =
    typeof route.query.documentId === "string"
      ? Number(route.query.documentId)
      : undefined;
  requestedDocument ||= documents.find((d) => d.id === requestedId);
  const initial = initialLessonContent(lesson.value, saved, requestedDocument);
  primaryContent.value = initial.type === "VIDEO" ? "VIDEO" : "TEXT";
  selectedDocument.value = initial.document;
};

const loadVideoNotes = async (lessonId: string, loadId: number) => {
  notesLoading.value = true;
  try {
    const notes = await courseApi.getVideoNotes(lessonId);
    if (loadId === lessonLoadId && !disposed) videoNotes.value = notes;
  } catch (error) {
    if (loadId === lessonLoadId && !disposed) {
      console.error("Failed to load video notes:", error);
      videoNotes.value = [];
    }
  } finally {
    if (loadId === lessonLoadId && !disposed) notesLoading.value = false;
  }
};

const flushProgress = () => { void persistCurrentProgress(); };
const handleVisibilityChange = () => {
  if (document.visibilityState === "hidden") flushProgress();
};
onMounted(() => {
  document.addEventListener("visibilitychange", handleVisibilityChange);
  window.addEventListener("pagehide", flushProgress);
  void loadLessonData(props.id);
});

onBeforeUnmount(() => {
  void persistCurrentProgress();
  disposed = true;
  lessonLoadId++;
  document.removeEventListener("visibilitychange", handleVisibilityChange);
  window.removeEventListener("pagehide", flushProgress);
});

watch(
  () => props.id,
  newId => {
    // Capture the old lesson's id and snapshot synchronously; network writes run in the background.
    void persistCurrentProgress();
    bookmarkReady.value = false;
    selectedDocument.value = null;
    if (window.innerWidth <= 900) sidebarOpen.value = false;
    void loadLessonData(newId);
  },
);

const scheduleProgressSave = (delay: number) => {
  // Throttle rather than debounce: continuous playback/scrolling still saves regularly.
  if (progressSaveTimer) return;
  progressSaveTimer = setTimeout(() => {
    progressSaveTimer = null;
    void persistCurrentProgress();
  }, delay);
};

const handleVideoProgress = (progress: LessonProgressRequest) => {
  if (!bookmarkReady.value || activeContentType.value !== "VIDEO") return;
  currentProgressSnapshot.value = { ...progress, contentType: "VIDEO" };
  scheduleProgressSave(5000);
};

const persistCurrentProgress = async (touch = false) => {
  if (progressSaveTimer) {
    clearTimeout(progressSaveTimer);
    progressSaveTimer = null;
  }
  if (!progressLessonId.value || !bookmarkReady.value) return null;
  return userStore.saveLessonProgress(progressLessonId.value, {
    ...currentProgressSnapshot.value,
    contentType: activeContentType.value,
    documentId: selectedDocument.value?.id,
    documentPage: selectedDocument.value
      ? currentProgressSnapshot.value.documentPage || 1
      : undefined,
    scrollOffset: selectedDocument.value
      ? currentProgressSnapshot.value.scrollOffset
      : undefined,
  }, touch);
};
const handleDocumentPosition = (position: {
  page: number;
  scrollOffset: number;
}) => {
  if (!selectedDocument.value || !bookmarkReady.value) return;
  currentProgressSnapshot.value = {
    ...currentProgressSnapshot.value,
    contentType: "DOCUMENT",
    documentId: selectedDocument.value.id,
    documentPage: position.page,
    scrollOffset: position.scrollOffset,
  };
  scheduleProgressSave(1000);
};
watch([selectedDocument, primaryContent], async ([document]) => {
  if (!bookmarkReady.value || !lesson.value) return;
  if (document) {
    const saved = userStore.getLatestLessonProgress(lesson.value.id);
    currentProgressSnapshot.value = {
      ...currentProgressSnapshot.value,
      documentId: document.id,
      documentPage:
        saved && saved.documentId === document.id ? saved.documentPage : 1,
      scrollOffset:
        saved && saved.documentId === document.id ? saved.scrollOffset : 0,
    };
  }
  await persistCurrentProgress();
});

const handleVideoEnded = async (progress: LessonProgressRequest) => {
  if (!userStore.canLearn || !lesson.value) return;
  if (progressSaveTimer) {
    clearTimeout(progressSaveTimer);
    progressSaveTimer = null;
  }
  const finishedLessonId = lesson.value.id;
  const hasQuiz = Boolean(lesson.value.hasQuiz && lesson.value.quizRequired);
  const nextLesson = course.value?.lessons[currentLessonIndex.value + 1];
  const wasCompleted = userStore.completedLessons.includes(finishedLessonId);
  currentProgressSnapshot.value = progress;
  await persistCurrentProgress();

  if (wasCompleted || disposed || lesson.value?.id !== finishedLessonId) return;

  if (hasQuiz) {
    quizPromptKind.value = "lesson";
    quizPromptLessonId.value = finishedLessonId;
    quizGateOpen.value = true;
    return;
  }

  if (promptForRequiredCourseQuiz(finishedLessonId)) return;
  const completed = await completeLessonWithFeedback(finishedLessonId);
  if (completed && nextLesson && !disposed && lesson.value?.id === finishedLessonId) {
    await router.push(`/lesson/${nextLesson.id}`);
  }
};

const switchLesson = async (lessonId: string): Promise<boolean> => {
  if (!lesson.value) return false;
  if (lessonId === lesson.value.id) return true;
  const index = course.value?.lessons.findIndex((l) => l.id === lessonId) ?? -1;
  if (index < 0) return false;
  if (
    !fullCourse.value?.canEdit &&
    !userStore.systemAdmin &&
    course.value?.lessons
      .slice(0, index)
      .some((l) => !userStore.completedLessons.includes(l.id))
  ) {
    alert("Hoàn thành các bài trước và kiểm tra bắt buộc để tiếp tục");
    return false;
  }
  await router.push(`/lesson/${lessonId}`);
  return true;
};
const openLessonContent = async (lessonId: string) => {
  if (lessonId === lesson.value?.id && selectedDocument.value) {
    showPrimaryContent(
      primaryContent.value === "TEXT" && !lesson.value.textBody?.trim()
        ? "VIDEO"
        : primaryContent.value,
    );
    return;
  }
  await switchLesson(lessonId);
};
const showDocument = async (lessonId: string, document: LessonDocument) => {
  if (lessonId !== lesson.value?.id) {
    pendingDocument.value = { lessonId, document };
    const switched = await switchLesson(lessonId);
    if (!switched) pendingDocument.value = null;
    return;
  }
  selectedDocument.value = document;
};
const showPrimaryContent = (type: "VIDEO" | "TEXT") => {
  if (
    !lesson.value ||
    (type === "VIDEO"
      ? !lesson.value.videoUrl?.trim()
      : !lesson.value.textBody?.trim())
  )
    return;
  primaryContent.value = type;
  selectedDocument.value = null;
};
const downloadDocument = async (
  document: import("../types").LessonDocument,
) => {
  try {
    const apiBaseUrl = import.meta.env.VITE_API_URL ?? "http://localhost:8080";
    const url =
      /^https?:\/\//i.test(document.fileUrl) ||
      document.fileUrl.startsWith("blob:")
        ? document.fileUrl
        : `${apiBaseUrl}${document.fileUrl.startsWith("/") ? document.fileUrl : `/${document.fileUrl}`}`;
    const response = await axiosClient.get<Blob>(url, { responseType: "blob" });
    const objectUrl = URL.createObjectURL(response.data);
    const link = window.document.createElement("a");
    link.href = objectUrl;
    link.download = document.originalFileName || document.name;
    window.document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(objectUrl);
  } catch (cause) {
    console.error("Failed to download document:", cause);
    alert("Không thể tải tài liệu. Vui lòng thử lại.");
  }
};
const goToAdjacentLesson = (offset: number) => {
  const nextLesson = course.value?.lessons[currentLessonIndex.value + offset];
  if (nextLesson) switchLesson(nextLesson.id);
};
const markAsCompleted = async () => {
  if (!lesson.value) return;
  await toggleLessonCompletion(lesson.value.id);
};
const hasPassedLessonQuiz = (lessonId: string) => {
  const targetLesson = course.value?.lessons.find(
    (item) => item.id === lessonId,
  );
  if (!targetLesson) return false;
  const score = userStore.lessonQuizScores[lessonId];
  const total = userStore.lessonQuizTotals[lessonId];
  return (
    score !== undefined &&
    total > 0 &&
    Math.round((score / total) * 100) >= (targetLesson.quizPassingScore ?? 80)
  );
};
const hasPassedCourseQuiz = () => {
  if (!course.value) return false;
  const score = userStore.quizScores[course.value.id];
  const total = userStore.quizTotals[course.value.id];
  return (
    score !== undefined &&
    total > 0 &&
    Math.round((score / total) * 100) >= (course.value.quizPassingScore ?? 80)
  );
};
const promptForRequiredCourseQuiz = (lessonId: string) => {
  if (!course.value?.hasQuiz || hasPassedCourseQuiz()) return false;
  const wouldCompleteCourse = course.value.lessons.every(
    (item) =>
      item.id === lessonId || userStore.completedLessons.includes(item.id),
  );
  if (!wouldCompleteCourse) return false;
  quizPromptKind.value = "course";
  quizPromptLessonId.value = null;
  quizGateOpen.value = true;
  return true;
};
const completeLessonWithFeedback = async (lessonId: string) => {
  try {
    await userStore.completeLesson(lessonId);
    return userStore.completedLessons.includes(lessonId);
  } catch (error) {
    const apiMessage = (
      error as { response?: { data?: { message?: unknown } } }
    ).response?.data?.message;
    alert(
      typeof apiMessage === "string"
        ? apiMessage
        : "Không thể đánh dấu hoàn thành bài học. Vui lòng thử lại.",
    );
    return false;
  }
};
const toggleLessonCompletion = async (lessonId: string) => {
  const targetLesson = course.value?.lessons.find(
    (item) => item.id === lessonId,
  );
  if (!targetLesson) return;

  if (userStore.completedLessons.includes(lessonId)) return;

  try {
    await persistCurrentProgress();
    await axiosClient.post(
      `/api/users/me/lessons/${lessonId}/content-complete`,
    );
  } catch (error) {
    const message = (error as { response?: { data?: { message?: string } } })
      .response?.data?.message;
    alert(message || "Hãy học xong nội dung trước khi hoàn thành bài.");
    return;
  }
  if (
    targetLesson.hasQuiz &&
    targetLesson.quizRequired &&
    !hasPassedLessonQuiz(lessonId)
  ) {
    quizPromptKind.value = "lesson";
    quizPromptLessonId.value = lessonId;
    quizGateOpen.value = true;
    return;
  }
  if (promptForRequiredCourseQuiz(lessonId)) return;
  await completeLessonWithFeedback(lessonId);
};
const startQuiz = (lessonId = lesson.value?.id) => {
  const targetLesson = course.value?.lessons.find(
    (item) => item.id === lessonId,
  );
  if (course.value && targetLesson?.hasQuiz) {
    router.push({
      path: `/quiz/${course.value.id}`,
      query: { lessonId: targetLesson.id },
    });
  } else if (course.value) {
    router.push(`/quiz/${course.value.id}`);
  }
};
const advanceChapter = () => {
  if (nextChapter.value) {
    const target =
      nextChapter.value.lessons.find(
        (l) => !userStore.completedLessons.includes(l.id),
      ) || nextChapter.value.lessons[0];
    if (target) router.push(`/lesson/${target.id}`);
  } else router.push(`/courses/${fullCourse.value?.id}`);
};
const startCourseQuiz = () => {
  if (course.value) router.push(`/quiz/${course.value.id}`);
};
const continueToQuiz = () => {
  if (quizPromptKind.value === "course") {
    quizGateOpen.value = false;
    quizPromptLessonId.value = null;
    startCourseQuiz();
    return;
  }
  const targetLessonId = quizPromptLessonId.value || lesson.value?.id;
  quizGateOpen.value = false;
  quizPromptLessonId.value = null;
  startQuiz(targetLessonId);
};
const closeQuizPrompt = () => {
  quizGateOpen.value = false;
  quizPromptLessonId.value = null;
  quizPromptKind.value = "lesson";
};
const sortVideoNotes = () => {
  videoNotes.value.sort(
    (a, b) =>
      a.timestampSeconds - b.timestampSeconds ||
      a.createdAt.localeCompare(b.createdAt),
  );
};
const createVideoNote = async (content: string, timestampSeconds: number) => {
  if (!lesson.value) return;
  noteSaving.value = true;
  try {
    videoNotes.value.push(
      await courseApi.createVideoNote(
        lesson.value.id,
        timestampSeconds,
        content,
      ),
    );
    sortVideoNotes();
  } catch (error) {
    console.error("Failed to create video note:", error);
    alert("Không thể lưu ghi chú. Vui lòng thử lại.");
  } finally {
    noteSaving.value = false;
  }
};
const updateVideoNote = async (note: VideoNote, content: string) => {
  if (!lesson.value) return;
  noteSaving.value = true;
  try {
    const updated = await courseApi.updateVideoNote(
      lesson.value.id,
      note.id,
      note.timestampSeconds,
      content,
    );
    const index = videoNotes.value.findIndex((item) => item.id === note.id);
    if (index >= 0) videoNotes.value[index] = updated;
    sortVideoNotes();
  } catch (error) {
    console.error("Failed to update video note:", error);
    alert("Không thể cập nhật ghi chú. Vui lòng thử lại.");
  } finally {
    noteSaving.value = false;
  }
};
const deleteVideoNote = async (note: VideoNote) => {
  if (!lesson.value) return;
  noteSaving.value = true;
  try {
    await courseApi.deleteVideoNote(lesson.value.id, note.id);
    videoNotes.value = videoNotes.value.filter((item) => item.id !== note.id);
  } catch (error) {
    console.error("Failed to delete video note:", error);
    alert("Không thể xóa ghi chú. Vui lòng thử lại.");
  } finally {
    noteSaving.value = false;
  }
};
const seekToNote = async (seconds: number) => {
  if (activeContentType.value !== "VIDEO") {
    showPrimaryContent("VIDEO");
    await nextTick();
  }
  mediaStage.value?.seekTo(seconds);
  scrollWithin(learningPage.value);
};
const openNoteComposer = (seconds: number) => {
  videoNotesPanel.value?.openAt(seconds);
};
const submitFeedback = async () => {
  if (!commentText.value.trim() || !lesson.value) return;
  await courseStore.addComment(
    lesson.value.id,
    null,
    commentText.value,
  );
  commentText.value = "";
};
const submitReply = async (
  rootCommentId: number,
  replyToCommentId: number,
  content: string,
) => {
  if (!lesson.value || !content.trim()) return;
  try {
    await courseStore.replyToComment(
      lesson.value.id,
      rootCommentId,
      replyToCommentId,
      content.trim(),
    );
  } catch (error) {
    console.error("Failed to reply to comment:", error);
    alert("Không thể gửi phản hồi. Vui lòng thử lại.");
  }
};
const loadMoreComments = async () => {
  if (!lesson.value || commentsLoadingMore.value) return;
  const id = lesson.value.id;
  const loadId = lessonLoadId;
  commentsLoadingMore.value = true;
  try {
    const page = await courseApi.getComments(id, commentPage.value + 1);
    if (disposed || loadId !== lessonLoadId || lesson.value?.id !== id) return;
    const ids = new Set(lesson.value.comments.map((comment) => comment.id));
    lesson.value.comments.push(...page.content.filter((comment) => !ids.has(comment.id)));
    lesson.value.commentCount = page.totalElements;
    commentPage.value = page.page;
  } catch (error) {
    if (!disposed && loadId === lessonLoadId) {
      console.error("Failed to load comments:", error);
      alert("Không thể tải thêm thảo luận. Vui lòng thử lại.");
    }
  } finally {
    if (loadId === lessonLoadId) commentsLoadingMore.value = false;
  }
};
const deleteComment = async (commentId: number) => {
  if (!lesson.value) return;
  try {
    const id = lesson.value.id;
    const loadId = lessonLoadId;
    const isRoot = lesson.value.comments.some((comment) => comment.id === commentId);
    await courseStore.deleteComment(id, commentId);
    if (isRoot) {
      const page = await courseApi.getComments(id);
      if (!disposed && loadId === lessonLoadId && lesson.value?.id === id) {
        lesson.value.comments = page.content;
        lesson.value.commentCount = page.totalElements;
        commentPage.value = 0;
      }
    }
  } catch (error) {
    console.error("Failed to delete comment:", error);
    alert("Không thể gỡ bình luận. Vui lòng thử lại.");
  }
};
</script>

<style>
.learning-page {
  margin: -2rem;
  background: var(--bg-main);
  min-height: calc(100vh - 73px);
  color: var(--text-main);
  --learning-motion: 320ms cubic-bezier(0.22, 1, 0.36, 1);
  transition: padding-right var(--learning-motion);
}
.learning-page.sidebar-visible {
  padding-right: 370px;
}
.quiz-gate-backdrop {
  position: fixed;
  inset: 0;
  z-index: 120;
  display: grid;
  place-items: center;
  padding: 1rem;
  background: rgba(5, 18, 14, 0.62);
  backdrop-filter: blur(2px);
}
.quiz-gate-dialog {
  position: relative;
  width: min(480px, 100%);
  padding: 1.6rem;
  border-radius: 14px;
  background: var(--surface-ffffff);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.28);
  text-align: center;
}
.quiz-gate-close {
  position: absolute;
  top: 0.75rem;
  right: 0.75rem;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--surface-f1f5f3);
  color: var(--ink-64748b);
  cursor: pointer;
}
.quiz-gate-icon {
  width: 58px;
  height: 58px;
  margin: 0 auto 0.85rem;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--surface-fff4cc);
  color: var(--ink-8a6b00);
}
.quiz-gate-eyebrow {
  color: var(--ink-8a6b00);
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.quiz-gate-dialog h2 {
  margin: 0.4rem 1.5rem 0.65rem;
  font-size: 1.25rem;
  line-height: 1.35;
  color: var(--ink-173c31);
}
.quiz-gate-dialog p {
  margin: 0;
  color: var(--ink-52645d);
  line-height: 1.6;
}
.quiz-gate-actions {
  margin-top: 1.25rem;
  display: flex;
  justify-content: center;
  gap: 0.6rem;
}
.quiz-gate-actions button {
  min-height: 42px;
  padding: 0 1rem;
  border-radius: 7px;
  cursor: pointer;
  font-weight: 700;
}
.quiz-gate-secondary {
  border: 1px solid var(--line-cbd5d1);
  background: var(--surface-ffffff);
  color: var(--ink-42534c);
}
.quiz-gate-primary {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid var(--primary-color);
  background: var(--primary-color);
  color: white;
}
.quiz-gate-enter-active,
.quiz-gate-leave-active {
  transition: opacity 0.2s ease;
}
.quiz-gate-enter-active .quiz-gate-dialog,
.quiz-gate-leave-active .quiz-gate-dialog {
  transition:
    transform 0.2s ease,
    opacity 0.2s ease;
}
.quiz-gate-enter-from,
.quiz-gate-leave-to {
  opacity: 0;
}
.quiz-gate-enter-from .quiz-gate-dialog,
.quiz-gate-leave-to .quiz-gate-dialog {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}
.learning-header {
  min-height: 68px;
  padding: 0 1.25rem;
  display: flex;
  align-items: center;
  gap: 1rem;
  background: #10251f;
  color: white;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}
.back-button {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.18);
  transition: 0.2s ease;
}
.back-button:hover {
  background: rgba(255, 255, 255, 0.1);
  transform: translateX(-2px);
}
.header-copy {
  min-width: 0;
  flex: 1;
  display: flex;
  align-items: center;
  gap: 0.9rem;
}
.course-name {
  color: var(--ink-e4c760);
  font-weight: 700;
  white-space: nowrap;
}
.header-separator {
  width: 1px;
  height: 24px;
  background: rgba(255, 255, 255, 0.25);
}
.header-copy h1 {
  font-size: 1rem;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-actions,
.progress-summary,
.outline-action,
.mobile-content-button {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.progress-summary {
  color: var(--ink-dbe7e3);
  font-size: 0.82rem;
}
.outline-action,
.mobile-content-button {
  min-height: 38px;
  padding: 0 0.85rem;
  color: white;
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 7px;
  cursor: pointer;
  font-weight: 600;
}
.outline-action:disabled {
  color: var(--ink-8ae2c2);
  border-color: rgba(138, 226, 194, 0.35);
  cursor: default;
}
.mobile-content-button {
  display: none;
}
.learning-stage {
  display: block;
  background: #17201e;
  height: clamp(480px, calc(100vh - 193px), 720px);
  position: relative;
  overflow: hidden;
}
.viewer-area {
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  padding: 14px 18px 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.viewer-shell {
  flex: 1;
  min-height: 0;
  display: grid;
  place-items: stretch;
  overflow: hidden;
  background: #080b0a;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.22);
}
.viewer-shell .card {
  border: 0;
  border-radius: 0;
  box-shadow: none;
}
.viewer-shell .video-container,
.viewer-shell .slide-viewer {
  width: 100%;
  height: 100%;
  min-height: 0;
  padding: 0;
}
.viewer-shell .video-title-bar,
.viewer-shell .video-footer-info,
.viewer-shell .viewer-header {
  margin: 0;
  padding: 0.7rem 1rem;
}
.viewer-shell .video-player-wrapper {
  flex: 1 1 auto;
  width: 100%;
  min-height: 0;
  max-height: none;
  aspect-ratio: auto;
  border-radius: 0;
}
.viewer-shell .video-element {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.viewer-shell .slide-content {
  border-radius: 0;
  min-height: 450px;
}
.viewer-navigation {
  height: 70px;
  display: grid;
  grid-template-columns: 150px 1fr 150px;
  align-items: center;
  color: white;
}
.viewer-navigation button {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  background: transparent;
  border: 0;
  color: var(--ink-d5ddda);
  cursor: pointer;
}
.viewer-navigation button:hover:not(:disabled) {
  color: var(--ink-e4c760);
}
.viewer-navigation button:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.viewer-lesson-title {
  min-width: 0;
  text-align: center;
  display: flex;
  flex-direction: column;
}
.viewer-lesson-title span {
  color: var(--ink-8ba29a);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
.viewer-lesson-title strong {
  font-size: 0.9rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.course-sidebar {
  position: fixed;
  top: 73px;
  right: 0;
  bottom: 0;
  width: 370px;
  min-width: 370px;
  background: var(--surface-ffffff);
  border-left: 1px solid var(--line-dce5e1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: -8px 0 24px rgba(9, 31, 24, 0.08);
  transition: transform var(--learning-motion);
  z-index: 90;
}
.sidebar-hidden .course-sidebar {
  transform: translateX(100%);
}
.sidebar-header {
  padding: 1rem 1.1rem 0.75rem;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.sidebar-header h2 {
  font-size: 1rem;
}
.sidebar-header p {
  color: var(--text-muted);
  font-size: 0.75rem;
  margin-top: 0.15rem;
}
.sidebar-close {
  border: 0;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
  padding: 0.2rem;
}
.sidebar-progress {
  height: 3px;
  background: var(--surface-e4eae8);
}
.sidebar-progress span {
  display: block;
  height: 100%;
  background: var(--primary-color);
  transition: width 0.4s ease;
}
.lesson-list {
  overflow-y: auto;
  flex: 1;
}
.lesson-row {
  width: 100%;
  padding: 0.85rem 1rem;
  display: flex;
  align-items: flex-start;
  gap: 0.7rem;
  background: var(--surface-ffffff);
  color: var(--text-main);
  border: 0;
  border-bottom: 1px solid var(--line-edf1ef);
  text-align: left;
  cursor: pointer;
}
.lesson-row:hover {
  background: var(--surface-f4f8f6);
}
.lesson-row.active {
  background: var(--primary-light);
  box-shadow: inset 4px 0 var(--primary-color);
}
.lesson-check {
  width: 18px;
  height: 18px;
  flex: 0 0 18px;
  margin-top: 1px;
  display: grid;
  place-items: center;
  border: 1.5px solid var(--line-94a3b8);
  border-radius: 3px;
  padding: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.lesson-row.completed .lesson-check {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}
.lesson-row-copy {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  min-width: 0;
  flex: 1;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.lesson-row-copy strong {
  font-size: 0.85rem;
  font-weight: 600;
  line-height: 1.35;
}
.lesson-row-copy small {
  display: flex;
  align-items: center;
  gap: 0.3rem;
  color: var(--text-muted);
}
.lesson-row-copy .lesson-quiz-note {
  color: var(--ink-806710);
  font-weight: 700;
}
.quiz-box {
  margin: 0.8rem;
  padding: 0.9rem;
  border-radius: 9px;
  background: var(--surface-f5f0dc);
  border: 1px solid var(--line-e7d692);
}
.quiz-box > div {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  color: var(--ink-6a5610);
}
.quiz-box p {
  margin: 0.4rem 0 0.7rem;
  font-size: 0.75rem;
  color: var(--ink-71683f);
}
.quiz-box button {
  border: 0;
  background: transparent;
  color: var(--primary-ink);
  font-weight: 700;
  display: flex;
  gap: 0.35rem;
  align-items: center;
  cursor: pointer;
}
.course-quiz-box {
  margin-top: 0;
  background: var(--surface-eaf5f1);
  border-color: var(--line-b8d8cc);
}
.course-quiz-box > div {
  color: var(--primary-ink);
}
.course-quiz-box p {
  color: var(--ink-476158);
}
.sidebar-reopen {
  position: fixed;
  right: 0;
  top: 165px;
  width: 35px;
  height: 52px;
  border: 0;
  border-radius: 8px 0 0 8px;
  background: var(--primary-color);
  color: white;
  cursor: pointer;
  z-index: 89;
}
.mobile-overlay {
  display: none;
}
.procedure-viewer {
  overflow-y: auto;
  padding: 2rem;
  background: var(--surface-e9efed);
}
.procedure-document {
  max-width: 900px;
  margin: auto;
  padding: 2.5rem;
  background: var(--surface-ffffff);
  min-height: 100%;
}
.procedure-heading {
  border-bottom: 3px solid var(--primary-color);
  padding-bottom: 1rem;
  margin-bottom: 1.5rem;
}
.procedure-heading span,
.eyebrow {
  color: var(--primary-ink);
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-weight: 700;
}
.procedure-heading h2 {
  margin-top: 0.35rem;
}
.procedure-section {
  display: grid;
  grid-template-columns: 42px 1fr;
  gap: 1rem;
  margin: 1.5rem 0;
}
.section-number {
  font-weight: 800;
  color: var(--ink-c5a539);
  font-size: 1.15rem;
}
.procedure-section h3 {
  font-size: 1rem;
  margin-bottom: 0.5rem;
}
.procedure-section ul {
  padding-left: 1rem;
  color: var(--ink-46564f);
}
.procedure-section li {
  margin: 0.4rem 0;
}
.discussion-viewer {
  height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  padding: 2rem;
  text-align: center;
  color: var(--ink-c5d5cf);
  background: radial-gradient(circle at center, #173c31 0%, #07110e 72%);
}
.discussion-viewer svg {
  color: var(--ink-e4c760);
  margin-bottom: 1rem;
}
.discussion-viewer span {
  color: var(--ink-79bfa6);
  text-transform: uppercase;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.1em;
}
.discussion-viewer h2 {
  color: white;
  margin: 0.4rem 0;
}
.discussion-viewer p {
  max-width: 520px;
  color: var(--ink-9db3ab);
}

/* Multi-file document workspace */
.document-workspace {
  height: 100%;
  min-height: 0;
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  position: relative;
  background: var(--surface-dfe8e5);
  overflow: hidden;
}
.document-library {
  min-width: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--surface-f8faf9);
  border-right: 1px solid var(--line-d3ddd9);
  z-index: 3;
}
.document-library-header {
  min-height: 65px;
  padding: 0.8rem 1rem;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--line-dbe4e0);
}
.document-library-header > div {
  display: flex;
  flex-direction: column;
}
.document-library-header span {
  color: var(--text-muted);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}
.document-library-header strong {
  font-size: 0.9rem;
}
.document-library-close {
  display: none;
  border: 0;
  background: transparent;
  color: var(--text-muted);
}
.document-list {
  flex: 1;
  overflow-y: auto;
  padding: 0.55rem;
}
.document-list button {
  width: 100%;
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 0.6rem;
  padding: 0.7rem;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
}
.document-list button:hover {
  background: var(--surface-eef4f1);
}
.document-list button.active {
  background: var(--primary-light);
  border-color: rgba(0, 91, 65, 0.18);
  color: var(--primary-ink);
}
.document-type-icon {
  width: 36px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 6px;
  background: var(--surface-ffffff);
  border: 1px solid var(--line-d7e0dc);
  color: var(--primary-ink);
}
.document-list button > span:nth-child(2) {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.document-list button strong {
  font-size: 0.8rem;
  line-height: 1.35;
}
.document-list button small {
  margin-top: 0.25rem;
  color: var(--text-muted);
  font-size: 0.7rem;
}
.document-preview {
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.document-preview-header {
  min-height: 65px;
  padding: 0.55rem 0.9rem;
  display: flex;
  align-items: center;
  gap: 0.8rem;
  background: var(--surface-ffffff);
  border-bottom: 1px solid var(--line-d3ddd9);
}
.document-library-toggle {
  display: none;
  align-items: center;
  gap: 0.4rem;
  border: 1px solid var(--line-ced9d5);
  background: var(--surface-ffffff);
  border-radius: 6px;
  padding: 0.45rem 0.6rem;
  color: var(--primary-ink);
  cursor: pointer;
}
.selected-file {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 0.55rem;
  color: var(--primary-ink);
}
.selected-file > span {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.selected-file strong {
  color: var(--text-main);
  font-size: 0.85rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.selected-file small {
  color: var(--text-muted);
  font-size: 0.7rem;
}
.open-document {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--primary-ink);
  font-size: 0.8rem;
  font-weight: 700;
  white-space: nowrap;
}
.document-preview-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.document-preview-body > .slide-viewer {
  min-height: 100%;
  height: max-content;
}
.document-preview-body iframe {
  width: 100%;
  height: 100%;
  min-height: 450px;
  border: 0;
  background: var(--surface-ffffff);
}
.external-document {
  min-height: 100%;
  display: grid;
  place-content: center;
  justify-items: center;
  text-align: center;
  background: var(--surface-ffffff);
  color: var(--text-muted);
}
.external-document h3 {
  margin-top: 0.8rem;
  color: var(--text-main);
}
.external-document a {
  margin-top: 1rem;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.6rem 0.9rem;
  border-radius: 7px;
  background: var(--primary-color);
  color: white;
}
.document-library-overlay {
  display: none;
}
.lesson-information {
  background: var(--surface-ffffff);
}
.content-tabs {
  z-index: 80;
  min-height: 52px;
  padding: 6px 1.1rem;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 0.35rem;
  background: #132b24;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  box-shadow: 0 4px 12px rgba(5, 18, 14, 0.14);
  overflow-x: auto;
}
.content-tabs button {
  position: relative;
  min-height: 38px;
  padding: 0 0.95rem;
  display: flex;
  align-items: center;
  gap: 0.45rem;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 7px;
  color: var(--ink-aebdb8);
  white-space: nowrap;
  font-size: 0.86rem;
  font-weight: 600;
  cursor: pointer;
  transition: 0.2s ease;
}
.content-tabs button:hover {
  color: white;
  background: rgba(255, 255, 255, 0.06);
}
.content-tabs button::after {
  content: "";
  position: absolute;
  left: 0.9rem;
  right: 0.9rem;
  bottom: 1px;
  height: 2px;
  border-radius: 2px;
  background: transparent;
}
.content-tabs button.active {
  color: white;
  background: rgba(0, 91, 65, 0.45);
  border-color: rgba(126, 205, 177, 0.22);
}
.content-tabs button.active svg {
  color: var(--ink-e4c760);
}
.content-tabs button.active::after {
  background: var(--surface-e4c760);
}
.info-layout {
  max-width: 1280px;
  margin: auto;
  padding: 2.5rem 2rem 4rem;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 3.5rem;
}
.info-main {
  min-width: 0;
}
.lesson-overview h2,
.section-title h2 {
  font-size: 1.55rem;
  margin: 0.35rem 0 1rem;
}
.hint-box {
  padding: 1rem 1.1rem;
  display: flex;
  gap: 0.8rem;
  background: var(--primary-light);
  border-left: 4px solid var(--primary-color);
  color: var(--primary-ink);
}
.hint-box svg {
  flex: 0 0 auto;
}
.hint-box p {
  color: var(--ink-3f5f54);
  margin-top: 0.2rem;
}
.lesson-facts {
  border-left: 1px solid var(--border-color);
  padding-left: 1.5rem;
}
.lesson-facts h3 {
  font-size: 1rem;
  margin-bottom: 1rem;
}
.lesson-facts > div {
  display: flex;
  gap: 0.7rem;
  color: var(--primary-ink);
  margin: 1rem 0;
}
.lesson-facts span {
  display: flex;
  flex-direction: column;
}
.lesson-facts small {
  color: var(--text-muted);
}
.lesson-facts strong {
  color: var(--text-main);
  font-size: 0.85rem;
}
.discussion-section {
  margin-top: 3rem;
  padding-top: 2rem;
  border-top: 1px solid var(--border-color);
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-title > svg {
  color: var(--primary-ink);
}
.feedback-form {
  padding: 1.1rem;
  background: var(--surface-f8faf9);
  border: 1px solid var(--border-color);
  border-radius: 10px;
}
.rating-row {
  display: flex;
  align-items: center;
  gap: 1rem;
  margin-bottom: 0.8rem;
  font-weight: 600;
}
.feedback-form textarea {
  width: 100%;
  resize: vertical;
  border: 1px solid var(--line-cfd9d5);
  border-radius: 7px;
  padding: 0.8rem;
  outline: none;
}
.feedback-form textarea:focus {
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(0, 91, 65, 0.1);
}
.submit-button {
  margin: 0.75rem 0 0 auto;
  padding: 0.65rem 1rem;
  display: flex;
  align-items: center;
  gap: 0.4rem;
  border: 0;
  border-radius: 7px;
  color: white;
  background: var(--primary-color);
  cursor: pointer;
  font-weight: 700;
}
.submit-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.comments-list {
  margin-top: 1rem;
}
.empty-comments {
  padding: 2rem;
  text-align: center;
  color: var(--text-muted);
}
.comment {
  padding: 1rem 0;
  display: grid;
  grid-template-columns: 40px 1fr;
  gap: 0.8rem;
  border-bottom: 1px solid var(--border-color);
}
.avatar {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--primary-color);
  color: white;
  font-weight: 700;
}
.comment-meta {
  display: flex;
  justify-content: space-between;
}
.comment-meta span {
  font-size: 0.75rem;
  color: var(--text-muted);
}
.comment p {
  margin-top: 0.35rem;
  color: var(--ink-43534d);
}
.loading-state {
  min-height: 60vh;
  display: grid;
  place-content: center;
  gap: 0.7rem;
  color: var(--text-muted);
  text-align: center;
}
.loading-state span {
  width: 30px;
  height: 30px;
  margin: auto;
  border: 3px solid var(--border-color);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@media (max-width: 1100px) {
  .learning-page.sidebar-visible {
    padding-right: 320px;
  }
  .course-sidebar {
    width: 320px;
    min-width: 320px;
  }
}
@media (max-width: 900px) {
  .learning-page {
    margin: -1rem;
  }
  .learning-page.sidebar-visible {
    padding-right: 0;
  }
  .learning-header {
    position: static;
  }
  .header-separator,
  .course-name,
  .outline-action {
    display: none;
  }
  .mobile-content-button {
    display: flex;
  }
  .mobile-content-button span {
    display: none;
  }
  .content-tabs {
    top: 133px;
  }
  .learning-stage,
  .learning-stage.sidebar-hidden {
    display: block;
    height: clamp(420px, calc(100vh - 185px), 620px);
  }
  .viewer-area {
    padding: 10px 10px 0;
    height: 100%;
  }
  .course-sidebar {
    position: fixed;
    top: 0;
    right: 0;
    bottom: 0;
    width: min(400px, 88vw);
    min-width: 0;
    max-height: none;
    transform: translateX(100%);
    z-index: 102;
    box-shadow: -12px 0 30px rgba(0, 0, 0, 0.2);
  }
  .course-sidebar.open {
    transform: translateX(0);
  }
  .mobile-overlay {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(7, 20, 16, 0.58);
    z-index: 101;
  }
  .sidebar-reopen {
    display: none;
  }
  .info-layout {
    grid-template-columns: 1fr;
    padding: 2rem 1.25rem 3rem;
    gap: 2rem;
  }
  .lesson-facts {
    border-left: 0;
    border-top: 1px solid var(--border-color);
    padding: 1.5rem 0 0;
  }
  .content-tabs {
    padding: 0 0.75rem;
  }
}
@media (max-width: 800px) {
  .document-workspace {
    grid-template-columns: 1fr;
  }
  .document-library {
    position: absolute;
    inset: 0 auto 0 0;
    width: min(320px, 86%);
    transform: translateX(-100%);
    transition: transform 0.22s ease;
    box-shadow: 10px 0 28px rgba(0, 0, 0, 0.2);
  }
  .document-library.open {
    transform: translateX(0);
  }
  .document-library-close,
  .document-library-toggle {
    display: flex;
  }
  .document-library-overlay {
    display: block;
    position: absolute;
    inset: 0;
    background: rgba(5, 18, 14, 0.45);
    z-index: 2;
  }
}
@media (max-width: 600px) {
  .learning-header {
    min-height: 58px;
    padding: 0 0.7rem;
  }
  .content-tabs {
    top: 122px;
    min-height: 48px;
    padding: 5px 0.6rem;
  }
  .content-tabs button {
    min-height: 36px;
    padding: 0 0.7rem;
    font-size: 0.78rem;
  }
  .header-copy h1 {
    font-size: 0.85rem;
  }
  .learning-stage,
  .learning-stage.sidebar-hidden {
    height: min(440px, calc(100vh - 170px));
    min-height: 340px;
  }
  .viewer-area {
    padding: 0;
    min-height: 0;
  }
  .viewer-shell .video-container,
  .viewer-shell .slide-viewer {
    min-height: 300px;
  }
  .viewer-shell .video-title-bar,
  .viewer-shell .video-footer-info,
  .viewer-shell .viewer-header {
    display: none;
  }
  .viewer-shell .slide-content {
    min-height: 300px;
    padding: 0.5rem;
  }
  .viewer-shell .slide-card {
    padding: 0.75rem;
  }
  .document-preview-body {
    padding: 6px;
  }
  .document-preview-header {
    min-height: 56px;
  }
  .document-library-toggle span {
    display: none;
  }
  .viewer-navigation {
    height: 60px;
    grid-template-columns: 70px 1fr 70px;
  }
  .viewer-navigation button span {
    display: none;
  }
  .procedure-viewer {
    padding: 0.7rem;
  }
  .procedure-document {
    padding: 1rem;
  }
  .content-tabs button {
    padding: 0 0.75rem;
    font-size: 0.8rem;
  }
  .info-layout {
    padding: 1.75rem 1rem 2.5rem;
  }
  .lesson-overview h2,
  .section-title h2 {
    font-size: 1.25rem;
  }
  .rating-row {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.4rem;
  }
}

/* Sidebar resources layout */
.learning-stage {
  height: clamp(540px, calc(100vh - 141px), 780px);
}
.lesson-entry {
  position: relative;
  background: var(--surface-ffffff);
  border-bottom: 1px solid var(--line-edf1ef);
  transition: background 0.2s ease;
}
.lesson-entry.active {
  background: var(--primary-light);
  box-shadow: inset 4px 0 var(--primary-color);
}
.lesson-entry .lesson-row {
  padding: 0.85rem 1rem 0.55rem;
  background: transparent;
  border-bottom: 0;
}
.lesson-entry:hover {
  background: var(--surface-f4f8f6);
}
.lesson-entry.active:hover {
  background: var(--primary-light);
}
.lesson-entry.completed .lesson-check {
  background: var(--primary-color);
  border-color: var(--primary-color);
  color: white;
}
.lesson-content-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.4rem;
  padding: 0 0.75rem 0.7rem 2.7rem;
}
.lesson-resources-toggle {
  padding: 0.35rem 0.5rem;
  display: flex;
  align-items: center;
  gap: 0.35rem;
  border: 1px solid rgba(0, 91, 65, 0.22);
  border-radius: 6px;
  background: var(--surface-ffffff);
  color: var(--primary-ink);
  font-size: 0.72rem;
  font-weight: 700;
  cursor: pointer;
}
.lesson-resources-toggle:hover,
.lesson-resources-toggle.expanded {
  background: var(--surface-e7f2ee);
  border-color: rgba(0, 91, 65, 0.4);
}
.lesson-content-actions button:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: 2px;
}
.lesson-resources-toggle > svg:last-child {
  transition: transform 0.2s ease;
}
.lesson-resources-toggle.expanded > svg:last-child {
  transform: rotate(180deg);
}
.resource-count {
  min-width: 17px;
  height: 17px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: var(--primary-color);
  color: #fff;
  font-size: 0.62rem;
}
.lesson-documents {
  margin: 0 0.75rem 0.7rem 2.7rem;
  padding: 0.25rem 0 0.25rem 0.7rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  border-left: 2px solid var(--line-b8d4ca);
}
.lesson-document-row {
  display: flex;
  align-items: center;
  min-width: 0;
  border-radius: 6px;
}
.lesson-documents .lesson-document-select {
  width: 100%;
  padding: 0.5rem 0.55rem;
  display: grid;
  grid-template-columns: 17px minmax(0, 1fr);
  gap: 0.45rem;
  align-items: flex-start;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--ink-42534c);
  text-align: left;
  cursor: pointer;
  flex: 1;
  min-width: 0;
}
.lesson-document-row:hover,
.lesson-document-row.selected {
  background: var(--surface-ffffff);
  color: var(--primary-ink);
  box-shadow: 0 1px 4px rgba(11, 42, 32, 0.09);
}
.lesson-document-row:hover .lesson-document-select,
.lesson-document-row.selected .lesson-document-select {
  color: var(--primary-ink);
}
.lesson-documents .lesson-document-download {
  display: grid;
  place-items: center;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  padding: 0;
  margin-right: 0.2rem;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--primary-ink);
  cursor: pointer;
}
.lesson-documents .lesson-document-download:hover {
  background: var(--primary-light);
}
.lesson-documents button:focus-visible {
  outline: 2px solid var(--primary-ink);
  outline-offset: -2px;
}
.lesson-document-select > span {
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.lesson-documents strong {
  font-size: 0.75rem;
  line-height: 1.3;
  overflow-wrap: anywhere;
}
.lesson-documents small {
  margin-top: 0.15rem;
  color: var(--text-muted);
  font-size: 0.65rem;
}

/* Document preview selected from the course sidebar */
.selected-document-preview {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #0f172a;
}
.selected-document-preview > .slide-viewer {
  flex: 1;
  min-height: 0;
  height: auto;
}
.selected-document-preview > iframe {
  flex: 1;
  width: 100%;
  min-height: 0;
  border: 0;
  background: var(--surface-ffffff);
}
.selected-document-preview > .word-preview-shell,
.selected-document-preview > .powerpoint-preview {
  flex: 1;
  min-height: 0;
}

@media (max-width: 900px) {
  .learning-stage,
  .learning-stage.sidebar-hidden {
    height: clamp(480px, calc(100vh - 133px), 680px);
  }
}
@media (max-width: 600px) {
  .learning-stage,
  .learning-stage.sidebar-hidden {
    height: min(500px, calc(100vh - 122px));
    min-height: 360px;
  }
}
.learning-page .outline-action:hover:not(:disabled),
.learning-page .mobile-content-button:hover,
.learning-page .sidebar-reopen:hover {
  background: #294c40;
  border-color: var(--line-8ae2c2);
}
.learning-page .sidebar-close:hover,
.learning-page .lesson-check:hover,
.learning-page .document-library-close:hover {
  background: var(--primary-light);
  color: var(--primary-ink);
}
.learning-page .lesson-row-copy:hover strong {
  text-decoration: underline;
  text-underline-offset: 3px;
}
.learning-page .quiz-box button:hover {
  background: var(--surface-eadca8);
  border-radius: 6px;
}
.learning-page .submit-button:hover:not(:disabled),
.learning-page .quiz-gate-primary:hover {
  background: var(--primary-hover);
  box-shadow: 0 3px 10px #005b4133;
}
.learning-page .quiz-gate-secondary:hover {
  background: var(--primary-light);
}
.header-copy {
  overflow: hidden;
}
.course-name {
  min-width: 0;
  max-width: 45%;
  overflow: hidden;
  text-overflow: ellipsis;
}
.chapter-completion-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 1rem;
  padding: 1.25rem;
}
@media (min-width: 769px) and (max-width: 900px) {
  .learning-page {
    margin: -2rem;
  }
}
@media (max-width: 900px) {
  .learning-stage {
    height: clamp(420px, calc(100dvh - 185px), 620px);
  }
}
@media (max-width: 600px) {
  .learning-stage {
    height: min(440px, calc(100dvh - 170px));
    min-height: 340px;
  }
  .quiz-gate-actions {
    flex-wrap: wrap;
  }
  .quiz-gate-actions button {
    flex: 1;
  }
  .chapter-completion-footer .btn {
    width: 100%;
  }
}
/* Lesson format controls in the learning header. */
.learning-header .back-button,
.learning-header .header-actions { flex-shrink: 0; }
.lesson-content-switch {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  border: 1px solid rgba(138, 226, 194, 0.28);
  border-radius: 10px;
  background: rgba(0, 0, 0, 0.12);
}
.lesson-content-switch button {
  min-height: 36px;
  padding: 0 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #dbe7e3;
  font: inherit;
  font-size: 0.85rem;
  font-weight: 600;
  white-space: nowrap;
  cursor: pointer;
  transition: background-color 0.2s ease, color 0.2s ease, box-shadow 0.2s ease;
}
.lesson-content-switch button:hover { background: rgba(255, 255, 255, 0.09); color: #fff; }
.lesson-content-switch button[aria-pressed="true"] {
  background: #e7f2ee;
  color: #005b41;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.18);
}
.lesson-content-switch button:focus-visible { outline: 2px solid #8ae2c2; outline-offset: 2px; }
.learning-header .header-actions { gap: 0; }
.header-completion {
  flex: 0 0 auto;
  min-width: 0;
  overflow: hidden;
  opacity: 0;
  visibility: hidden;
  transition: width var(--learning-motion), opacity 180ms ease, visibility 0s 320ms;
}
.header-completion.visible {
  opacity: 1;
  visibility: visible;
  transition: width var(--learning-motion), opacity 220ms ease 70ms, visibility 0s;
}
.header-completion-content {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  padding-left: 0.5rem;
  width: max-content;
  white-space: nowrap;
}
.header-completion-content > * { flex-shrink: 0; }
.learning-header .mobile-content-button { margin-left: 0.5rem; }
@media (max-width: 1100px) {
  .learning-header { flex-wrap: wrap; gap: 0.5rem 0.75rem; padding-top: 0.6rem; padding-bottom: 0.6rem; }
  .learning-header .header-actions { flex-wrap: wrap; justify-content: flex-end; margin-left: auto; max-width: 100%; }
}
@media (max-width: 900px) {
  .learning-header {
    display: grid;
    grid-template-columns: 40px minmax(0, 1fr) auto 40px;
    align-items: center;
    gap: 0.5rem;
    padding: 0.65rem 0.75rem;
  }
  .learning-header .back-button { grid-column: 1; grid-row: 1; }
  .learning-header .header-copy { grid-column: 2 / 4; grid-row: 1; }
  .learning-header .header-copy h1 { font-size: 0.85rem; }
  .learning-header .header-actions { display: contents; }
  .learning-header .mobile-content-button {
    grid-column: 4;
    grid-row: 1;
    justify-content: center;
    width: 40px;
    height: 40px;
    padding: 0;
    margin: 0;
  }
  .learning-header .lesson-content-switch {
    grid-column: 1 / 3;
    grid-row: 2;
    justify-self: start;
    margin: 0;
    max-width: 100%;
  }
  .learning-header .header-completion {
    grid-column: 3 / 5;
    grid-row: 2;
    justify-self: end;
    width: auto !important;
    max-width: 100%;
    transition: opacity 180ms ease, visibility 0s 180ms;
  }
  .learning-header .header-completion.visible {
    transition: opacity 220ms ease, visibility 0s;
  }
  .header-completion-content { padding-left: 0; }
  .lesson-content-switch button {
    min-height: 34px;
    padding: 0 0.5rem;
    gap: 0.35rem;
    font-size: 0.78rem;
  }
  .progress-summary { font-size: 0.7rem; gap: 0.3rem; }
}
@media (max-width: 360px) {
  .lesson-content-switch { gap: 2px; padding: 3px; }
  .lesson-content-switch button { padding: 0 0.35rem; font-size: 0.75rem; }
  .progress-summary { font-size: 0.65rem; }
  .progress-summary svg { width: 14px; height: 14px; }
}
@media (prefers-reduced-motion: reduce) {
  .learning-page,
  .course-sidebar,
  .lesson-content-switch button,
  .header-completion,
  .header-completion.visible { transition: none; }
}
</style>
