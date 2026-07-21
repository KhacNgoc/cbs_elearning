<template>
  <main class="viewer-area">
    <div class="viewer-shell">
      <div v-if="selectedDocument" class="selected-document-preview">
        <div class="selected-document-toolbar">
          <div><FileText :size="18" /><span><strong>{{ selectedDocument.name }}</strong><small>{{ selectedDocument.size || 'Tài liệu bài học' }}</small></span></div>
          <button @click="$emit('close-document')"><PlayCircle :size="16" /> Quay lại video</button>
        </div>
        <SlideViewer v-if="selectedDocument.type === 'slides'" :key="selectedDocument.id" :slides="selectedDocument.slides || []" :topic-name="selectedDocument.name" />
        <iframe v-else-if="selectedDocument.type === 'pdf' && selectedDocument.url" :src="selectedDocument.url" :title="selectedDocument.name"></iframe>
        <div v-else class="external-document"><FileText :size="42" /><h3>{{ selectedDocument.name }}</h3><a v-if="selectedDocument.url" :href="selectedDocument.url" target="_blank" rel="noopener">Mở tài liệu</a></div>
      </div>
      <VideoPlayer v-else :video-url="lesson.videoUrl" :title="lesson.title" />
    </div>
    <div class="viewer-navigation">
      <button :disabled="currentIndex <= 0" @click="$emit('adjacent', -1)"><ChevronLeft :size="20" /><span>Bài trước</span></button>
      <div class="viewer-lesson-title"><span>Bài {{ currentIndex + 1 }}</span><strong>{{ lesson.title }}</strong></div>
      <button :disabled="currentIndex >= totalLessons - 1" @click="$emit('adjacent', 1)"><span>Bài tiếp theo</span><ChevronRight :size="20" /></button>
    </div>
  </main>
</template>

<script setup lang="ts">
import { ChevronLeft, ChevronRight, FileText, PlayCircle } from 'lucide-vue-next';
import type { Lesson, LessonDocument } from '../../types';
import SlideViewer from '../SlideViewer.vue';
import VideoPlayer from '../VideoPlayer.vue';
defineProps<{ lesson: Lesson; selectedDocument: LessonDocument | null; currentIndex: number; totalLessons: number }>();
defineEmits<{ adjacent: [offset: number]; 'close-document': [] }>();
</script>
