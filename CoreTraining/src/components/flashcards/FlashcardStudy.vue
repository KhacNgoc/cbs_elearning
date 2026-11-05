<template>
  <div class="flashcard-study" @keydown="onKey" tabindex="-1" ref="root" data-autofocus>
    <template v-if="current">
      <div class="study-toolbar">
        <span class="study-count">Thẻ {{ position + 1 }}/{{ queue.length }}</span>
        <span class="study-seen">Đã lướt qua {{ seen.size }}/{{ queue.length }} thẻ</span>
        <span v-if="showLesson" class="study-lesson" :title="current.lessonTitle">{{ current.lessonTitle }}</span>
        <div class="study-actions">
          <button
            v-if="queue.length > 1"
            type="button"
            class="study-shuffle"
            :class="{ active: shuffled }"
            :aria-pressed="shuffled"
            :disabled="position >= queue.length - 1"
            title="Trộn ngẫu nhiên các thẻ phía sau"
            @keydown.enter.stop
            @keydown.space.stop
            @click="toggleShuffle"
          >
            <Shuffle :size="15" /> {{ shuffled ? "Đang trộn thẻ" : "Trộn thẻ" }}
          </button>
        </div>
      </div>
      <div class="study-progress" role="progressbar" aria-label="Số thẻ đã lướt qua"
        aria-valuemin="0" :aria-valuemax="queue.length" :aria-valuenow="seen.size">
        <span :style="{ width: `${(seen.size / queue.length) * 100}%` }" />
      </div>

      <!-- Keyed by card so moving on never animates the next card's answer into view. -->
      <button
        :key="current.id"
        type="button"
        class="flip-card"
        :class="{ flipped }"
        :aria-pressed="flipped"
        @click="flipped = !flipped"
      >
        <span class="flip-inner">
          <span class="flip-face front" :aria-hidden="flipped">
            <span class="card-type">{{ TYPE_LABELS[current.type] }}</span>
            <span class="card-origin" :class="{ mine: current.mine }">
              <component :is="current.mine ? PencilLine : BookOpen" :size="13" />
              {{ current.mine ? "Thẻ của tôi" : "Thẻ bài học" }}
            </span>
            <span class="card-text">
              <template v-for="(part, index) in frontParts" :key="index">
                <span v-if="part.blank" class="cloze-blank" aria-label="chỗ trống">&nbsp;</span>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
            <small class="flip-hint"><RotateCcw :size="14" /> Chạm hoặc nhấn Space để lật thẻ</small>
          </span>
          <span class="flip-face back" :aria-hidden="!flipped">
            <span class="card-type">{{ current.type === 'CLOZE' ? 'Đáp án' : 'Giải thích' }}</span>
            <span class="card-origin" :class="{ mine: current.mine }">
              <component :is="current.mine ? PencilLine : BookOpen" :size="13" />
              {{ current.mine ? "Thẻ của tôi" : "Thẻ bài học" }}
            </span>
            <span v-if="current.type === 'CLOZE'" class="card-text">
              <template v-for="(part, index) in frontParts" :key="index">
                <mark v-if="part.blank" class="cloze-answer">{{ current.back }}</mark>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
            <template v-else>
              <strong class="card-term">{{ current.front }}</strong>
              <span class="card-text back-text">{{ current.back }}</span>
            </template>
            <small v-if="current.source" class="card-source">
              <FileText :size="13" /> {{ sourceLabel(current.source) }}
            </small>
          </span>
        </span>
      </button>

      <div class="study-nav">
        <button
          type="button"
          class="btn btn-secondary"
          :disabled="position === 0"
          @keydown.enter.stop
          @keydown.space.stop
          @click="move(-1)"
        >
          <ChevronLeft :size="17" /> Thẻ trước
        </button>
        <button
          type="button"
          class="btn btn-primary"
          @keydown.enter.stop
          @keydown.space.stop
          @click="move(1)"
        >
          {{ position < queue.length - 1 ? "Thẻ tiếp" : "Hoàn thành" }} <ChevronRight :size="17" />
        </button>
      </div>
    </template>

    <div v-else class="study-finished" role="status">
      <CheckCircle2 :size="30" />
      <strong>Bạn đã lướt qua hết {{ queue.length }} thẻ</strong>
      <div class="finished-actions">
        <button type="button" class="btn btn-secondary" @click="restart(cards)">Xem lại từ đầu</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from "vue";
