<template>
  <main class="viewer-area">
    <div class="viewer-shell">
      <DocumentFullscreen v-if="selectedDocument">
        <PdfDocumentPreview
          v-if="selectedDocument.documentType === 'PDF'"
          :key="`pdf:${selectedDocument.id ?? selectedDocument.fileUrl}`"
          :url="resolveDocumentUrl(selectedDocument.fileUrl)"
          :initial-page="initialDocumentPage"
          @position-change="$emit('document-position', $event)"
        />
        <PowerPointDocumentPreview
          v-else-if="selectedDocument.documentType === 'POWERPOINT'"
          :key="`powerpoint:${selectedDocument.id ?? selectedDocument.fileUrl}`"
          :url="resolveDocumentUrl(selectedDocument.fileUrl)"
          :original-file-name="selectedDocument.originalFileName"
          :initial-page="initialDocumentPage"
          :initial-scroll-offset="initialScrollOffset"
          @position-change="$emit('document-position', $event)"
        />
        <WordDocumentPreview
          v-else
          :key="`word:${selectedDocument.id ?? selectedDocument.fileUrl}`"
          :url="resolveDocumentUrl(selectedDocument.fileUrl)"
          :original-file-name="selectedDocument.originalFileName"
          :initial-page="initialDocumentPage"
          :initial-scroll-offset="initialScrollOffset"
          @position-change="$emit('document-position', $event)"
        />
      </DocumentFullscreen>
      <VideoPlayer
        v-else-if="hasVideo && primaryContent === 'VIDEO'"
        ref="videoPlayer"
        :lesson-id="lesson.id"
        :video-url="lesson.videoUrl"
        :title="lesson.title"
        :initial-position-seconds="initialPositionSeconds"
        :initial-watched-seconds="initialWatchedSeconds"
        @time-update="$emit('time-update', $event)"
        @progress-update="$emit('progress-update', $event)"
        @ended="$emit('video-ended', $event)"
        @pause="$emit('pause')"
        @add-note="$emit('add-note', $event)"
      />
      <article
        v-else-if="lesson.textBody"
        class="lesson-text-viewer"
        @scroll="checkTextEnd"
        ref="textReader"
      >
        <h2>{{ lesson.title }}</h2>
        <LessonRichText :content="lesson.textBody" />
      </article>
      <div v-else class="empty-lesson-media">
        <FileText :size="34" />
        <strong>Bài học chưa có nội dung</strong>
        <span>Nội dung văn bản, video hoặc Slide chưa được cập nhật. Vui lòng liên hệ quản trị viên.</span>
      </div>
    </div>
    <div class="viewer-navigation">
      <button aria-label="Bài trước" :disabled="currentIndex <= 0" @click="$emit('adjacent', -1)">
        <ChevronLeft :size="20" /><span>Bài trước</span>
      </button>
      <div class="viewer-lesson-title">
        <span>Bài {{ currentIndex + 1 }}</span
        ><strong>{{ lesson.title }}</strong>
      </div>
      <button aria-label="Bài tiếp theo"
        :disabled="currentIndex >= totalLessons - 1"
        @click="$emit('adjacent', 1)"
      >
        <span>Bài tiếp theo</span><ChevronRight :size="20" />
      </button>
    </div>
  </main>
</template>

<script setup lang="ts">
import {
  computed,
  defineAsyncComponent,
  ref,
  watch,
  nextTick,
  onMounted,
  onBeforeUnmount,
} from "vue";
import { ChevronLeft, ChevronRight, FileText } from "lucide-vue-next";
import type {
  Lesson,
  LessonDocument,
  LessonProgressRequest,
} from "../../types";
import VideoPlayer from "../VideoPlayer.vue";
import DocumentFullscreen from "./DocumentFullscreen.vue";
import LessonRichText from "./LessonRichText.vue";
import PowerPointDocumentPreview from "./PowerPointDocumentPreview.vue";
import WordDocumentPreview from "./WordDocumentPreview.vue";
const PdfDocumentPreview = defineAsyncComponent(
  () => import("./PdfDocumentPreview.vue"),
);
const videoPlayer = ref<InstanceType<typeof VideoPlayer> | null>(null);
const props = defineProps<{
  lesson: Lesson;
  primaryContent: "VIDEO" | "TEXT";
  selectedDocument: LessonDocument | null;
  currentIndex: number;
  totalLessons: number;
  initialPositionSeconds?: number;
  initialWatchedSeconds?: number;
  initialDocumentPage?: number;
  initialScrollOffset?: number;
}>();
const hasVideo = computed(() => Boolean(props.lesson.videoUrl?.trim()));
const emit = defineEmits<{
  "text-completed": [];
  pause: [];
  adjacent: [offset: number];
  "time-update": [seconds: number];
  "progress-update": [progress: LessonProgressRequest];
  "document-position": [
    position: { page: number; scrollOffset: number; completed?: boolean },
  ];
  "video-ended": [progress: LessonProgressRequest];
  "add-note": [seconds: number];
}>();
const textReader = ref<HTMLElement | null>(null);
let textReported = false;
function checkTextEnd() {
  const el = textReader.value;
  if (
    !textReported &&
    el &&
    el.clientHeight > 0 &&
    document.visibilityState !== "hidden" &&
    el.scrollTop + el.clientHeight >= el.scrollHeight - 4
  ) {
    textReported = true;
    emit("text-completed");
  }
}
watch(
  () => [props.lesson.id, props.primaryContent, props.selectedDocument],
  async () => {
    textReported = false;
    await nextTick();
    checkTextEnd();
  },
  { immediate: true },
);
onMounted(() => {
  document.addEventListener("visibilitychange", checkTextEnd);
  window.addEventListener("resize", checkTextEnd);
});
onBeforeUnmount(() => {
  document.removeEventListener("visibilitychange", checkTextEnd);
  window.removeEventListener("resize", checkTextEnd);
});
defineExpose({
  seekTo: (seconds: number) => videoPlayer.value?.seekTo(seconds),
  resetProgress: () => videoPlayer.value?.resetProgress(),
});
const resolveDocumentUrl = (url: string) => {
  if (/^https?:\/\//i.test(url) || url.startsWith("blob:")) return url;
  return `${import.meta.env.VITE_API_URL ?? "http://localhost:8080"}${url.startsWith("/") ? url : `/${url}`}`;
};
</script>

<style scoped>
.lesson-text-viewer {
  min-height: 420px;
  padding: 2rem;
  background: var(--surface-ffffff);
  color: var(--text-main);
  -webkit-user-select: none;
  user-select: none;
}
.lesson-text-viewer h2 {
  font-size: 1.3rem;
  margin-bottom: 1rem;
}
.lesson-text-viewer p {
  white-space: pre-wrap;
  line-height: 1.8;
}
.empty-lesson-media {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: var(--surface-f8fafc);
  color: var(--text-muted);
  text-align: center;
}

.empty-lesson-media strong {
  color: var(--text-main);
  font-size: 1rem;
}

.empty-lesson-media span {
  font-size: 0.85rem;
}
.lesson-text-viewer {
  height: 100%;
  min-height: 0;
  overflow: auto;
  overflow-wrap: anywhere;
}
.empty-lesson-media {
  height: 100%;
  min-height: 0;
  padding: 1rem;
}
@media (max-width: 600px) {
  .lesson-text-viewer {
    padding: 1rem;
  }
  .lesson-text-viewer h2 {
    font-size: 1.1rem;
  }
}
</style>
