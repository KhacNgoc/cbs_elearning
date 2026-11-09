<template>
  <aside class="course-sidebar" :class="{ open }">
    <div class="sidebar-header">
      <div>
        <h2>Nội dung khóa học</h2>
        <p>
          {{ completedCount }}/{{ course?.lessons.length || 0 }} bài đã hoàn
          thành
        </p>
      </div>
      <button
        class="sidebar-close"
        aria-label="Đóng nội dung khóa học"
        @click="$emit('close')"
      >
        <X :size="20" />
      </button>
    </div>
    <div class="sidebar-progress">
      <span :style="{ width: `${progress}%` }"></span>
    </div>
    <div class="lesson-list">
      <section
        v-for="(chapter, chapterIndex) in chapters"
        :key="chapter.id"
        class="sidebar-chapter"
      >
        <button
          type="button"
          class="chapter-toggle"
          :class="{ 'current-chapter': chapter.id === activeChapterId }"
          :aria-expanded="expandedChapterId === chapter.id"
          :aria-controls="`chapter-lessons-${chapter.id}`"
          @click="expandedChapterId = expandedChapterId === chapter.id ? null : chapter.id"
        >
          <span class="chapter-toggle-copy">
            <strong>Chương {{ chapterIndex + 1 }}. {{ chapter.title }}</strong>
            <small>
              {{ chapterCompletedCount(chapter) }}/{{ chapter.lessons.length }} bài hoàn thành
              <span v-if="chapter.id === activeChapterId"> · Đang học</span>
            </small>
          </span>
          <ChevronDown :size="18" />
        </button>
        <Transition
          name="chapter-reveal"
          appear
          @before-enter="prepareChapterEnter"
          @before-leave="prepareChapterLeave"
          @leave-cancelled="prepareChapterEnter"
        >
          <div
            v-if="expandedChapterId === chapter.id"
            :id="`chapter-lessons-${chapter.id}`"
            class="chapter-lessons"
          >
            <div class="chapter-lessons-inner">
              <div
                v-for="(item, index) in chapter.lessons"
                :key="item.id"
                class="lesson-entry"
                :style="{ '--lesson-delay': `${Math.min(index, 5) * 25}ms` }"
                :class="{
                  active: item.id === activeLessonId,
                  completed: completedLessons.includes(item.id),
                }"
              >
                <div class="lesson-row">
                  <button
                    class="lesson-check"
                    type="button"
                    :aria-label="
                      completedLessons.includes(item.id)
                        ? `Bỏ hoàn thành bài ${item.title}`
                        : `Đánh dấu hoàn thành bài ${item.title}`
                    "
                    :aria-checked="completedLessons.includes(item.id)"
                    role="checkbox"
                    @click="$emit('toggle-completion', item.id)"
                  >
                    <Check v-if="completedLessons.includes(item.id)" :size="14" />
                  </button>
                  <button
                    class="lesson-row-copy"
                    type="button"
                    @click="openLesson(item.id)"
                  >
                    <strong>{{ index + 1 }}. {{ item.title }}</strong>
                    <small><PlayCircle :size="13" /> Bài học</small>
                    <small v-if="item.hasQuiz" class="lesson-quiz-note">
                      <Award :size="13" />
                      {{
                        hasPassedLessonQuiz(item)
                          ? "Đã đạt bài kiểm tra"
                          : `${item.quizRequired ? "Kiểm tra bắt buộc" : "Kiểm tra tùy chọn"} · Đạt ≥ ${item.quizPassingScore ?? 80}%`
                      }}
                    </small>
                  </button>
                </div>
                <div
                  v-if="getDocuments(item).length"
                  class="lesson-content-actions"
                  role="group"
                  :aria-label="`Các phần của bài ${item.title}`"
                >
                  <button
                    v-if="getDocuments(item).length"
                    type="button"
                    class="lesson-resources-toggle"
                    :aria-expanded="expandedLessons.has(item.id)"
                    :class="{ expanded: expandedLessons.has(item.id) }"
                    @click="toggleDocuments(item.id)"
                  >
                    <FolderOpen :size="14" /><span>Tài liệu tham khảo</span
                    ><span class="resource-count">{{ getDocuments(item).length }}</span
                    ><ChevronDown :size="14" />
                  </button>
                </div>
                <div v-if="expandedLessons.has(item.id)" class="lesson-documents">
                  <div
                    v-for="document in getDocuments(item)"
                    :key="document.id || document.fileUrl"
                    class="lesson-document-row"
                    :class="{ selected: selectedDocumentId === document.id }"
                  >
                    <button
                      class="lesson-document-select"
                      @click="$emit('select-document', item.id, document)"
                    >
                      <FileText :size="15" /><span
                        ><strong>{{ document.name }}</strong
                        ><small
                          >{{ document.documentType }} ·
                          {{ formatFileSize(document.fileSize) }}</small
                        ></span
                      >
                    </button>
                    <button
                      type="button"
                      class="lesson-document-download"
                      :aria-label="`Tải xuống ${document.name}`"
                      :title="`Tải xuống ${document.name}`"
                      @click="$emit('download-document', document)"
                    >
                      <Download :size="16" />
                    </button>
                  </div>
                </div>
              </div>
              <p v-if="!chapter.lessons.length" class="chapter-empty">
                Chương chưa có bài học.
              </p>
            </div>
          </div>
        </Transition>
      </section>
    </div>
    <div v-if="activeLesson?.hasQuiz" class="quiz-box">
      <div>
        <Award :size="22" /><strong>{{
          activeLessonPassed ? "Bài kiểm tra đã hoàn thành" : "Kiểm tra bài học"
        }}</strong>
      </div>
      <p v-if="activeLessonPassed && missingContent" class="quiz-box-missing" role="status">
        Bạn đã đạt bài kiểm tra, nhưng bài học chưa hoàn thành. Còn cần:
        {{ missingContent }}.
      </p>
      <p v-else-if="activeLessonPassed">
        Bạn đã đạt bài kiểm tra này. Xem lại các câu đúng, câu sai và đáp án đã
        chọn.
      </p>
      <p v-else>
        {{
          activeLesson.quizRequired
            ? `Đạt tối thiểu ${activeLesson.quizPassingScore ?? 80}% để hoàn thành bài và học tiếp.`
            : "Kiểm tra tự luyện, không bắt buộc để tiếp tục học."
        }}
      </p>
      <button @click="$emit('quiz')">
        {{
          activeLessonPassed ? "Xem chi tiết bài đã làm" : "Làm bài kiểm tra"
        }}
        <ArrowRight :size="16" />
      </button>
    </div>
    <div v-if="course?.hasQuiz" class="quiz-box course-quiz-box">
      <div>
        <Trophy :size="22" /><strong>{{
          courseQuizPassed
            ? "Đã hoàn thành kiểm tra tổng"
            : "Kiểm tra tổng kết khóa học"
        }}</strong>
      </div>
      <p v-if="courseQuizPassed">
        Bạn đã đạt bài kiểm tra tổng của khóa học {{ course.name }}. Có thể mở
        lại để xem chi tiết bài làm.
      </p>
      <p v-else>
        Đánh giá kiến thức tổng hợp của khóa học {{ course.name }}. Ngưỡng đạt
        tối thiểu {{ course.quizPassingScore ?? 80 }}%.
      </p>
      <button @click="$emit('course-quiz')">
        {{
          courseQuizPassed ? "Xem chi tiết bài đã làm" : "Làm bài kiểm tra tổng"
        }}
        <ArrowRight :size="16" />
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import {
  ArrowRight,
  Award,
  Check,
  ChevronDown,
  Download,
  FileText,
  FolderOpen,
  PlayCircle,
  Trophy,
  X,
} from "lucide-vue-next";
import type { Lesson, LessonDocument, Course, Chapter } from "../../types";
const props = defineProps<{
  course: Course | null | undefined;
  chapters: Chapter[];
  activeLessonId: string;
  completedLessons: string[];
  completedCount: number;
  progress: number;
  open: boolean;
  selectedDocumentId?: number;
  lessonQuizScores: Record<string, number>;
  lessonQuizTotals: Record<string, number>;
  courseQuizScores: Record<string, number>;
  courseQuizTotals: Record<string, number>;
  /** Content still blocking the active lesson after its quiz is passed. */
  missingContent?: string;
}>();
const emit = defineEmits<{
  close: [];
  select: [lessonId: string];
  "toggle-completion": [lessonId: string];
  "select-document": [lessonId: string, document: LessonDocument];
  "download-document": [document: LessonDocument];
  quiz: [];
  "course-quiz": [];
}>();
const prepareChapterEnter = (element: Element) => {
  element.removeAttribute("inert");
  element.removeAttribute("aria-hidden");
};
const prepareChapterLeave = (element: Element) => {
  // Keep exiting lessons out of keyboard navigation while the panel closes.
  element.setAttribute("inert", "");
  element.setAttribute("aria-hidden", "true");
};
const expandedChapterId = ref<string | null>(null);
const activeChapterId = computed(() =>
  props.chapters.find(chapter => chapter.lessons.some(item => item.id === props.activeLessonId))?.id,
);
watch(activeChapterId, id => { expandedChapterId.value = id ?? null; }, { immediate: true });
const chapterCompletedCount = (chapter: Chapter) =>
  chapter.lessons.filter(item => props.completedLessons.includes(item.id)).length;
