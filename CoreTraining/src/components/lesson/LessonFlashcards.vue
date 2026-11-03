<template>
  <!-- The page has no inline deck: learners open it from the lesson header or the completion reminder. -->
  <AdminModal :open="studying" :title="studyTitle" :width="680" @close="studying = false">
    <FlashcardStudy
      v-if="studying"
      :cards="session"
      @reviewed="applyReview"
      @exit="studying = false"
    />
    <template #footer>
      <span class="study-shortcuts">
        <kbd>Space</kbd> lật thẻ · <kbd>1</kbd>–<kbd>4</kbd> đánh giá · <kbd>Esc</kbd> đóng
      </span>
      <button type="button" class="btn btn-secondary" @click="studying = false">Đóng</button>
    </template>
  </AdminModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import AdminModal from "../AdminModal.vue";
import FlashcardStudy from "../flashcards/FlashcardStudy.vue";
import {
  flashcardApi,
  type Flashcard,
  type FlashcardDeck,
  type FlashcardStats,
} from "../../api/flashcardApi";
import { deckStats, studyQueue } from "../../utils/flashcards";

const props = defineProps<{ lessonId: string; lessonTitle?: string }>();
// The lesson page shows its own shortcuts to this deck, so it needs the counts.
const emit = defineEmits<{ stats: [stats: FlashcardStats | null] }>();

const deck = ref<FlashcardDeck | null>(null);
const studying = ref(false);
const session = ref<Flashcard[]>([]);
let requestId = 0;

async function load() {
  const current = ++requestId;
  studying.value = false;
  try {
    const result = await flashcardApi.lessonDeck(props.lessonId);
    if (current === requestId) deck.value = result;
  } catch {
    // Flashcards are optional support content; the lesson stays usable without them.
    if (current === requestId) deck.value = null;
  }
  if (current === requestId) emit("stats", deck.value?.stats.total ? deck.value.stats : null);
}

watch(() => props.lessonId, load, { immediate: true });

function applyReview(updated: Flashcard) {
  if (!deck.value) return;
  const cards = deck.value.cards.map((card) => (card.id === updated.id ? updated : card));
  deck.value = { ...deck.value, cards, stats: deckStats(cards) };
  emit("stats", deck.value.stats);
}

/** Starts with cards that are due or new; once caught up, lets the learner run through the whole deck. */
function open() {
  if (!deck.value) return;
  const { dueCount, newCount } = deck.value.stats;
  session.value = studyQueue(deck.value.cards, dueCount + newCount > 0 ? "due" : "all");
  studying.value = session.value.length > 0;
}

defineExpose({ open });

const studyTitle = computed(() =>
  props.lessonTitle ? `Flashcard · ${props.lessonTitle}` : "Flashcard bài học",
);
</script>

<style scoped>
/* The dialog already has its own close controls. */
:global(.admin-modal-body .flashcard-study .study-exit) {
  display: none;
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