import {
  BookOpen,
  CheckCircle2,
  ChevronLeft,
  ChevronRight,
  FileText,
  PencilLine,
  RotateCcw,
  Shuffle,
} from "lucide-vue-next";
import { TYPE_LABELS, type Flashcard } from "../../api/flashcardApi";
import { frontParts as splitFront, shuffleCards, sourceLabel } from "../../utils/flashcards";

const props = defineProps<{ cards: Flashcard[]; showLesson?: boolean }>();
// A parent may bind this to keep the choice across study sessions.
const shuffled = defineModel<boolean>("shuffled", { default: false });

const root = ref<HTMLElement | null>(null);
const queue = ref<Flashcard[]>([]);
// Runs one past the last card to show the finished screen.
const position = ref(0);
const flipped = ref(false);
const seen = ref(new Set<number>());
// The deck order, restored when the learner turns shuffling off.
let deckOrder: Flashcard[] = [];

const current = computed(() => queue.value[position.value] ?? null);
const frontParts = computed(() => splitFront(current.value));

function restart(cards: Flashcard[]) {
  deckOrder = [...cards];
  queue.value = shuffled.value ? shuffleCards(cards) : [...cards];
  position.value = 0;
  flipped.value = false;
  seen.value = new Set(current.value ? [current.value.id] : []);
  void nextTick(() => root.value?.focus());
}

watch(() => props.cards, (cards) => restart(cards), { immediate: true });
// Counts each card once, however many times the learner comes back to it.
watch(current, (card) => { if (card) seen.value.add(card.id); }, { immediate: true });
onMounted(() => root.value?.focus());

/** Reorders only the cards still ahead, so the ones behind stay where the learner left them. */
function toggleShuffle() {
  // A bound model only reads back its new value after the parent re-renders.
  const shuffle = !shuffled.value;
  shuffled.value = shuffle;
  const behind = queue.value.slice(0, position.value);
  const ahead = queue.value.slice(position.value);
  const aheadIds = new Set(ahead.map((card) => card.id));
  queue.value = [
    ...behind,
    ...(shuffle ? shuffleCards(ahead) : deckOrder.filter((card) => aheadIds.has(card.id))),
  ];
  flipped.value = false;
  // Hands keyboard focus back so Space flips the new card instead of pressing this button again.
  root.value?.focus();
}

function move(step: 1 | -1) {
  const next = position.value + step;
  if (next < 0 || next > queue.value.length) return;
  position.value = next;
  flipped.value = false;
  root.value?.focus();
}

function onKey(event: KeyboardEvent) {
  if (!current.value || (event.target as HTMLElement)?.closest("input, textarea, select")) return;
  if (event.key === " " || event.key === "Enter") {
    event.preventDefault();
    flipped.value = !flipped.value;
  } else if (event.key === "ArrowRight") {
    event.preventDefault();
    move(1);
  } else if (event.key === "ArrowLeft") {
    event.preventDefault();
    move(-1);
  }
}
</script>

