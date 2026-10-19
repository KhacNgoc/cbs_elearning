<template>
  <main class="viewer-area">
    <nav
      v-if="hasVideo && hasText"
      class="lesson-content-switcher"
      aria-label="Chọn nội dung bài học"
    >
      <button
        type="button"
        :aria-pressed="!selectedDocument && primaryContent === 'VIDEO'"
        @click="$emit('select-content', 'VIDEO')"
      >
        <PlayCircle :size="17" /><span>Video</span>
      </button>
      <button
        type="button"
        :aria-pressed="!selectedDocument && primaryContent === 'TEXT'"
        @click="$emit('select-content', 'TEXT')"
      >
        <FileText :size="17" /><span>Nội dung bài học</span>
      </button>
    </nav>
    <div v-if="selectedDocument" class="lesson-viewer-toolbar">
      <div class="viewer-content-title">
        <FileText :size="18" />
        <span
          ><strong :title="selectedDocument.name">{{
            selectedDocument.name
          }}</strong
          ><small
            >{{ selectedDocument.documentType }} · Tài liệu bài học</small
          ></span
        >
      </div>
      <div class="viewer-toolbar-actions">
        <button
          v-if="(hasText || hasVideo) && !(hasVideo && hasText)"
          type="button"
          class="viewer-back"
          @click="$emit('select-content', hasText ? 'TEXT' : 'VIDEO')"
        >
          <ArrowLeft :size="16" /><span>{{
            hasText ? "Quay lại nội dung" : "Quay lại video"
          }}</span>
        </button>
        <button
          type="button"
          class="viewer-download"
          @click="$emit('download-document', selectedDocument)"
        >
          <Download :size="16" /><span>Tải xuống</span>
        </button>
      </div>
    </div>
    <div class="viewer-shell">
      <div v-if="selectedDocument" class="selected-document-preview">
        <PdfDocumentPreview
          v-if="selectedDocument.documentType === 'PDF'"
          :key="selectedDocument.id || selectedDocument.fileUrl"
          :url="resolveDocumentUrl(selectedDocument.fileUrl)"
          :initial-page="initialDocumentPage"
          @position-change="$emit('document-position', $event)"
        />
        <PowerPointDocumentPreview
          v-else-if="selectedDocument.documentType === 'POWERPOINT'"
          :key="selectedDocument.id || selectedDocument.fileUrl"
          :url="resolveDocumentUrl(selectedDocument.fileUrl)"
          :original-file-name="selectedDocument.originalFileName"
          :initial-page="initialDocumentPage"
          :initial-scroll-offset="initialScrollOffset"
          @position-change="$emit('document-position', $event)"
        />
        <WordDocumentPreview
          v-else
          :key="selectedDocument.id || selectedDocument.fileUrl"
          :url="resolveDocumentUrl(selectedDocument.fileUrl)"
          :original-file-name="selectedDocument.originalFileName"
          :initial-page="initialDocumentPage"
          :initial-scroll-offset="initialScrollOffset"
          @position-change="$emit('document-position', $event)"
        />
      </div>
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
        @add-note="$emit('add-note', $event)"
      />
      <article v-else-if="lesson.textBody" class="lesson-text-viewer">
        <h2>{{ lesson.title }}</h2>
        <LessonRichText :content="lesson.textBody" />
      </article>
      <div v-else class="empty-lesson-media">
        <FileText :size="34" />
        <strong>Bài học chưa có nội dung</strong>
        <span>Video hoặc tài liệu sẽ được cập nhật sau.</span>
      </div>
    </div>
    <div class="viewer-navigation">
      <button :disabled="currentIndex <= 0" @click="$emit('adjacent', -1)">
        <ChevronLeft :size="20" /><span>Bài trước</span>
      </button>
      <div class="viewer-lesson-title">
        <span>Bài {{ currentIndex + 1 }}</span
        ><strong>{{ lesson.title }}</strong>
      </div>
      <button
        :disabled="currentIndex >= totalLessons - 1"
        @click="$emit('adjacent', 1)"
      >
        <span>Bài tiếp theo</span><ChevronRight :size="20" />
      </button>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, ref } from "vue";
