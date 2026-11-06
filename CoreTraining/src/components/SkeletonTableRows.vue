<template>
  <tr v-for="row in rows" :key="row">
    <td v-for="column in columns" :key="column">
      <span v-if="row === 1 && column === 1" class="sr-only" role="status">{{ label }}</span>
      <SkeletonBlock :width="`${WIDTHS[(row + column) % WIDTHS.length]}%`" />
    </td>
  </tr>
</template>

<script setup lang="ts">
import SkeletonBlock from "./SkeletonBlock.vue";

withDefaults(
  defineProps<{ columns: number; rows?: number; label?: string }>(),
  { rows: 6, label: "Đang tải dữ liệu" },
);

const WIDTHS = [78, 58, 88, 66, 48];
</script>
