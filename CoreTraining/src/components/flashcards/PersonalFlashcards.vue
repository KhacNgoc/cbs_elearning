<template>
  <div class="personal-flashcards">
    <form class="personal-form" @submit.prevent="save">
      <div class="form-head">
        <strong>{{ editing ? "Sửa thẻ của bạn" : "Thêm thẻ của bạn" }}</strong>
        <div class="type-switch" role="radiogroup" aria-label="Loại thẻ">
          <button
            v-for="type in TYPES"
            :key="type"
            type="button"
            role="radio"
            :aria-checked="form.type === type"
            @click="form.type = type"
          >
            {{ TYPE_LABELS[type] }}
          </button>
        </div>
      </div>
      <label class="field">
        <span>{{ form.type === "CLOZE" ? "Câu có chỗ trống" : "Mặt trước (thuật ngữ / câu hỏi)" }}</span>
        <textarea
          ref="frontInput"
          v-model="form.front"
          rows="2"
          maxlength="1000"
          :placeholder="form.type === 'CLOZE' ? 'VD: Hồ sơ mở CIF phải được phê duyệt trong vòng ____.' : 'VD: CIF'"
          data-autofocus
        />
        <small v-if="form.type === 'CLOZE'" class="field-hint">
          Dùng ____ để đánh dấu cụm từ cần nhớ.
          <button type="button" class="link-button" @click="insertBlank">Chèn chỗ trống</button>
        </small>
      </label>
      <label class="field">
        <span>{{ form.type === "CLOZE" ? "Đáp án cho chỗ trống" : "Mặt sau (giải thích)" }}</span>
        <textarea
          v-model="form.back"
          :rows="form.type === 'CLOZE' ? 1 : 3"
          maxlength="4000"
          :placeholder="form.type === 'CLOZE' ? 'VD: 2 ngày làm việc' : 'VD: Mã định danh duy nhất của khách hàng.'"
        />
      </label>
      <p v-if="touched && draftError" class="field-error">{{ draftError }}</p>
      <div class="form-actions">
        <button v-if="editing" type="button" class="btn btn-secondary" @click="reset">Hủy sửa</button>
        <button type="submit" class="btn btn-primary" :disabled="saving">
          <Plus v-if="!editing" :size="16" />{{ editing ? "Lưu thay đổi" : "Thêm thẻ" }}
        </button>
      </div>
    </form>

    <div class="personal-list">
      <div class="list-head">
        <strong>Thẻ của tôi</strong>
        <span>{{ cards.length }} thẻ · chỉ bạn nhìn thấy</span>
      </div>
      <p v-if="!cards.length" class="list-empty">
        Bạn chưa có thẻ nào ở bài này. Ghi lại thuật ngữ hay ý chính cần nhớ, thẻ sẽ được nhắc ôn theo lịch.
      </p>
      <ul v-else>
        <li v-for="card in cards" :key="card.id" :class="{ active: editing?.id === card.id }">
          <div class="card-copy">
            <span class="card-type">{{ TYPE_LABELS[card.type] }}</span>
            <strong>{{ card.front }}</strong>
            <span class="card-back">{{ card.back }}</span>
          </div>
          <div v-if="deletingId === card.id" class="card-actions confirm">
            <span>Xóa thẻ này?</span>
            <button type="button" class="mini-button" @click="deletingId = null">Không</button>
            <button type="button" class="mini-button danger" :disabled="removing" @click="remove(card)">Xóa</button>
          </div>
          <div v-else class="card-actions">
            <button type="button" class="icon-button" :aria-label="`Sửa thẻ ${card.front}`" @click="edit(card)">
              <Edit :size="15" />
            </button>
            <button type="button" class="icon-button danger" :aria-label="`Xóa thẻ ${card.front}`" @click="deletingId = card.id">
              <Trash2 :size="15" />
            </button>
          </div>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref } from "vue";
import { Edit, Plus, Trash2 } from "lucide-vue-next";
import {
  flashcardApi,
  apiErrorMessage,
  CLOZE_BLANK,
  TYPE_LABELS,
  type Flashcard,
  type FlashcardType,
} from "../../api/flashcardApi";
import { flashcardDraftError } from "../../utils/flashcards";
import { showToast } from "../../utils/toast";

const props = defineProps<{ lessonId: string; cards: Flashcard[] }>();
const emit = defineEmits<{ saved: [card: Flashcard]; removed: [id: number] }>();

const TYPES: FlashcardType[] = ["TERM", "CLOZE"];

const form = reactive({ type: "TERM" as FlashcardType, front: "", back: "" });
const editing = ref<Flashcard | null>(null);
const touched = ref(false);
const saving = ref(false);
const deletingId = ref<number | null>(null);
const removing = ref(false);
const frontInput = ref<HTMLTextAreaElement | null>(null);

const draftError = computed(() => flashcardDraftError(form));

function reset() {
  editing.value = null;
  touched.value = false;
  Object.assign(form, { type: "TERM", front: "", back: "" });
}

function edit(card: Flashcard) {
  editing.value = card;
  touched.value = false;
  Object.assign(form, { type: card.type, front: card.front, back: card.back });
  nextTick(() => frontInput.value?.focus());
}

