<template>
  <SearchSelect
    :id="id"
    :model-value="modelValue"
    :search="searchCourses"
    :all-label="allLabel"
    :allow-all="allowAll"
    :limit="limit"
    :selected-label="selectedLabel"
    :disabled="disabled"
    search-placeholder="Gõ tên hoặc mã khóa học..."
    empty-text="Không tìm thấy khóa học phù hợp."
    more-text="Gõ thêm để tìm các khóa học khác."
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>

<script setup lang="ts">
// Course filter for large catalogs: searches on the server instead of loading every course into a <select>.
import SearchSelect, { type SearchSelectOption } from "./SearchSelect.vue";
import { adminApi } from "../api/adminApi";

const props = withDefaults(
  defineProps<{
    modelValue: string;
    /** Admin page whose course permission scopes the search. */
    page?: "exams" | "documents";
    allLabel?: string;
    id?: string;
    limit?: number;
    /** Name of the course already in modelValue, when the parent knows it (e.g. opened from a link). */
    selectedLabel?: string;
    /** Offer an "all courses" choice; otherwise allLabel is only the placeholder. */
    allowAll?: boolean;
    disabled?: boolean;
    /** Only offer courses where the user has this action on `page` (e.g. "create"). */
    requiredAction?: string;
  }>(),
  { page: undefined, allLabel: "Tất cả khóa học", id: undefined, limit: 30, selectedLabel: "", allowAll: true, disabled: false, requiredAction: undefined },
);
const emit = defineEmits<{ "update:modelValue": [value: string] }>();

async function searchCourses(query: string, limit: number): Promise<SearchSelectOption[]> {
  const result = await adminApi.searchCourses(query || undefined, limit, props.page);
  const allowed = props.requiredAction && props.page
    ? result.filter((c) => c.adminActions?.[props.page!]?.includes(props.requiredAction!))
    : result;
  return allowed.map((c) => ({ value: c.id, label: c.name, hint: c.code }));
}
</script>
