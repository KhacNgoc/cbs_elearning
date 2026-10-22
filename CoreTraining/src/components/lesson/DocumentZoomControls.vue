<template>
  <div class="document-zoom" role="group" aria-label="Thu phóng tài liệu">
    <button type="button" aria-label="Thu nhỏ" title="Thu nhỏ" :disabled="disabled || modelValue <= 50"
      @click="$emit('update:modelValue', modelValue - 25)"><Minus :size="17" /></button>
    <output aria-live="polite">{{ modelValue }}%</output>
    <button type="button" aria-label="Phóng to" title="Phóng to" :disabled="disabled || modelValue >= 300"
      @click="$emit('update:modelValue', modelValue + 25)"><Plus :size="17" /></button>
    <button type="button" class="fit-document" aria-label="Đưa tài liệu về vừa khung" title="Đưa tài liệu về vừa khung" :disabled="disabled"
      @click="$emit('update:modelValue', 100)"><RotateCcw :size="17" /><span>Vừa khung</span></button>
  </div>
</template>
<script setup lang="ts">
import { Minus, Plus, RotateCcw } from 'lucide-vue-next';
defineProps<{ modelValue: number; disabled?: boolean }>();
defineEmits<{ 'update:modelValue': [value: number] }>();
</script>
<style scoped>
.document-zoom {
  position: absolute; bottom: 8px; right: 8px; z-index: 3;
  display: flex; align-items: center; gap: 2px; padding: 3px;
  background: var(--bg-card); color: var(--text-main); border: 1px solid var(--border-color);
  border-radius: 7px; box-shadow: 0 2px 8px #0002;
}
button {
  display: grid; place-items: center; width: 32px; height: 32px;
  border: 0; border-radius: 4px; background: transparent; color: var(--primary-ink); cursor: pointer;
}
.fit-document { display: flex; align-items: center; justify-content: center; gap: 5px; width: auto; padding: 0 8px; font-size: .75rem; white-space: nowrap; }
@media (max-width: 600px) {
  .fit-document { width: 32px; padding: 0; }
  .fit-document span { display: none; }
}
button:hover:not(:disabled) { background: var(--primary-light); }
button:disabled { opacity: .4; cursor: default; }
button:focus-visible { outline: 2px solid var(--primary-ink); outline-offset: -2px; }
output { min-width: 40px; text-align: center; font-size: .75rem; font-variant-numeric: tabular-nums; }
</style>
