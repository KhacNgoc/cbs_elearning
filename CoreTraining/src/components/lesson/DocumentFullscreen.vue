<template>
  <Teleport to="body" :disabled="!viewportExpanded">
    <div
      ref="workspace"
      class="selected-document-preview document-fullscreen"
      :class="{ 'viewport-expanded': viewportExpanded }"
      :role="viewportExpanded ? 'dialog' : undefined"
      :aria-modal="viewportExpanded ? true : undefined"
      :aria-label="viewportExpanded ? 'Xem tài liệu toàn màn hình' : undefined"
      @keydown="handleKeydown"
    >
      <slot />
      <button
        ref="fullscreenButton"
        type="button"
        class="document-fullscreen-button"
        :aria-label="expanded ? 'Thoát toàn màn hình' : 'Xem toàn màn hình'"
        :title="expanded ? 'Thoát toàn màn hình (Esc)' : 'Xem toàn màn hình'"
        :aria-pressed="expanded"
        :disabled="pending"
        @click="toggleFullscreen"
      >
        <Minimize v-if="expanded" :size="18" />
        <Maximize v-else :size="18" />
        <span>{{ expanded ? 'Thu gọn' : 'Toàn màn hình' }}</span>
      </button>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue';
import { Maximize, Minimize } from 'lucide-vue-next';

const workspace = ref<HTMLElement | null>(null);
const fullscreenButton = ref<HTMLButtonElement | null>(null);
const nativeExpanded = ref(false);
const viewportExpanded = ref(false);
const pending = ref(false);
const expanded = computed(() => nativeExpanded.value || viewportExpanded.value);
let previousOverflow = '';
let previousFocus: HTMLElement | null = null;
let disposed = false;

function closeViewport() {
  if (!viewportExpanded.value) return;
  viewportExpanded.value = false;
  document.body.style.overflow = previousOverflow;
  void nextTick(() => previousFocus?.focus({ preventScroll: true }));
}
function syncFullscreen() {
  const wasExpanded = nativeExpanded.value;
  nativeExpanded.value = document.fullscreenElement === workspace.value;
  if (wasExpanded && !nativeExpanded.value)
    previousFocus?.focus({ preventScroll: true });
}
async function toggleFullscreen() {
  if (pending.value) return;
  if (viewportExpanded.value) {
    closeViewport();
    return;
  }
  pending.value = true;
  try {
    if (nativeExpanded.value) {
      await document.exitFullscreen();
      return;
    }
    previousFocus = document.activeElement instanceof HTMLElement
      ? document.activeElement : null;
    const element = workspace.value;
    if (!element) return;
    if (element.requestFullscreen && document.fullscreenEnabled !== false) {
      try {
        await element.requestFullscreen();
        return;
      } catch {
        // Mobile browsers can reject element fullscreen; use the full viewport.
      }
    }
    if (disposed) return;
    previousOverflow = document.body.style.overflow;
    viewportExpanded.value = true;
    document.body.style.overflow = 'hidden';
    await nextTick();
    fullscreenButton.value?.focus({ preventScroll: true });
  } finally {
    pending.value = false;
  }
}
function handleKeydown(event: KeyboardEvent) {
  if (!viewportExpanded.value || event.key !== 'Tab') return;
  const controls = [...(workspace.value?.querySelectorAll<HTMLElement>(
    'button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]',
  ) || [])].filter(element => element.getClientRects().length > 0);
  const first = controls[0];
  const last = controls[controls.length - 1];
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault();
    last?.focus();
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault();
    first?.focus();
  }
}
function handleEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') closeViewport();
}
onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreen);
  document.addEventListener('keydown', handleEscape);
});
onBeforeUnmount(() => {
  disposed = true;
  document.removeEventListener('fullscreenchange', syncFullscreen);
  document.removeEventListener('keydown', handleEscape);
  closeViewport();
  if (workspace.value && document.fullscreenElement === workspace.value)
    void document.exitFullscreen().catch(() => {});
});
</script>

<style scoped>
.document-fullscreen { position: relative; }
.document-fullscreen:fullscreen,
.document-fullscreen.viewport-expanded {
  width: 100%;
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--bg-main);
}
.document-fullscreen.viewport-expanded {
  position: fixed;
  inset: 0;
  z-index: 10000;
  height: 100dvh;
}
.document-fullscreen-button {
  position: absolute;
  top: max(8px, env(safe-area-inset-top));
  right: max(8px, env(safe-area-inset-right));
  z-index: 5;
  min-width: 40px;
  height: 40px;
  padding: 0 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  font: inherit;
  font-size: 0.75rem;
  white-space: nowrap;
  border: 1px solid var(--border-color);
  border-radius: 7px;
  background: var(--bg-card);
  color: var(--primary-ink);
  box-shadow: 0 2px 8px #0002;
  cursor: pointer;
}
@media (max-width: 600px) {
  .document-fullscreen-button { width: 40px; padding: 0; }
  .document-fullscreen-button span { display: none; }
}
.document-fullscreen-button:hover { background: var(--primary-light); }
.document-fullscreen-button:focus-visible { outline: 2px solid var(--primary-ink); outline-offset: -3px; }
.document-fullscreen-button:disabled { cursor: wait; }
</style>
