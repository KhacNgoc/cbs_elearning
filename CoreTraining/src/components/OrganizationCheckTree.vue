<template>
  <div class="check-tree" :class="{ 'fill-height': fillHeight }">
    <div class="check-tree-tools">
      <label
        ><input
          type="checkbox"
          :checked="allSelected"
          :indeterminate="!!modelValue.length && !allSelected"
          :disabled="disabled || !selectable.length"
          @change="checkAll(($event.target as HTMLInputElement).checked)"
        />
        Chọn tất cả</label
      ><button
        type="button"
        @click="expanded = new Set(allRows.map((r) => r.unit.id))"
      >
        Mở tất cả</button
      ><button type="button" @click="expanded = new Set()">Thu gọn</button>
    </div>
    <input
      v-model="search"
      class="tree-search"
      type="search"
      placeholder="Tìm đơn vị / phòng…"
      aria-label="Tìm đơn vị trong cây"
    />
    <div
      class="check-tree-body"
      role="tree"
      aria-label="Cơ cấu đơn vị"
      aria-multiselectable="true"
      :tabindex="fillHeight ? 0 : undefined"
    >
      <div
        v-for="row in visibleRows"
        :key="row.unit.id"
        role="treeitem"
        :aria-level="row.depth + 1"
        :aria-expanded="row.hasChildren ? expanded.has(row.unit.id) : undefined"
        :aria-checked="mixed(row.unit.id) ? 'mixed' : checked(row.unit.id)"
        class="check-tree-row"
        :style="{ paddingLeft: `${row.depth * 20}px` }"
      >
        <button
          v-if="row.hasChildren"
          type="button"
          class="branch-toggle"
          :aria-label="`${expanded.has(row.unit.id) ? 'Thu gọn' : 'Mở rộng'} ${row.unit.name}`"
          @click="toggle(row.unit.id)"
        >
          <ChevronRight
            :size="16"
            :class="{ expanded: expanded.has(row.unit.id) }"
          /></button
        ><span v-else class="branch-spacer" />
        <label
          ><input
            type="checkbox"
            :checked="checked(row.unit.id)"
            :indeterminate="mixed(row.unit.id)"
            :disabled="disabled || !selectable.includes(row.unit.id)"
            @change="
              change(row.unit.id, ($event.target as HTMLInputElement).checked)
            "
          /><span
            >{{ row.unit.name
            }}<small
              >{{ row.unit.id
              }}{{ row.unit.active ? "" : " · Ngừng hoạt động" }}</small
            ></span
          ></label
        >
      </div>
      <p v-if="!visibleRows.length" class="tree-empty">
        Không có đơn vị phù hợp.
      </p>
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { ChevronRight } from "lucide-vue-next";
import type { OrganizationUnit } from "../types/organization";
import {
  buildUnitForest,
  filterUnitForest,
  flattenUnitForest,
  unitDescendants,
} from "../utils/organizationTree";
const props = withDefaults(
  defineProps<{
    units: OrganizationUnit[];
    modelValue: string[];
    disabled?: boolean;
    cascade?: boolean;
    fillHeight?: boolean;
  }>(),
  { disabled: false, cascade: true, fillHeight: false },
);
const emit = defineEmits<{ "update:modelValue": [value: string[]] }>();
const search = ref(""),
  expanded = ref(new Set<string>());
