<template>
  <main class="reports-page">
    <header>
      <div>
        <h1>Báo cáo nội dung bài học</h1>
        <p>Xem phản ánh của học viên, ảnh minh chứng và lưu kết quả xử lý.</p>
      </div>
      <button class="btn btn-secondary" :disabled="loading" @click="load">
        Tải lại
      </button>
    </header>
    <div class="reports-filters">
      <input
        v-model="search"
        type="search"
        placeholder="Tìm bài học, khóa học, người báo cáo…"
        aria-label="Tìm báo cáo"
      /><select v-model="status" aria-label="Lọc trạng thái báo cáo">
        <option value="">Tất cả trạng thái</option>
        <option v-for="(label, key) in reportStatus" :key="key" :value="key">
          {{ label }}
        </option></select
      ><span
        >{{ newCount }} báo cáo mới</span
      >
    </div>
    <p v-if="error" class="report-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">Đang tải báo cáo…</p>
    <div v-else class="reports-table">
      <table>
        <thead>
          <tr>
            <th>Bài học / khóa học</th>
            <th>Người báo cáo</th>
            <th>Ngày gửi</th>
            <th>Trạng thái</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in pageItems" :key="r.id">
            <td>
              <strong>{{ r.lessonTitle }}</strong
              ><small>{{ r.courseName }}</small>
            </td>
            <td>{{ r.reporterName }}</td>
            <td>{{ formatDate(r.createdAt) }}</td>
            <td>
              <span class="status" :class="r.status">{{
                reportStatus[r.status]
              }}</span>
            </td>
            <td>
              <button class="report-link" @click="select(r)">
                Xem báo cáo #{{ r.id }}
              </button>
            </td>
          </tr>
          <tr v-if="!items.length">
            <td colspan="5" class="empty">Không có báo cáo phù hợp.</td>
          </tr>
        </tbody>
      </table>
    </div>
    <nav class="reports-filters" aria-label="Phân trang báo cáo">
      <button :disabled="loading || page <= 1" @click="page--">Trang trước</button>
      <span>Trang {{ page }}</span>
      <button :disabled="loading || !hasMore" @click="page++">Trang sau</button>
      <select v-model.number="pageSize" aria-label="Số báo cáo mỗi trang"><option v-for="size in [10,20,50,100]" :key="size" :value="size">{{ size }}</option></select>
    </nav>
    <AdminModal
      :open="!!detail"
      title="Chi tiết báo cáo nội dung"
      :busy="saving"
      :width="850"
      @close="detail = null"
      @submit="save"
    >
      <template v-if="detail"
        ><h2>{{ detail.lessonTitle }}</h2>
        <p class="report-meta">
          {{ detail.courseName }} · {{ detail.reporterName }} ·
          {{ formatDate(detail.createdAt) }}
        </p>
        <p class="report-content">{{ detail.content }}</p>
        <div class="report-images">
          <figure v-for="(src, index) in detail.images" :key="src">
            <ProtectedImage :src="src" :alt="`Ảnh minh chứng ${index + 1}`" />
            <figcaption>Ảnh minh chứng {{ index + 1 }}</figcaption>
          </figure>
        </div>
        <label class="report-field"
          >Trạng thái xử lý<select
            v-model="detail.status"
            :disabled="!detail.canReview"
          >
            <option
              v-for="(label, key) in reportStatus"
              :key="key"
              :value="key"
              :disabled="key === 'NEW'"
            >
              {{ label }}
            </option>
          </select></label
        ><label class="report-field"
          >Kết quả / ghi chú xử lý<textarea
            v-model="detail.resolution"
            :readonly="!detail.canReview"
            maxlength="5000"
            rows="4"
            placeholder="Ghi lại nội dung đã kiểm tra và biện pháp xử lý…"
          />
        </label>
        <p v-if="detail.reviewerName" class="report-meta">
          Xử lý gần nhất: {{ detail.reviewerName }} ·
          {{ formatDate(detail.reviewedAt!) }}
        </p>
        <p v-if="detailError" class="report-error" role="alert">
          {{ detailError }}
        </p></template
      >
      <template #footer
        ><button
          type="button"
          class="btn btn-secondary"
          :disabled="saving"
          @click="detail = null"
        >
          Đóng</button
        ><button
          v-if="detail?.canReview"
          type="submit"
          class="btn btn-primary"
          :disabled="saving"
        >
          {{ saving ? "Đang lưu…" : "Lưu xử lý" }}
        </button></template
      >
    </AdminModal>
  </main>
</template>
<script setup lang="ts">
import { ref, onBeforeUnmount, onMounted, watch } from "vue";
import { adminListApi } from "../api/adminListApi";
import { useRoute } from "vue-router";
import {
  contentToolsApi,
  contentToolsError,
  reportStatus,
  type LessonReport,
} from "../api/contentToolsApi";
import AdminModal from "../components/AdminModal.vue";
import ProtectedImage from "../components/ProtectedImage.vue";

import { showToast } from "../utils/toast";
const route = useRoute();
const items = ref<LessonReport[]>([]),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  detailError = ref(""),
  search = ref(""),
  status = ref(""),
  detail = ref<LessonReport | null>(null);
