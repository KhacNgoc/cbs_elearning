<template>
  <div class="star-rating">
    <button
      v-for="star in 5"
      :key="star"
      type="button"
      class="star-btn"
      :class="{
        'is-active': star <= (hoverRating || modelValue),
        'is-readonly': readonly,
      }"
      @mouseenter="onMouseEnter(star)"
      @mouseleave="onMouseLeave"
      @click="onClick(star)"
      :disabled="readonly"
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        class="star-icon"
      >
        <polygon
          points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"
        />
      </svg>
    </button>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";

const props = defineProps({
  modelValue: {
    type: Number,
    default: 0,
  },
  readonly: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(["update:modelValue"]);
const hoverRating = ref(0);

const onMouseEnter = (star: number) => {
  if (!props.readonly) {
    hoverRating.value = star;
  }
};

const onMouseLeave = () => {
  if (!props.readonly) {
    hoverRating.value = 0;
  }
};

const onClick = (star: number) => {
  if (!props.readonly) {
    emit("update:modelValue", star);
  }
};
</script>

<style scoped>
.star-rating {
  display: inline-flex;
  gap: 0.25rem;
}

.star-btn {
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
  color: var(--border-color);
  transition:
    transform 0.15s ease,
    color 0.15s ease;
}

.star-btn:not(.is-readonly):hover {
  transform: scale(1.15);
}

.star-icon {
  width: 24px;
  height: 24px;
  fill: transparent;
  stroke: var(--text-muted);
}

.star-btn.is-active .star-icon {
  fill: var(--accent-secondary);
  stroke: var(--accent-secondary);
}

.star-btn.is-readonly {
  cursor: default;
}
</style>
