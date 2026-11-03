<template>
  <!-- The page has no inline deck: learners open it from the lesson header or the completion reminder. -->
  <AdminModal :open="!!tab" :title="studyTitle" :width="680" @close="tab = null">
    <div class="deck-tabs" role="tablist" aria-label="Bộ thẻ">
      <button
        v-for="option in TABS"
        :key="option.key"
        type="button"
        role="tab"
        :aria-selected="tab === option.key"
        @click="selectTab(option.key)"
      >
        <component :is="option.icon" :size="16" />
        {{ option.label }}
        <span class="tab-count" :class="{ pending: pendingCount(option.key) }">
          {{ pendingCount(option.key) || cardsFor(option.key).length }}
        </span>
      </button>
    </div>

    <template v-if="tab === 'lesson'">
      <FlashcardStudy
        v-if="lessonCards.length"
        :key="sessionKey"
        :cards="session"
        @reviewed="applyReview"
        @exit="tab = null"
      />
      <p v-else class="tab-empty">
        Bài này chưa có thẻ do giảng viên soạn. Bạn có thể tự tạo thẻ ở tab <strong>Thẻ của tôi</strong>.
      </p>
    </template>
    <template v-else-if="tab === 'mine'">
      <FlashcardStudy
        v-if="studyingMine"
        :key="sessionKey"
        :cards="session"
        @reviewed="applyReview"
        @exit="studyingMine = false"
      />
      <PersonalFlashcards
        v-else
        :lesson-id="lessonId"
        :cards="myCards"
        @saved="applySaved"
        @removed="applyRemoved"
      />
    </template>

    <template #footer>
      <span v-if="studying" class="study-shortcuts">
        <kbd>Space</kbd> lật thẻ · <kbd>1</kbd>–<kbd>4</kbd> đánh giá · <kbd>Esc</kbd> đóng
      </span>
      <button v-if="tab === 'mine' && studyingMine" type="button" class="btn btn-secondary footer-action" @click="studyingMine = false">
        <PencilLine :size="16" /> Quản lý thẻ của tôi
      </button>
      <button
        v-else-if="tab === 'mine' && myCards.length"
        type="button"
        class="btn btn-primary footer-action"
        @click="startMineStudy"
      >
        <Layers3 :size="16" /> Ôn thẻ của tôi
      </button>
      <button type="button" class="btn btn-secondary" @click="tab = null">Đóng</button>
    </template>
  </AdminModal>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { BookOpen, Layers3, PencilLine } from "lucide-vue-next";
import AdminModal from "../AdminModal.vue";
import FlashcardStudy from "../flashcards/FlashcardStudy.vue";
import PersonalFlashcards from "../flashcards/PersonalFlashcards.vue";
import {
  flashcardApi,
  type Flashcard,
  type FlashcardDeck,
  type FlashcardStats,
} from "../../api/flashcardApi";
import { deckStats, studyQueue, upsertCard } from "../../utils/flashcards";

type DeckTab = "lesson" | "mine";

const props = defineProps<{ lessonId: string; lessonTitle?: string }>();
// The lesson page shows its own shortcuts to this deck, so it needs the counts.
const emit = defineEmits<{ stats: [stats: FlashcardStats | null] }>();

const TABS = [
  { key: "lesson", label: "Thẻ bài học", icon: BookOpen },
  { key: "mine", label: "Thẻ của tôi", icon: PencilLine },
] as const;

const deck = ref<FlashcardDeck | null>(null);
// Lesson cards are studied straight away; the learner's own tab lists and edits cards until they start a session.
const tab = ref<DeckTab | null>(null);
const studyingMine = ref(false);
const session = ref<Flashcard[]>([]);
// Remounts the study view so each new session starts from its first card.
const sessionKey = ref(0);
let requestId = 0;

const lessonCards = computed(() => deck.value?.cards.filter((card) => !card.mine) ?? []);
const myCards = computed(() => deck.value?.cards.filter((card) => card.mine) ?? []);
const studying = computed(() =>
  (tab.value === "lesson" && lessonCards.value.length > 0) || (tab.value === "mine" && studyingMine.value),
);

const cardsFor = (key: DeckTab) => (key === "lesson" ? lessonCards.value : myCards.value);
const pendingCount = (key: DeckTab) => {
  const stats = deckStats(cardsFor(key));
  return stats.dueCount + stats.newCount;
};

async function load() {
  const current = ++requestId;
  tab.value = null;
  try {
    const result = await flashcardApi.lessonDeck(props.lessonId);
    if (current === requestId) deck.value = result;
  } catch {
    // Flashcards are optional support content; the lesson stays usable without them.
    if (current === requestId) deck.value = null;
  }
  // Stats are emitted even for an empty deck so the lesson keeps a way in to write cards.
  if (current === requestId) emit("stats", deck.value?.stats ?? null);
}

watch(() => props.lessonId, load, { immediate: true });

function replaceCards(cards: Flashcard[]) {
  if (!deck.value) return;
  deck.value = { ...deck.value, cards, stats: deckStats(cards) };
  emit("stats", deck.value.stats);
}

function applyReview(updated: Flashcard) {
  if (!deck.value) return;
  replaceCards(deck.value.cards.map((card) => (card.id === updated.id ? updated : card)));
}

function applySaved(saved: Flashcard) {
  if (!deck.value) return;
  replaceCards(upsertCard(deck.value.cards, saved));
}

function applyRemoved(id: number) {
  if (!deck.value) return;
  replaceCards(deck.value.cards.filter((card) => card.id !== id));
}

/** Starts with cards that are due or new; once caught up, lets the learner run through the whole set. */
function startSession(cards: Flashcard[]) {
  const stats = deckStats(cards);
  session.value = studyQueue(cards, stats.dueCount + stats.newCount > 0 ? "due" : "all");
  sessionKey.value++;
}

function selectTab(key: DeckTab) {
  if (tab.value === key) return;
  tab.value = key;
  studyingMine.value = false;
  if (key === "lesson") startSession(lessonCards.value);
}

function startMineStudy() {
  startSession(myCards.value);
  studyingMine.value = true;
}

/** Opens on the lesson cards when there are any, otherwise where the learner writes their own. */
function open() {
  if (!deck.value) return;
  tab.value = null;
  selectTab(lessonCards.value.length ? "lesson" : "mine");
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
.deck-tabs {
  display: flex;
  gap: 0.25rem;
  margin-bottom: 1rem;
  border-bottom: 1px solid var(--border-color);
}
.deck-tabs button {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: -1px;
  padding: 0.55rem 0.85rem;
  border: 0;
  border-bottom: 2px solid transparent;
  background: transparent;
  color: var(--text-muted);
  font-weight: 700;
  cursor: pointer;
}
.deck-tabs button[aria-selected="true"] {
  border-bottom-color: var(--primary-color);
  color: var(--primary-ink);
}
.tab-count {
  min-width: 1.4rem;
  padding: 0.05rem 0.4rem;
  border-radius: 9999px;
  background: var(--surface-f1f5f9);
  color: var(--text-muted);
  font-size: 0.72rem;
  text-align: center;
}
.tab-count.pending {
  background: var(--primary-light);
  color: var(--primary-ink);
}
.tab-empty {
  margin: 0;
  padding: 1.5rem 1rem;
  border: 1px dashed var(--border-color);
  border-radius: var(--radius-md);
  color: var(--text-muted);
  text-align: center;
}
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
