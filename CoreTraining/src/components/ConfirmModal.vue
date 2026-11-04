<template>
  <AdminModal
    :open="open"
    :title="title"
    :width="460"
    :busy="loading"
    :submit-label="confirmLabel"
    danger
    alert
    @close="$emit('cancel')"
    @submit="$emit('confirm')"
  >
    <div v-if="!notes?.length" class="confirm-modal-icon"><TriangleAlert :size="25" /></div>
    <p class="confirm-message" :class="{ 'confirm-main-question': !!notes?.length }">{{ message }}</p>
    <div v-if="notes && notes.length" class="confirm-notes-group">
      <p v-for="(note, idx) in notes" :key="idx" class="confirm-note-item">
        {{ note }}
      </p>
    </div>
    <template #footer
      ><button
        type="button"
        class="btn btn-secondary"
        :disabled="loading"
        @click="$emit('cancel')"
      >
        Hủy bỏ</button
      ><button type="submit" class="btn btn-danger" :disabled="loading">
        {{ loading ? "Đang xử lý…" : confirmLabel }}
      </button></template
    >
  </AdminModal>
</template>
<script setup lang="ts">
import AdminModal from "./AdminModal.vue";
import { TriangleAlert } from "lucide-vue-next";
withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    message: string;
    notes?: string[];
    confirmLabel?: string;
    loading?: boolean;
  }>(),
  { confirmLabel: "Xác nhận", loading: false },
);
defineEmits<{ confirm: []; cancel: [] }>();
</script>
<style scoped>
.confirm-modal-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 1rem;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: var(--surface-feecec);
  color: var(--ink-b42318);
}
.confirm-message {
  margin: 0;
  color: var(--text-muted);
  line-height: 1.6;
  white-space: pre-line;
  overflow-wrap: anywhere;
}
.confirm-main-question {
  color: var(--text-main);
  font-size: 0.92rem;
  font-weight: 400;
  line-height: 1.5;
}
.confirm-notes-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin-top: 0.55rem;
}
.confirm-note-item {
  margin: 0;
  font-size: 0.8rem;
  font-weight: 400;
  font-style: italic;
  line-height: 1.45;
  color: var(--text-muted);
}
.btn-danger {
  background: #dc2626;
  color: white;
  border: 1px solid #dc2626;
}
</style>
