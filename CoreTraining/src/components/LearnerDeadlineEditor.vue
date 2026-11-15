<template>
  <section class="deadline-editor">
    <h3>Hạn hoàn thành theo học viên</h3>
    <p class="deadline-help">
      Hạn riêng ưu tiên hơn hạn mặc định của khóa học.
    </p>
    <div class="search-box">
      <Search :size="16" /><input
        v-model="query"
        type="search"
        placeholder="Tìm mã hoặc tên khóa học..."
        aria-label="Tìm khóa học"
        @keydown.enter.prevent="search"
      /><span v-if="loading && loaded" class="search-status" role="status"
        >Đang tải…</span
      >
    </div>
    <!-- Skeleton only before the first page arrives; later loads keep the current rows in place. -->
    <SkeletonLoader
      v-if="loading && !loaded"
      variant="list"
      :rows="3"
      label="Đang tải khóa học"
    />
    <p v-else-if="error" role="alert">
      {{ error }}
      <button type="button" class="btn btn-secondary" @click="load">
        Thử lại
      </button>
    </p>
    <div
      v-else
      class="deadline-list"
      :class="{ refreshing: loading }"
      :aria-busy="loading"
    >
      <div v-for="row in rows" :key="row.courseId" class="deadline-row">
        <label
          >{{ row.courseName
          }}<input
            v-model="row.editDate"
            type="date"
            :disabled="disabled || saving === row.courseId"
          /><small
            >{{
              row.overridden ? "Đang dùng hạn riêng" : "Đang dùng hạn mặc định"
            }}
            · Mặc định: {{ formatLearningDate(row.defaultDate) }}</small
          ></label
        >
        <div>
          <button
            type="button"
            class="btn btn-secondary"
            :disabled="disabled || !!saving || !row.editDate"
            @click="save(row, false)"
          >
            Lưu hạn riêng</button
          ><button
            v-if="row.overridden"
            type="button"
            class="btn btn-secondary"
            :disabled="disabled || !!saving"
            @click="save(row, true)"
          >
            Dùng hạn mặc định
          </button>
        </div>
      </div>
      <p v-if="!rows.length" class="deadline-empty">
        {{
          query.trim()
            ? "Không có khóa học phù hợp với từ khóa."
            : "Chưa có khóa học tham gia."
        }}
      </p>
    </div>
    <TablePagination
      v-if="!error && total > 0"
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="total"
      :page-count="pageCount"
      unit="khóa học"
      unit-label="Khóa học"
      compact
    />
  </section>
</template>
<script setup lang="ts">
import SkeletonLoader from "./SkeletonLoader.vue";
import TablePagination from "./TablePagination.vue";
import { Search } from "lucide-vue-next";
import { computed, onBeforeUnmount, ref, watch } from "vue";
import { axiosClient } from "../api/axiosClient";
import { formatLearningDate } from "../utils/learning";
import { showToast } from "../utils/toast";
interface Deadline {
  courseId: string;
  courseName: string;
  dueDate?: string;
  defaultDate?: string;
  overridden: boolean;
  editDate: string;
}
interface DeadlinePage {
  items: Omit<Deadline, "editDate">[];
  page: number;
  size: number;
  totalElements: number;
}
const props = defineProps<{ userId: number | string; disabled?: boolean }>();
const rows = ref<Deadline[]>([]),
  loading = ref(false),
  saving = ref(""),
  error = ref(""),
  query = ref(""),
  page = ref(1),
  pageSize = ref(10),
  total = ref(0),
  // True once a page has been shown; later loads refresh it in place instead of a skeleton.
  loaded = ref(false);
const pageCount = computed(() =>
  Math.max(1, Math.ceil(total.value / pageSize.value)),
);
let request = 0;
let searchTimer: ReturnType<typeof setTimeout> | undefined;
// Loads only the shown page; the server filters by course name, code or id.
async function load() {
  const id = ++request;
  clearTimeout(searchTimer);
  loading.value = true;
  error.value = "";
  try {
    const data = (
      await axiosClient.get<DeadlinePage>(
        `/api/admin/users/${props.userId}/course-deadlines/paged`,
        {
          params: {
            page: page.value - 1,
            size: pageSize.value,
            search: query.value.trim() || undefined,
          },
        },
      )
    ).data;
    if (id !== request) return;
    total.value = data.totalElements;
    // A save or narrower search can leave the current page past the end.
    const last = Math.max(1, Math.ceil(data.totalElements / pageSize.value));
    if (page.value > last) {
      page.value = last;
      return;
    }
    rows.value = data.items.map((r) => ({ ...r, editDate: r.dueDate || "" }));
    loaded.value = true;
  } catch {
    if (id === request) error.value = "Không tải được hạn hoàn thành.";
  } finally {
    if (id === request) loading.value = false;
  }
}
// A new keyword starts from page 1; changing the page itself triggers the load.
// Also bound to Enter: the editor sits inside the account form, so Enter must search, not save the account.
function search() {
  clearTimeout(searchTimer);
  if (page.value !== 1) page.value = 1;
  else void load();
}
// Typing waits for a 300ms pause, so one request goes out per burst of keystrokes.
watch(query, () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(search, 300);
});
watch([page, pageSize], () => void load());
onBeforeUnmount(() => clearTimeout(searchTimer));
async function save(row: Deadline, reset: boolean) {
  saving.value = row.courseId;
  try {
    await axiosClient.put(`/api/admin/users/${props.userId}/course-deadlines`, {
      courseId: row.courseId,
      dueDate: reset ? null : row.editDate,
    });
    await load();
    showToast("Đã lưu hạn hoàn thành.");
  } catch {
    showToast("Không lưu được hạn hoàn thành.", "error");
  } finally {
    saving.value = "";
  }
}
// The parent keys this editor by account, so a new account starts on page 1.
watch(() => props.userId, load, { immediate: true });
</script>
<style scoped>
.deadline-editor {
  padding-top: 1rem;
  margin-top: 1rem;
  border-top: 1px solid var(--border-color);
}
.deadline-editor h3 {
  font-size: 1rem;
}
.deadline-help {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 0.5rem 0;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0.5rem 0;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  padding: 0.5rem 0.7rem;
  color: var(--text-muted);
  background: var(--surface-ffffff);
}
.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--text-main);
  font-size: 0.85rem;
}
.search-status {
  flex-shrink: 0;
  font-size: 0.75rem;
  white-space: nowrap;
}
.deadline-list {
  transition: opacity 0.15s;
}
/* Rows of the previous page stay visible but inert until the next page replaces them. */
.deadline-list.refreshing {
  opacity: 0.55;
  pointer-events: none;
}
.deadline-empty {
  padding: 1rem 0;
  font-size: 0.85rem;
  color: var(--text-muted);
}
.deadline-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
  border-bottom: 1px solid var(--border-color);
  padding: 1rem 0;
}
.deadline-row label {
  display: flex;
  flex: 1;
  min-width: 180px;
  flex-direction: column;
  gap: 0.5rem;
  font-size: 0.85rem;
}
.deadline-row input {
  padding: 0.5rem;
  border: 1px solid var(--border-color);
  border-radius: 6px;
}
.deadline-row small {
  color: var(--text-muted);
}
.deadline-row > div {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}
.deadline-row .btn {
  font-size: 0.75rem;
}
</style>
