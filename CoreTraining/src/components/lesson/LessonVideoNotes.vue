<template>
  <section ref="sectionElement" class="video-notes-section">
    <div class="notes-heading">
      <div>
        <span class="eyebrow">Ghi chú cá nhân</span>
        <h2>Ghi chú theo thời lượng video</h2>
      </div>
      <button class="open-composer-button" @click="openAt(currentTime)">
        <NotebookPen :size="17" /> Thêm ghi chú
      </button>
    </div>

    <form v-if="composerOpen" data-testid="note-composer" class="note-composer" @submit.prevent="submitNote">
      <button type="button" class="timestamp-button current" @click="$emit('seek', currentTimestamp)">
        <Clock3 :size="15" /> {{ formatTimestamp(currentTimestamp) }}
      </button>
      <textarea ref="composerTextarea" v-model="newContent" maxlength="2000" rows="3"
        placeholder="Nhập ghi chú tại thời điểm đang xem..." @keydown.ctrl.enter="submitNote"
        @keydown.meta.enter="submitNote"></textarea>
      <div class="composer-footer">
        <small>Ghi chú này chỉ hiển thị với bạn · Ctrl/⌘ + Enter để lưu</small>
        <div class="composer-actions">
          <button type="button" class="cancel-note-button" @click="closeComposer">Hủy</button>
          <button type="submit" class="save-note-button" :disabled="saving || !newContent.trim()">
            <LoaderCircle v-if="saving" class="spinning" :size="17" />
            <Plus v-else :size="17" />
            Lưu ghi chú
          </button>
        </div>
      </div>
    </form>

    <div v-if="loading" class="notes-state">
      <LoaderCircle class="spinning" :size="22" /> Đang tải ghi chú...
    </div>
    <div v-else-if="notes.length === 0" class="notes-state empty">
      <StickyNote :size="26" />
      <strong>Chưa có ghi chú</strong>
      <span>Trong khi xem video, hãy nhập nội dung phía trên để lưu đúng mốc thời gian.</span>
    </div>
    <div v-else class="notes-list">
      <article v-for="note in notes" :key="note.id" class="video-note">
        <button class="timestamp-button" @click="$emit('seek', note.timestampSeconds)">
          <Play :size="13" fill="currentColor" /> {{ formatTimestamp(note.timestampSeconds) }}
        </button>

        <div v-if="editingId === note.id" class="note-edit-area">
          <textarea v-model="editingContent" maxlength="2000" rows="3"
            @keydown.ctrl.enter="saveEdit(note)" @keydown.meta.enter="saveEdit(note)"></textarea>
          <div>
            <button class="text-action" @click="cancelEdit">Hủy</button>
            <button class="text-action primary" :disabled="saving || !editingContent.trim()"
              @click="saveEdit(note)">Lưu</button>
          </div>
        </div>
        <div v-else class="note-body">
          <p>{{ note.content }}</p>
          <small>{{ formatUpdatedAt(note.updatedAt) }}</small>
        </div>

        <div v-if="editingId !== note.id" class="note-actions">
          <button title="Sửa ghi chú" @click="startEdit(note)"><Pencil :size="16" /></button>
          <button title="Xóa ghi chú" class="danger" @click="requestDelete(note)">
            <Trash2 :size="16" />
          </button>
        </div>
      </article>
    </div>
    <ConfirmModal
      :open="noteToDelete !== null"
      title="Xóa ghi chú?"
      :message="deleteConfirmationMessage"
      confirm-label="Xóa ghi chú"
      :loading="saving"
      @cancel="cancelDelete"
      @confirm="confirmDelete"
    />
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue';
import {
  Clock3, LoaderCircle, NotebookPen, Pencil, Play, Plus, StickyNote, Trash2
} from 'lucide-vue-next';
import type { VideoNote } from '../../types';
import ConfirmModal from '../ConfirmModal.vue';

