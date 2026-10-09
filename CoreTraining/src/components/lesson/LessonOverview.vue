<template>
  <div class="info-layout">
    <div class="info-main">
      <div class="lesson-overview">
        <span class="eyebrow">Tổng quan bài học</span><h2>{{ lesson.title }}</h2>
        <div class="hint-box"><Lightbulb :size="22" /><div><strong>Gợi ý nghiệp vụ</strong><p>{{ lesson.hint }}</p></div></div>
      </div>
      <section v-if="lesson.procedureSteps?.length" class="procedure-content">
        <article v-for="(section,index) in lesson.procedureSteps" :key="index">
          <h3>{{section.title}}</h3><ol><li v-for="(step,stepIndex) in section.steps" :key="stepIndex">{{step}}</li></ol>
        </article>
      </section>
      <slot></slot>
    </div>
    <aside class="lesson-facts">
      <h3>Thông tin bài học</h3>
      <div><BookOpen :size="18" /><span><small>Khóa học</small><strong>{{ courseName }}</strong></span></div>
      <div><Layers3 :size="18" /><span><small>Nội dung</small><strong>Tài liệu, video & quy trình</strong></span></div>
      <div><ShieldCheck :size="18" /><span><small>Trạng thái</small><strong>{{ completed ? 'Đã hoàn thành' : 'Đang học' }}</strong></span></div>
    </aside>
  </div>
</template>
<script setup lang="ts">
import { BookOpen, Layers3, Lightbulb, ShieldCheck } from 'lucide-vue-next';
import type { Lesson } from '../../types';
defineProps<{ lesson: Lesson; courseName?: string; completed: boolean }>();
</script>
<style scoped>
.procedure-content{display:flex;flex-direction:column;gap:1rem;margin:1.25rem 0}.procedure-content article{padding:1.25rem;background:white;border:1px solid var(--border-color);border-radius:12px}.procedure-content h3{font-size:1rem;color:var(--primary-color);margin-bottom:.75rem}.procedure-content ol{padding-left:1.25rem;line-height:1.7}.procedure-content li{padding:.2rem 0}
</style>
