<template>
  <section class="bank-panel">
    <header class="bank-heading">
      <div>
        <h1>
          {{ picker ? "Chọn câu hỏi từ ngân hàng" : "Ngân hàng câu hỏi" }}
        </h1>
        <p>
          {{
            picker
              ? "Chỉ câu hỏi đang sử dụng có thể thêm vào bài kiểm tra."
              : "Quản lý câu hỏi độc lập, dùng lại giữa các bài kiểm tra trong phạm vi được cấp."
          }}
        </p>
      </div>
      <button
        v-if="!picker && writableCourses.length"
        type="button"
        class="btn btn-primary"
        @click="edit()"
      >
        Thêm câu hỏi
      </button>
    </header>
    <p class="bank-note">
      Câu hỏi được sao chép khi thêm vào bài kiểm tra. Các lần sửa hoặc ngừng sử
      dụng trong ngân hàng không thay đổi bản đã thêm.
    </p>
    <div class="bank-filters">
      <input
        v-model="search"
        type="search"
        aria-label="Tìm câu hỏi trong ngân hàng"
        placeholder="Tìm mã, nội dung, bài học hoặc lĩnh vực…"
      /><select v-model="status" aria-label="Lọc trạng thái">
        <option value="">Tất cả trạng thái</option>
        <option v-for="(label, key) in bankStatus" :key="key" :value="key">
          {{ label }}
        </option></select
      ><select v-model="difficulty" aria-label="Lọc mức độ khó">
        <option value="">Tất cả mức độ</option>
        <option v-for="(label, key) in difficultyLabel" :key="key" :value="key">
          {{ label }}
        </option></select
      ><button
        type="button"
        class="btn btn-secondary"
        :disabled="loading"
        @click="load"
      >
        Tải lại
      </button>
    </div>
    <p v-if="error" class="tool-error" role="alert">{{ error }}</p>
    <p v-if="loading" role="status">Đang tải ngân hàng câu hỏi…</p>
    <div v-else class="bank-table">
      <table>
        <thead>
          <tr>
            <th v-if="picker">Chọn</th>
            <th>Mã / câu hỏi</th>
            <th>Phân loại</th>
            <th>Liên quan</th>
            <th>Trạng thái</th>
            <th>Thao tác</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="q in pageItems" :key="q.id">
            <td v-if="picker">
              <input
                type="checkbox"
                v-model="selected"
                :value="q.id"
                :disabled="
                  q.status !== 'ACTIVE' || existingIds?.includes(q.id!)
                "
                :aria-label="`Chọn ${q.code}`"
              />
            </td>
            <td>
              <strong>{{ q.code }}</strong>
              <p>{{ q.text }}</p>
              <small v-if="existingIds?.includes(q.id!)"
                >Đã có trong bài kiểm tra</small
              >
            </td>
            <td>
              {{ q.type === "MULTIPLE_CHOICE" ? "Nhiều đáp án" : "Một đáp án"
              }}<small>{{ difficultyLabel[q.difficulty] }}</small>
            </td>
            <td>
              {{ q.lessonTitle || q.domainName || q.courseName
              }}<small>{{ q.courseName }}</small>
            </td>
            <td>
              <span class="bank-badge" :class="q.status">{{
                bankStatus[q.status]
              }}</span>
            </td>
            <td>
              <button type="button" class="bank-link" @click="inspect(q)">
                Xem</button
              ><button
                v-if="!picker && q.allowedActions?.includes('update')"
                type="button"
                class="bank-link"
                @click="edit(q)"
              >
                Sửa
              </button>
            </td>
          </tr>
          <tr v-if="!filtered.length">
            <td :colspan="picker ? 6 : 5" class="bank-empty">
              Chưa có câu hỏi phù hợp. Có thể thêm mới hoặc lưu câu hỏi từ một
              bài kiểm tra hiện có.
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    <TablePagination
      v-model:page="page"
      v-model:page-size="pageSize"
      :total="filtered.length"
      :page-count="pageCount"
      unit="câu hỏi"
      unit-label="Câu hỏi"
    />
    <div v-if="picker" class="bank-selection">
      <span>Đã chọn {{ selected.length }} câu</span
      ><button
        type="button"
        class="btn btn-primary"
        :disabled="!selected.length || saving || loading"
        @click="add"
      >
        {{ saving ? "Đang thêm…" : "Thêm vào bài kiểm tra" }}
      </button>
    </div>
    <AdminModal :open="!!detail" title="Chi tiết câu hỏi" @close="detail = null"
      ><template v-if="detail"
        ><strong
          >{{ detail.code }} · {{ difficultyLabel[detail.difficulty] }}</strong
        >
        <p class="question-body">{{ detail.text }}</p>
        <ol class="bank-answers" type="A">
          <li
            v-for="(option, index) in detail.options"
            :key="index"
            :class="{ correct: detail.correctAnswers.includes(index) }"
          >
            {{ option }}
            <strong v-if="detail.correctAnswers.includes(index)"
              >✓ Đáp án đúng</strong
            >
          </li>
        </ol>
        <p class="question-body">
          <strong>Giải thích: </strong
          >{{ detail.explanation || "Chưa có giải thích." }}
        </p></template
      ><template #footer
        ><button type="button" class="btn btn-secondary" @click="detail = null">
          Đóng
        </button></template
      ></AdminModal
    >
    <AdminModal
      :open="formOpen"
      :title="form.id ? 'Sửa câu hỏi ngân hàng' : 'Thêm câu hỏi vào ngân hàng'"
      :busy="saving"
      :width="820"
      @close="formOpen = false"
      @submit="save"
    >
      <div class="bank-form-grid">
        <label
          >Mã câu hỏi<input
            v-model="form.code"
            maxlength="80"
            pattern="[A-Za-z0-9][A-Za-z0-9_-]{0,79}"
            required
            placeholder="VD: TD-CN-001" /></label
        ><label
          >Khóa học quản lý<select
            v-model="form.courseId"
            required
            @change="form.lessonId = ''"
          >
            <option value="" disabled>Chọn khóa học</option>
            <option v-for="c in formCourses" :key="c.id" :value="c.id">
              {{ c.name }}
            </option>
          </select></label
        ><label
          >Bài học liên quan<select v-model="form.lessonId">
            <option value="">Theo lĩnh vực của khóa học</option>
            <option
              v-for="l in relatedCourse?.lessons"
              :key="l.id"
              :value="l.id"
            >
              {{ l.title }}
            </option>
          </select></label
        ><label
          >Lĩnh vực liên quan<input
            :value="
              relatedCourse?.domainName || 'Theo lĩnh vực của khóa học đã chọn'
            "
            readonly
        /></label>
      </div>
      <label class="bank-field"
        >Nội dung câu hỏi<textarea
          v-model="form.text"
          rows="4"
          maxlength="20000"
          required
        />
      </label>
      <div class="bank-form-grid">
        <label
          >Loại câu hỏi<select
            v-model="form.type"
            @change="form.correctAnswers = [0]"
          >
            <option value="SINGLE_CHOICE">Một đáp án đúng</option>
            <option value="MULTIPLE_CHOICE">Nhiều đáp án đúng</option>
          </select></label
        ><label
          >Mức độ khó<select v-model="form.difficulty">
            <option
              v-for="(label, key) in difficultyLabel"
              :key="key"
              :value="key"
            >
              {{ label }}
            </option>
          </select></label
        >
      </div>
      <p class="bank-help">
        Đánh dấu đáp án đúng. Câu nhiều lựa chọn cần ít nhất 2 đáp án đúng và
        không chọn tất cả.
      </p>
      <div class="bank-option" v-for="(_, index) in form.options" :key="index">
        <input
          :type="form.type === 'SINGLE_CHOICE' ? 'radio' : 'checkbox'"
          name="bank-correct"
          :checked="form.correctAnswers.includes(index)"
          :aria-label="`Đáp án ${String.fromCharCode(65 + index)} đúng`"
          @change="toggleAnswer(index)"
        /><strong>{{ String.fromCharCode(65 + index) }}</strong
        ><input
          v-model="form.options[index]"
          :aria-label="`Nội dung đáp án ${String.fromCharCode(65 + index)}`"
          maxlength="5000"
          required
        /><button
          v-if="form.options.length > 3"
          type="button"
          @click="removeOption(index)"
          :aria-label="`Xóa đáp án ${index + 1}`"
        >
          ×
        </button>
      </div>
      <button
        v-if="form.options.length < 6"
        type="button"
        class="bank-link"
        @click="form.options.push('')"
      >
        + Thêm đáp án
      </button>
      <label class="bank-field"
        >Giải thích<textarea
          v-model="form.explanation"
          rows="3"
          maxlength="20000"
          placeholder="Giải thích lý do chọn đáp án đúng…"
        /></label
      ><label class="bank-field"
        >Trạng thái sử dụng<select v-model="form.status">
          <option v-for="(label, key) in bankStatus" :key="key" :value="key">
            {{ label }}
          </option>
        </select></label
      >
      <p v-if="formError" class="tool-error" role="alert">{{ formError }}</p>
    </AdminModal>
  </section>
