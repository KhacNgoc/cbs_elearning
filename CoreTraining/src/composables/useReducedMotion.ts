import { onBeforeUnmount, onMounted, ref } from 'vue';

export function useReducedMotion() {
  const media = typeof window === 'undefined' ? null : window.matchMedia('(prefers-reduced-motion: reduce)');
  const reduced = ref(media?.matches || false);
  const update = (event: MediaQueryListEvent) => { reduced.value = event.matches; };
  onMounted(() => media?.addEventListener('change', update));
  onBeforeUnmount(() => media?.removeEventListener('change', update));
  return reduced;
}