import {
  ArrowLeft,
  ChevronLeft,
  ChevronRight,
  Download,
  FileText,
  PlayCircle,
} from "lucide-vue-next";
import type {
  Lesson,
  LessonDocument,
  LessonProgressRequest,
} from "../../types";
import VideoPlayer from "../VideoPlayer.vue";
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
const hasText = computed(() => Boolean(props.lesson.textBody?.trim()));
defineEmits<{
  adjacent: [offset: number];
  "select-content": [type: "VIDEO" | "TEXT"];
  "download-document": [document: LessonDocument];
  "time-update": [seconds: number];
  "progress-update": [progress: LessonProgressRequest];
  "document-position": [position: { page: number; scrollOffset: number }];
  "video-ended": [progress: LessonProgressRequest];
  "add-note": [seconds: number];
}>();
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
.lesson-content-switcher {
  display: flex;
  gap: 0.4rem;
  padding: 0.6rem 0.9rem;
  flex-shrink: 0;
  background: #162c24;
  border-bottom: 1px solid #344d42;
}
.lesson-content-switcher button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  min-height: 40px;
  padding: 0.5rem 0.9rem;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #c4d5cc;
  font-size: 0.85rem;
  font-weight: 600;
  cursor: pointer;
}
.lesson-content-switcher button:hover {
  background: #253f33;
  color: #fff;
}
.lesson-content-switcher button[aria-pressed="true"] {
  background: #005b41;
  border-color: #629b83;
  color: #fff;
}
.lesson-content-switcher button:focus-visible {
  outline: 2px solid #e4c760;
  outline-offset: 2px;
}
@media (max-width: 600px) {
  .lesson-content-switcher {
    padding: 0.5rem 0.75rem;
  }
  .lesson-content-switcher button {
    flex: 1;
    min-height: 44px;
    padding: 0.5rem;
    font-size: 0.8rem;
  }
}
.lesson-viewer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  min-height: 54px;
  padding: 0.55rem 0.9rem;
  background: #1d211f;
  color: #fff;
  border-bottom: 1px solid #343b38;
  flex-shrink: 0;
}
.viewer-content-title {
  display: flex;
  align-items: center;
  gap: 0.55rem;
  min-width: 0;
  flex: 1;
}
.viewer-content-title > svg {
  flex-shrink: 0;
  color: #e4c760;
}
.viewer-content-title > span {
  display: grid;
  min-width: 0;
  gap: 0.15rem;
}
.viewer-content-title strong {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 0.85rem;
}
.viewer-content-title small {
  font-size: 0.68rem;
  color: #b4c4bc;
}
.viewer-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-shrink: 0;
  min-width: 0;
}
.viewer-back,
.viewer-download {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  min-height: 36px;
  padding: 0.45rem 0.75rem;
  border: 1px solid var(--primary-color);
  border-radius: 6px;
  background: var(--primary-color);
  color: #fff;
  white-space: nowrap;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}
.viewer-back {
  background: transparent;
  border-color: #61756a;
  color: #e5f0e9;
}
.viewer-back:hover {
  background: #294337;
  border-color: #9ec6b1;
}
.viewer-download:hover {
  background: var(--primary-hover);
}
.viewer-back:focus-visible,
.viewer-download:focus-visible {
  outline: 2px solid #8ae2c2;
  outline-offset: 2px;
}
@media (max-width: 600px) {
  .lesson-viewer-toolbar {
    flex-wrap: wrap;
    gap: 0.6rem;
    padding: 0.65rem 0.75rem;
  }
  .viewer-content-title {
    flex-basis: 100%;
  }
  .viewer-toolbar-actions {
    width: 100%;
  }
  .viewer-back,
  .viewer-download {
    min-height: 40px;
    flex: 1;
  }
}
.lesson-text-viewer {
  min-height: 420px;
  padding: 2rem;
  background: var(--surface-ffffff);
  color: var(--text-main);
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
