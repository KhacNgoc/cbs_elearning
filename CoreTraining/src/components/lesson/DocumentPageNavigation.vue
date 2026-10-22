<template>
  <nav class="document-pagination" aria-label="Chuyển trang tài liệu">
    <button type="button" class="page-arrow previous" aria-label="Trang trước"
      :disabled="disabled || page <= 1" @click="$emit('go', page - 1)">
      <ChevronLeft :size="28" :stroke-width="2.5" />
    </button>
    <button type="button" class="page-arrow next" aria-label="Trang sau"
      :disabled="disabled || page >= total" @click="$emit('go', page + 1)">
      <ChevronRight :size="28" :stroke-width="2.5" />
    </button>
    <label class="page-counter">
      Trang <input aria-label="Số trang" type="number" min="1" :max="total || 1"
        :value="page" :disabled="disabled || !total"
        @change="changePage($event)" /> / {{ total || '—' }}
    </label>
  </nav>
</template>
<script setup lang="ts">
import { ChevronLeft, ChevronRight } from 'lucide-vue-next';
const props = defineProps<{ page: number; total: number; disabled?: boolean }>();
const emit = defineEmits<{ go: [page: number] }>();
function changePage(event: Event) {
  const input = event.target as HTMLInputElement;
  const value = Number(input.value);
  if (input.value && Number.isFinite(value))
    emit('go', Math.min(Math.max(1, Math.floor(value)), props.total));
  input.value = String(props.page);
}
</script>
<style scoped>
.document-pagination { position: absolute; inset: 0; pointer-events: none; z-index: 2; }
.page-arrow {
  position: absolute; top: 50%; transform: translateY(-50%);
  display: grid; place-items: center; width: 42px; height: 84px;
  padding: 0; border: 0; background: #08785c; color: #fff;
  cursor: pointer; pointer-events: auto; box-shadow: 0 2px 12px #0003;
}
.previous { left: 0; border-radius: 0 14px 14px 0; }
.next { right: 0; border-radius: 14px 0 0 14px; }
.page-arrow:hover:not(:disabled) { background: #09654e; }
.page-arrow:disabled { opacity: .28; cursor: default; }
.page-counter {
  position: absolute; bottom: 8px; left: 8px;
  display: flex; align-items: center; gap: 6px; padding: 4px 10px;
  border-radius: 6px; background: var(--bg-card); color: var(--text-main);
  font-size: .8rem; white-space: nowrap; pointer-events: auto;
}
.page-counter input {
  width: 55px; padding: 3px; border: 1px solid var(--border-color);
  border-radius: 4px; background: var(--bg-main); color: var(--text-main); text-align: center;
}
button:focus-visible, input:focus-visible { outline: 2px solid var(--primary-ink); outline-offset: -3px; }
@media (max-width: 600px) { .page-arrow { width: 32px; height: 64px; } }
</style>
