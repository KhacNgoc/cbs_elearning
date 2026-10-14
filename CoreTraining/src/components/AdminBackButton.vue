<template>
  <RouterLink :to="to" class="admin-back-button" :aria-label="label" :title="label">
    <ArrowLeft :size="21" aria-hidden="true" />
  </RouterLink>
</template>

<script setup lang="ts">
import { ArrowLeft } from 'lucide-vue-next';
defineProps<{ to: string; label: string }>();
</script>

<style scoped>
.admin-back-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-ffffff);
  color: var(--primary-ink);
  transition: background-color .18s ease, border-color .18s ease, box-shadow .18s ease;
  touch-action: manipulation;
}
.admin-back-button:hover {
  background: var(--primary-light);
  border-color: var(--primary-color);
  box-shadow: var(--shadow-sm);
}
.admin-back-button:active { background: var(--surface-d7e9e3); }
.admin-back-button:focus-visible { outline: 3px solid var(--accent-color); outline-offset: 3px; }
@media(prefers-reduced-motion:reduce){.admin-back-button{transition:none}}
</style>
