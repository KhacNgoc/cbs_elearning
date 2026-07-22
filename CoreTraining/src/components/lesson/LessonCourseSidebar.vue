<template>
  <aside class="course-sidebar" :class="{ open }">
    <div class="sidebar-header">
      <div><h2>Nội dung khóa học</h2><p>{{ completedCount }}/{{ topic?.lessons.length || 0 }} bài đã hoàn thành</p></div>
      <button class="sidebar-close" aria-label="Đóng nội dung khóa học" @click="$emit('close')"><X :size="20" /></button>
    </div>
    <div class="sidebar-progress"><span :style="{ width: `${progress}%` }"></span></div>
    <div class="lesson-list">
      <div v-for="(item, index) in topic?.lessons" :key="item.id" class="lesson-entry"
        :class="{ active: item.id === activeLessonId, completed: completedLessons.includes(item.id) }">
        <button class="lesson-row" @click="openLesson(item.id)">
          <span class="lesson-check"><Check v-if="completedLessons.includes(item.id)" :size="14" /></span>
          <span class="lesson-row-copy"><strong>{{ index + 1 }}. {{ item.title }}</strong><small><PlayCircle :size="13" /> Bài học</small></span>
        </button>
        <button v-if="getDocuments(item).length" class="lesson-resources-toggle" :class="{ expanded: expandedLessons.has(item.id) }"
          @click="toggleDocuments(item.id)">
          <FolderOpen :size="14" /><span>Tài liệu</span><span class="resource-count">{{ getDocuments(item).length }}</span><ChevronDown :size="14" />
        </button>
        <div v-if="expandedLessons.has(item.id)" class="lesson-documents">
          <button v-for="document in getDocuments(item)" :key="document.id || document.fileUrl" :class="{ selected: selectedDocumentId === document.id }"
            @click="$emit('select-document', item.id, document)">
            <FileText :size="15" /><span><strong>{{ document.name }}</strong><small>{{ document.documentType }} · {{ formatFileSize(document.fileSize) }}</small></span>
          </button>
        </div>
      </div>
    </div>
    <div class="quiz-box">
      <div><Award :size="22" /><strong>Đánh giá năng lực</strong></div>
      <p>Kiểm tra và củng cố kiến thức sau khi hoàn thành chủ đề.</p>
      <button @click="$emit('quiz')">Làm bài kiểm tra <ArrowRight :size="16" /></button>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { ArrowRight, Award, Check, ChevronDown, FileText, FolderOpen, PlayCircle, X } from 'lucide-vue-next';
import type { Lesson, LessonDocument, Topic } from '../../types';
defineProps<{ topic: Topic | null | undefined; activeLessonId: string; completedLessons: string[]; completedCount: number; progress: number; open: boolean; selectedDocumentId?: number }>();
const emit = defineEmits<{ close: []; select: [lessonId: string]; 'select-document': [lessonId: string, document: LessonDocument]; quiz: [] }>();
const expandedLessons = ref(new Set<string>());
const getDocuments = (lesson: Lesson): LessonDocument[] => {
  return lesson.documents || [];
};
const formatFileSize = (size?: number) => size ? `${(size / 1024 / 1024).toFixed(1)} MB` : 'Tệp đính kèm';
const toggleDocuments = (lessonId: string) => {
  const next = new Set(expandedLessons.value);
  next.has(lessonId) ? next.delete(lessonId) : next.add(lessonId);
  expandedLessons.value = next;
};
const openLesson = (lessonId: string) => { emit('select', lessonId); };
</script>