const expandedLessons = ref(new Set<string>());
const activeLesson = computed(() =>
  props.course?.lessons.find((item) => item.id === props.activeLessonId),
);
const hasPassedLessonQuiz = (lesson: Lesson) => {
  const score = props.lessonQuizScores[lesson.id];
  const total = props.lessonQuizTotals[lesson.id];
  return (
    score !== undefined &&
    total > 0 &&
    Math.round((score / total) * 100) >= (lesson.quizPassingScore ?? 80)
  );
};
const activeLessonPassed = computed(() =>
  activeLesson.value ? hasPassedLessonQuiz(activeLesson.value) : false,
);
const courseQuizPassed = computed(() => {
  if (!props.course) return false;
  const score = props.courseQuizScores[props.course.id];
  const total = props.courseQuizTotals[props.course.id];
  return (
    score !== undefined &&
    total > 0 &&
    Math.round((score / total) * 100) >= (props.course.quizPassingScore ?? 80)
  );
});
const getDocuments = (lesson: Lesson): LessonDocument[] => {
  return (lesson.documents || []).filter(document => document.purpose !== 'SLIDE');
};
const formatFileSize = (size?: number) =>
  size ? `${(size / 1024 / 1024).toFixed(1)} MB` : "Tệp đính kèm";
const toggleDocuments = (lessonId: string) => {
  const next = new Set(expandedLessons.value);
  next.has(lessonId) ? next.delete(lessonId) : next.add(lessonId);
  expandedLessons.value = next;
};
const openLesson = (lessonId: string) => {
  emit("select", lessonId);
};
</script>

