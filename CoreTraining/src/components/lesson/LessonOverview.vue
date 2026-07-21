<template>
  <div class="info-layout">
    <div class="info-main">
      <div class="lesson-overview">
        <span class="eyebrow">Tổng quan bài học</span><h2>{{ lesson.title }}</h2>
        <div class="hint-box"><Lightbulb :size="22" /><div><strong>Gợi ý nghiệp vụ</strong><p>{{ lesson.hint }}</p></div></div>
      </div>
      <slot></slot>
    </div>
    <aside class="lesson-facts">
      <h3>Thông tin bài học</h3>
      <div><BookOpen :size="18" /><span><small>Chủ đề</small><strong>{{ topicName }}</strong></span></div>
      <div><Layers3 :size="18" /><span><small>Nội dung</small><strong>Tài liệu, video & quy trình</strong></span></div>
      <div><ShieldCheck :size="18" /><span><small>Trạng thái</small><strong>{{ completed ? 'Đã hoàn thành' : 'Đang học' }}</strong></span></div>
    </aside>
  </div>
</template>
<script setup lang="ts">
import { BookOpen, Layers3, Lightbulb, ShieldCheck } from 'lucide-vue-next';
import type { Lesson } from '../../types';
defineProps<{ lesson: Lesson; topicName?: string; completed: boolean }>();
</script>
