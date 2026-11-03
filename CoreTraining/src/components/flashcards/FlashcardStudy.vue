<template>
  <div class="flashcard-study" @keydown="onKey" tabindex="-1" ref="root" data-autofocus>
    <template v-if="current">
      <div class="study-toolbar">
        <span class="study-count">Thẻ {{ position + 1 }}/{{ queue.length }}</span>
        <span v-if="showLesson" class="study-lesson" :title="current.lessonTitle">{{ current.lessonTitle }}</span>
        <button type="button" class="study-exit" @click="$emit('exit')">
          <X :size="15" /> Kết thúc
        </button>
      </div>
      <div class="study-progress" role="progressbar" aria-label="Tiến độ phiên ôn"
        aria-valuemin="0" :aria-valuemax="queue.length" :aria-valuenow="position">
        <span :style="{ width: `${(position / queue.length) * 100}%` }" />
      </div>

      <button
        type="button"
        class="flip-card"
        :class="{ flipped }"
        :aria-pressed="flipped"
        @click="flipped = !flipped"
      >
        <span class="flip-inner">
          <span class="flip-face front" :aria-hidden="flipped">
            <span class="card-type">{{ TYPE_LABELS[current.type] }}</span>
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

      <div v-if="flipped" class="rating-row" role="group" aria-label="Mức độ ghi nhớ">
        <button
          v-for="(rating, index) in RATINGS"
          :key="rating"
          type="button"
          class="rating-button"
          :class="rating.toLowerCase()"
          :disabled="saving"
          @click="rate(rating)"
        >
          <strong>{{ RATING_LABELS[rating] }}</strong>
          <small>{{ RATING_HINTS[rating] }} · phím {{ index + 1 }}</small>
        </button>
      </div>
      <div v-else class="rating-row single">
        <button type="button" class="btn btn-primary reveal-button" @click="flipped = true">
          Xem đáp án
        </button>
      </div>
    </template>

    <div v-else class="study-finished" role="status">
      <CheckCircle2 :size="30" />
      <strong>Hoàn thành phiên ôn tập</strong>
      <span>{{ reviewedCount }} thẻ đã ôn · {{ counts.AGAIN }} thẻ chưa nhớ</span>
      <div class="finished-actions">
        <button v-if="forgotten.length" type="button" class="btn btn-secondary" @click="restart(forgotten)">
          Ôn lại {{ forgotten.length }} thẻ chưa nhớ
        </button>
        <button type="button" class="btn btn-primary" @click="$emit('exit')">Đóng</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from "vue";
import { CheckCircle2, FileText, RotateCcw, X } from "lucide-vue-next";
import {
  flashcardApi,
  apiErrorMessage,
  RATING_LABELS,
  TYPE_LABELS,
  type Flashcard,
  type FlashcardRating,
} from "../../api/flashcardApi";
import { showToast } from "../../utils/toast";
import { frontParts as splitFront, sourceLabel } from "../../utils/flashcards";

const props = defineProps<{ cards: Flashcard[]; showLesson?: boolean }>();
const emit = defineEmits<{ reviewed: [card: Flashcard]; exit: []; finished: [] }>();

const RATINGS: FlashcardRating[] = ["AGAIN", "HARD", "GOOD", "EASY"];
const RATING_HINTS: Record<FlashcardRating, string> = {
  AGAIN: "Ôn lại ngay",
  HARD: "Nhớ lờ mờ",
  GOOD: "Nhớ được",
  EASY: "Rất chắc",
};

const root = ref<HTMLElement | null>(null);
const queue = ref<Flashcard[]>([]);
const position = ref(0);
const flipped = ref(false);
const saving = ref(false);
const reviewedCount = ref(0);
const counts = reactive<Record<FlashcardRating, number>>({ AGAIN: 0, HARD: 0, GOOD: 0, EASY: 0 });
const forgotten = ref<Flashcard[]>([]);

const current = computed(() => queue.value[position.value] ?? null);
const frontParts = computed(() => splitFront(current.value));

function restart(cards: Flashcard[]) {
  queue.value = [...cards];
  position.value = 0;
  flipped.value = false;
  reviewedCount.value = 0;
  forgotten.value = [];
  (Object.keys(counts) as FlashcardRating[]).forEach((key) => (counts[key] = 0));
  void nextTick(() => root.value?.focus());
}

watch(() => props.cards, (cards) => restart(cards), { immediate: true });
onMounted(() => root.value?.focus());

async function rate(rating: FlashcardRating) {
  const card = current.value;
  if (!card || saving.value) return;
  saving.value = true;
  try {
    const updated = await flashcardApi.review(card.id, rating);
    counts[rating] += 1;
    reviewedCount.value += 1;
    if (rating === "AGAIN") forgotten.value.push(updated);
    emit("reviewed", updated);
    flipped.value = false;
    position.value += 1;
    if (position.value >= queue.value.length) emit("finished");
  } catch (error) {
    showToast(apiErrorMessage(error, "Không lưu được kết quả ôn tập. Vui lòng thử lại."), "error");
  } finally {
    saving.value = false;
  }
}

function onKey(event: KeyboardEvent) {
  if (!current.value || (event.target as HTMLElement)?.closest("input, textarea, select")) return;
  if (event.key === " " || event.key === "Enter") {
    event.preventDefault();
    flipped.value = !flipped.value;
  } else if (flipped.value && ["1", "2", "3", "4"].includes(event.key)) {
    event.preventDefault();
    void rate(RATINGS[Number(event.key) - 1]);
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
.study-exit {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-left: auto;
  padding: 0.35rem 0.6rem;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--surface-ffffff);
  color: var(--text-muted);
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
}
.study-exit:hover {
  color: var(--primary-ink);
  border-color: var(--primary-color);
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
.rating-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 0.55rem;
}
.rating-row.single {
  display: flex;
  justify-content: center;
}
.reveal-button {
  min-width: 200px;
}
.rating-button {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.15rem;
  padding: 0.6rem 0.4rem;
  border: 1px solid var(--border-color);
  border-radius: 10px;
  background: var(--surface-ffffff);
  color: var(--text-color);
  font: inherit;
  cursor: pointer;
}
.rating-button strong {
  font-size: 0.88rem;
}
.rating-button small {
  color: var(--text-muted);
  font-size: 0.72rem;
}
.rating-button:disabled {
  opacity: 0.55;
  cursor: wait;
}
.rating-button.again {
  border-color: var(--danger-border);
  background: var(--danger-surface);
  color: var(--danger-ink);
}
.rating-button.hard {
  border-color: var(--warning-border);
  background: var(--warning-surface);
  color: var(--warning-ink);
}
.rating-button.good {
  border-color: var(--primary-color);
  background: var(--primary-light);
  color: var(--primary-ink);
}
.rating-button.easy {
  border-color: var(--success-border);
  background: var(--success-surface);
  color: var(--success-ink);
}
.rating-button:not(:disabled):hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-sm);
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
.study-finished span {
  color: var(--text-muted);
  font-size: 0.85rem;
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
  .rating-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .rating-button small {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .flip-inner,
  .study-progress span {
    transition: none;
  }
}
</style>
