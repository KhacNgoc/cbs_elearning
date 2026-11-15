<template>
  <div ref="root" class="search-select" :class="`search-select-${size}`" @keydown.esc.stop="close">
    <button
      :id="id"
      type="button"
      class="search-select-trigger"
      :class="{ open }"
      aria-haspopup="listbox"
      :aria-expanded="open"
      :aria-label="ariaLabel"
      :disabled="disabled"
      @click="toggle"
    >
      <span :class="{ placeholder: !modelValue }">{{ triggerText }}</span>
      <ChevronDown :size="16" aria-hidden="true" />
    </button>
    <div v-if="open" class="search-select-popover">
      <div class="search-select-input">
        <Search :size="15" aria-hidden="true" />
        <input
          ref="input"
          v-model="query"
          type="search"
          :placeholder="searchPlaceholder"
          :aria-label="searchPlaceholder"
          @input="onInput"
          @keydown.down.prevent="move(1)"
          @keydown.up.prevent="move(-1)"
          @keydown.enter.prevent="chooseActive"
        />
        <LoaderCircle v-if="loading" :size="14" class="spin" aria-hidden="true" />
      </div>
      <ul ref="list" class="search-select-list" role="listbox">
        <li v-if="allowAll">
          <button type="button" role="option" :aria-selected="!modelValue" :class="{ selected: !modelValue }" @click="choose(null)">
            {{ allLabel }}
          </button>
        </li>
        <li v-for="(option, index) in shown" :key="option.value">
          <button
            type="button"
            role="option"
            :aria-selected="option.value === modelValue"
            :class="{ selected: option.value === modelValue, active: index === activeIndex }"
            @click="choose(option)"
          >
            {{ option.label }}<small v-if="option.hint">{{ option.hint }}</small>
          </button>
        </li>
        <li v-if="!loading && !shown.length" class="search-select-note">{{ emptyText }}</li>
        <li v-else-if="hasMore" class="search-select-note">{{ moreText }}</li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Select for long lists (courses, programmes, units). It never renders the whole list: either `search` asks the
 * server for at most `limit` matches, or `options` (already loaded) are filtered here and the first `limit`
 * matches are shown. Matching ignores case and Vietnamese diacritics.
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from "vue";
import { ChevronDown, LoaderCircle, Search } from "lucide-vue-next";

export interface SearchSelectOption {
  value: string;
  label: string;
  /** Second line, e.g. a course code or the unit type. */
  hint?: string;
}

const props = withDefaults(
  defineProps<{
    modelValue: string;
    /** Loaded options, filtered here. */
    options?: SearchSelectOption[];
    /** Server search; used instead of `options` when given. */
    search?: (query: string, limit: number) => Promise<SearchSelectOption[]>;
    /** "All" choice, and the trigger text while nothing is selected. */
    allLabel?: string;
    /** Offer allLabel as a choice that clears the value; otherwise it is only the placeholder. */
    allowAll?: boolean;
    searchPlaceholder?: string;
    emptyText?: string;
    moreText?: string;
    limit?: number;
    /** Label of the selected value when it is not among the loaded options (e.g. opened from a link). */
    selectedLabel?: string;
    id?: string;
    ariaLabel?: string;
    disabled?: boolean;
    /** "sm" matches the 37px filter selects of the report and list pages. */
    size?: "md" | "sm";
  }>(),
  {
    options: () => [],
    search: undefined,
    allLabel: "Tất cả",
    allowAll: true,
    searchPlaceholder: "Gõ để tìm...",
    emptyText: "Không tìm thấy kết quả phù hợp.",
    moreText: "Gõ thêm để tìm các kết quả khác.",
    limit: 30,
    selectedLabel: "",
    id: undefined,
    ariaLabel: undefined,
    disabled: false,
    size: "md",
  },
);
const emit = defineEmits<{ "update:modelValue": [value: string]; select: [option: SearchSelectOption | null] }>();

const root = ref<HTMLElement | null>(null);
const input = ref<HTMLInputElement | null>(null);
const list = ref<HTMLElement | null>(null);
const open = ref(false);
const query = ref("");
const remote = ref<SearchSelectOption[]>([]);
const loading = ref(false);
const chosenLabel = ref("");
const activeIndex = ref(-1);
let timer: ReturnType<typeof setTimeout> | undefined;
let requestId = 0;

const normalize = (text: string) =>
  text.toLocaleLowerCase("vi").normalize("NFD").replace(/\p{M}/gu, "").replace(/đ/g, "d");

