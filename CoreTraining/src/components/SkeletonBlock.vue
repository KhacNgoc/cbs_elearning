<template>
  <span
    class="skeleton-block"
    aria-hidden="true"
    :style="{ width, height, borderRadius: radius }"
  ></span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{ width?: string; height?: string; radius?: string }>(),
  { width: "100%", height: "0.85rem", radius: "var(--radius-sm)" },
);
</script>

<style scoped>
.skeleton-block {
  position: relative;
  display: block;
  max-width: 100%;
  flex-shrink: 0;
  overflow: hidden;
  background: var(--border-color);
}

.skeleton-block::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent,
    color-mix(in srgb, var(--bg-card) 60%, transparent),
    transparent
  );
  transform: translateX(-100%);
  animation: skeleton-shimmer 1.4s ease-in-out infinite;
}

@keyframes skeleton-shimmer {
  to {
    transform: translateX(100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-block::after {
    display: none;
    animation: none;
  }
}
</style>
