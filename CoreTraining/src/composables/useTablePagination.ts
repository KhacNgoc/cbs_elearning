import { computed, ref, watch, type Ref } from 'vue';

export function useTablePagination<T>(rows: Readonly<Ref<readonly T[]>>) {
  const page = ref(1);
  const pageSize = ref(10);
  const total = computed(() => rows.value.length);
  const pageCount = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)));
  const paginated = computed(() => rows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
  watch(pageSize, () => { page.value = 1; });
  watch(pageCount, count => { page.value = Math.min(page.value, count); });
  function reset() { page.value = 1; }
  return { page, pageSize, total, pageCount, paginated, reset };
}
