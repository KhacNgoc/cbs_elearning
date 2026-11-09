<template>
  <div class="lesson-upload-progress" role="status">
    <progress :value="percent" max="100"></progress>
    <small>{{
      percent < 100
        ? `Đang tải tệp lên: ${percent}%`
        : "Đã gửi tệp, đang xử lý…"
    }}</small>
  </div>
</template>

<script setup lang="ts">
defineProps<{ percent: number }>();
</script>

<style scoped>
.lesson-upload-progress {
  display: grid;
  gap: 0.25rem;
  margin-top: 0.5rem;
  scroll-margin-top: 6rem;
}

.lesson-upload-progress progress {
  width: 100%;
}

.lesson-upload-progress small {
  color: var(--text-muted);
}
</style>
