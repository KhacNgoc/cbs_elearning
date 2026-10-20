<template>
  <div class="info-layout">
    <div class="info-main">
      <div class="lesson-overview">
        <span class="eyebrow">Tổng quan bài học</span>
        <h2>{{ lesson.title }}</h2>
        <LessonRichText
          v-if="overviewText"
          :content="overviewText"
          class="overview-text"
        />
        <div class="hint-box">
          <Lightbulb :size="22" />
          <div>
            <strong>Gợi ý nghiệp vụ</strong>
            <p>{{ lesson.hint }}</p>
          </div>
        </div>
      </div>
      <section v-if="lesson.procedureSteps?.length" class="procedure-content">
        <article v-for="(section, index) in lesson.procedureSteps" :key="index">
          <h3>{{ section.title }}</h3>
          <ol>
            <li v-for="(step, stepIndex) in section.steps" :key="stepIndex">
              {{ step }}
            </li>
          </ol>
        </article>
      </section>
      <slot></slot>
    </div>
    <aside class="lesson-facts">
      <h3>Thông tin bài học</h3>
      <div>
        <BookOpen :size="18" /><span
          ><small>Khóa học</small><strong>{{ courseName }}</strong></span
        >
      </div>
      <div>
        <Layers3 :size="18" /><span
          ><small>Nội dung</small
          ><strong>Tài liệu, video & quy trình</strong></span
        >
      </div>
      <div>
        <ShieldCheck :size="18" /><span
          ><small>Trạng thái</small
          ><strong>{{ completed ? "Đã hoàn thành" : "Đang học" }}</strong></span
        >
      </div>
      <slot name="actions" />
    </aside>
  </div>
</template>
<script setup lang="ts">
import { computed } from "vue";
import { BookOpen, Layers3, Lightbulb, ShieldCheck } from "lucide-vue-next";
import type { Lesson } from "../../types";
import LessonRichText from "./LessonRichText.vue";
const props = defineProps<{
  lesson: Lesson;
  courseName?: string;
  completed: boolean;
}>();
const overviewText = computed(() => {
  // Text-only lessons already show their content in the main viewer.
  if (!props.lesson.videoUrl?.trim()) return "";
  const lines = (props.lesson.textBody || "").trim().split(/\r?\n/);
  // Seeded content starts with the title, which is already shown above.
  if (lines[0]?.trim() === props.lesson.title.trim()) lines.shift();
  return lines.join("\n").trim();
});
</script>
<style scoped>
.overview-text {
  margin: 0 0 1.25rem;
  padding: 1.15rem 1.25rem;
  border: 1px solid var(--line-dce9e3);
  border-radius: var(--radius-md);
  background: var(--surface-f7faf8);
  color: var(--ink-3f544b);
  font-size: 0.95rem;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
@media (max-width: 600px) {
  .overview-text {
    padding: 1rem;
  }
}
.procedure-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin: 1.25rem 0;
}
.procedure-content article {
  padding: 1.25rem;
  background: var(--surface-ffffff);
  border: 1px solid var(--border-color);
  border-radius: 12px;
}
.procedure-content h3 {
  font-size: 1rem;
  color: var(--primary-ink);
  margin-bottom: 0.75rem;
}
.procedure-content ol {
  padding-left: 1.25rem;
  line-height: 1.7;
}
.procedure-content li {
  padding: 0.2rem 0;
}
</style>
