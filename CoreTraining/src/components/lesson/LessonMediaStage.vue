<template>
  <main class="viewer-area">
    <div class="viewer-shell">
      <div v-if="selectedDocument" class="selected-document-preview">
        <div class="selected-document-toolbar">
          <div><FileText :size="18" /><span><strong>{{ selectedDocument.name }}</strong><small>{{ selectedDocument.documentType }} · Tài liệu bài học</small></span></div>
          <div class="selected-document-actions">
            <button class="download-document-button" @click="$emit('download-document', selectedDocument)"><Download :size="16" /> Tải xuống</button>
            <button @click="$emit('close-document')"><PlayCircle :size="16" /> Quay lại video</button>
          </div>
        </div>
        <iframe v-if="selectedDocument.documentType === 'PDF'" :src="resolveDocumentUrl(selectedDocument.fileUrl)" :title="selectedDocument.name"></iframe>
        <PowerPointDocumentPreview v-else-if="selectedDocument.documentType === 'POWERPOINT'" :url="resolveDocumentUrl(selectedDocument.fileUrl)" :original-file-name="selectedDocument.originalFileName" />
        <WordDocumentPreview v-else :url="resolveDocumentUrl(selectedDocument.fileUrl)" :original-file-name="selectedDocument.originalFileName" />
      </div>
      <VideoPlayer ref="videoPlayer" v-else :video-url="lesson.videoUrl" :title="lesson.title"
        @time-update="$emit('time-update', $event)" @add-note="$emit('add-note', $event)" />
    </div>
    <div class="viewer-navigation">
      <button :disabled="currentIndex <= 0" @click="$emit('adjacent', -1)"><ChevronLeft :size="20" /><span>Bài trước</span></button>
      <div class="viewer-lesson-title"><span>Bài {{ currentIndex + 1 }}</span><strong>{{ lesson.title }}</strong></div>
      <button :disabled="currentIndex >= totalLessons - 1" @click="$emit('adjacent', 1)"><span>Bài tiếp theo</span><ChevronRight :size="20" /></button>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ChevronLeft, ChevronRight, Download, FileText, PlayCircle } from 'lucide-vue-next';
import type { Lesson, LessonDocument } from '../../types';
import VideoPlayer from '../VideoPlayer.vue';
import PowerPointDocumentPreview from './PowerPointDocumentPreview.vue';
import WordDocumentPreview from './WordDocumentPreview.vue';
const videoPlayer = ref<InstanceType<typeof VideoPlayer> | null>(null);
defineProps<{ lesson: Lesson; selectedDocument: LessonDocument | null; currentIndex: number; totalLessons: number }>();
defineEmits<{
  adjacent: [offset: number];
  'close-document': [];
  'download-document': [document: LessonDocument];
  'time-update': [seconds: number];
  'add-note': [seconds: number];
}>();
defineExpose({ seekTo: (seconds: number) => videoPlayer.value?.seekTo(seconds) });
const resolveDocumentUrl = (url: string) => {
  if (/^https?:\/\//i.test(url) || url.startsWith('blob:')) return url;
  return `${import.meta.env.VITE_API_URL || 'http://localhost:8080'}${url.startsWith('/') ? url : `/${url}`}`;
};
</script>