const props = defineProps<{
  notes: VideoNote[];
  currentTime: number;
  loading: boolean;
  saving: boolean;
}>();

const emit = defineEmits<{
  create: [content: string, timestampSeconds: number];
  update: [note: VideoNote, content: string];
  delete: [note: VideoNote];
  seek: [seconds: number];
}>();

const newContent = ref('');
const composerOpen = ref(false);
const composerTimestamp = ref<number | null>(null);
const sectionElement = ref<HTMLElement | null>(null);
const composerTextarea = ref<HTMLTextAreaElement | null>(null);
const editingId = ref<number | null>(null);
const editingContent = ref('');
const noteToDelete = ref<VideoNote | null>(null);
const currentTimestamp = computed(() =>
  Math.max(0, Math.floor(composerTimestamp.value ?? props.currentTime))
);
const deleteConfirmationMessage = computed(() =>
  noteToDelete.value
    ? `Ghi chú tại ${formatTimestamp(noteToDelete.value.timestampSeconds)} sẽ bị xóa và không thể khôi phục.`
    : ''
);

const openAt = async (seconds: number) => {
  composerTimestamp.value = Math.max(0, Math.floor(seconds));
  composerOpen.value = true;
  await nextTick();
  sectionElement.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  composerTextarea.value?.focus();
};

const closeComposer = () => {
  composerOpen.value = false;
  composerTimestamp.value = null;
  newContent.value = '';
};

const submitNote = () => {
  const content = newContent.value.trim();
  if (!content || props.saving) return;
  emit('create', content, currentTimestamp.value);
  closeComposer();
};

const startEdit = (note: VideoNote) => {
  editingId.value = note.id;
  editingContent.value = note.content;
};

const cancelEdit = () => {
  editingId.value = null;
  editingContent.value = '';
};

const saveEdit = (note: VideoNote) => {
  const content = editingContent.value.trim();
  if (!content || props.saving) return;
  emit('update', note, content);
  cancelEdit();
};

const requestDelete = (note: VideoNote) => {
  noteToDelete.value = note;
};

const cancelDelete = () => {
  if (props.saving) return;
  noteToDelete.value = null;
};

const confirmDelete = () => {
  if (!noteToDelete.value || props.saving) return;
  emit('delete', noteToDelete.value);
  noteToDelete.value = null;
};

