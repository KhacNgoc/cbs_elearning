<template>
  <nav ref="navigation" class="document-pagination" aria-label="Chuyển trang tài liệu">
    <button
      type="button"
      class="page-arrow previous"
      aria-label="Trang trước"
      title="Trang trước (←)"
      aria-keyshortcuts="ArrowLeft"
      :disabled="disabled || (prevDisabled ?? page <= 1)"
      @click="$emit('go', page - 1)"
    >
      <ChevronLeft :size="28" :stroke-width="2.5" />
    </button>
    <button
      type="button"
      class="page-arrow next"
      aria-label="Trang sau"
      title="Trang sau (→)"
      aria-keyshortcuts="ArrowRight"
      :disabled="disabled || (nextDisabled ?? page >= total)"
      @click="$emit('go', page + 1)"
    >
      <ChevronRight :size="28" :stroke-width="2.5" />
    </button>
    <span class="page-counter" role="status"
      >Trang {{ page }} / {{ total || "—" }}</span
    >
  </nav>
</template>
<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from "vue";
import { ChevronLeft, ChevronRight } from "lucide-vue-next";
const props = defineProps<{
  page: number;
  total: number;
  disabled?: boolean;
  /** When set, overrides the default first-page disable rule. */
  prevDisabled?: boolean;
  /** When set, overrides the default last-page disable rule (needed for in-slide animations). */
  nextDisabled?: boolean;
}>();
const emit = defineEmits<{ go: [page: number] }>();
const navigation = ref<HTMLElement | null>(null);
const nativeArrowControls = 'input, textarea, select, video, audio, [contenteditable]:not([contenteditable="false"]), [role="textbox"], [role="combobox"], [role="slider"], [role="listbox"], [role="menu"], [role="tablist"]';

function handleArrowKey(event: KeyboardEvent) {
  if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
  if (event.defaultPrevented || event.isComposing || event.repeat || event.ctrlKey || event.altKey || event.metaKey || event.shiftKey
      || props.disabled || props.total < 1) return;
  const viewer = navigation.value?.parentElement;
  if (!viewer || !viewer.getClientRects().length) return;
  const target = event.target instanceof Element ? event.target : null;
  if (target?.closest(nativeArrowControls) || document.activeElement?.closest(nativeArrowControls)
      || window.getSelection()?.toString()) return;
  if (document.fullscreenElement && !document.fullscreenElement.contains(viewer)) return;
  // Unrelated dialogs take precedence; a fullscreen document dialog can own the viewer.
  const otherDialog = [...document.querySelectorAll<HTMLElement>('dialog[open], [aria-modal="true"]')]
    .some(dialog => !dialog.contains(viewer) && !dialog.hidden && dialog.getAttribute('aria-hidden') !== 'true'
      && window.getComputedStyle(dialog).display !== 'none');
  if (otherDialog) return;
  const focusedViewer = document.activeElement?.closest('.pdf-preview, .powerpoint-preview, .word-preview-shell');
  if (focusedViewer && focusedViewer !== viewer) return;
  const goingNext = event.key === 'ArrowRight';
  const blocked = goingNext
    ? (props.nextDisabled ?? props.page >= props.total)
    : (props.prevDisabled ?? props.page <= 1);
  if (blocked) return;
  const next = props.page + (goingNext ? 1 : -1);
  event.preventDefault();
  emit('go', next);
}

onMounted(() => document.addEventListener('keydown', handleArrowKey));
onBeforeUnmount(() => document.removeEventListener('keydown', handleArrowKey));
</script>
<style scoped>
.document-pagination {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 2;
}
.page-arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  display: grid;
  place-items: center;
  width: 42px;
  height: 84px;
  padding: 0;
  border: 0;
  background: #08785c;
  color: #fff;
  cursor: pointer;
  pointer-events: auto;
  box-shadow: 0 2px 12px #0003;
}
.previous {
  left: 0;
  border-radius: 0 14px 14px 0;
}
.next {
  right: 0;
  border-radius: 14px 0 0 14px;
}
.page-arrow:hover:not(:disabled) {
  background: #09654e;
}
.page-arrow:disabled {
  opacity: 0.28;
  cursor: default;
}
.page-counter {
  position: absolute;
  bottom: 8px;
  left: 8px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-main);
  font-size: 0.8rem;
  white-space: nowrap;
  pointer-events: auto;
}
.page-counter input {
  width: 55px;
  padding: 3px;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--bg-main);
  color: var(--text-main);
  text-align: center;
}
button:focus-visible,
input:focus-visible {
  outline: 2px solid var(--primary-ink);
  outline-offset: -3px;
}
@media (max-width: 600px) {
  .page-arrow {
    width: 32px;
    height: 64px;
  }
}
</style>
