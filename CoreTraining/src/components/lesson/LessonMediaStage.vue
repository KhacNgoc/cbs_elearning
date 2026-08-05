<template>
  <main class="viewer-area">
    <div class="viewer-shell">
      <div v-if="selectedDocument" class="selected-document-preview">
        <div class="selected-document-toolbar">
          <div><FileText :size="18" /><span><strong>{{ selectedDocument.name }}</strong><small>{{ selectedDocument.documentType }} · Tài liệu bài học</small></span></div>
          <div class="selected-document-actions">
            <button class="download-document-button" @click="$emit('download-document', selectedDocument)"><Download :size="16" /> Tải xuống</button>
            <button v-if="hasVideo" @click="$emit('close-document')"><PlayCircle :size="16" /> Quay lại video</button>
          </div>
        </div>
        <iframe v-if="selectedDocument.documentType === 'PDF'" :src="resolveDocumentUrl(selectedDocument.fileUrl)" :title="selectedDocument.name"></iframe>
        <PowerPointDocumentPreview v-else-if="selectedDocument.documentType === 'POWERPOINT'" :url="resolveDocumentUrl(selectedDocument.fileUrl)" :original-file-name="selectedDocument.originalFileName" />
        <WordDocumentPreview v-else :url="resolveDocumentUrl(selectedDocument.fileUrl)" :original-file-name="selectedDocument.originalFileName" />
      </div>
      <VideoPlayer v-else-if="hasVideo" ref="videoPlayer" :lesson-id="lesson.id" :video-url="lesson.videoUrl" :title="lesson.title"
        :initial-position-seconds="initialPositionSeconds" :initial-watched-seconds="initialWatchedSeconds"
        @time-update="$emit('time-update', $event)" @progress-update="$emit('progress-update', $event)"
        @ended="$emit('video-ended', $event)" @add-note="$emit('add-note', $event)" />
      <div v-else class="empty-lesson-media">
        <FileText :size="34" />
        <strong>Bài học chưa có nội dung</strong>
        <span>Video hoặc tài liệu sẽ được cập nhật sau.</span>
      </div>
    </div>
    <div class="viewer-navigation">
      <button :disabled="currentIndex <= 0" @click="$emit('adjacent', -1)"><ChevronLeft :size="20" /><span>Bài trước</span></button>
      <div class="viewer-lesson-title"><span>Bài {{ currentIndex + 1 }}</span><strong>{{ lesson.title }}</strong></div>
      <button :disabled="currentIndex >= totalLessons - 1" @click="$emit('adjacent', 1)"><span>Bài tiếp theo</span><ChevronRight :size="20" /></button>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { ChevronLeft, ChevronRight, Download, FileText, PlayCircle } from 'lucide-vue-next';
import type { Lesson, LessonDocument, LessonProgressRequest } from '../../types';
import VideoPlayer from '../VideoPlayer.vue';
import PowerPointDocumentPreview from './PowerPointDocumentPreview.vue';
import WordDocumentPreview from './WordDocumentPreview.vue';
const videoPlayer = ref<InstanceType<typeof VideoPlayer> | null>(null);
const props = defineProps<{ lesson: Lesson; selectedDocument: LessonDocument | null; currentIndex: number; totalLessons: number; initialPositionSeconds?: number; initialWatchedSeconds?: number }>();
const hasVideo = computed(() => Boolean(props.lesson.videoUrl?.trim()));
defineEmits<{
  adjacent: [offset: number];
  'close-document': [];
  'download-document': [document: LessonDocument];
  'time-update': [seconds: number];
  'progress-update': [progress: LessonProgressRequest];
  'video-ended': [progress: LessonProgressRequest];
  'add-note': [seconds: number];
}>();
defineExpose({
  seekTo: (seconds: number) => videoPlayer.value?.seekTo(seconds),
  resetProgress: () => videoPlayer.value?.resetProgress()
});
const resolveDocumentUrl = (url: string) => {
  if (/^https?:\/\//i.test(url) || url.startsWith('blob:')) return url;
  return `${import.meta.env.VITE_API_URL ?? 'http://localhost:8080'}${url.startsWith('/') ? url : `/${url}`}`;
};
</script>

<style scoped>
.empty-lesson-media {
  min-height: 420px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: #f8fafc;
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
</style>