const formatTimestamp = (seconds: number) => {
  const safeSeconds = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(safeSeconds / 3600);
  const minutes = Math.floor((safeSeconds % 3600) / 60);
  const remainingSeconds = safeSeconds % 60;
  return hours > 0
    ? `${hours}:${minutes.toString().padStart(2, '0')}:${remainingSeconds.toString().padStart(2, '0')}`
    : `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
};

const formatUpdatedAt = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime())
    ? ''
    : `Cập nhật ${date.toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })}`;
};

defineExpose({ openAt });
</script>

<style scoped>
.video-notes-section { margin-top: 2.5rem; padding-top: 2rem; border-top: 1px solid var(--border-color); }
.notes-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 1rem; }
.notes-heading h2 { margin: .35rem 0 1rem; font-size: 1.45rem; }
.open-composer-button { padding: .55rem .75rem; display: flex; align-items: center; gap: .4rem; border: 1px solid rgba(0,91,65,.28); border-radius: 7px; background: var(--surface-ffffff); color: var(--primary-ink); font-weight: 700; cursor: pointer; white-space: nowrap; }
.open-composer-button:hover { background: var(--primary-light); border-color: var(--primary-color); }
.note-composer { padding: 1rem; border: 1px solid var(--line-d8e2de); border-radius: 10px; background: var(--surface-f8faf9); }
.timestamp-button { width: fit-content; padding: .35rem .55rem; display: inline-flex; align-items: center; gap: .35rem; border: 0; border-radius: 5px; background: var(--surface-e5f1ed); color: var(--primary-ink); font-size: .78rem; font-weight: 800; cursor: pointer; }
.timestamp-button:hover { background: var(--surface-d4e8e1); }
.timestamp-button.current { margin-bottom: .65rem; background: var(--primary-color); color: white; }
.note-composer textarea, .note-edit-area textarea { width: 100%; padding: .75rem; resize: vertical; border: 1px solid var(--line-cbd8d3); border-radius: 7px; background: var(--surface-ffffff); color: var(--text-main); font: inherit; outline: none; }
.note-composer textarea:focus, .note-edit-area textarea:focus { border-color: var(--primary-color); box-shadow: 0 0 0 3px rgba(0,91,65,.1); }
.composer-footer { margin-top: .65rem; display: flex; align-items: center; justify-content: space-between; gap: 1rem; }
.composer-footer small { color: var(--text-muted); }
.composer-actions { display: flex; align-items: center; gap: .4rem; }
.cancel-note-button { padding: .58rem .75rem; border: 0; background: transparent; color: var(--ink-52655e); font-weight: 700; cursor: pointer; }
.cancel-note-button:hover { color: var(--text-main); }
.save-note-button { padding: .58rem .85rem; display: flex; align-items: center; gap: .4rem; border: 0; border-radius: 7px; background: var(--primary-color); color: white; font-weight: 700; cursor: pointer; white-space: nowrap; }
.save-note-button:disabled, .text-action:disabled { opacity: .5; cursor: not-allowed; }
.notes-state { min-height: 110px; display: flex; align-items: center; justify-content: center; gap: .55rem; color: var(--text-muted); }
.notes-state.empty { flex-direction: column; text-align: center; }
.notes-state.empty svg { color: var(--ink-8aa89d); }
.notes-state.empty strong { color: var(--text-main); }
.notes-state.empty span { max-width: 480px; font-size: .86rem; }
.notes-list { margin-top: 1rem; border-top: 1px solid var(--line-e4ebe8); }
.video-note { padding: 1rem .25rem; display: grid; grid-template-columns: 78px minmax(0,1fr) auto; align-items: flex-start; gap: .85rem; border-bottom: 1px solid var(--line-e4ebe8); }
.note-body p { white-space: pre-wrap; color: var(--ink-344b43); line-height: 1.55; }
.note-body small { display: block; margin-top: .35rem; color: var(--text-muted); font-size: .7rem; }
.note-actions { display: flex; gap: .2rem; opacity: 0; transition: opacity .15s ease; }
.video-note:hover .note-actions, .note-actions:focus-within { opacity: 1; }
.note-actions button { width: 32px; height: 32px; display: grid; place-items: center; border: 0; border-radius: 6px; background: transparent; color: var(--ink-64756f); cursor: pointer; }
.note-actions button:hover { background: var(--surface-edf3f1); color: var(--primary-ink); }
.note-actions button.danger:hover { background: var(--surface-fff0f0); color: var(--ink-c53030); }
.note-edit-area > div { margin-top: .45rem; display: flex; justify-content: flex-end; gap: .35rem; }
.text-action { padding: .4rem .7rem; border: 0; border-radius: 5px; background: var(--surface-e8eeec); color: var(--ink-4f625b); font-weight: 700; cursor: pointer; }
.text-action.primary { background: var(--primary-color); color: white; }
.spinning { animation: notes-spin .8s linear infinite; }
@keyframes notes-spin { to { transform: rotate(360deg); } }

@media (max-width: 600px) {
  .notes-heading { align-items: flex-start; }
  .open-composer-button { font-size: .75rem; }
  .composer-footer { align-items: flex-start; flex-direction: column; }
  .composer-actions { width: 100%; justify-content: flex-end; }
  .video-note { grid-template-columns: 70px minmax(0,1fr); }
  .note-actions { grid-column: 2; opacity: 1; justify-content: flex-end; }
}
</style>
