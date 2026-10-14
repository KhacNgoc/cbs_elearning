<template>
  <footer class="table-pagination" :class="{ compact }" :aria-label="`Phân trang ${unit}`">
    <span class="page-summary">{{ total }} {{ unit }} · Trang {{ page }} / {{ pageCount }}</span>
    <div class="page-controls">
      <label class="page-size">
        <span>{{ compact ? 'Hiển thị' : `${unitLabel} / trang` }}</span>
        <select :value="pageSize" :aria-label="`Số ${unit} hiển thị mỗi trang`" @change="changeSize">
          <option v-for="size in sizes" :key="size" :value="size">{{ size }}</option>
        </select>
      </label>
      <div class="page-navigation">
        <button type="button" :disabled="page <= 1" aria-label="Trang trước" title="Trang trước" @click="$emit('update:page', page - 1)"><ChevronLeft :size="18" /></button>
        <button type="button" :disabled="page >= pageCount" aria-label="Trang sau" title="Trang sau" @click="$emit('update:page', page + 1)"><ChevronRight :size="18" /></button>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next';
const props = withDefaults(defineProps<{ total: number; page: number; pageCount: number; pageSize: number; unit?: string; unitLabel?: string; compact?: boolean; sizes?: number[] }>(), {
  unit: 'bản ghi', unitLabel: 'Bản ghi', compact: false, sizes: () => [10,20,50,100],
});
const emit = defineEmits<{ 'update:page': [page: number]; 'update:pageSize': [size: number] }>();
function changeSize(event: Event) {
  const size = Number((event.target as HTMLSelectElement).value);
  if (!props.sizes.includes(size)) return;
  emit('update:page', 1);
  emit('update:pageSize', size);
}
</script>

<style scoped>
.table-pagination { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; flex-shrink: 0; gap: .75rem; margin-top: auto; padding-top: 1rem; border-top: 1px solid var(--border-color); min-height: 57px; color: var(--text-muted); font-size: .8rem; }
.page-summary { min-width: 0; overflow-wrap: anywhere; }
.page-controls { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: .75rem; min-width: 0; margin-left: auto; }
.page-size { display: flex; align-items: center; gap: .5rem; white-space: nowrap; }
.page-size select { min-height: 36px; padding: .35rem .55rem; border: 1px solid var(--border-color); border-radius: 6px; background: white; color: var(--text-main); font: inherit; cursor: pointer; }
.page-navigation { display: flex; gap: .5rem; flex-shrink: 0; }
.page-navigation button { display: grid; place-items: center; width: 36px; height: 36px; padding: 0; border: 1px solid var(--border-color); border-radius: 6px; background: white; color: var(--primary-color); cursor: pointer; }
.page-navigation button:hover:not(:disabled) { background: var(--primary-light); border-color: var(--primary-color); }
.page-navigation button:disabled { opacity: .45; cursor: not-allowed; }
.compact .page-summary { flex-basis: 100%; }
.compact .page-controls { width: 100%; margin-left: 0; justify-content: space-between; gap: .5rem; }
</style>
