<template>
  <section class="knowledge-sources" aria-labelledby="knowledge-source-title">
    <header class="source-heading">
      <div>
        <h3 id="knowledge-source-title"><BookOpen :size="18" /> Nguồn dùng để tạo câu hỏi</h3>
        <p>Chọn phần tài liệu chứa kiến thức của bài học này. AI chỉ dùng nguồn bạn đã chọn và lưu.</p>
      </div>
      <button type="button" :disabled="loading || disabled || saving || uploading" @click="refreshDocuments">Làm mới</button>
    </header>
    <p v-if="loading" class="source-message" role="status">Đang tải tài liệu và nguồn đã lưu…</p>
    <p v-if="error" class="source-error" role="alert">{{ error }} <button v-if="loadFailed" type="button" @click="load">Thử lại</button></p>
    <template v-if="!loading && !loadFailed">
      <fieldset :disabled="disabled || saving || uploading" class="source-fieldset">
        <div v-if="!documents.length" class="source-empty">
          <FileText :size="24" /><strong>Khóa học chưa có tài liệu AI</strong>
          <p>Tải PDF hoặc Word lên, chờ xử lý xong rồi chọn phần dùng cho bài học.</p>
        </div>
<div class="admin-table-scroll"><table class="admin-data-table"><thead><tr><th>Chọn</th><th>Tài liệu / nguồn kiến thức</th><th>Trạng thái</th></tr></thead><tbody>
        <template v-for="document in paginatedDocuments" :key="document.id"><tr :class="{selected:selections[document.id]}"><td><ToggleSwitch :show-label="false" :model-value="Boolean(selections[document.id])" :label="`Dùng tài liệu ${document.fileName}`" :disabled="disabled || saving || uploading || (document.status!=='INDEXED' && !selections[document.id])" @update:model-value="toggleDocument(document)" /></td><td><strong>{{ document.fileName }}</strong><small>{{ isPdf(document)?'PDF · chọn theo trang':'Word · chọn theo mục' }}</small></td><td><span class="document-status" :class="document.status.toLowerCase()">{{ statusLabel(document.status) }}</span></td></tr>
        <tr v-if="selections[document.id] || document.status==='FAILED'"><td colspan="3">
          <p v-if="document.status === 'FAILED'" class="source-error">Không xử lý được tài liệu. Hãy kiểm tra file và tải lại.</p>
          <div v-if="selections[document.id]" class="source-options">
            <p v-if="outlineLoading[document.id]" class="source-message">Đang đọc nội dung tài liệu…</p>
            <p v-if="outlineErrors[document.id]" class="source-error">{{ outlineErrors[document.id] }} <button type="button" @click="readOutline(document.id)">Thử lại</button></p>
            <template v-if="outlines[document.id]">
              <label class="scope-mode">
                <span>Phần tài liệu dùng cho bài học</span>
                <select v-model="selections[document.id]!.mode">
                  <option value="all">Toàn bộ tài liệu</option>
                  <option v-if="isPdf(document)" value="pages">Một hoặc nhiều khoảng trang</option>
                  <option v-else value="sections">Các mục được chọn</option>
                </select>
              </label>
              <p v-if="selections[document.id]!.mode === 'all'" class="scope-help">Chỉ chọn toàn bộ nếu tất cả nội dung tài liệu phù hợp với bài học này.</p>
              <div v-else-if="selections[document.id]!.mode === 'pages'" class="page-ranges">
                <p class="scope-help">Dùng số trang của file PDF, không phải số in trên trang. Trang có nội dung đã xử lý: {{ pageList(document.id) }}.</p>
                <div v-for="(range, index) in selections[document.id]!.ranges" :key="index" class="page-range">
                  <label>Từ trang<input v-model.number="range.from" type="number" min="1" :max="maxPage(document.id)" /></label>
                  <label>Đến trang<input v-model.number="range.to" type="number" min="1" :max="maxPage(document.id)" /></label>
                  <button v-if="selections[document.id]!.ranges.length > 1" type="button" aria-label="Bỏ khoảng trang" @click="selections[document.id]!.ranges.splice(index, 1)"><X :size="16" /></button>
                </div>
                <button type="button" class="text-button" @click="selections[document.id]!.ranges.push({ from: 1, to: 1 })"><Plus :size="15" /> Thêm khoảng trang</button>
              </div>
              <div v-else class="section-choices">
                <p class="scope-help">Chọn mục liên quan đến bài học. Các mục lấy từ tiêu đề trong file Word, không chia theo trang.</p>
                <label v-for="section in outlines[document.id]!.sections" :key="section">
                  <ToggleSwitch :model-value="selections[document.id]!.sections.includes(section)" :label="section" :disabled="disabled || saving || uploading" @update:model-value="enabled=>toggleSection(document.id,section,enabled)" />
                </label>
              </div>
              <details class="source-preview">
                <summary>Xem trích đoạn trong phần đã chọn</summary>
                <p v-if="!preview(document.id).length" class="source-message">Phạm vi này chưa có nội dung. Hãy chọn lại.</p>
                <article v-for="excerpt in preview(document.id)" :key="`${excerpt.page}-${excerpt.section}`">
                  <strong>{{ isPdf(document) ? `Trang ${excerpt.page}` : excerpt.section }}</strong>
                  <p>{{ excerpt.excerpt }}</p>
                </article>
                <small>Trích đoạn giúp nhận biết nội dung; AI sử dụng các đoạn đã xử lý trong toàn bộ phạm vi được lưu.</small>
              </details>
            </template>
          </div>
