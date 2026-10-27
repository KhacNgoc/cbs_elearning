<template>
  <div class="form-group content-selection">
    <label id="lesson-content-types-label">Nội dung bài học</label>
    <details aria-labelledby="lesson-content-types-label">
      <summary aria-describedby="lesson-content-types-help">
        <span class="content-selection-copy">
          <span class="content-selection-action">Chọn nội dung bài học</span>
          <span class="content-selection-value">{{ selected.length ? selected.map(type => labels[type]).join(', ') : 'Chưa chọn nội dung' }}</span>
        </span>
      </summary>
      <label v-for="type in types" :key="type" class="content-option">
        <input v-model="selected" type="checkbox" :value="type" :disabled="readOnly" /> {{ labels[type] }}
      </label>
    </details>
    <small id="lesson-content-types-help" class="content-selection-help">Nhấn để mở danh sách. Có thể chọn nhiều mục.</small>
    <section v-if="selected.includes('SLIDE')" class="slide-editor" aria-label="Slide PowerPoint">
      <div class="slide-heading">
        <h2>Slide PowerPoint <span class="required-mark" aria-hidden="true">*</span></h2>
        <p>Mỗi bài học sử dụng một file PowerPoint (.pptx).</p>
      </div>
      <p v-if="slides.length > 1" class="slide-error" role="alert">Bài học đang có nhiều file slide. Vui lòng xóa file dư và chỉ giữ lại một file.</p>
      <div v-for="{ document, index } in slides" :key="index" class="document-form-card">
        <div class="document-form-fields">
          <label class="slide-name-field">
            <span>Tên hiển thị <span class="required-mark" aria-hidden="true">*</span></span>
            <input v-model="document.name" aria-label="Tên slide" class="form-control" placeholder="Ví dụ: Tổng quan nghiệp vụ" required :disabled="readOnly" />
          </label>
          <div class="document-file-field">
            <span class="field-label">File PowerPoint <span class="required-mark" aria-hidden="true">*</span></span>
            <div class="file-upload-control">
              <label v-if="!readOnly" class="file-picker" :title="document.file?.name || document.originalFileName || 'Chọn file PowerPoint từ máy'">
                <span>{{ document.file?.name || document.originalFileName || 'Chọn file PowerPoint từ máy' }}</span>
                <input type="file" aria-label="Chọn file PowerPoint" accept=".pptx,application/vnd.openxmlformats-officedocument.presentationml.presentation" :required="!document.fileUrl && !document.file" @change="chooseFile($event, document)" />
              </label>
              <span v-else class="readonly-file">{{ document.originalFileName || document.name || 'Chưa có file slide' }}</span>
              <button v-if="!readOnly && (document.file || document.fileUrl)" type="button" class="file-clear-btn" title="Gỡ file slide" aria-label="Gỡ file slide" @click="clearFile(document, $event)">Gỡ file</button>
            </div>
            <small v-if="document.file || document.fileSize" class="selected-file-meta">PPTX · {{ formatFileSize(document.file?.size || document.fileSize) }}</small>
          </div>
        </div>
        <button v-if="!readOnly && slides.length > 1" type="button" class="remove-extra" :aria-label="`Xóa slide dư ${document.name}`" @click="documents.splice(index, 1)">Xóa file dư</button>
      </div>
      <p class="slide-help">Chỉ hỗ trợ .pptx. Với file .ppt, hãy chuyển sang .pptx trước khi tải lên.</p>
    </section>
  </div>
</template>
<script setup lang="ts">
import { computed, watch } from 'vue';
import type { LessonDocument } from '../../types';
import type { LessonContentType } from '../../utils/lessonContent';
import { showToast } from '../../utils/toast';
type DocumentForm = LessonDocument & { file?: File };
const selected = defineModel<LessonContentType[]>({ required: true });
const documents = defineModel<DocumentForm[]>('documents', { required: true });
const props = defineProps<{ readOnly?: boolean }>();
const types: LessonContentType[] = ['TEXT', 'VIDEO', 'SLIDE'];
const labels = { TEXT: 'Văn bản', VIDEO: 'Video', SLIDE: 'Slide PowerPoint' };
const slides = computed(() => documents.value.map((document, index) => ({ document, index })).filter(({ document }) => document.purpose === 'SLIDE'));

// Open one upload card on selection, preserving hidden unsaved values. Legacy
// multiple files stay visible so an editor can explicitly remove the extras.
watch([() => selected.value.includes('SLIDE'), () => slides.value.length, () => props.readOnly], () => {
  if (selected.value.includes('SLIDE') && !props.readOnly && !slides.value.length)
    documents.value.push({ name: '', fileUrl: '', documentType: 'POWERPOINT', purpose: 'SLIDE' });
}, { immediate: true });
function chooseFile(event: Event, document: DocumentForm) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  if (!/\.pptx$/i.test(file.name)) {
    showToast('Slide bắt buộc hỗ trợ .pptx; hãy chuyển .ppt sang .pptx trước khi tải lên.', 'warning');
    input.value = '';
    return;
  }
  document.file = file;
  document.documentType = 'POWERPOINT';
  document.originalFileName = file.name;
  document.fileSize = file.size;
  if (!document.name.trim()) document.name = file.name.replace(/\.pptx$/i, '');
}

