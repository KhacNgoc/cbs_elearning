<template>
  <div class="flashcard-manager">
    <section class="editor-card">
      <div class="section-heading">
        <span><Layers3 :size="16" /></span>
        <div>
          <h2>Flashcard bài học</h2>
          <p>
            {{ cards.length }} thẻ · {{ publishedCount }} đã phát hành. Flashcard hỗ trợ ghi nhớ,
            không tính vào điều kiện hoàn thành bài học.
          </p>
        </div>
        <div v-if="canCreate" class="question-tools">
          <button class="ai-button" :disabled="generating" @click="showAiPanel = !showAiPanel">
            <Sparkles :size="16" /> {{ generating ? "Đang tạo..." : "Tạo bằng AI" }}
          </button>
          <button class="primary-button" @click="openForm()"><Plus :size="16" /> Thêm thẻ</button>
        </div>
      </div>

      <section v-if="canCreate && showAiPanel" class="ai-panel">
        <div class="ai-panel-heading">
          <div>
            <strong>Tạo flashcard nháp từ tài liệu của bài học</strong>
            <small>AI chỉ dùng nguồn kiến thức đã lưu của bài. Thẻ được kiểm định và chỉ hiển thị với học viên sau khi phát hành.</small>
          </div>
          <button aria-label="Đóng" @click="showAiPanel = false"><X :size="18" /></button>
        </div>
        <LessonKnowledgeSources
          permission-page="courses"
          :key="`${courseId}:${lessonId}`"
          :course-id="courseId"
          :lesson-id="lessonId"
          :disabled="generating || savingDrafts || !canUpdate"
          @ready="sourceReady = $event"
        />
        <div class="ai-settings">
          <label><span>Số thẻ</span><input v-model.number="aiConfig.count" type="number" min="1" max="20" /></label>
          <fieldset class="type-choices">
            <legend>Loại thẻ</legend>
            <label v-for="type in TYPES" :key="type">
              <input v-model="aiConfig.types" type="checkbox" :value="type" /> {{ TYPE_LABELS[type] }}
            </label>
          </fieldset>
          <button class="primary-button" :disabled="generating || !sourceReady || !aiConfig.types.length" @click="generate">
            <LoaderCircle v-if="generating" class="spin" :size="17" />
            <Sparkles v-else :size="17" />
            {{ generating ? `Đang tạo... ${progress?.percent ?? 0}%` : "Tạo bản nháp" }}
          </button>
        </div>
        <div v-if="progress" class="ai-progress" :class="{ failed: progress.failed }" role="status" aria-live="polite">
          <div class="ai-progress-header"><span>{{ progress.label }}</span><strong>{{ progress.percent }}%</strong></div>
          <div class="ai-progress-track" role="progressbar" aria-label="Tiến độ tạo flashcard" aria-valuemin="0" aria-valuemax="100" :aria-valuenow="progress.percent">
            <div class="ai-progress-fill" :style="{ width: `${progress.percent}%` }" />
          </div>
          <small v-if="progress.detail">{{ progress.detail }}</small>
        </div>
        <p v-for="warning in aiWarnings" :key="warning" class="ai-warning">{{ warning }}</p>

        <div v-if="drafts.length" class="ai-drafts">
          <div class="admin-table-scroll">
            <table class="admin-data-table">
              <colgroup><col style="width:80px" /><col /></colgroup>
              <thead><tr><th>Chọn</th><th>Bản nháp flashcard</th></tr></thead>
              <tbody>
                <tr v-for="(draft, index) in drafts" :key="index">
                  <td>
                    <ToggleSwitch v-model="draft.selected" :show-label="false" :label="`Chọn thẻ ${index + 1}`" :disabled="savingDrafts" />
                  </td>
                  <td class="card-cell">
                    <div class="draft-heading-actions">
                      <span class="question-type-badge">{{ TYPE_LABELS[draft.type] }}</span>
                      <button v-if="editingDraft !== index" class="draft-edit-button" @click="editingDraft = index">
                        <Edit :size="15" /> Sửa
                      </button>
                    </div>
                    <div v-if="editingDraft === index" class="ai-draft-editor">
                      <label class="field"><span>Mặt trước</span><textarea v-model="draft.front" rows="2" maxlength="1000" /></label>
                      <label class="field"><span>Mặt sau</span><textarea v-model="draft.back" rows="3" maxlength="4000" /></label>
                      <p v-if="flashcardDraftError(draft)" class="field-error">{{ flashcardDraftError(draft) }}</p>
                      <div class="draft-editor-actions">
                        <button class="primary-button" :disabled="!!flashcardDraftError(draft)" @click="editingDraft = null">Hoàn tất chỉnh sửa</button>
                      </div>
                    </div>
                    <template v-else>
                      <strong class="card-front">{{ draft.front }}</strong>
                      <p class="card-back">{{ draft.back }}</p>
                    </template>
                    <small v-if="draft.source" class="draft-sources">Nguồn: {{ sourceLabel(draft.source) }}</small>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="ai-draft-actions">
            <button class="secondary-button" :disabled="savingDrafts" @click="discardDrafts">Bỏ bản nháp</button>
            <button class="secondary-button" :disabled="savingDrafts || !selectedDrafts.length" @click="saveDrafts('DRAFT')">
              Lưu nháp {{ selectedDrafts.length }} thẻ
            </button>
            <button class="primary-button" :disabled="savingDrafts || !selectedDrafts.length" @click="saveDrafts('PUBLISHED')">
              {{ savingDrafts ? "Đang lưu..." : `Lưu & phát hành ${selectedDrafts.length} thẻ` }}
            </button>
          </div>
        </div>
      </section>

      <div class="list-toolbar">
        <label class="search-box">
          <Search :size="18" />
          <input v-model="search" type="search" aria-label="Tìm flashcard" placeholder="Tìm theo nội dung mặt trước hoặc mặt sau..." />
        </label>
        <select v-model="statusFilter" aria-label="Lọc theo trạng thái">
          <option value="">Tất cả trạng thái</option>
          <option value="PUBLISHED">Đã phát hành</option>
          <option value="DRAFT">Bản nháp</option>
        </select>
        <div v-if="canUpdate && selectedIds.length" class="bulk-actions">
          <span>Đã chọn {{ selectedIds.length }}</span>
          <button class="secondary-button" :disabled="bulkSaving" @click="setStatus(selectedIds, 'PUBLISHED')">Phát hành</button>
          <button class="secondary-button" :disabled="bulkSaving" @click="setStatus(selectedIds, 'DRAFT')">Chuyển về nháp</button>
        </div>
      </div>

      <div class="admin-table-scroll">
        <table class="admin-data-table flashcard-table">
          <colgroup>
            <col style="width:48px" /><col style="width:5%" /><col style="width:43%" /><col style="width:12%" />
            <col style="width:14%" /><col style="width:12%" /><col style="width:14%" />
          </colgroup>
          <thead>
            <tr>
              <th scope="col">
                <input v-if="canUpdate" type="checkbox" aria-label="Chọn tất cả thẻ trên trang" :checked="allOnPageSelected" @change="togglePage" />
              </th>
              <th scope="col">STT</th>
              <th scope="col">Mặt trước / mặt sau</th>
              <th scope="col">Loại</th>
              <th scope="col">Nguồn</th>
              <th scope="col">Trạng thái</th>
              <th scope="col">Thao tác</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading"><td colspan="7" class="admin-empty">Đang tải flashcard...</td></tr>
            <template v-else>
            <tr v-for="(card, index) in paginated" :key="card.id">
              <td><input v-if="canUpdate" v-model="selectedIds" type="checkbox" :value="card.id" :aria-label="`Chọn thẻ ${card.front}`" /></td>
              <td>{{ (page - 1) * pageSize + index + 1 }}</td>
              <td class="card-cell">
                <strong class="card-front">{{ card.front }}</strong>
                <p class="card-back">{{ card.back }}</p>
              </td>
              <td><span class="question-type-badge">{{ TYPE_LABELS[card.type] }}</span></td>
              <td>
                <div class="source-cell">
                  <span class="origin-badge" :class="card.origin.toLowerCase()">{{ card.origin === "AI" ? "AI tạo" : "Thủ công" }}</span>
                  <small v-if="card.source">{{ sourceLabel(card.source) }}</small>
                </div>
              </td>
              <td>
                <span class="status-badge" :class="{ published: card.status === 'PUBLISHED' }">
                  {{ card.status === "PUBLISHED" ? "Đã phát hành" : "Bản nháp" }}
                </span>
              </td>
              <td>
                <div v-if="canUpdate || canDelete" class="admin-row-actions">
                  <button v-if="canUpdate" type="button" class="admin-row-edit" @click="openForm(card)">Sửa</button>
                  <button v-if="canDelete" type="button" class="admin-row-delete" @click="deleting = card">Xóa</button>
                </div>
                <span v-else>Chỉ xem</span>
              </td>
            </tr>
            </template>
            <tr v-if="!loading && !filtered.length">
              <td colspan="7" class="admin-empty">
                {{ search || statusFilter ? "Không tìm thấy flashcard phù hợp." : "Bài học chưa có flashcard. Thêm thẻ thủ công hoặc tạo bằng AI từ tài liệu bài học." }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <TablePagination v-model:page="page" v-model:page-size="pageSize" :total="filtered.length" :page-count="pageCount" unit="thẻ" unit-label="Flashcard" />
    </section>

    <AdminModal
      :open="formOpen"
      :title="editing ? 'Chỉnh sửa flashcard' : 'Thêm flashcard'"
      :busy="saving"
      :width="760"
      submit-label="Lưu thẻ"
      :submit-disabled="!!formError"
      @close="formOpen = false"
      @submit="saveForm"
    >
      <div class="card-form">
        <div class="form-row">
          <label class="field">
            <span>Loại thẻ</span>
            <select v-model="form.type">
              <option value="TERM">Thuật ngữ – định nghĩa</option>
              <option value="CLOZE">Điền khuyết</option>
            </select>
          </label>
          <label class="field">
            <span>Trạng thái</span>
            <select v-model="form.status">
              <option value="DRAFT">Bản nháp</option>
              <option value="PUBLISHED">Phát hành cho học viên</option>
            </select>
          </label>
        </div>
        <label class="field">
          <span>{{ form.type === "CLOZE" ? "Câu có chỗ trống" : "Thuật ngữ / câu hỏi (mặt trước)" }}</span>
          <textarea ref="frontInput" v-model="form.front" rows="3" maxlength="1000"
            :placeholder="form.type === 'CLOZE' ? 'VD: Hồ sơ mở CIF phải được phê duyệt trong vòng ____.' : 'VD: CIF'" />
          <small v-if="form.type === 'CLOZE'" class="field-hint">
            Dùng ____ (từ 3 dấu gạch dưới) để đánh dấu cụm từ cần ghi nhớ.
            <button type="button" class="link-button" @click="insertBlank">Chèn chỗ trống</button>
          </small>
        </label>
        <label class="field">
          <span>{{ form.type === "CLOZE" ? "Đáp án cho chỗ trống" : "Giải thích (mặt sau)" }}</span>
          <textarea v-model="form.back" :rows="form.type === 'CLOZE' ? 2 : 4" maxlength="4000"
            :placeholder="form.type === 'CLOZE' ? 'VD: 2 ngày làm việc' : 'VD: Mã định danh duy nhất của khách hàng trên hệ thống.'" />
        </label>
        <p v-if="formError && formTouched" class="field-error">{{ formError }}</p>
        <div class="card-preview" aria-hidden="true">
          <span>Xem trước</span>
          <strong>
            <template v-for="(part, index) in frontParts(form)" :key="index">
              <span v-if="part.blank" class="preview-blank">&nbsp;</span>
              <template v-else>{{ part.text }}</template>
            </template>
          </strong>
        </div>
      </div>
    </AdminModal>

    <ConfirmModal
      :open="!!deleting"
      title="Xóa flashcard"
      :message="`Xóa thẻ “${deleting?.front ?? ''}”? Lịch ôn tập của học viên với thẻ này cũng bị xóa.`"
      confirm-label="Xóa thẻ"
      :loading="removing"
      @cancel="deleting = null"
      @confirm="removeCard"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from "vue";
import { Edit, Layers3, LoaderCircle, Plus, Search, Sparkles, X } from "lucide-vue-next";
import AdminModal from "../AdminModal.vue";
import ConfirmModal from "../ConfirmModal.vue";
import ToggleSwitch from "../ToggleSwitch.vue";
import TablePagination from "../TablePagination.vue";
import LessonKnowledgeSources from "../LessonKnowledgeSources.vue";
import { useTablePagination } from "../../composables/useTablePagination";
import {
  adminFlashcardApi,
  apiErrorMessage,
  CLOZE_BLANK,
  TYPE_LABELS,
  type AdminFlashcard,
  type FlashcardSource,
  type FlashcardStatus,
  type FlashcardType,
} from "../../api/flashcardApi";
import { flashcardDraftError, frontParts, sourceLabel } from "../../utils/flashcards";
import { describeQuizJob, QuizGenerationJobError, type QuizGenerationJob } from "../../utils/quizGenerationJob";
import { showToast } from "../../utils/toast";

const props = defineProps<{ courseId: string; lessonId: string; actions: string[]; readOnly?: boolean }>();

const TYPES: FlashcardType[] = ["TERM", "CLOZE"];
const can = (action: string) => !props.readOnly && props.actions.includes(action);
const canCreate = computed(() => can("create"));
const canUpdate = computed(() => can("update"));
const canDelete = computed(() => can("delete"));

const cards = ref<AdminFlashcard[]>([]);
const loading = ref(false);
const search = ref("");
const statusFilter = ref<"" | FlashcardStatus>("");
const selectedIds = ref<number[]>([]);
const bulkSaving = ref(false);

const publishedCount = computed(() => cards.value.filter((card) => card.status === "PUBLISHED").length);
const filtered = computed(() => {
  const query = search.value.trim().toLowerCase();
  return cards.value.filter((card) =>
    (!statusFilter.value || card.status === statusFilter.value) &&
    (!query || `${card.front} ${card.back}`.toLowerCase().includes(query)));
});
const { page, pageSize, pageCount, paginated } = useTablePagination(filtered);
const allOnPageSelected = computed(() =>
  paginated.value.length > 0 && paginated.value.every((card) => selectedIds.value.includes(card.id)));

function togglePage() {
  const ids = paginated.value.map((card) => card.id);
  selectedIds.value = allOnPageSelected.value
    ? selectedIds.value.filter((id) => !ids.includes(id))
    : [...new Set([...selectedIds.value, ...ids])];
}

let loadId = 0;
async function load() {
  const current = ++loadId;
  loading.value = true;
  try {
    const result = await adminFlashcardApi.list(props.lessonId);
    if (current === loadId) cards.value = result;
  } catch (error) {
    if (current === loadId) showToast(apiErrorMessage(error, "Không tải được flashcard."), "error");
  } finally {
    if (current === loadId) loading.value = false;
  }
}
function replaceCards(updated: AdminFlashcard[]) {
  const byId = new Map(updated.map((card) => [card.id, card]));
  cards.value = cards.value.map((card) => byId.get(card.id) ?? card);
}

async function setStatus(ids: number[], status: FlashcardStatus) {
  bulkSaving.value = true;
  try {
    replaceCards(await adminFlashcardApi.updateStatus(props.lessonId, ids, status));
    selectedIds.value = [];
    showToast(status === "PUBLISHED" ? `Đã phát hành ${ids.length} thẻ.` : `Đã chuyển ${ids.length} thẻ về nháp.`);
  } catch (error) {
    showToast(apiErrorMessage(error, "Không cập nhật được trạng thái thẻ."), "error");
  } finally {
    bulkSaving.value = false;
  }
}

// Create / edit form
const formOpen = ref(false);
const formTouched = ref(false);
const saving = ref(false);
const editing = ref<AdminFlashcard | null>(null);
const frontInput = ref<HTMLTextAreaElement | null>(null);
const form = reactive({ type: "TERM" as FlashcardType, front: "", back: "", status: "DRAFT" as FlashcardStatus });
const formError = computed(() => flashcardDraftError(form));

function openForm(card?: AdminFlashcard) {
  editing.value = card ?? null;
  Object.assign(form, card
    ? { type: card.type, front: card.front, back: card.back, status: card.status }
    : { type: "TERM", front: "", back: "", status: "DRAFT" });
  formTouched.value = false;
  formOpen.value = true;
}

function insertBlank() {
  const input = frontInput.value;
  const start = input?.selectionStart ?? form.front.length;
  const end = input?.selectionEnd ?? start;
  form.front = `${form.front.slice(0, start)}${CLOZE_BLANK}${form.front.slice(end)}`;
  void nextTick(() => {
    input?.focus();
    input?.setSelectionRange(start + CLOZE_BLANK.length, start + CLOZE_BLANK.length);
  });
}

async function saveForm() {
  formTouched.value = true;
  if (formError.value) return;
  saving.value = true;
  try {
    if (editing.value) {
      const source = editing.value.source;
      const saved = await adminFlashcardApi.update(props.lessonId, editing.value.id, {
        ...form,
        origin: editing.value.origin,
        sourceDocumentId: source?.documentId ?? null,
        sourcePage: source?.page ?? null,
        sourceSection: source?.section ?? null,
        version: editing.value.version,
      });
      replaceCards([saved]);
      showToast("Đã cập nhật flashcard.");
    } else {
      cards.value = [...cards.value, await adminFlashcardApi.create(props.lessonId, { ...form })];
      showToast("Đã thêm flashcard.");
    }
    formOpen.value = false;
  } catch (error) {
    showToast(apiErrorMessage(error, "Không lưu được flashcard."), "error");
  } finally {
    saving.value = false;
  }
}

// Delete
const deleting = ref<AdminFlashcard | null>(null);
const removing = ref(false);
async function removeCard() {
  if (!deleting.value) return;
  removing.value = true;
  try {
    const id = deleting.value.id;
    await adminFlashcardApi.remove(props.lessonId, id);
    cards.value = cards.value.filter((card) => card.id !== id);
    selectedIds.value = selectedIds.value.filter((selected) => selected !== id);
    deleting.value = null;
    showToast("Đã xóa flashcard.");
  } catch (error) {
    showToast(apiErrorMessage(error, "Không xóa được flashcard."), "error");
  } finally {
    removing.value = false;
  }
}

// AI drafting
type Draft = { type: FlashcardType; front: string; back: string; source: FlashcardSource | null; selected: boolean };
const showAiPanel = ref(false);
const sourceReady = ref(false);
const generating = ref(false);
const savingDrafts = ref(false);
const aiConfig = reactive({ count: 10, types: [...TYPES] as FlashcardType[] });
const aiWarnings = ref<string[]>([]);
const drafts = ref<Draft[]>([]);
const editingDraft = ref<number | null>(null);
const progress = ref<{ percent: number; label: string; detail: string; failed: boolean } | null>(null);
const selectedDrafts = computed(() => drafts.value.filter((draft) => draft.selected));
let abort: AbortController | null = null;

const jobDetail = (job: QuizGenerationJob) => {
  const done = job.completedSteps ?? 0;
  const total = job.totalSteps ?? 0;
  switch (job.stage) {
    case "retrieving": return "Đang tìm các đoạn tài liệu phù hợp";
    case "generating": return `Đã tạo ${done}/${total || job.requestedQuestions} thẻ đạt yêu cầu`;
    case "verifying": return `Đã kiểm định ${done}/${total} thẻ`;
    case "finalizing": return `${job.acceptedQuestions}/${job.requestedQuestions} thẻ đạt yêu cầu`;
    default: return "";
  }
};

function discardDrafts() {
  abort?.abort();
  drafts.value = [];
  aiWarnings.value = [];
  editingDraft.value = null;
  progress.value = null;
}

async function generate() {
  const count = Math.min(20, Math.max(1, Math.round(aiConfig.count || 1)));
  aiConfig.count = count;
  const controller = new AbortController();
  abort = controller;
  generating.value = true;
  drafts.value = [];
  aiWarnings.value = [];
  progress.value = { percent: 0, label: "Đang gửi yêu cầu tạo flashcard", detail: "", failed: false };
  try {
    const result = await adminFlashcardApi.generateWithProgress(
      props.lessonId,
      { count, types: aiConfig.types },
      {
        signal: controller.signal,
        onProgress: (job) => {
          progress.value = {
            percent: job.percent,
            label: describeQuizJob(job).replace("câu hỏi", "flashcard").replace("câu chưa đạt", "thẻ chưa đạt"),
            detail: jobDetail(job),
            failed: false,
          };
        },
      },
    );
    progress.value = null;
    const sources = new Map(result.sources.map((source) => [source.id, source]));
    drafts.value = result.cards.map((card) => {
      const source = sources.get(card.sourceIds[0]);
      return {
        type: card.type,
        front: card.front,
        back: card.back,
        source: source ? { documentId: source.documentId, documentName: source.documentName, page: source.page, section: source.section } : null,
        selected: true,
      };
    });
    aiWarnings.value = result.warnings;
    if (!drafts.value.length && !aiWarnings.value.length) aiWarnings.value = ["AI chưa tạo được thẻ đạt yêu cầu. Hãy thử lại hoặc bổ sung nguồn."];
  } catch (error) {
    if (error instanceof QuizGenerationJobError && error.kind === "cancelled") return;
    progress.value = {
      percent: progress.value?.percent ?? 0,
      label: apiErrorMessage(error, "Không thể tạo flashcard. Vui lòng thử lại.").replace("câu hỏi", "flashcard"),
      detail: "",
      failed: true,
    };
  } finally {
    if (abort === controller) abort = null;
    generating.value = false;
  }
}

async function saveDrafts(status: FlashcardStatus) {
  const invalid = selectedDrafts.value.find((draft) => flashcardDraftError(draft));
  if (invalid) {
    showToast(flashcardDraftError(invalid)!, "warning");
    return;
  }
  savingDrafts.value = true;
  try {
    const created = await adminFlashcardApi.createBatch(props.lessonId, selectedDrafts.value.map((draft) => ({
      type: draft.type,
      front: draft.front,
      back: draft.back,
      status,
      origin: "AI",
      sourceDocumentId: draft.source?.documentId ?? null,
      sourcePage: draft.source?.page ?? null,
      sourceSection: draft.source?.section ?? null,
    })));
    cards.value = [...cards.value, ...created];
    showToast(status === "PUBLISHED" ? `Đã lưu và phát hành ${created.length} thẻ.` : `Đã lưu ${created.length} thẻ nháp.`);
    drafts.value = drafts.value.filter((draft) => !draft.selected);
    if (!drafts.value.length) {
      aiWarnings.value = [];
      showAiPanel.value = false;
    }
  } catch (error) {
    showToast(apiErrorMessage(error, "Không lưu được flashcard."), "error");
  } finally {
    savingDrafts.value = false;
  }
}

// Registered last: the immediate run resets state declared above.
watch(() => props.lessonId, () => {
  selectedIds.value = [];
  discardDrafts();
  void load();
}, { immediate: true });

onBeforeUnmount(() => abort?.abort());
</script>

<style scoped>
.flashcard-manager :deep(button),
.flashcard-manager :deep(input),
.flashcard-manager :deep(select),
.flashcard-manager :deep(textarea) { font-family: var(--font-family); }
/* Same table type scale as the lesson quiz tab next to this one. */
.flashcard-manager :deep(.admin-data-table),
.flashcard-manager :deep(.admin-data-table *) { font-family: var(--font-family); }
.flashcard-manager :deep(.admin-data-table) { font-size: var(--admin-text-body, .875rem); line-height: 1.5; }
.flashcard-manager :deep(.admin-data-table th) { font-size: var(--admin-text-label, .8125rem); font-weight: 600; }
.flashcard-manager :deep(.admin-data-table td) { font-weight: 400; }
.flashcard-manager .admin-data-table .card-front { font-weight: 700; }
.flashcard-manager .admin-data-table .question-type-badge,
.flashcard-manager .admin-data-table .origin-badge,
.flashcard-manager .admin-data-table .status-badge { font-size: var(--admin-text-label, .8125rem); font-weight: 600; }
.flashcard-manager .admin-data-table .source-cell small,
.flashcard-manager .admin-data-table .draft-sources { margin: 0; font-size: var(--admin-text-caption, .75rem); line-height: 1.45; }
.editor-card { margin-bottom: 2rem; }
.section-heading { display: flex; align-items: center; flex-wrap: wrap; gap: .7rem; margin-bottom: 1rem; }
.section-heading > span { display: grid; place-items: center; width: 30px; height: 30px; border-radius: 50%; background: var(--primary-color); color: white; }
.section-heading h2 { margin: 0 0 .15rem; font-size: var(--admin-heading-section); line-height: 1.3; }
.section-heading p { margin: 0; color: var(--text-muted); font-size: var(--admin-text-body); }
.question-tools { display: flex; gap: .45rem; margin-left: auto; }
.primary-button, .secondary-button, .ai-button { display: inline-flex; align-items: center; justify-content: center; gap: .4rem; padding: .65rem .85rem; border-radius: var(--radius-sm); cursor: pointer; font-size: var(--admin-text-label, .8125rem); font-weight: 700; white-space: nowrap; }
.primary-button { border: 1px solid var(--primary-color); background: var(--primary-color); color: white; }
.secondary-button { border: 1px solid var(--border-color); background: var(--surface-ffffff); color: var(--primary-ink); }
.primary-button:disabled, .secondary-button:disabled { opacity: .5; cursor: not-allowed; }
.ai-button { border: 1px solid #7c3aed; background: var(--surface-f5f3ff); color: var(--ink-6d28d9); }
.ai-button:disabled { opacity: .65; cursor: wait; }
.ai-panel { margin-bottom: 1.2rem; padding: 1rem; border: 1px solid var(--line-ddd6fe); border-radius: var(--radius-md); background: var(--surface-faf9ff); }
.ai-panel-heading { display: flex; justify-content: space-between; gap: 1rem; margin-bottom: .9rem; }
.ai-panel-heading > div { display: flex; flex-direction: column; gap: .2rem; }
.ai-panel-heading small { color: var(--text-muted); }
.ai-panel-heading > button { border: 0; background: transparent; cursor: pointer; }
.ai-settings { display: grid; grid-template-columns: minmax(120px, 160px) 1fr auto; gap: .9rem; align-items: end; margin-top: .9rem; }
.ai-settings label > span, .type-choices legend { display: block; margin-bottom: .3rem; font-size: var(--admin-text-label); font-weight: 700; }
.ai-settings input[type="number"] { width: 100%; padding: .65rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); }
.type-choices { display: flex; flex-wrap: wrap; gap: .5rem 1rem; margin: 0; padding: 0; border: 0; }
.type-choices legend { width: 100%; padding: 0; }
.type-choices label { display: inline-flex; align-items: center; gap: .35rem; padding: .55rem .7rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); font-size: var(--admin-text-body); cursor: pointer; }
.ai-progress { margin: .85rem 0 0; padding: .7rem .8rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); }
.ai-progress-header { display: flex; justify-content: space-between; gap: .75rem; margin-bottom: .45rem; font-size: var(--admin-text-label); font-weight: 700; }
.ai-progress-track { height: 8px; overflow: hidden; border-radius: 999px; background: var(--border-color); }
.ai-progress-fill { height: 100%; border-radius: inherit; background: var(--primary-color); transition: width .4s ease; }
.ai-progress small { display: block; margin-top: .4rem; color: var(--text-muted); }
.ai-progress.failed .ai-progress-header { color: var(--ink-9a3412); }
.ai-progress.failed .ai-progress-fill { background: var(--ink-9a3412); }
.ai-warning { margin: .75rem 0 0; padding: .65rem .75rem; border-radius: var(--radius-sm); background: var(--surface-fff7ed); color: var(--ink-9a3412); }
.ai-drafts { display: grid; gap: .75rem; margin-top: 1rem; }
.draft-heading-actions { display: flex; align-items: center; gap: .5rem; margin-bottom: .4rem; }
.draft-edit-button { display: inline-flex; align-items: center; gap: .3rem; padding: .25rem .45rem; border: 1px solid var(--line-c4b5fd); border-radius: 6px; background: var(--surface-ffffff); color: var(--ink-6d28d9); cursor: pointer; font-weight: 700; }
.ai-draft-editor { display: grid; gap: .6rem; margin-top: .4rem; padding: .85rem; border: 1px solid var(--line-ddd6fe); border-radius: var(--radius-sm); background: var(--surface-faf9ff); }
.draft-editor-actions, .ai-draft-actions { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: .5rem; }
.draft-sources { display: block; margin-top: .35rem; color: var(--ink-475569); }
.question-type-badge { display: inline-flex; padding: .2rem .5rem; border-radius: 999px; background: var(--surface-ede9fe); color: var(--ink-6d28d9); font-size: var(--admin-text-caption); font-weight: 800; white-space: nowrap; }
.list-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: .6rem; margin-bottom: 1rem; }
.search-box { display: flex; flex: 1 1 320px; align-items: center; gap: .5rem; max-width: 560px; padding: 0 .75rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); color: var(--text-muted); background: var(--surface-ffffff); }
.search-box input { width: 100%; height: 42px; border: 0; outline: 0; background: transparent; }
.list-toolbar select, .field select, .field textarea { width: 100%; padding: .65rem .7rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); color: var(--text-color); }
.list-toolbar select { width: auto; min-width: 180px; height: 44px; }
.bulk-actions { display: flex; align-items: center; gap: .45rem; margin-left: auto; font-size: var(--admin-text-label); color: var(--text-muted); }
.flashcard-table { min-width: 960px; }
.card-cell { min-width: 0; }
.card-front { display: block; font-weight: 700; overflow-wrap: anywhere; }
.card-back { margin: .3rem 0 0; color: var(--text-muted); overflow-wrap: anywhere; white-space: pre-line; }
.source-cell { display: flex; flex-direction: column; gap: .3rem; }
.source-cell small { color: var(--text-muted); overflow-wrap: anywhere; }
.origin-badge, .status-badge { display: inline-flex; width: fit-content; padding: .25rem .55rem; border-radius: 6px; background: var(--surface-f1f5f9); color: var(--text-muted); font-size: var(--admin-text-caption); font-weight: 700; white-space: nowrap; }
.origin-badge.ai { background: var(--surface-ede9fe); color: var(--ink-6d28d9); }
.status-badge.published { background: var(--primary-light); color: var(--primary-ink); }
.card-form { display: grid; gap: .9rem; }
.form-row { display: grid; grid-template-columns: 1fr 1fr; gap: .9rem; }
.field { display: block; }
.field > span { display: block; margin-bottom: .35rem; font-size: var(--admin-text-label); font-weight: 700; }
.field-hint { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin-top: .35rem; color: var(--text-muted); font-size: var(--admin-text-caption); }
.link-button { padding: .2rem .5rem; border: 1px solid var(--border-color); border-radius: 6px; background: var(--surface-ffffff); color: var(--primary-ink); font-size: var(--admin-text-caption); font-weight: 700; cursor: pointer; }
.field-error { margin: 0; color: var(--danger-ink); font-size: var(--admin-text-label); }
.card-preview { display: flex; flex-direction: column; gap: .4rem; padding: .9rem 1rem; border: 1px dashed var(--border-color); border-radius: var(--radius-md); background: var(--surface-f8fafc); }
.card-preview > span { color: var(--text-muted); font-size: var(--admin-text-caption); font-weight: 700; text-transform: uppercase; }
.card-preview strong { font-size: 1rem; line-height: 1.6; overflow-wrap: anywhere; }
.preview-blank { display: inline-block; min-width: 4em; margin: 0 .2em; border-bottom: 2px solid var(--accent-color); }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 760px) {
  .question-tools { width: 100%; margin-left: 0; flex-wrap: wrap; }
  .ai-settings, .form-row { grid-template-columns: 1fr; }
  .bulk-actions { margin-left: 0; flex-wrap: wrap; }
  .list-toolbar select { width: 100%; }
}
@media (prefers-reduced-motion: reduce) {
  .ai-progress-fill { transition: none; }
  .spin { animation: none; }
}
</style>