</td></tr></template></tbody></table></div>
        <TablePagination v-model:page="documentPage" v-model:page-size="documentPageSize" :total="documents.length" :page-count="documentPageCount" unit="tài liệu" unit-label="Tài liệu" />
        <label v-if="sourceUser.can('documents','create')" class="source-upload"><FileUp :size="17" /> {{ uploading ? 'Đang tải lên…' : 'Thêm tài liệu PDF / Word' }}<input type="file" accept=".pdf,.docx" :disabled="disabled || uploading || saving" @change="uploadDocument" /></label>
        <p class="scope-help">Tài liệu được lưu vào kho chung của khóa học. Bạn có thể dùng các phần khác nhau cho nhiều bài học.</p>
      </fieldset>
      <p v-if="validationError" class="source-error" role="status">{{ validationError }}</p>
      <footer class="source-footer">
        <div role="status">
          <strong>{{ selectedCount ? `${selectedCount} tài liệu được chọn` : 'Chưa chọn nguồn cho bài học' }}</strong>
          <small>{{ dirty ? 'Có thay đổi chưa lưu. Lưu nguồn trước khi tạo câu hỏi.' : ready ? 'Nguồn đã lưu. Có thể tạo câu hỏi.' : 'Chọn và lưu nguồn để tiếp tục.' }}</small>
        </div>
        <button type="button" class="save-sources" :disabled="disabled || saving || uploading || !dirty || Boolean(validationError)" @click="save">
          <Save :size="16" /> {{ saving ? 'Đang lưu…' : 'Lưu nguồn cho bài học' }}
        </button>
      </footer>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import ToggleSwitch from './ToggleSwitch.vue';
import TablePagination from './TablePagination.vue';
import {useTablePagination} from '../composables/useTablePagination';
import { BookOpen, FileText, FileUp, Plus, Save, X } from 'lucide-vue-next';
import {useUserStore} from '../stores/userStore';
import { adminApi } from '../api/adminApi';
import type { CourseDocument, DocumentOutline, LessonKnowledgeSource } from '../api/adminApi';