const forest = computed(() => buildUnitForest(props.units));
const allRows = computed(() => flattenUnitForest(forest.value));
const selectable = computed(() =>
  allRows.value
    .filter(
      (r) =>
        r.unit.active &&
        r.ancestors.every((id) => props.units.find((u) => u.id === id)?.active),
    )
    .map((r) => r.unit.id),
);
const filtered = computed(() =>
  search.value
    ? filterUnitForest(forest.value, (u) =>
        `${u.name} ${u.id}`
          .toLocaleLowerCase("vi")
          .includes(search.value.toLocaleLowerCase("vi")),
      )
    : forest.value,
);
const visibleRows = computed(() =>
  flattenUnitForest(filtered.value, expanded.value),
);
watch(search, () => {
  expanded.value = search.value
    ? new Set(
        flattenUnitForest(filtered.value)
          .filter((r) => r.hasChildren)
          .map((r) => r.unit.id),
      )
    : new Set(forest.value.map((n) => n.unit.id));
});
watch(
  forest,
  () => {
    expanded.value = new Set(
      allRows.value.filter((r) => r.hasChildren).map((r) => r.unit.id),
    );
  },
  { immediate: true },
);
function ids(id: string) {
  return props.cascade
    ? [...unitDescendants(props.units, id)].filter((i) =>
        selectable.value.includes(i),
      )
    : [id];
}
function covered(id: string) {
  return (
    props.modelValue.includes(id) ||
    (props.cascade &&
      allRows.value
        .find((r) => r.unit.id === id)
        ?.ancestors.some((a) => props.modelValue.includes(a)) === true)
  );
}
function checked(id: string) {
  return covered(id);
}
function mixed(id: string) {
  return (
    !covered(id) &&
    props.cascade &&
    ids(id).some((i) => props.modelValue.includes(i))
  );
}
const allSelected = computed(
  () => !!selectable.value.length && selectable.value.every(covered),
);
function checkAll(yes: boolean) {
  emit("update:modelValue", yes ? [...selectable.value] : []);
}
function change(id: string, yes: boolean) {
  const next = new Set(props.modelValue);
  // Expand inherited selections before removing a descendant so the unchecked branch stays excluded.
  if (props.cascade)
    for (const selected of props.modelValue)
      for (const child of ids(selected)) next.add(child);
  for (const child of ids(id)) yes ? next.add(child) : next.delete(child);
  if (!yes && props.cascade)
    for (const parent of allRows.value.find((r) => r.unit.id === id)
      ?.ancestors || [])
      next.delete(parent);
  emit("update:modelValue", [...next]);
}
function toggle(id: string) {
  const next = new Set(expanded.value);
  next.has(id) ? next.delete(id) : next.add(id);
  expanded.value = next;
}
</script>
<style scoped>
.check-tree.fill-height {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
}
.fill-height .check-tree-tools,
.fill-height .tree-search {
  flex-shrink: 0;
}
.fill-height .check-tree-body {
  flex: 1;
  min-height: 0;
  max-height: none;
  overflow: auto;
  overscroll-behavior: contain;
  scrollbar-gutter: stable;
}
.fill-height .check-tree-body:focus-visible {
  outline: 2px solid var(--primary-color);
  outline-offset: -2px;
}

.check-tree-tools {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-bottom: 0.8rem;
  font-size: 0.82rem;
}
.check-tree-tools label {
  display: flex;
  align-items: center;
  gap: 0.45rem;
}
.check-tree-tools button {
  background: none;
  border: 0;
  color: var(--primary-color, var(--ink-00634b));
  cursor: pointer;
  font-size: 0.78rem;
}
.tree-search {
  width: 100%;
  padding: 0.65rem 0.8rem;
  border: 1px solid var(--border-color, var(--line-dce5ef));
  border-radius: 6px;
  margin-bottom: 0.65rem;
}
.check-tree-body {
  max-height: 440px;
  overflow: auto;
}
.check-tree-row {
  display: flex;
  align-items: center;
  min-height: 48px;
}
.check-tree-row label {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  cursor: pointer;
  min-width: 0;
  flex: 1;
  font-size: 0.86rem;
}
.check-tree-row label span {
  overflow-wrap: anywhere;
}
.check-tree-row small {
  display: block;
  color: var(--text-muted, var(--ink-708098));
  font-size: 0.72rem;
  margin-top: 2px;
}
.branch-toggle,
.branch-spacer {
  flex: 0 0 24px;
  width: 24px;
}
.branch-toggle {
  border: 0;
  background: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  color: var(--text-muted);
}
.expanded {
  transform: rotate(90deg);
}
input[type="checkbox"] {
  accent-color: #00634b;
  width: 16px;
  height: 16px;
  flex-shrink: 0;
}
.tree-empty {
  font-size: 0.85rem;
  color: var(--text-muted);
  padding: 1rem;
}
</style>