const matches = computed(() => {
  if (props.search) return remote.value;
  const term = normalize(query.value.trim());
  return term ? props.options.filter((o) => normalize(`${o.label} ${o.hint ?? ""}`).includes(term)) : props.options;
});
const shown = computed(() => matches.value.slice(0, props.limit));
const hasMore = computed(() => (props.search ? remote.value.length >= props.limit : matches.value.length > props.limit));
const triggerText = computed(() => {
  if (!props.modelValue) return props.allLabel;
  const known = props.options.find((o) => o.value === props.modelValue) ?? remote.value.find((o) => o.value === props.modelValue);
  return known?.label || chosenLabel.value || props.selectedLabel || props.modelValue;
});

async function runSearch() {
  if (!props.search) return;
  const id = ++requestId;
  loading.value = true;
  try {
    const result = await props.search(query.value.trim(), props.limit);
    if (id === requestId) remote.value = result;
  } catch {
    if (id === requestId) remote.value = [];
  } finally {
    if (id === requestId) loading.value = false;
  }
}

function onInput() {
  activeIndex.value = -1;
  if (!props.search) return;
  clearTimeout(timer);
  timer = setTimeout(runSearch, 250);
}

async function toggle() {
  if (props.disabled) return;
  if (open.value) return close();
  open.value = true;
  activeIndex.value = -1;
  if (props.search && (!remote.value.length || query.value)) {
    query.value = "";
    void runSearch();
  } else query.value = "";
  await nextTick();
  input.value?.focus();
}

function close() {
  open.value = false;
}

function choose(option: SearchSelectOption | null) {
  chosenLabel.value = option?.label ?? "";
  emit("update:modelValue", option?.value ?? "");
  emit("select", option);
  close();
}

function move(step: number) {
  if (!shown.value.length) return;
  activeIndex.value = (activeIndex.value + step + shown.value.length) % shown.value.length;
  void nextTick(() => list.value?.querySelector(".active")?.scrollIntoView({ block: "nearest" }));
}

function chooseActive() {
  const option = shown.value[activeIndex.value] ?? (shown.value.length === 1 ? shown.value[0] : undefined);
  if (option) choose(option);
}

function onDocumentClick(event: MouseEvent) {
  if (open.value && root.value && !root.value.contains(event.target as Node)) close();
}

onMounted(() => document.addEventListener("mousedown", onDocumentClick));
onBeforeUnmount(() => {
  document.removeEventListener("mousedown", onDocumentClick);
  clearTimeout(timer);
  ++requestId;
});
</script>

<style scoped>
.search-select {
  position: relative;
  min-width: 0;
}
.search-select-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  width: 100%;
  min-height: 44px;
  padding: 0.65rem 0.8rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-ffffff);
  color: var(--text-main);
  font: inherit;
  font-weight: 400;
  text-align: left;
  cursor: pointer;
}
.search-select-sm .search-select-trigger {
  min-height: 37px;
  padding: 0 10px;
  border-radius: 6px;
  font-size: 12px;
}
.search-select-trigger span {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.search-select-trigger .placeholder {
  color: var(--text-main);
}
.search-select-trigger:disabled {
  cursor: not-allowed;
  opacity: 0.7;
}
.search-select-trigger:focus-visible,
.search-select-trigger.open {
  outline: 2px solid var(--primary-color);
  outline-offset: 1px;
}
.search-select-popover {
  position: absolute;
  z-index: 30;
  top: calc(100% + 4px);
  left: 0;
  right: 0;
  min-width: 240px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-ffffff);
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.12);
  overflow: hidden;
}
.search-select-input {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.75rem;
  border-bottom: 1px solid var(--border-color);
  color: var(--text-muted);
}
.search-select-input input {
  flex: 1;
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-main);
  font: inherit;
  font-size: 16px;
}
.search-select-list {
  max-height: 280px;
  margin: 0;
  padding: 0.25rem;
  overflow-y: auto;
  list-style: none;
}
.search-select-list button {
  display: block;
  width: 100%;
  padding: 0.55rem 0.65rem;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--text-main);
  font: inherit;
  font-weight: 400;
  text-align: left;
  cursor: pointer;
}
.search-select-sm .search-select-list button {
  font-size: 12px;
}
.search-select-list button:hover,
.search-select-list button.selected,
.search-select-list button.active {
  background: var(--primary-light);
}
.search-select-list small {
  display: block;
  margin-top: 0.15rem;
  color: var(--text-muted);
  font-size: 0.75rem;
}
.search-select-note {
  padding: 0.6rem 0.65rem;
  color: var(--text-muted);
  font-size: 0.78rem;
  font-weight: 400;
}
.spin {
  animation: search-select-spin 1s linear infinite;
}
@keyframes search-select-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
