<template>
  <div
    class="skeleton-loader"
    :class="`skeleton-${variant}`"
    :role="plain ? undefined : 'status'"
    :aria-busy="plain ? undefined : true"
    :aria-live="plain ? undefined : 'polite'"
    :aria-hidden="plain ? true : undefined"
  >
    <span v-if="!plain" class="sr-only">{{ label }}</span>
    <slot>
      <template v-if="variant === 'lines'">
        <SkeletonBlock v-for="row in rows" :key="row" :width="widthAt(row)" />
      </template>

      <template v-else-if="variant === 'list'">
        <div v-for="row in rows" :key="row" class="skeleton-list-row">
          <SkeletonBlock width="2.25rem" height="2.25rem" radius="50%" />
          <div class="skeleton-stack">
            <SkeletonBlock :width="widthAt(row, 0.6)" />
            <SkeletonBlock :width="widthAt(row + 2, 0.4)" height="0.7rem" />
          </div>
        </div>
      </template>

      <template v-else-if="variant === 'cards'">
        <div v-for="row in rows" :key="row" class="skeleton-card">
          <SkeletonBlock height="8.5rem" radius="0" />
          <div class="skeleton-stack skeleton-card-body">
            <SkeletonBlock :width="widthAt(row)" height="1rem" />
            <SkeletonBlock :width="widthAt(row + 2, 0.5)" height="0.7rem" />
            <SkeletonBlock height="0.5rem" radius="var(--radius-full)" />
          </div>
        </div>
      </template>

      <template v-else-if="variant === 'stats'">
        <div v-for="row in rows" :key="row" class="skeleton-card skeleton-stat">
          <SkeletonBlock width="55%" height="0.7rem" />
          <SkeletonBlock width="40%" height="1.5rem" />
        </div>
      </template>

      <template v-else-if="variant === 'table'">
        <div class="skeleton-table-row skeleton-table-head" :style="tableGrid">
          <SkeletonBlock
            v-for="column in columns"
            :key="column"
            width="60%"
            height="0.7rem"
          />
        </div>
        <div
          v-for="row in rows"
          :key="row"
          class="skeleton-table-row"
          :style="tableGrid"
        >
          <SkeletonBlock
            v-for="column in columns"
            :key="column"
            :width="widthAt(row + column, 0.55)"
          />
        </div>
      </template>

      <template v-else-if="variant === 'tree'">
        <div
          v-for="row in rows"
          :key="row"
          class="skeleton-tree-row"
          :class="{ 'is-nested': row % 3 === 0 }"
        >
          <SkeletonBlock width="1rem" height="1rem" />
          <SkeletonBlock :width="widthAt(row, 0.45)" />
        </div>
      </template>

      <template v-else-if="variant === 'document'">
        <div class="skeleton-sheet">
          <SkeletonBlock width="55%" height="1.25rem" />
          <SkeletonBlock v-for="row in rows" :key="row" :width="widthAt(row)" />
        </div>
      </template>

      <SkeletonBlock
        v-else-if="variant === 'media'"
        :height="height || '100%'"
        :class="{ 'skeleton-ratio': !height }"
        radius="inherit"
      />
    </slot>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import SkeletonBlock from "./SkeletonBlock.vue";

const props = withDefaults(
  defineProps<{
    variant?: "lines" | "list" | "cards" | "stats" | "table" | "tree" | "document" | "media";
    rows?: number;
    columns?: number;
    label?: string;
    height?: string;
    /** Nested inside another SkeletonLoader: only that outer one announces the loading state. */
    plain?: boolean;
  }>(),
  { variant: "lines", rows: 3, columns: 4, label: "Đang tải dữ liệu" },
);

const tableGrid = computed(() => ({
  gridTemplateColumns: `repeat(${props.columns}, minmax(0, 1fr))`,
}));

const WIDTHS = [100, 86, 72, 92, 64];

function widthAt(index: number, scale = 1) {
  return `${WIDTHS[index % WIDTHS.length] * scale}%`;
}
</script>

<style scoped>
.skeleton-loader {
  display: grid;
  gap: 0.75rem;
  width: 100%;
  min-width: 0;
}

.skeleton-stack {
  display: grid;
  flex: 1;
  gap: 0.5rem;
  min-width: 0;
}

.skeleton-list-row {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.5rem 0;
}

.skeleton-cards {
  grid-template-columns: repeat(auto-fill, minmax(min(260px, 100%), 1fr));
  gap: 1rem;
}

.skeleton-stats {
  grid-template-columns: repeat(auto-fit, minmax(min(160px, 100%), 1fr));
  gap: 1rem;
}

.skeleton-card {
  overflow: hidden;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--bg-card);
}

.skeleton-card-body {
  padding: 1rem;
}

.skeleton-stat {
  display: grid;
  gap: 0.75rem;
  padding: 1rem;
}

.skeleton-table,
.skeleton-tree {
  gap: 0;
}

.skeleton-table-row {
  display: grid;
  align-items: center;
  gap: 1rem;
  padding: 0.9rem 1rem;
  border-bottom: 1px solid var(--border-color);
}

.skeleton-table-head {
  padding-block: 0.75rem;
  background: var(--bg-subtle);
}

.skeleton-tree-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.55rem 0.85rem;
}

.skeleton-tree-row.is-nested {
  padding-left: 2.25rem;
}

.skeleton-sheet {
  display: grid;
  gap: 0.8rem;
  align-content: start;
  width: min(100%, 760px);
  min-height: 60vh;
  margin-inline: auto;
  padding: clamp(1rem, 4vw, 2.5rem);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  background: var(--bg-card);
}

.skeleton-ratio {
  aspect-ratio: 16 / 9;
}
</style>
