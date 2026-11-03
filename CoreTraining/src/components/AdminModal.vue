<script lang="ts">
const openDialogs: symbol[] = [];
let originalOverflow = "";
</script>
<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from "vue";
import { X } from "lucide-vue-next";
const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    busy?: boolean;
    width?: number;
    submitLabel?: string;
    submitDisabled?: boolean;
    danger?: boolean;
    alert?: boolean;
  }>(),
  {
    busy: false,
    width: 720,
    submitLabel: "Lưu thay đổi",
    submitDisabled: false,
    danger: false,
    alert: false,
  },
);
const emit = defineEmits<{ close: []; submit: [] }>();
const dialog = ref<HTMLFormElement | null>(null);
const key = Symbol("admin-dialog");
const titleId = `admin-dialog-${Math.random().toString(36).slice(2)}`;
let previousFocus: HTMLElement | null = null;
function close() {
  if (!props.busy) emit("close");
}
function release() {
  const index = openDialogs.indexOf(key);
  if (index < 0) return;
  openDialogs.splice(index, 1);
  if (!openDialogs.length) document.body.style.overflow = originalOverflow;
  previousFocus?.focus();
}
watch(
  () => props.open,
  async (open) => {
    if (open) {
      previousFocus =
        document.activeElement instanceof HTMLElement
          ? document.activeElement
          : null;
      if (!openDialogs.length) {
        originalOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";
      }
      openDialogs.push(key);
      await nextTick();
      // Content that handles its own keys (e.g. flashcard study) asks for focus explicitly.
      (
        dialog.value?.querySelector<HTMLElement>("[data-autofocus]") ||
        dialog.value?.querySelector<HTMLElement>(
          "input:not([disabled]), select:not([disabled]), textarea:not([disabled])",
        ) ||
        dialog.value
      )?.focus();
    } else release();
  },
  { immediate: true },
);
function keydown(event: KeyboardEvent) {
  if (openDialogs.at(-1) !== key) return;
  if (event.key === "Escape") {
    event.preventDefault();
    close();
  }
  if (event.key !== "Tab") return;
  const items = [
    ...(dialog.value?.querySelectorAll<HTMLElement>(
      'button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),a[href],[tabindex="0"]',
    ) || []),
  ].filter((el) => el.getClientRects().length > 0);
  const first = items[0],
    last = items.at(-1);
  if (!first) {
    event.preventDefault();
    return;
  }
  if (
    event.shiftKey &&
    (document.activeElement === first ||
      document.activeElement === dialog.value)
  ) {
    event.preventDefault();
    last?.focus();
  } else if (
    !event.shiftKey &&
    (document.activeElement === last || document.activeElement === dialog.value)
  ) {
    event.preventDefault();
    first.focus();
  }
}
onBeforeUnmount(release);
</script>
<template>
  <Teleport to="body"
    ><Transition name="modal"
      ><div
        v-if="open"
        class="admin-modal-overlay"
        @keydown="keydown"
      >
        <form
          ref="dialog"
          class="admin-modal-dialog"
          :style="{ maxWidth: `${width}px` }"
          :role="alert ? 'alertdialog' : 'dialog'"
          aria-modal="true"
          :aria-labelledby="titleId"
          tabindex="-1"
          @submit.prevent="!busy && !submitDisabled && $emit('submit')"
        >
          <header class="admin-modal-header">
            <h2 :id="titleId">{{ title }}</h2>
            <button
              type="button"
              class="admin-modal-close"
              :disabled="busy"
              aria-label="Đóng form"
              @click="close"
            >
              <X :size="20" />
            </button>
          </header>
          <div class="admin-modal-body"><slot /></div>
          <footer class="admin-modal-footer">
            <slot name="footer"
              ><button
                type="button"
                class="btn btn-secondary"
                :disabled="busy"
                @click="close"
              >
                Đóng</button
              ><button
                type="submit"
                class="btn"
                :class="danger ? 'btn-danger' : 'btn-primary'"
                :disabled="busy || submitDisabled"
              >
                {{ busy ? "Đang xử lý…" : submitLabel }}
              </button></slot
            >
          </footer>
        </form>
      </div></Transition
    ></Teleport
  >
</template>
<style scoped>
.admin-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1600;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.25rem;
  background: #0f172a66;
  backdrop-filter: blur(4px);
}
.admin-modal-dialog {
  width: 100%;
  max-height: calc(100dvh - 2.5rem);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--surface-ffffff);
  border-radius: 12px;
  box-shadow: 0 24px 60px #0f172a3d;
  color: var(--text-main);
  outline: 0;
  margin: 0;
  padding: 0;
}
.admin-modal-header {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid var(--border-color);
  background: var(--surface-ffffff);
}
.admin-modal-header h2 {
  margin: 0;
  font-size: 1.125rem;
  line-height: 1.4;
  color: var(--text-main);
}
.admin-modal-close {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  border: 0;
  border-radius: 6px;
  padding: 0.4rem;
  background: transparent;
  color: var(--text-muted);
  cursor: pointer;
}
.admin-modal-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
  padding: 1.5rem;
}
.admin-modal-footer {
  flex: 0 0 auto;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 0.65rem;
  padding: 1rem 1.5rem;
  border-top: 1px solid var(--border-color);
  background: var(--surface-ffffff);
  box-shadow: 0 -8px 20px #0f231b08;
}
.btn-danger {
  background: #dc2626;
  color: white;
  border: 1px solid #dc2626;
}
.admin-modal-dialog :deep(label) {
  font-size: 0.8125rem;
}
.admin-modal-dialog :deep(input:not([type="checkbox"]):not([type="radio"])),
.admin-modal-dialog :deep(select),
.admin-modal-dialog :deep(textarea) {
  font-size: 0.875rem;
}
.admin-modal-dialog :deep(.form-control) {
  width: 100%;
}
.admin-modal-dialog
  :deep(input:not([type="radio"]):not([type="checkbox"]):not([type="file"])),
.admin-modal-dialog :deep(select),
.admin-modal-dialog :deep(textarea) {
  width: 100%;
  box-sizing: border-box;
  padding: 0.65rem 0.75rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  color: var(--text-main);
  background: var(--surface-ffffff);
}
.admin-modal-dialog :deep(.form-group) {
  margin-bottom: 1rem;
}
.admin-modal-dialog :deep(.form-group > label) {
  display: block;
  margin-bottom: 0.45rem;
}
.admin-modal-dialog :deep(.field-help) {
  display: block;
  font-size: 0.75rem;
  line-height: 1.5;
  color: var(--text-muted);
  margin-top: 0.4rem;
}
@media (max-width: 600px) {
  .admin-modal-overlay {
    padding: 0.65rem;
  }
  .admin-modal-dialog {
    max-height: calc(100dvh - 1.3rem);
  }
  .admin-modal-header,
  .admin-modal-body,
  .admin-modal-footer {
    padding: 1rem;
  }
  .admin-modal-footer {
    flex-wrap: wrap;
  }
}
</style>
