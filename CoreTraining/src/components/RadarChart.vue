<template>
  <div class="chart-wrapper">
    <Radar :data="chartData" :options="chartOptions" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import { Radar } from 'vue-chartjs';
import {
  Chart as ChartJS,
  Title,
  Tooltip,
  Legend,
  PointElement,
  LineElement,
  RadialLinearScale,
  Filler
} from 'chart.js';
import { useUserStore } from '../stores/userStore';

ChartJS.register(
  Title,
  Tooltip,
  Legend,
  PointElement,
  LineElement,
  RadialLinearScale,
  Filler
);

const userStore = useUserStore();

// Mapped courses in order corresponding to Image 3 (7 segments of radar)
const coursesOrder = [
  { id: 'TIEN_VAY', label: 'TIẾN VAY' },
  { id: 'CHUYEN_TIEN', label: 'CHUYỂN TIỀN' },
  { id: 'TIEN_GUI', label: 'TIỀN GỬI' },
  { id: 'CIF', label: 'CIF' },
  { id: 'TELLER', label: 'TELLER' },
  { id: 'GL', label: 'GL' },
  { id: 'THE', label: 'THẺ' }
];

const chartData = computed(() => {
  const dataValues = coursesOrder.map(course => {
    const quiz = userStore.getCourseQuizScore(course.id);
    // If no quiz, base it slightly on study progress (max 20%) to show some shape, or 0%
    if (quiz) {
      return quiz.percent;
    }
    const progress = userStore.getCourseProgressPercent(course.id);
    return Math.round(progress * 0.2); // Up to 20% just by studying lessons
  });

  return {
    labels: coursesOrder.map(t => t.label),
    datasets: [
      {
        label: 'Năng lực nghiệp vụ (%)',
        backgroundColor: 'rgba(0, 91, 65, 0.2)', // BIDV green with opacity
        borderColor: '#005B41',
        pointBackgroundColor: '#D4AF37', // Gold point
        pointBorderColor: '#fff',
        pointHoverBackgroundColor: '#fff',
        pointHoverBorderColor: '#005B41',
        data: dataValues
      }
    ]
  };
});

const chartOptions = {
  responsive: true,
  maintainAspectRatio: false,
  scales: {
    r: {
      angleLines: {
        color: '#E2E8F0'
      },
      grid: {
        color: '#E2E8F0'
      },
      pointLabels: {
        font: {
          family: 'Outfit',
          size: 11,
          weight: 'bold' as const
        },
        color: '#1E293B'
      },
      ticks: {
        display: false, // Hide ticks to keep clean, or show with small size
        maxTicksLimit: 5
      },
      min: 0,
      max: 100
    }
  },
  plugins: {
    legend: {
      display: false // Hide legend to match design mockup
    },
    tooltip: {
      callbacks: {
        label: function(context: any) {
          return ` Mức độ: ${context.raw}%`;
        }
      }
    }
  }
};
</script>

<style scoped>
.chart-wrapper {
  position: relative;
  width: 100%;
  height: 320px;
  display: flex;
  justify-content: center;
  align-items: center;
}
</style>