const sourceUser=useUserStore();
const props = defineProps<{ courseId: string; lessonId: string; disabled?: boolean; permissionPage?:string }>();
const emit = defineEmits<{ ready: [ready: boolean] }>();
type Selection = { mode: 'all' | 'pages' | 'sections'; ranges: { from: number; to: number }[]; sections: string[] };
const documents = ref<CourseDocument[]>([]);
const {page:documentPage,pageSize:documentPageSize,pageCount:documentPageCount,paginated:paginatedDocuments,reset:resetDocumentPage}=useTablePagination(documents);
function toggleSection(id:string,section:string,enabled:boolean){const choices=selections.value[id]?.sections;if(!choices)return;const index=choices.indexOf(section);if(enabled && index<0)choices.push(section);else if(!enabled && index>=0)choices.splice(index,1);}
const selections = ref<Record<string, Selection>>({});
watch([()=>props.courseId,()=>props.lessonId],resetDocumentPage);
const outlines = ref<Record<string, DocumentOutline>>({});
const outlineLoading = ref<Record<string, boolean>>({});
const outlineErrors = ref<Record<string, string>>({});
const loading = ref(true);
const loadFailed = ref(false);
const saving = ref(false);
const uploading = ref(false);
const error = ref('');
const saved = ref('[]');
let epoch = 0;
let poll: ReturnType<typeof setTimeout> | undefined;
const isPdf = (document: CourseDocument) => document.fileName.toLowerCase().endsWith('.pdf');
const statusLabel = (status: string) => ({ INDEXED: 'Sẵn sàng', INDEXING: 'Đang xử lý', PENDING: 'Chờ xử lý', FAILED: 'Xử lý thất bại' }[status] || status);
const errorMessage = (error: unknown) => {
  const response = (error as { response?: { data?: { message?: string } } })?.response;
  return response?.data?.message || 'Không thể hoàn tất thao tác. Vui lòng thử lại.';
};
const sourceValues = computed<LessonKnowledgeSource[]>(() => Object.entries(selections.value).flatMap(([documentId, value]) => {
  if (value.mode === 'pages') return value.ranges.map(range => ({ documentId, pageFrom: range.from, pageTo: range.to }));
  if (value.mode === 'sections') return value.sections.map(section => ({ documentId, section }));
  return [{ documentId }];
}));
const serialize = (sources: LessonKnowledgeSource[]) => JSON.stringify(sources.map(source => ({
  documentId: source.documentId, pageFrom: source.pageFrom ?? null, pageTo: source.pageTo ?? null, section: source.section ?? null,
})).sort((a, b) => JSON.stringify(a).localeCompare(JSON.stringify(b))));
const dirty = computed(() => serialize(sourceValues.value) !== saved.value);
const selectedCount = computed(() => Object.keys(selections.value).length);
const maxPage = (id: string) => Math.max(1, ...(outlines.value[id]?.pages || []));
const pageList = (id: string) => {
  const pages = outlines.value[id]?.pages || [];
  return pages.length > 20 ? `${pages[0]}–${pages[pages.length - 1]} (có thể có trang trống)` : pages.join(', ');
};
const validationError = computed(() => {
  if (sourceValues.value.length > 50) return 'Chỉ chọn tối đa 50 khoảng trang hoặc mục.';
  for (const [id, value] of Object.entries(selections.value)) {
    if (documents.value.find(document => document.id === id)?.status !== 'INDEXED') return 'Có tài liệu chưa sẵn sàng. Hãy chờ xử lý hoặc bỏ chọn tài liệu đó.';
    const outline = outlines.value[id];
    if (!outline || outlineLoading.value[id] || outlineErrors.value[id]) return 'Cần đọc được nội dung tất cả tài liệu đã chọn trước khi lưu.';
    if (value.mode === 'pages' && (!value.ranges.length || value.ranges.some(range => !Number.isInteger(range.from) || !Number.isInteger(range.to)
      || range.from < 1 || range.to < range.from || range.to > maxPage(id)
      || !outline.pages.some(page => page >= range.from && page <= range.to)))) return 'Kiểm tra khoảng trang: trang đầu không lớn hơn trang cuối và phải có nội dung đã xử lý.';
    if (value.mode === 'sections' && (!value.sections.length || value.sections.some(section => !outline.sections.includes(section)))) return 'Hãy chọn ít nhất một mục hợp lệ trong tài liệu Word.';
  }
  if (new Set(sourceValues.value.map(value => serialize([value]))).size !== sourceValues.value.length) return 'Có phạm vi bị trùng. Hãy bỏ phạm vi lặp lại.';
  return '';
});
const ready = computed(() => !loading.value && !loadFailed.value && !saving.value && !uploading.value && !dirty.value && !validationError.value && sourceValues.value.length > 0);
watch(ready, value => emit('ready', value), { immediate: true });
const preview = (id: string) => {
  const selection = selections.value[id];
  return (outlines.value[id]?.excerpts || []).filter(excerpt => selection?.mode === 'all'
    || (selection?.mode === 'pages' && selection.ranges.some(range => excerpt.page >= range.from && excerpt.page <= range.to))
    || (selection?.mode === 'sections' && selection.sections.includes(excerpt.section))).slice(0, 6);
};
const readOutline = async (id: string) => {
  const current = epoch;
  outlineLoading.value[id] = true;
  outlineErrors.value[id] = '';
  try {
    const outline = await adminApi.getDocumentOutline(props.courseId, id,props.permissionPage);
    if (current === epoch) outlines.value[id] = outline;
  } catch (failure) {
    if (current === epoch) outlineErrors.value[id] = errorMessage(failure);
  } finally {
    if (current === epoch) outlineLoading.value[id] = false;
  }
};
const scheduleRefresh = () => {
  clearTimeout(poll);
  if (documents.value.some(document => ['PENDING', 'INDEXING'].includes(document.status))) {
    poll = setTimeout(() => void refreshDocuments(), 3000);
  }
};
const refreshDocuments = async () => {
  const current = epoch;
  try {
    const fresh = await adminApi.getCourseDocuments(props.courseId,props.permissionPage);
    if (current !== epoch) return;
    documents.value = fresh;
    scheduleRefresh();
  } catch (failure) {
    if (current === epoch) error.value = errorMessage(failure);
  }
};
const load = async () => {
  const current = ++epoch;
  clearTimeout(poll);
  loading.value = true;
  loadFailed.value = false;
  error.value = '';
  selections.value = {};
  outlines.value = {};
  outlineErrors.value = {};
  outlineLoading.value = {};
  try {
    const [docs, sources] = await Promise.all([adminApi.getCourseDocuments(props.courseId,props.permissionPage), adminApi.getLessonKnowledgeSources(props.lessonId,props.permissionPage)]);
    if (current !== epoch) return;
    documents.value = docs;
    for (const source of sources) {
      const selection = selections.value[source.documentId] ||= { mode: 'all', ranges: [], sections: [] };
      if (source.pageFrom != null && source.pageTo != null) {
        selection.mode = 'pages';
        selection.ranges.push({ from: source.pageFrom, to: source.pageTo });
      } else if (source.section) {
        selection.mode = 'sections';
        selection.sections.push(source.section);
      }
    }
    saved.value = serialize(sources);
    for (const selection of Object.values(selections.value)) {
      if (!selection.ranges.length) selection.ranges.push({ from: 1, to: 1 });
    }
    await Promise.all(Object.keys(selections.value).map(id => readOutline(id)));
    if (current === epoch) scheduleRefresh();
  } catch (failure) {
    if (current === epoch) { error.value = errorMessage(failure); loadFailed.value = true; }
  } finally {
    if (current === epoch) loading.value = false;
  }
};
const toggleDocument = async (document: CourseDocument) => {
  if (selections.value[document.id]) { delete selections.value[document.id]; return; }
  selections.value[document.id] = { mode: 'all', ranges: [{ from: 1, to: 1 }], sections: [] };
  if (!outlines.value[document.id]) await readOutline(document.id);
};
const save = async () => {
  if (validationError.value || props.disabled) return;
  const current = epoch;
  saving.value = true;
  error.value = '';
  try {
    const sources = await adminApi.saveLessonKnowledgeSources(props.lessonId, sourceValues.value,props.permissionPage);
    if (current === epoch) saved.value = serialize(sources);
  } catch (failure) {
    if (current === epoch) error.value = errorMessage(failure);
  } finally {
    if (current === epoch) saving.value = false;
  }
};
const uploadDocument = async (event: Event) => {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  const current = epoch;
  uploading.value = true;
  error.value = '';
  try {
    await adminApi.uploadCourseDocument(props.courseId, file);
    if (current === epoch) await refreshDocuments();
  } catch (failure) {
    if (current === epoch) error.value = errorMessage(failure);
  } finally {
    input.value = '';
    if (current === epoch) uploading.value = false;
  }
};
watch(() => [props.courseId, props.lessonId], () => { saving.value = false; uploading.value = false; void load(); }, { immediate: true });
onBeforeUnmount(() => { epoch++; clearTimeout(poll); });
</script>

