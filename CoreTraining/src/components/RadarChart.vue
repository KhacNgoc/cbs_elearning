<template>
  <div>
    <div v-if="evaluated.length >= 3" class="chart-wrapper"><Radar :data="chartData" :options="chartOptions" aria-label="Năng lực nghiệp vụ theo kết quả kiểm tra, thang điểm 100" /></div>
    <p v-else class="chart-note">{{ evaluated.length ? 'Chưa đủ 3 lĩnh vực có kết quả để vẽ biểu đồ radar.' : 'Chưa có kết quả kiểm tra để đánh giá năng lực.' }}</p>
    <ul class="competency-list"><li v-for="item in competencies" :key="item.id"><span>{{ item.label }}</span><strong>{{ item.value === null ? 'Chưa đánh giá' : `${item.value}/100` }}</strong><small v-if="item.count">{{ item.count }} bài đánh giá</small></li></ul>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import { Radar } from 'vue-chartjs';
import { Chart as ChartJS, Tooltip, Legend, PointElement, LineElement, RadialLinearScale, Filler } from 'chart.js';
import type { ChartOptions } from 'chart.js';
import { useReducedMotion } from '../composables/useReducedMotion';
ChartJS.register(Tooltip,Legend,PointElement,LineElement,RadialLinearScale,Filler);
const props=defineProps<{competencies:{id:string;label:string;value:number|null;count:number}[]}>();
const evaluated=computed(()=>props.competencies.filter(c=>c.value!==null));
const chartData=computed(()=>({labels:evaluated.value.map(c=>c.label),datasets:[{label:'Năng lực nghiệp vụ /100',data:evaluated.value.map(c=>c.value),backgroundColor:'rgba(0,91,65,.12)',borderColor:'#005B41',pointBackgroundColor:'#005B41',borderWidth:2}]}));
const reducedMotion=useReducedMotion();
const chartOptions=computed<ChartOptions<'radar'>>(()=>({responsive:true,maintainAspectRatio:false,animation:{duration:reducedMotion.value?0:240,easing:'easeOutCubic'},scales:{r:{min:0,max:100,ticks:{stepSize:25},pointLabels:{font:{size:12}}}},plugins:{legend:{display:false}}}));
</script>
<style scoped>
.chart-wrapper{height:300px;position:relative;width:100%}.chart-note{color:var(--text-muted);padding:1rem 0}.competency-list{list-style:none;padding:0}.competency-list li{display:flex;flex-wrap:wrap;gap:.5rem;justify-content:space-between;padding:.7rem 0;border-bottom:1px solid var(--border-color);font-size:.8rem}.competency-list strong{color:var(--primary-color)}.competency-list small{color:var(--text-muted)}
</style>
