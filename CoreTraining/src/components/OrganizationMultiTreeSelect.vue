<template>
  <div
    class="multi-tree-select"
    ref="container"
    @keydown.esc.stop="open = false"
  >
    <button
      type="button"
      class="multi-control"
      :disabled="disabled"
      aria-haspopup="dialog"
      :aria-expanded="open"
      @click="open = !open"
    >
      <span>{{
        modelValue.length
          ? `Đã chọn ${modelValue.length} đơn vị`
          : "Chọn các đơn vị của chương trình"
      }}</span
      ><ChevronDown :size="16" />
    </button>
    <div v-if="modelValue.length" class="selected-unit-tags">
      <span v-for="id in modelValue" :key="id"
        >{{ units.find((u) => u.id === id)?.name || id
        }}<button
          type="button"
          :disabled="disabled"
          :aria-label="`Bỏ đơn vị ${id}`"
          @click="
            $emit(
              'update:modelValue',
              modelValue.filter((x) => x !== id),
            )
          "
        >
          ×
        </button></span
      >
    </div>
    <div
      v-if="open"
      class="multi-popup"
      role="dialog"
      aria-label="Chọn đơn vị của chương trình"
    >
      <OrganizationCheckTree
        :model-value="modelValue"
        :units="units"
        :disabled="disabled"
        :cascade="false"
        @update:model-value="$emit('update:modelValue', $event)"
      /><button type="button" class="done-choice" @click="open = false">
        Xong
      </button>
    </div>
  </div>
</template>
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from "vue";
import { ChevronDown } from "lucide-vue-next";
import OrganizationCheckTree from "./OrganizationCheckTree.vue";
import type { OrganizationUnit } from "../types/organization";
defineProps<{
  units: OrganizationUnit[];
  modelValue: string[];
  disabled?: boolean;
}>();
defineEmits<{ "update:modelValue": [ids: string[]] }>();
const open = ref(false),
  container = ref<HTMLElement | null>(null);
function outside(e: PointerEvent) {
  if (!container.value?.contains(e.target as Node)) open.value = false;
}
onMounted(() => document.addEventListener("pointerdown", outside));
onBeforeUnmount(() => document.removeEventListener("pointerdown", outside));
</script>
<style scoped>
.multi-control {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  border: 1px solid var(--line-dce5ef);
  border-radius: 6px;
  background: var(--surface-ffffff);
  padding: 0.7rem 0.8rem;
  color: var(--ink-263449);
  cursor: pointer;
}
.selected-unit-tags {
  display: flex;
  gap: 5px;
  flex-wrap: wrap;
  margin-top: 0.5rem;
}
.selected-unit-tags span {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: var(--surface-e8f3ee);
  color: var(--ink-00634b);
  border-radius: 4px;
  padding: 4px 6px;
  font-size: 0.78rem;
}
.selected-unit-tags button {
  border: 0;
  background: none;
  color: inherit;
  cursor: pointer;
}
.multi-popup {
  margin-top: 6px;
  padding: 12px;
  border: 1px solid var(--line-dce5ef);
  border-radius: 8px;
  background: var(--surface-ffffff);
  box-shadow: 0 6px 20px #163d2514;
}
.done-choice {
  display: block;
  margin: 8px 0 0 auto;
  background: #00634b;
  color: white;
  border: 0;
  border-radius: 5px;
  padding: 7px 16px;
  cursor: pointer;
}
</style>