<style scoped>
.flashcard-study {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;
  outline: none;
}
.study-toolbar {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-width: 0;
}
.study-count {
  font-size: 0.8rem;
  font-weight: 700;
  color: var(--primary-ink);
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.study-seen {
  color: var(--text-muted);
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}
.study-lesson {
  min-width: 0;
  overflow: hidden;
  padding: 0.2rem 0.55rem;
  border-radius: 9999px;
  background: var(--primary-light);
  color: var(--primary-ink);
  font-size: 0.74rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.study-actions {
  display: flex;
  flex-shrink: 0;
  gap: 0.4rem;
  margin-left: auto;
}
.study-shuffle {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  padding: 0.35rem 0.6rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-ffffff);
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}
.study-shuffle:not(:disabled):hover {
  color: var(--primary-ink);
  border-color: var(--primary-color);
}
.study-shuffle.active {
  border-color: var(--primary-color);
  background: var(--primary-light);
  color: var(--primary-ink);
}
.study-shuffle:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.study-progress {
  height: 6px;
  overflow: hidden;
  border-radius: 9999px;
  background: var(--border-color);
}
.study-progress span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--primary-color);
  transition: width 0.3s ease;
}
.flip-card {
  position: relative;
  width: 100%;
  min-height: 260px;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: center;
  cursor: pointer;
  perspective: 1400px;
}
.flip-card:focus-visible {
  outline: 2px solid var(--primary-ink);
  outline-offset: 4px;
  border-radius: 14px;
}
.flip-inner {
  position: relative;
  display: grid;
  min-height: 260px;
  transform-style: preserve-3d;
  transition: transform 0.45s ease;
}
.flip-card.flipped .flip-inner {
  transform: rotateY(180deg);
}
.flip-face {
  grid-area: 1 / 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.9rem;
  padding: 2rem 1.5rem 1.5rem;
  border: 1px solid var(--border-color);
  border-radius: 14px;
  background: var(--surface-ffffff);
  box-shadow: var(--shadow-sm);
  backface-visibility: hidden;
}
.flip-face.back {
  transform: rotateY(180deg);
  border-color: var(--success-border);
  background: var(--success-surface);
}
.card-type {
  position: absolute;
  top: 0.9rem;
  left: 1rem;
  padding: 0.18rem 0.55rem;
  border-radius: 9999px;
  background: var(--primary-light);
  color: var(--primary-ink);
  font-size: 0.72rem;
  font-weight: 700;
}
.card-origin {
  position: absolute;
  top: 0.9rem;
  right: 1rem;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  padding: 0.18rem 0.55rem;
  border-radius: 9999px;
  background: var(--surface-f1f5f9);
  color: var(--text-muted);
  font-size: 0.72rem;
  font-weight: 700;
}
.card-origin.mine {
  background: var(--surface-ede9fe);
  color: var(--ink-6d28d9);
}
.card-text {
  max-width: 640px;
  font-size: 1.15rem;
  line-height: 1.6;
  font-weight: 600;
  overflow-wrap: anywhere;
}
.back-text {
  font-size: 1rem;
  font-weight: 500;
}
.card-term {
  color: var(--primary-ink);
  font-size: 1.2rem;
}
.cloze-blank {
  display: inline-block;
  min-width: 4.5em;
  margin: 0 0.2em;
  border-bottom: 2px solid var(--accent-color);
  vertical-align: baseline;
}
.cloze-answer {
  padding: 0.05em 0.3em;
  border-radius: 6px;
  background: var(--warning-surface);
  color: var(--warning-ink);
  font-weight: 700;
}
.flip-hint,
.card-source {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: var(--text-muted);
  font-size: 0.76rem;
}
.study-nav {
  display: flex;
  justify-content: space-between;
  gap: 0.55rem;
}
.study-nav .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
}
.study-nav .btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.study-finished {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.45rem;
  padding: 2rem 1rem;
  border: 1px dashed var(--success-border);
  border-radius: 14px;
  background: var(--success-surface);
  color: var(--success-ink);
  text-align: center;
}
.finished-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 0.5rem;
  margin-top: 0.6rem;
}
@media (max-width: 600px) {
  .flip-card,
  .flip-inner {
    min-height: 220px;
  }
  .flip-face {
    padding: 2.4rem 1rem 1.2rem;
  }
  .card-text {
    font-size: 1.02rem;
  }
}
@media (prefers-reduced-motion: reduce) {
  .flip-inner,
  .study-progress span {
    transition: none;
  }
}
</style>
