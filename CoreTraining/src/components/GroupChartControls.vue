<template>
  <!-- The order and the group pick of a group chart (see useGroupChart). -->
  <div class="group-chart-controls">
    <div v-if="sorts.length > 1" class="sort-toggle" role="group" aria-label="Cách xếp biểu đồ">
      <button
        v-for="option in sorts"
        :key="option.id"
        type="button"
        :aria-pressed="sort === option.id"
        :class="{ active: sort === option.id }"
        @click="emit('update:sort', option.id)"
      >{{ option.label }}</button>
    </div>
    <ChartSubjectPicker
      :model-value="picked"
      :options="names"
      :max="GROUP_CHART_LIMIT"
      :noun="noun"
      @update:model-value="emit('update:picked', $event)"
    />
  </div>
</template>

<script setup lang="ts">
import ChartSubjectPicker from './ChartSubjectPicker.vue';
import { GROUP_CHART_LIMIT } from '../utils/reportGroups';

defineProps<{ sorts: { id: string; label: string }[]; sort: string; picked: string[]; names: string[]; noun: string }>();
const emit = defineEmits<{ 'update:sort': [string]; 'update:picked': [string[]] }>();
</script>

<style scoped>
.group-chart-controls { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.sort-toggle { display: flex; background: var(--bg-subtle); border-radius: 6px; padding: 3px; max-width: 100%; }
.sort-toggle button {
  border: 0; border-radius: 4px; padding: 6px 11px; font-size: 11px; color: var(--text-muted);
  background: transparent; white-space: nowrap; cursor: pointer;
}
.sort-toggle button.active { background: var(--bg-card); color: var(--primary-ink); box-shadow: var(--shadow-sm); font-weight: 600; }
@media (max-width: 600px) { .group-chart-controls { justify-content: flex-start; } }
@media print { .group-chart-controls { display: none; } }
</style>
