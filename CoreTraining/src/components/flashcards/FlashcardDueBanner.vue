<template>
  <section v-if="summary && summary.dueTotal > 0" class="flashcard-due-banner" aria-labelledby="flashcard-due-title">
    <span class="banner-icon"><Layers3 :size="24" /></span>
    <div class="banner-copy">
      <span class="eyebrow">ÔN TẬP HÔM NAY</span>
      <h2 id="flashcard-due-title">Bạn có {{ summary.dueTotal }} flashcard cần ôn</h2>
      <div class="course-chips">
        <RouterLink
          v-for="course in summary.courses.slice(0, 4)"
          :key="course.courseId"
          :to="reviewRoute(course.courseId)"
          class="course-chip"
        >
          <span>{{ course.courseName }}</span><strong>{{ course.due }}</strong>
        </RouterLink>
        <span v-if="summary.courses.length > 4" class="more-courses">
          +{{ summary.courses.length - 4 }} khóa khác
        </span>
      </div>
    </div>
    <RouterLink class="btn btn-secondary" :to="reviewRoute(summary.courses[0].courseId)">
      Ôn ngay <ArrowRight :size="17" />
    </RouterLink>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ArrowRight, Layers3 } from "lucide-vue-next";
import { flashcardApi, type FlashcardSummary } from "../../api/flashcardApi";

const summary = ref<FlashcardSummary | null>(null);
const reviewRoute = (courseId: string) => ({ name: "FlashcardReview", query: { courseId } });

onMounted(async () => {
  try {
    summary.value = await flashcardApi.summary();
  } catch {
    // Reminders are optional; the dashboard renders without them.
    summary.value = null;
  }
});
</script>

<style scoped>
.flashcard-due-banner {
  display: flex;
  align-items: center;
  gap: 1.1rem;
  padding: 1.1rem 1.5rem;
  border: 1px solid var(--warning-border);
  border-radius: var(--radius-lg);
  background: var(--warning-surface);
}
.banner-icon {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--surface-ffffff);
  color: var(--warning-ink);
}
.banner-copy {
  flex: 1;
  min-width: 0;
}
.eyebrow {
  font-size: 0.7rem;
  letter-spacing: 1px;
  color: var(--warning-ink);
  font-weight: 700;
}
h2 {
  margin: 0.25rem 0 0.55rem;
  font-size: 1.05rem;
  color: var(--text-color);
}
.course-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.course-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.45rem;
  max-width: 100%;
  padding: 0.25rem 0.35rem 0.25rem 0.65rem;
  border: 1px solid var(--warning-border);
  border-radius: 9999px;
  background: var(--surface-ffffff);
  color: var(--text-color);
  font-size: 0.78rem;
  text-decoration: none;
}
.course-chip span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.course-chip strong {
  min-width: 1.6rem;
  padding: 0.05rem 0.4rem;
  border-radius: 9999px;
  background: var(--warning-ink);
  color: var(--surface-ffffff);
  font-size: 0.72rem;
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.course-chip:hover {
  border-color: var(--warning-ink);
}
.more-courses {
  align-self: center;
  color: var(--text-muted);
  font-size: 0.78rem;
}
.flashcard-due-banner > .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  flex-shrink: 0;
  text-decoration: none;
}
@media (max-width: 760px) {
  .flashcard-due-banner {
    flex-wrap: wrap;
    padding: 1rem;
  }
  .flashcard-due-banner > .btn {
    margin-left: auto;
  }
}
</style>
