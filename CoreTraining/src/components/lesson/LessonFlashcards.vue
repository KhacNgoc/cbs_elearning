<template>
  <!-- The page has no inline deck: learners open it from the lesson header or the completion reminder. -->
  <AdminModal :open="!!view" :title="studyTitle" :width="680" fixed-height @close="view = null">
    <FlashcardStudy
      v-if="view === 'study'"
      :key="sessionKey"
      v-model:shuffled="shuffled"
      :cards="session"
      @exit="view = null"
    />
    <PersonalFlashcards
      v-else-if="view === 'manage'"
      :lesson-id="lessonId"
      :cards="myCards"
      @saved="applySaved"
      @removed="applyRemoved"
    />

    <template #footer>
      <span v-if="view === 'study'" class="study-shortcuts">
        <kbd>Space</kbd> lật thẻ · <kbd>←</kbd> <kbd>→</kbd> chuyển thẻ · <kbd>Esc</kbd> đóng
      </span>
      <button v-if="view === 'study'" type="button" class="btn btn-secondary footer-action" @click="view = 'manage'">
        <PencilLine :size="16" /> Thêm / sửa thẻ của tôi
      </button>
      <button
        v-else-if="deck?.cards.length"
        type="button"
        class="btn btn-primary footer-action"
        @click="startStudy"
      >
        <Layers3 :size="16" /> Học thẻ ({{ deck.cards.length }})
      </button>
      <button type="button" class="btn btn-secondary" @click="view = null">Đóng</button>
    </template>
  </AdminModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { Layers3, PencilLine } from "lucide-vue-next";
import AdminModal from "../AdminModal.vue";
import FlashcardStudy from "../flashcards/FlashcardStudy.vue";
import PersonalFlashcards from "../flashcards/PersonalFlashcards.vue";
import { flashcardApi, type Flashcard, type FlashcardDeck } from "../../api/flashcardApi";
import { upsertCard } from "../../utils/flashcards";

const props = defineProps<{ lessonId: string; lessonTitle?: string }>();
// The lesson page shows its own shortcuts to this deck, so it needs the count; null when it failed to load.
const emit = defineEmits<{ total: [total: number | null] }>();

const deck = ref<FlashcardDeck | null>(null);
// One deck mixes lesson cards and the learner's own; writing their own cards is a secondary view.
const view = ref<"study" | "manage" | null>(null);
const session = ref<Flashcard[]>([]);
// Remounts the study view so each new session starts from its first card.
const sessionKey = ref(0);
// Lives here so the choice survives leaving to edit cards and starting a new session.
const shuffled = ref(false);
let requestId = 0;

const myCards = computed(() => deck.value?.cards.filter((card) => card.mine) ?? []);

async function load() {
  const current = ++requestId;
  view.value = null;
  try {
    const result = await flashcardApi.lessonDeck(props.lessonId);
    if (current === requestId) deck.value = result;
  } catch {
    // Flashcards are optional support content; the lesson stays usable without them.
    if (current === requestId) deck.value = null;
  }
  // Emitted even for an empty deck so the lesson keeps a way in to write cards.
  if (current === requestId) emit("total", deck.value?.cards.length ?? null);
}

watch(() => props.lessonId, load, { immediate: true });

function replaceCards(cards: Flashcard[]) {
  if (!deck.value) return;
  deck.value = { ...deck.value, cards };
  emit("total", cards.length);
}

function applySaved(saved: Flashcard) {
  if (!deck.value) return;
  replaceCards(upsertCard(deck.value.cards, saved));
}

function applyRemoved(id: number) {
  if (!deck.value) return;
  replaceCards(deck.value.cards.filter((card) => card.id !== id));
}

function startStudy() {
  session.value = [...(deck.value?.cards ?? [])];
  sessionKey.value++;
  view.value = "study";
}

/** Opens on the cards when there are any, otherwise where the learner writes their own. */
function open() {
  if (!deck.value) return;
  if (deck.value.cards.length) startStudy();
  else view.value = "manage";
}

defineExpose({ open });

const studyTitle = computed(() =>
  props.lessonTitle ? `Flashcard · ${props.lessonTitle}` : "Flashcard bài học",
);
</script>

<style scoped>
.footer-action {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}
.study-shortcuts {
  margin-right: auto;
  color: var(--text-muted);
  font-size: var(--admin-text-caption, 0.75rem);
}
.study-shortcuts kbd {
  padding: 0.05rem 0.35rem;
  border: 1px solid var(--border-color);
  border-radius: 4px;
  background: var(--surface-f7faf8, var(--surface-ffffff));
  font-family: inherit;
  font-size: 0.7rem;
}
@media (max-width: 760px) {
  .study-shortcuts {
    display: none;
  }
}
</style>
