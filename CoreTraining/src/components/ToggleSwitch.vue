<template>
  <button
    type="button"
    class="toggle-switch"
    role="switch"
    :aria-checked="modelValue"
    :aria-label="label"
    :disabled="disabled"
    @click="$emit('update:modelValue', !modelValue)"
  >
    <span class="toggle-track" :class="{ on: modelValue }" aria-hidden="true"
      ><span class="toggle-thumb"></span
    ></span>
    <span v-if="label && showLabel" class="toggle-label">{{ label }}</span>
  </button>
</template>
<script setup lang="ts">
withDefaults(
  defineProps<{
    modelValue: boolean;
    label?: string;
    disabled?: boolean;
    showLabel?: boolean;
  }>(),
  { disabled: false, showLabel: true },
);
defineEmits<{ "update:modelValue": [value: boolean] }>();
</script>
<style scoped>
.toggle-switch {
  display: inline-flex;
  align-items: center;
  gap: 0.65rem;
  border: 0 !important;
  padding: 0.2rem 0 !important;
  border-radius: 6px;
  background: transparent !important;
  color: var(--text-main);
  cursor: pointer;
  font: inherit;
  text-align: left;
  box-shadow: none !important;
  flex-shrink: 0;
}
.toggle-track {
  position: relative;
  display: inline-block;
  width: 38px;
  height: 22px;
  border-radius: 20px;
  background: var(--surface-c4cfca);
  transition: background 0.15s;
  flex: 0 0 38px;
}
.toggle-track.on {
  background: var(--primary-color, #005b41);
}
.toggle-thumb {
  position: absolute;
  left: 3px;
  top: 3px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: var(--surface-ffffff);
  box-shadow: 0 1px 3px #0002;
  transition: transform 0.15s;
}
.on .toggle-thumb {
  transform: translateX(16px);
}
.toggle-label {
  line-height: 1.45;
  font-weight: 500;
}
.toggle-switch:focus-visible {
  outline: 3px solid #00825d66;
  outline-offset: 4px;
}
.toggle-switch:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
@media (prefers-reduced-motion: reduce) {
  .toggle-track,
  .toggle-thumb {
    transition: none;
  }
}
</style>
