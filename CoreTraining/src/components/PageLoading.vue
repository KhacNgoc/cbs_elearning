<template>
  <div class="page-loading-space">
    <Teleport to="body">
      <div class="page-loading" role="status" aria-live="polite" aria-busy="true">
        <span class="page-loading-ring" aria-hidden="true"></span>
        <p>{{ label }}</p>
      </div>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{ label?: string }>(), { label: 'Đang tải dữ liệu...' });
</script>

<style scoped>
.page-loading-space {
  width: 100%;
  min-height: 60vh;
}

.page-loading {
  position: fixed;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 1rem;
  pointer-events: none;
}

.page-loading-ring {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border: 4px solid var(--primary-light);
  border-top-color: var(--primary-color);
  border-radius: 50%;
  animation: page-loading-spin 750ms linear infinite;
}

.page-loading p {
  margin: 0;
  color: var(--text-muted);
  font-size: .85rem;
  text-align: center;
}

@keyframes page-loading-spin {
  to { transform: rotate(360deg); }
}
</style>
