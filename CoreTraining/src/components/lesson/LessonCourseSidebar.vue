<template>
  <aside class="course-sidebar" :class="{ open }">
    <div class="sidebar-header">
      <div>
        <h2>Nội dung chương</h2>
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
      <div
        v-for="(item, index) in course?.lessons"
        :key="item.id"
        class="lesson-entry"
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
        <button
          v-if="getDocuments(item).length"
          class="lesson-resources-toggle"
          :class="{ expanded: expandedLessons.has(item.id) }"
          @click="toggleDocuments(item.id)"
        >
          <FolderOpen :size="14" /><span>Tài liệu</span
          ><span class="resource-count">{{ getDocuments(item).length }}</span
          ><ChevronDown :size="14" />
        </button>
        <div v-if="expandedLessons.has(item.id)" class="lesson-documents">
          <button
            v-for="document in getDocuments(item)"
            :key="document.id || document.fileUrl"
            :class="{ selected: selectedDocumentId === document.id }"
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
        </div>
      </div>
    </div>
    <div v-if="activeLesson?.hasQuiz" class="quiz-box">
      <div>
        <Award :size="22" /><strong>{{
          activeLessonPassed ? "Bài kiểm tra đã hoàn thành" : "Kiểm tra bài học"
        }}</strong>
      </div>
      <p v-if="activeLessonPassed">
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
import { computed, ref } from "vue";
import {
  ArrowRight,
  Award,
  Check,
  ChevronDown,
  FileText,
  FolderOpen,
  PlayCircle,
  Trophy,
  X,
} from "lucide-vue-next";
import type { Lesson, LessonDocument, Course } from "../../types";
const props = defineProps<{
  course: Course | null | undefined;
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
}>();
const emit = defineEmits<{
  close: [];
  select: [lessonId: string];
  "toggle-completion": [lessonId: string];
  "select-document": [lessonId: string, document: LessonDocument];
  quiz: [];
  "course-quiz": [];
}>();
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
  return lesson.documents || [];
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
