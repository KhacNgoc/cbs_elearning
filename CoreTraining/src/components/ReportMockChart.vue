<template><div class="report-canvas"><canvas ref="canvas" role="img" :aria-label="description">{{ description }}</canvas></div></template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { Chart, registerables } from 'chart.js';
import type { ChartConfiguration } from 'chart.js';
import { useTheme } from '../composables/useTheme';
Chart.register(...registerables);
const props = defineProps<{ config: ChartConfiguration; description: string }>();
const canvas = ref<HTMLCanvasElement>();
const { isDark } = useTheme();
let chart: Chart | undefined;
function render() {
  chart?.destroy();
  if (canvas.value) chart = new Chart(canvas.value, { ...props.config, options: { responsive: true, maintainAspectRatio: false, animation: false, color: isDark.value ? '#a8b8ca' : '#64748b', ...props.config.options } });
}
onMounted(render);
watch(() => props.config, render, { deep: true });
watch(isDark, render);
onBeforeUnmount(() => chart?.destroy());
</script>
<style scoped>.report-canvas{height:280px;min-width:0;position:relative}@media(max-width:600px){.report-canvas{height:250px}}@media print{.report-canvas{break-inside:avoid}}</style>
