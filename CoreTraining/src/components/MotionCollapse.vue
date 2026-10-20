<template>
  <Transition name="collapse">
    <div v-if="open" class="motion-collapse">
      <div class="motion-collapse-inner"><slot /></div>
    </div>
  </Transition>
</template>
<script setup lang="ts">
defineProps<{ open: boolean }>();
</script>
<style scoped>
.motion-collapse {
  display: grid;
  grid-template-rows: 1fr;
}
.motion-collapse-inner {
  min-height: 0;
  overflow: hidden;
}
.collapse-enter-active,
.collapse-leave-active {
  transition:
    grid-template-rows var(--motion-duration) var(--motion-easing),
    opacity var(--motion-fast) ease;
}
.collapse-enter-from,
.collapse-leave-to {
  grid-template-rows: 0fr;
  opacity: 0;
}
.collapse-leave-active {
  pointer-events: none;
}
</style>