const page = ref(1), pageSize = ref(10), hasMore = ref(false), newCount = ref(0);
const pageItems = items;
const cursors = ref<(string | undefined)[]>([undefined]);
let requestId = 0, detailRequest = 0;
let filterTimer: ReturnType<typeof setTimeout> | undefined;
const formatDate = (value: string) => new Date(value).toLocaleString("vi-VN");
function select(r: LessonReport) {
  detail.value = {
    ...r,
    status: r.status === "NEW" && r.canReview ? "IN_REVIEW" : r.status,
    resolution: r.resolution || "",
  };
  detailError.value = "";
}
async function selectQuery() {
  const request = ++detailRequest;
  detail.value = null;
  const id = Number(route.query.report);
  if (!Number.isSafeInteger(id) || id < 1) return;
  try {
    const result = await adminListApi.report(id);
    if (request === detailRequest) select(result);
  } catch (e) { if (request === detailRequest) error.value = contentToolsError(e); }
}
async function load() {
  const request = ++requestId;
  loading.value = true;
  error.value = "";
  try {
    const result = await adminListApi.reports({ query: search.value, status: status.value, size: pageSize.value, cursor: cursors.value[page.value - 1] });
    if (request !== requestId) return;
    items.value = result.items; newCount.value = result.newCount; hasMore.value = result.hasMore;
    cursors.value[page.value] = result.nextCursor || undefined;
  } catch (e) { if (request === requestId) error.value = contentToolsError(e); }
  finally { if (request === requestId) loading.value = false; }
}
watch([search, status, pageSize], () => {
  ++requestId; clearTimeout(filterTimer); cursors.value = [undefined]; page.value = 1;
  filterTimer = setTimeout(() => { filterTimer = undefined; void load(); }, 250);
});
watch(page, () => { if (!filterTimer) void load(); });
onBeforeUnmount(() => { ++requestId; ++detailRequest; clearTimeout(filterTimer); });
async function save() {
  if (!detail.value || saving.value) return;
  saving.value = true;
  detailError.value = "";
  try {
    const saved = await contentToolsApi.review(detail.value);
    items.value = items.value.map((r) => (r.id === saved.id ? saved : r));
    detail.value = null;
    await load();
    showToast("Đã lưu kết quả xử lý báo cáo.");
  } catch (e) {
    detailError.value = contentToolsError(e);
  } finally {
    saving.value = false;
  }
}
watch(() => route.query.report, selectQuery);
onMounted(() => { void load(); void selectQuery(); });
</script>
<style scoped>
.reports-page {
  padding: 2rem;
  min-width: 0;
}
.reports-page header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}
.reports-page h1 {
  font-size: 1.5rem;
}
.reports-page header p,
.report-meta {
  color: var(--text-muted);
  font-size: 0.85rem;
  margin: 0.5rem 0 1rem;
}
.reports-filters {
  display: flex;
  gap: 1rem;
  align-items: center;
  flex-wrap: wrap;
  margin: 1.5rem 0;
}
.reports-filters input {
  flex: 1;
  min-width: 200px;
}
.reports-filters input,
.reports-filters select {
  padding: 0.7rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}
.reports-filters span {
  font-size: 0.85rem;
  color: var(--primary-ink);
}
.reports-table {
  overflow: auto;
}
.reports-table table {
  width: 100%;
  border-collapse: collapse;
}
.reports-table th,
.reports-table td {
  padding: 1rem;
  border-bottom: 1px solid var(--border-color);
  text-align: left;
  font-size: 0.875rem;
}
.reports-table th {
  background: var(--bg-main);
}
.reports-table small {
  display: block;
  margin-top: 0.4rem;
  color: var(--text-muted);
}
.status {
  display: inline-block;
  padding: 0.35rem 0.55rem;
  border-radius: 6px;
  background: var(--surface-f1f5f9);
  white-space: nowrap;
}
.status.NEW {
  color: var(--ink-9a5b00);
  background: var(--surface-fff4d6);
}
.status.RESOLVED {
  background: var(--primary-light);
  color: var(--primary-ink);
}
.report-link {
  background: none;
  border: 0;
  color: var(--primary-ink);
  cursor: pointer;
  text-decoration: underline;
}
.report-content {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  padding: 1rem;
  border-radius: 8px;
  background: var(--surface-f8fafc);
  line-height: 1.7;
}
.report-images {
  display: grid;
  gap: 1rem;
  margin: 1rem 0;
}
.report-images :deep(img) {
  max-width: 100%;
  max-height: 650px;
  object-fit: contain;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}
.report-images figcaption {
  font-size: 0.8rem;
  color: var(--text-muted);
  margin: 0.35rem 0;
}
.report-field {
  display: grid;
  gap: 0.5rem;
  margin: 1rem 0;
}
.report-error {
  color: var(--ink-b91c1c);
  margin: 1rem 0;
}
.empty {
  text-align: center;
  padding: 2rem !important;
  color: var(--text-muted);
}
@media (max-width: 650px) {
  .reports-filters > input,
  .reports-filters > select {
    flex: 1 1 100%;
    width: 100%;
    min-width: 0;
  }
  .reports-page {
    padding: 1rem;
  }
  .reports-table table {
    min-width: 650px;
  }
}
</style>