function insertBlank() {
  const input = frontInput.value;
  const start = input?.selectionStart ?? form.front.length;
  const end = input?.selectionEnd ?? start;
  form.front = form.front.slice(0, start) + CLOZE_BLANK + form.front.slice(end);
  nextTick(() => {
    input?.focus();
    input?.setSelectionRange(start + CLOZE_BLANK.length, start + CLOZE_BLANK.length);
  });
}

async function save() {
  touched.value = true;
  if (draftError.value || saving.value) return;
  saving.value = true;
  const request = { type: form.type, front: form.front, back: form.back };
  try {
    const saved = editing.value
      ? await flashcardApi.updateMine(editing.value.id, { ...request, version: editing.value.version ?? undefined })
      : await flashcardApi.createMine(props.lessonId, request);
    emit("saved", saved);
    showToast(editing.value ? "Đã cập nhật thẻ." : "Đã thêm thẻ.");
    reset();
    nextTick(() => frontInput.value?.focus());
  } catch (error) {
    showToast(apiErrorMessage(error, "Không lưu được thẻ."), "error");
  } finally {
    saving.value = false;
  }
}

async function remove(card: Flashcard) {
  removing.value = true;
  try {
    await flashcardApi.removeMine(card.id);
    if (editing.value?.id === card.id) reset();
    deletingId.value = null;
    emit("removed", card.id);
    showToast("Đã xóa thẻ.");
  } catch (error) {
    showToast(apiErrorMessage(error, "Không xóa được thẻ."), "error");
  } finally {
    removing.value = false;
  }
}
</script>

<style scoped>
.personal-flashcards { display: grid; gap: 1.1rem; }
.personal-form { display: grid; gap: .8rem; padding: 1rem; border: 1px solid var(--border-color); border-radius: var(--radius-md); background: var(--surface-f8fafc); }
.form-head { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .6rem; }
.type-switch { display: inline-flex; padding: .2rem; border: 1px solid var(--border-color); border-radius: 999px; background: var(--surface-ffffff); }
.type-switch button { padding: .3rem .8rem; border: 0; border-radius: 999px; background: transparent; color: var(--text-muted); font-weight: 700; cursor: pointer; }
.type-switch button[aria-checked="true"] { background: var(--primary-light); color: var(--primary-ink); }
.field { display: block; }
.field > span { display: block; margin-bottom: .35rem; font-size: var(--admin-text-label, .85rem); font-weight: 700; }
.field textarea { width: 100%; padding: .6rem .7rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); color: var(--text-color); font-family: var(--font-family); resize: vertical; }
.field-hint { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; margin-top: .35rem; color: var(--text-muted); font-size: var(--admin-text-caption, .75rem); }
.link-button { padding: .2rem .5rem; border: 1px solid var(--border-color); border-radius: 6px; background: var(--surface-ffffff); color: var(--primary-ink); font-size: var(--admin-text-caption, .75rem); font-weight: 700; cursor: pointer; }
.field-error { margin: 0; color: var(--danger-ink); font-size: var(--admin-text-label, .85rem); }
.form-actions { display: flex; justify-content: flex-end; gap: .5rem; }
.form-actions .btn { display: inline-flex; align-items: center; gap: .35rem; }
.list-head { display: flex; flex-wrap: wrap; align-items: baseline; justify-content: space-between; gap: .4rem; margin-bottom: .5rem; }
.list-head span, .list-empty { color: var(--text-muted); font-size: var(--admin-text-label, .85rem); }
.list-empty { margin: 0; padding: .9rem 1rem; border: 1px dashed var(--border-color); border-radius: var(--radius-md); }
.personal-list ul { display: grid; gap: .5rem; max-height: 320px; margin: 0; padding: 0; overflow-y: auto; list-style: none; }
.personal-list li { display: flex; align-items: flex-start; justify-content: space-between; gap: .75rem; padding: .7rem .85rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); }
.personal-list li.active { border-color: var(--primary-color); }
.card-copy { display: flex; flex-direction: column; gap: .2rem; min-width: 0; }
.card-copy strong { overflow-wrap: anywhere; }
.card-type { color: var(--text-muted); font-size: var(--admin-text-caption, .75rem); font-weight: 700; text-transform: uppercase; }
.card-back { color: var(--text-muted); overflow-wrap: anywhere; white-space: pre-line; }
.card-actions { display: flex; flex-shrink: 0; align-items: center; gap: .3rem; }
.card-actions.confirm { font-size: var(--admin-text-label, .85rem); }
.mini-button { padding: .25rem .6rem; border: 1px solid var(--border-color); border-radius: 6px; background: var(--surface-ffffff); color: var(--text-color); font-weight: 700; cursor: pointer; }
.mini-button.danger { border-color: var(--danger-ink); color: var(--danger-ink); }
.icon-button { display: inline-flex; padding: .35rem; border: 1px solid var(--border-color); border-radius: 6px; background: var(--surface-ffffff); color: var(--text-muted); cursor: pointer; }
.icon-button:hover { color: var(--primary-ink); }
.icon-button.danger:hover { color: var(--danger-ink); }
</style>