<style scoped>
.source-fieldset{min-width:0}.source-options{margin-top:0!important}.admin-data-table .selected td{background:var(--primary-light)}.admin-data-table td:first-child{max-width:190px}

.knowledge-sources { margin: 1rem 0; padding: 1.1rem; border: 1px solid #c9ded4; border-radius: 12px; background: white; }
.source-heading, .source-footer { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.source-heading h3 { display: flex; align-items: center; gap: .5rem; font-size: .95rem; color: var(--primary-color); }
.source-heading p, .scope-help, .source-message { margin: .45rem 0 .8rem; color: #64748b; font-size: .8rem; line-height: 1.6; }
button, .source-upload { display: inline-flex; align-items: center; justify-content: center; gap: .4rem; min-height: 38px; padding: .45rem .75rem; border: 1px solid #cbd5d1; border-radius: 7px; background: white; color: #36564a; font-size: .8rem; font-weight: 600; cursor: pointer; }
button:hover:not(:disabled), .source-upload:hover { background: var(--primary-light); }
button:disabled { opacity: .5; cursor: not-allowed; }
.source-fieldset { min-width: 0; border: 0; }
.source-document { margin: .65rem 0; border: 1px solid #e2e8f0; border-radius: 9px; overflow: hidden; }
.source-document.selected { border-color: #97c7b2; }
.document-choice { display: flex; align-items: center; gap: .65rem; padding: .85rem; cursor: pointer; }
.document-choice > span:first-of-type { min-width: 0; flex: 1; }
.document-choice strong { display: block; overflow-wrap: anywhere; font-size: .85rem; }
.document-choice small, .source-footer small { display: block; margin-top: .25rem; color: #64748b; font-size: .75rem; }
input[type=checkbox] { width: 17px; height: 17px; accent-color: var(--primary-color); flex-shrink: 0; }
.document-choice > svg { flex-shrink: 0; color: var(--primary-color); }
.document-status { border-radius: 20px; padding: .25rem .5rem; background: #f1f5f9; color: #64748b; font-size: .7rem; white-space: nowrap; }
.document-status.indexed { color: #166534; background: #e8f5ee; }
.document-status.failed { color: #b91c1c; background: #fef2f2; }
.source-options { padding: 0 .85rem .85rem; border-top: 1px solid #e9f0ec; }
.scope-mode { display: grid; gap: .4rem; margin-top: .8rem; font-size: .8rem; font-weight: 600; }
select, input[type=number] { min-width: 0; width: 100%; min-height: 38px; padding: .45rem .6rem; border: 1px solid #cbd5d1; border-radius: 7px; background: white; color: #334155; }
.page-range { display: flex; align-items: flex-end; gap: .6rem; margin-bottom: .6rem; }
.page-range label { display: grid; gap: .3rem; font-size: .8rem; max-width: 180px; }
.section-choices label { display: flex; align-items: center; gap: .5rem; padding: .5rem 0; font-size: .85rem; overflow-wrap: anywhere; }
.section-choices { max-height: 260px; overflow-y: auto; }
.source-preview { margin-top: .85rem; border-top: 1px dashed #dce9e3; padding-top: .7rem; }
.source-preview summary { color: var(--primary-color); font-size: .8rem; cursor: pointer; }
.source-preview article { padding: .75rem; margin-top: .65rem; border-radius: 7px; background: #f7faf8; font-size: .8rem; line-height: 1.6; }
.source-preview p { margin-top: .35rem; white-space: pre-wrap; overflow-wrap: anywhere; }
.source-preview small { color: #64748b; font-size: .72rem; }
.source-upload { position: relative; margin-top: .65rem; }
/* Keep the native file input inside its trigger so focusing it only scrolls
   the admin content pane, rather than extending and scrolling the document. */
.source-upload input { position: absolute; inset: 0; width: 100%; height: 100%; opacity: 0; cursor: pointer; }
.source-upload input:disabled { cursor: not-allowed; }
.source-upload:focus-within { outline: 2px solid var(--primary-color); outline-offset: 2px; }
.source-fieldset:disabled .source-upload { opacity: .5; cursor: not-allowed; }
.source-footer { padding-top: .85rem; border-top: 1px solid #e2e8f0; align-items: center; }
.source-footer strong { color: #36564a; font-size: .85rem; }
.save-sources { color: white; background: var(--primary-color); border-color: var(--primary-color); flex-shrink: 0; }
.save-sources:hover:not(:disabled) { background: var(--primary-hover); }
.source-error { margin: .6rem 0; color: #b91c1c; font-size: .8rem; line-height: 1.5; }
.source-empty { display: grid; justify-items: center; gap: .5rem; padding: 1.25rem; color: #64748b; text-align: center; font-size: .85rem; background: #f8fafc; border-radius: 8px; }
@media(max-width:600px) { .knowledge-sources { padding: .85rem; } .source-footer { align-items: stretch; flex-direction: column; } .document-choice { flex-wrap: wrap; } .document-status { margin-left: 2rem; } }
</style>
