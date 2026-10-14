<template>
  <div class="progress-bar-container">
    <div class="progress-info" v-if="showText">
      <span class="progress-label">{{ label }}</span>
      <span class="progress-value">{{ progress }}%</span>
    </div>
    <div class="progress-track" :style="{ height: height }">
      <div 
        class="progress-fill" 
        :class="{ accent: accent }" 
        :style="{ width: `${progress}%` }"
      ></div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  progress: {
    type: Number,
    required: true,
    default: 0
  },
  showText: {
    type: Boolean,
    default: false
  },
  label: {
    type: String,
    default: ''
  },
  accent: {
    type: Boolean,
    default: false
  },
  height: {
    type: String,
    default: '8px'
  }
});
</script>

<style scoped>
.progress-bar-container {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.progress-info {
  display: flex;
  justify-content: space-between;
  font-size: 0.85rem;
  font-weight: 500;
}

.progress-label {
  color: var(--text-main);
}

.progress-value {
  color: var(--primary-ink);
  font-weight: 600;
}
</style>
