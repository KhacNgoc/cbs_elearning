<template>
  <!-- Picks which groups a comparison chart shows; an empty pick means "the chart's own top list". -->
  <div ref="root" class="subject-picker">
    <button type="button" class="picker-toggle" :aria-expanded="open" @click="open = !open">
      <ListFilter :size="14" />
      {{ modelValue.length ? `${modelValue.length} ${noun} đã chọn` : `Chọn ${noun}` }}
    </button>
    <div v-if="open" class="picker-panel" role="dialog" :aria-label="`Chọn ${noun} để so sánh`" @keydown.esc="open = false">
      <input
        ref="searchInput"
        v-model="query"
        type="search"
        class="picker-search"
        :placeholder="`Tìm ${noun}…`"
        :aria-label="`Tìm ${noun}`"
      />
      <p class="picker-hint">Chọn tối đa {{ max }} {{ noun }}. Đã chọn {{ modelValue.length }}/{{ max }}.</p>
      <ul class="picker-list">
        <li v-for="name in visible" :key="name">
          <label :title="name">
            <input
              type="checkbox"
              :checked="picked.has(name)"
              :disabled="!picked.has(name) && modelValue.length >= max"
              @change="toggle(name)"
            />
            <span>{{ name }}</span>
          </label>
        </li>
        <li v-if="!visible.length" class="picker-empty">Không tìm thấy {{ noun }} phù hợp.</li>
      </ul>
      <p v-if="matches.length > visible.length" class="picker-hint">
        Hiển thị {{ visible.length }}/{{ matches.length }} kết quả, nhập thêm để thu hẹp.
      </p>
      <div class="picker-actions">
        <button type="button" class="picker-link" :disabled="!modelValue.length" @click="emit('update:modelValue', [])">Bỏ chọn tất cả</button>
        <button type="button" class="picker-link" @click="open = false">Đóng</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { ListFilter } from 'lucide-vue-next';

const props = defineProps<{ options: string[]; modelValue: string[]; max: number; noun: string }>();
const emit = defineEmits<{ 'update:modelValue': [string[]] }>();
/** Rendering every option of a large organisation would cost more than it helps; searching narrows the rest. */
const RENDER_LIMIT = 200;

const open = ref(false), query = ref('');
const root = ref<HTMLElement>(), searchInput = ref<HTMLInputElement>();
const picked = computed(() => new Set(props.modelValue));
const matches = computed(() => {
  const q = query.value.trim().toLowerCase();
  return q ? props.options.filter(name => name.toLowerCase().includes(q)) : props.options;
});
// Picked names stay on top so they can be unticked without searching for them.
const visible = computed(() => {
  const chosen = matches.value.filter(name => picked.value.has(name));
  const rest = matches.value.filter(name => !picked.value.has(name));
  return [...chosen, ...rest].slice(0, Math.max(RENDER_LIMIT, chosen.length));
});

function toggle(name: string) {
  emit('update:modelValue', picked.value.has(name)
    ? props.modelValue.filter(n => n !== name)
    : props.modelValue.length < props.max ? [...props.modelValue, name] : props.modelValue);
}
function closeOutside(event: PointerEvent) {
  if (root.value && !root.value.contains(event.target as Node)) open.value = false;
}
watch(open, value => {
  if (value) {
    document.addEventListener('pointerdown', closeOutside);
    void nextTick(() => searchInput.value?.focus());
  } else {
    document.removeEventListener('pointerdown', closeOutside);
    query.value = '';
  }
});
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeOutside));
</script>

<style scoped>
.subject-picker { position: relative; }
.picker-toggle {
  display: inline-flex; align-items: center; gap: 6px; padding: 7px 11px;
  border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card);
  color: var(--text-main); font-size: 11px; white-space: nowrap; cursor: pointer;
}
.picker-panel {
  position: absolute; right: 0; top: calc(100% + 6px); z-index: 20;
  width: min(320px, calc(100vw - 32px)); padding: 12px;
  border: 1px solid var(--border-color); border-radius: 8px; background: var(--bg-card);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.18);
}
.picker-search {
  width: 100%; box-sizing: border-box; padding: 7px 10px; font-size: 12px;
  border: 1px solid var(--border-color); border-radius: 6px; background: var(--bg-card); color: var(--text-main);
}
.picker-hint { margin: 8px 0; font-size: 10px; color: var(--text-muted); }
.picker-list { list-style: none; margin: 0; padding: 0; max-height: 260px; overflow-y: auto; overscroll-behavior: contain; }
.picker-list label { display: flex; align-items: center; gap: 8px; padding: 6px 4px; font-size: 12px; cursor: pointer; border-radius: 4px; }
.picker-list label:hover { background: var(--bg-subtle); }
.picker-list span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.picker-list input { accent-color: var(--primary-color); flex-shrink: 0; }
.picker-empty { padding: 8px 4px; font-size: 12px; color: var(--text-muted); }
.picker-actions { display: flex; justify-content: space-between; margin-top: 8px; }
.picker-link { border: 0; background: none; padding: 4px 2px; font-size: 11px; font-weight: 600; color: var(--primary-ink); cursor: pointer; }
.picker-link:disabled { color: var(--text-muted); cursor: not-allowed; }
/* Phones: a bottom sheet, since the toggle may sit at either edge after wrapping. */
@media (max-width: 600px) {
  .picker-panel { position: fixed; left: 16px; right: 16px; top: auto; bottom: 16px; width: auto; }
}
@media print { .subject-picker { display: none; } }
</style>