</template>
<script setup lang="ts">
import { ref, computed, onMounted } from "vue";
import { axiosClient } from "../api/axiosClient";
import {
  contentToolsApi,
  contentToolsError,
  bankStatus,
  difficultyLabel,
  type BankQuestion,
} from "../api/contentToolsApi";
import type { Course } from "../types";
import AdminModal from "./AdminModal.vue";
import TablePagination from "./TablePagination.vue";
import { useTablePagination } from "../composables/useTablePagination";
import { showToast } from "../utils/toast";
const props = defineProps<{
  picker?: boolean;
  lessonId?: string;
  existingIds?: number[];
}>();
const emit = defineEmits<{ added: [] }>();
const items = ref<BankQuestion[]>([]),
  courses = ref<Course[]>([]),
  loading = ref(false),
  saving = ref(false),
  error = ref(""),
  search = ref(""),
  status = ref(props.picker ? "ACTIVE" : ""),
  difficulty = ref(""),
  selected = ref<number[]>([]),
  detail = ref<BankQuestion | null>(null),
  formOpen = ref(false),
  formError = ref("");
const blank = (): BankQuestion => ({
  code: "",
  courseId: "",
  lessonId: "",
  text: "",
  type: "SINGLE_CHOICE",
  options: ["", "", "", ""],
  correctAnswers: [0],
  explanation: "",
  difficulty: "MEDIUM",
  status: "DRAFT",
});
const form = ref<BankQuestion>(blank());
const writableCourses = computed(() =>
  courses.value.filter((c) => c.adminActions?.exams?.includes("create")),
);
const formCourses = computed(() =>
  courses.value.filter((c) =>
    c.adminActions?.exams?.includes(form.value.id ? "update" : "create"),
  ),
);
const relatedCourse = computed(() =>
  courses.value.find((c) => c.id === form.value.courseId),
);
const filtered = computed(() =>
  items.value.filter(
    (q) =>
      (!status.value || q.status === status.value) &&
      (!difficulty.value || q.difficulty === difficulty.value) &&
      [q.code, q.text, q.lessonTitle, q.domainName, q.courseName]
        .join(" ")
        .toLocaleLowerCase("vi")
        .includes(search.value.toLocaleLowerCase("vi")),
  ),
);
const {
  page,
  pageSize,
  pageCount,
  paginated: pageItems,
} = useTablePagination(filtered);
async function load() {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const results = await Promise.all([
      contentToolsApi.bank(),
      axiosClient.get<Course[]>("/api/admin/courses", {
        params: { page: "exams" },
      }),
    ]);
    items.value = results[0];
    courses.value = results[1].data;
    selected.value = selected.value.filter((id) =>
      items.value.some((q) => q.id === id && q.status === "ACTIVE"),
    );
  } catch (e) {
    error.value = contentToolsError(e);
  } finally {
    loading.value = false;
  }
}
function inspect(q: BankQuestion) {
  detail.value = q;
}
function edit(q?: BankQuestion) {
  form.value = q
    ? {
        ...q,
        lessonId: q.lessonId || "",
        options: [...q.options],
        correctAnswers: [...q.correctAnswers],
      }
    : blank();
  formError.value = "";
  formOpen.value = true;
}
function toggleAnswer(index: number) {
  form.value.correctAnswers =
    form.value.type === "SINGLE_CHOICE"
      ? [index]
      : form.value.correctAnswers.includes(index)
        ? form.value.correctAnswers.filter((i) => i !== index)
        : [...form.value.correctAnswers, index];
}
function removeOption(index: number) {
  form.value.options.splice(index, 1);
  form.value.correctAnswers = form.value.correctAnswers
    .filter((i) => i !== index)
    .map((i) => (i > index ? i - 1 : i));
}
async function save() {
  if (saving.value) return;
  saving.value = true;
  formError.value = "";
  try {
    await contentToolsApi.saveBank(form.value);
    formOpen.value = false;
    showToast("Đã lưu câu hỏi ngân hàng.");
    await load();
  } catch (e) {
    formError.value = contentToolsError(e);
  } finally {
    saving.value = false;
  }
}
async function add() {
  if (!props.lessonId || saving.value) return;
  saving.value = true;
  error.value = "";
  try {
    await contentToolsApi.addToQuiz(props.lessonId, selected.value);
    selected.value = [];
    showToast("Đã thêm câu hỏi vào bài kiểm tra.");
    emit("added");
  } catch (e) {
    error.value = contentToolsError(e);
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<style scoped>
.bank-panel {
  min-width: 0;
}
.bank-heading {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: flex-start;
}
.bank-heading h1 {
  font-size: 1.4rem;
}
.bank-heading p,
.bank-help {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin: 0.5rem 0 1rem;
}
.bank-note {
  padding: 0.85rem 1rem;
  background: var(--primary-light);
  color: var(--primary-ink);
  border-radius: 8px;
  margin: 1rem 0;
  font-size: 0.85rem;
}
.bank-filters {
  display: flex;
  gap: 0.7rem;
  margin: 1rem 0;
  flex-wrap: wrap;
}
.bank-filters input {
  flex: 1;
  min-width: 200px;
}
.bank-filters input,
.bank-filters select {
  padding: 0.65rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
}
.bank-table {
  overflow: auto;
}
.bank-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.875rem;
}
.bank-table th,
.bank-table td {
  padding: 0.9rem;
  border-bottom: 1px solid var(--border-color);
  text-align: left;
  vertical-align: top;
}
.bank-table th {
  background: var(--bg-main);
  white-space: nowrap;
}
.bank-table td p {
  white-space: pre-wrap;
  margin-top: 0.5rem;
  max-width: 460px;
  overflow-wrap: anywhere;
}
.bank-table small {
  display: block;
  color: var(--text-muted);
  margin-top: 0.4rem;
}
.bank-badge {
  display: inline-block;
  padding: 0.3rem 0.55rem;
  border-radius: 6px;
  background: var(--surface-edf2f7);
  white-space: nowrap;
  font-size: 0.75rem;
}
.bank-badge.ACTIVE {
  background: var(--primary-light);
  color: var(--primary-ink);
}
.bank-link {
  border: 0;
  background: transparent;
  color: var(--primary-ink);
  text-decoration: underline;
  cursor: pointer;
  padding: 0.4rem;
}
.bank-empty {
  text-align: center;
  padding: 2rem !important;
  color: var(--text-muted);
}
.bank-selection {
  position: sticky;
  bottom: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 0;
  background: var(--surface-ffffff);
  border-top: 1px solid var(--border-color);
}
.tool-error {
  color: var(--ink-b91c1c);
  margin: 1rem 0;
}
.bank-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}
.bank-form-grid label,
.bank-field {
  display: grid;
  gap: 0.45rem;
}
.bank-field {
  margin: 1rem 0;
}
.bank-option {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin: 0.6rem 0;
}
.bank-option > input[type="radio"],
.bank-option > input[type="checkbox"] {
  flex: 0 0 auto;
  width: 18px;
  height: 18px;
}
.bank-option > input:not([type]) {
  flex: 1;
  min-width: 0;
}
.bank-option button {
  background: transparent;
  border: 0;
  color: var(--ink-b91c1c);
  cursor: pointer;
  font-size: 1.4rem;
}
.bank-answers {
  padding-left: 1.5rem;
}
.bank-answers li {
  padding: 0.6rem;
  margin: 0.3rem 0;
}
.bank-answers .correct {
  background: var(--primary-light);
  color: var(--primary-ink);
}
.bank-answers strong {
  font-size: 0.8rem;
}
.question-body {
  white-space: pre-wrap;
  margin: 1rem 0;
  overflow-wrap: anywhere;
}
@media (max-width: 650px) {
  .bank-filters > input,
  .bank-filters > select {
    flex: 1 1 100%;
    width: 100%;
    min-width: 0;
  }
  .bank-selection {
    flex-wrap: wrap;
    gap: .75rem;
  }
  .bank-form-grid {
    grid-template-columns: 1fr;
  }
  .bank-heading {
    flex-direction: column;
  }
  .bank-table table {
    min-width: 650px;
  }
}
</style>
