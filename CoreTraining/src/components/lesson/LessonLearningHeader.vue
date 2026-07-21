<template>
  <header class="learning-header">
    <router-link to="/" class="back-button" aria-label="Quay lại trang chủ"><ArrowLeft :size="20" /></router-link>
    <div class="header-copy">
      <span class="course-name">{{ topicName }}</span><span class="header-separator"></span><h1>{{ lessonTitle }}</h1>
    </div>
    <div class="header-actions">
      <div class="progress-summary"><Trophy :size="17" /><span>{{ completedCount }}/{{ totalLessons }} bài hoàn thành</span></div>
      <button class="outline-action" :disabled="completed" @click="$emit('complete')">
        <CheckCircle2 :size="17" /><span>{{ completed ? 'Đã hoàn thành' : 'Đánh dấu hoàn thành' }}</span>
      </button>
      <button class="mobile-content-button" @click="$emit('open-content')"><ListVideo :size="20" /><span>Nội dung</span></button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ArrowLeft, CheckCircle2, ListVideo, Trophy } from 'lucide-vue-next';
defineProps<{ topicName?: string; lessonTitle: string; completedCount: number; totalLessons: number; completed: boolean }>();
defineEmits<{ complete: []; 'open-content': [] }>();
</script>
