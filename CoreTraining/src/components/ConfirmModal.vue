<template>
  <Teleport to="body">
    <div v-if="open" class="confirm-modal-overlay" @click.self="$emit('cancel')">
      <section
        class="confirm-modal"
        role="alertdialog"
        aria-modal="true"
        :aria-labelledby="titleId"
        :aria-describedby="descriptionId"
      >
        <div class="confirm-modal-icon">
          <TriangleAlert :size="25" />
        </div>
        <div>
          <h3 :id="titleId">{{ title }}</h3>
          <p :id="descriptionId">{{ message }}</p>
        </div>
        <div class="confirm-modal-actions">
          <button type="button" class="cancel-button" @click="$emit('cancel')">
            Hủy
          </button>
          <button
            type="button"
            class="confirm-button"
            :disabled="loading"
            @click="$emit('confirm')"
          >
            {{ loading ? "Đang xử lý..." : confirmLabel }}
          </button>
        </div>
      </section>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { TriangleAlert } from "lucide-vue-next";

const props = withDefaults(
  defineProps<{
    open: boolean;
    title: string;
    message: string;
    confirmLabel?: string;
    loading?: boolean;
  }>(),
  {
    confirmLabel: "Xác nhận",
    loading: false,
  },
);

defineEmits<{
  confirm: [];
  cancel: [];
}>();

const idSuffix = Math.random().toString(36).slice(2, 9);
const titleId = computed(() => `confirm-modal-title-${idSuffix}`);
const descriptionId = computed(() => `confirm-modal-description-${idSuffix}`);
</script>

<style scoped>
.confirm-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 2000;
  padding: 1rem;
  display: grid;
  place-items: center;
  background: rgb(15 23 42 / 55%);
  backdrop-filter: blur(2px);
}

.confirm-modal {
  width: min(430px, 100%);
  padding: 1.5rem;
  border-radius: 14px;
  background: white;
  box-shadow: 0 24px 60px rgb(15 23 42 / 24%);
  animation: confirm-modal-in 0.18s ease-out;
}

.confirm-modal-icon {
  width: 48px;
  height: 48px;
  margin-bottom: 1rem;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #feecec;
  color: #b42318;
}

.confirm-modal h3 {
  margin: 0;
  color: var(--text-main, #1e293b);
  font-size: 1.15rem;
}

.confirm-modal p {
  margin: 0.55rem 0 0;
  color: var(--text-muted, #64748b);
  line-height: 1.55;
}

.confirm-modal-actions {
  margin-top: 1.4rem;
  display: flex;
  justify-content: flex-end;
  gap: 0.6rem;
}

.confirm-modal-actions button {
  padding: 0.65rem 0.95rem;
  border-radius: 8px;
  font-weight: 700;
  cursor: pointer;
}

.cancel-button {
  border: 1px solid #d8e0dd;
  background: white;
  color: #475569;
}

.confirm-button {
  border: 1px solid #dc2626;
  background: #dc2626;
  color: white;
}

.confirm-button:hover:not(:disabled) {
  background: #b91c1c;
}

.confirm-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

@keyframes confirm-modal-in {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.98);
  }
}
</style>