<style scoped>
.sidebar-chapter { border-bottom: 1px solid var(--border-color); }
.chapter-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  width: 100%;
  padding: 1rem;
  border: 0;
  background: var(--surface-f8faf9);
  color: var(--text-main);
  text-align: left;
  cursor: pointer;
}
.chapter-toggle:hover { background: var(--primary-light); }
.chapter-toggle.current-chapter { color: var(--primary-ink); }
.chapter-toggle:focus-visible { outline: 2px solid var(--primary-color); outline-offset: -3px; }
.chapter-toggle-copy { min-width: 0; display: flex; flex-direction: column; gap: 0.3rem; }
.chapter-toggle strong { font-size: 0.85rem; line-height: 1.5; overflow-wrap: anywhere; }
.chapter-toggle small { font-size: 0.72rem; font-weight: 400; color: var(--text-muted); }
.chapter-toggle > svg { flex-shrink: 0; transition: transform 180ms ease; }
.chapter-toggle[aria-expanded="true"] > svg { transform: rotate(180deg); }
.chapter-empty { padding: 1rem; color: var(--text-muted); font-size: 0.8rem; }
.chapter-lessons { display: grid; grid-template-rows: 1fr; }
.chapter-lessons-inner { min-height: 0; overflow: hidden; }
.chapter-reveal-enter-active,
.chapter-reveal-leave-active {
  transition:
    grid-template-rows 320ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 180ms ease;
}
.chapter-reveal-enter-from,
.chapter-reveal-leave-to { grid-template-rows: 0fr; opacity: 0; }
.chapter-reveal-leave-active { pointer-events: none; }
.chapter-reveal-enter-active .lesson-entry {
  animation: lesson-reveal 180ms ease-out var(--lesson-delay, 0ms) both;
}
@keyframes lesson-reveal {
  from { opacity: 0; transform: translateY(5px); }
  to { opacity: 1; transform: translateY(0); }
}
@media (prefers-reduced-motion: reduce) {
  .chapter-toggle > svg,
  .chapter-reveal-enter-active,
  .chapter-reveal-leave-active { transition: none; }
  .chapter-reveal-enter-active .lesson-entry { animation: none; }
}
</style>