function clearFile(document: DocumentForm, event: MouseEvent) {
  document.file = undefined;
  document.fileUrl = '';
  document.originalFileName = '';
  document.fileSize = undefined;
  const input = (event.currentTarget as HTMLElement).closest('.file-upload-control')?.querySelector<HTMLInputElement>('input[type="file"]');
  if (input) input.value = '';
}

function formatFileSize(size?: number) {
  if (size == null) return '';
  return size < 1024 * 1024 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1024 / 1024).toFixed(1)} MB`;
}
</script>
<style scoped>
.content-selection { margin: 1rem 0; }
details { border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; }
summary { display: flex; align-items: center; justify-content: space-between; gap: 1rem; min-height: 44px; padding: .65rem .85rem; cursor: pointer; list-style: none; }
summary::-webkit-details-marker { display: none; }
summary::after { content: ''; width: .45rem; height: .45rem; flex: 0 0 auto; margin-right: .2rem; border-right: 2px solid currentColor; border-bottom: 2px solid currentColor; transform: rotate(45deg); }
details[open] > summary::after { transform: rotate(225deg); }
summary:hover { background: var(--bg-main); }
summary:focus-visible { outline: 2px solid var(--primary-color); outline-offset: -3px; }
details[open] > summary { border-bottom: 1px solid var(--border-color); }
.content-selection-copy { display: flex; flex-direction: column; gap: .15rem; min-width: 0; }
.content-selection-action { color: var(--text-main); font-weight: 600; }
.content-selection-value { color: var(--text-main); overflow-wrap: anywhere; }
.content-selection-help { display: block; margin-top: .35rem; color: var(--text-muted); font-size: var(--lesson-help-font-size, .75rem); line-height: 1.6; }
.content-option { display: flex; gap: .5rem; padding: .6rem; }
.slide-editor { margin-top: 1.25rem; }
.slide-heading h2 { margin: 0 0 .2rem; font-size: 1rem; color: var(--text-main); }
.slide-heading p, .slide-help { margin: 0; color: var(--text-muted); font-size: .8rem; line-height: 1.6; }
.document-form-card { margin-top: .85rem; padding: 1rem; border: 1px solid var(--border-color); border-radius: var(--radius-md, 10px); background: var(--bg-main); }
.document-form-fields { display: grid; grid-template-columns: minmax(0, .8fr) minmax(0, 1.2fr); gap: .85rem; }
.slide-name-field, .document-file-field { min-width: 0; }
.slide-name-field > span, .field-label { display: block; margin-bottom: .4rem; color: var(--text-main); font-size: .75rem; font-weight: 600; }
.form-control { box-sizing: border-box; width: 100%; min-height: 44px; border: 1px solid var(--border-color); border-radius: var(--radius-sm, 6px); padding: .6rem .8rem; font: inherit; font-size: .85rem; color: var(--text-main); background: var(--surface-ffffff); }
.form-control:focus { outline: none; border-color: var(--primary-color); box-shadow: 0 0 0 3px var(--primary-light); }
.file-upload-control { display: flex; align-items: stretch; gap: .45rem; }
.file-picker, .readonly-file { position: relative; box-sizing: border-box; display: flex; align-items: center; gap: .55rem; flex: 1; min-width: 0; min-height: 44px; padding: .55rem .75rem; margin: 0; border: 1px dashed var(--line-9ab9ae); border-radius: var(--radius-sm, 6px); background: var(--surface-ffffff); color: var(--primary-ink); cursor: pointer; }
.file-picker:hover { border-color: var(--primary-color); background: var(--primary-light); }
.file-picker:focus-within { outline: 2px solid var(--primary-color); outline-offset: 2px; }
.file-picker span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: .82rem; font-weight: 600; }
.file-picker input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.readonly-file { cursor: default; overflow-wrap: anywhere; font-size: .82rem; }
.file-clear-btn { display: inline-flex; align-items: center; justify-content: center; flex: 0 0 auto; padding: .55rem .8rem; white-space: nowrap; font: inherit; font-size: .82rem; border: 1px solid var(--line-f0c5c5); border-radius: var(--radius-sm, 6px); background: var(--surface-fff7f7); color: var(--ink-b42318); cursor: pointer; }
.file-clear-btn:hover { border-color: #d92d20; background: var(--surface-fee4e2); }
.file-clear-btn:focus-visible, .remove-extra:focus-visible { outline: 2px solid #d92d20; outline-offset: 2px; }
.selected-file-meta { display: block; margin-top: .35rem; color: var(--text-muted); font-size: .72rem; }
.slide-help { margin-top: .55rem; }
.slide-error { color: var(--ink-b42318); font-size: .82rem; }
.remove-extra { display: inline-flex; align-items: center; gap: .4rem; margin-top: .75rem; padding: .4rem .6rem; border: 1px solid var(--line-f0c5c5); border-radius: 6px; background: var(--surface-fff7f7); color: var(--ink-b42318); cursor: pointer; }
@media (max-width: 700px) { .document-form-fields { grid-template-columns: minmax(0, 1fr); } .document-form-card { padding: .85rem; } }
</style>
