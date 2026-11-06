<template>
  <section class="deadline-editor">
    <h3>Hạn hoàn thành theo học viên</h3>
    <p class="deadline-help">
      Hạn riêng ưu tiên hơn hạn mặc định của khóa học.
    </p>
    <SkeletonLoader v-if="loading" variant="list" :rows="3" label="Đang tải khóa học" />
    <p v-else-if="error" role="alert">
      {{ error }}
      <button type="button" class="btn btn-secondary" @click="load">
        Thử lại
      </button>
    </p>
    <template v-else
      ><div v-for="row in rows" :key="row.courseId" class="deadline-row">
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
      <p v-if="!rows.length">Chưa có khóa học tham gia.</p></template
    >
  </section>
</template>
<script setup lang="ts">
import SkeletonLoader from "./SkeletonLoader.vue";
import { ref, watch } from "vue";
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
const props = defineProps<{ userId: number | string; disabled?: boolean }>();
const rows = ref<Deadline[]>([]),
  loading = ref(false),
  saving = ref(""),
  error = ref("");
let request = 0;
async function load() {
  const id = ++request;
  loading.value = true;
  error.value = "";
  try {
    const data = (
      await axiosClient.get<Deadline[]>(
        `/api/admin/users/${props.userId}/course-deadlines`,
      )
    ).data;
    if (id === request)
      rows.value = data.map((r) => ({ ...r, editDate: r.dueDate || "" }));
  } catch {
    if (id === request) error.value = "Không tải được hạn hoàn thành.";
  } finally {
    if (id === request) loading.value = false;
  }
}
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
