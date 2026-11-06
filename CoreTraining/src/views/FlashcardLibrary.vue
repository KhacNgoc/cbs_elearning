<template>
  <main class="flashcard-library animate-fade-in">
    <header class="library-header">
      <h1>Flashcard của tôi</h1>
      <p>Chọn thẻ từ nhiều bài, nhiều khóa để học chung. Chỉ hiện các bài bạn đã mở khóa.</p>
    </header>

    <SkeletonLoader v-if="loading" class="card" variant="list" :rows="6" label="Đang tải flashcard" />
    <div v-else-if="error" class="card library-state" role="alert">
      <p>{{ error }}</p>
      <button class="btn btn-primary" @click="load">Thử lại</button>
    </div>
    <div v-else-if="!courses.length" class="card library-state">
      <p>Bạn chưa có khóa học nào. Đăng ký khóa học để bắt đầu dùng flashcard.</p>
      <RouterLink class="btn btn-primary" to="/">Về trang chủ</RouterLink>
    </div>
    <template v-else>
      <FlashcardPlanList
        :plans="plans"
        :lessons="allLessons"
        :all-loaded="!nextCursor"
        :error="plansError"
        :busy-id="busyPlanId"
        @study="openPlan($event.id)"
        @edit="openPlanForm($event)"
        @toggle="togglePlan"
        @remove="removePlan"
        @retry="loadPlans"
      />

      <div class="selection-bar">
        <span class="selection-count" aria-live="polite">Đã chọn <strong>{{ selectedTotal }}</strong> thẻ</span>
        <button type="button" class="btn btn-secondary" :disabled="!selectedTotal" @click="selection = {}">
          Bỏ chọn
        </button>
        <button type="button" class="btn btn-secondary" :disabled="!selectedTotal" @click="openPlanForm(null)">
          <CalendarClock :size="16" /> Đặt lịch học
        </button>
        <button type="button" class="btn btn-primary" :disabled="!selectedTotal || starting" @click="startStudy">
          <Layers3 :size="16" /> {{ starting ? "Đang tải thẻ..." : `Học ${selectedTotal} thẻ` }}
        </button>
      </div>

      <section v-for="course in courses" :key="course.id" class="card course-group">
        <div class="course-row">
          <input
            type="checkbox"
            :aria-label="`Chọn tất cả thẻ của khóa ${course.name}`"
            :checked="groupState(course.lessons) === 'all'"
            :indeterminate="groupState(course.lessons) === 'some'"
            :disabled="!courseTotal(course)"
            @change="selection = toggleLessons(selection, course.lessons)"
          />
          <button type="button" class="row-toggle" :aria-expanded="openCourses.has(course.id)" @click="toggle(openCourses, course.id)">
            <component :is="openCourses.has(course.id) ? ChevronDown : ChevronRight" :size="18" />
            <strong>{{ course.name }}</strong>
            <small>{{ courseTotal(course) }} thẻ · {{ course.lessons.length }} bài</small>
          </button>
        </div>

        <ul v-if="openCourses.has(course.id)" class="lesson-list">
          <li v-for="lesson in course.lessons" :key="lesson.id">
            <div class="lesson-row">
              <input
                type="checkbox"
                :aria-label="`Chọn tất cả thẻ của ${lesson.title}`"
                :checked="groupState([lesson]) === 'all'"
                :indeterminate="groupState([lesson]) === 'some'"
                :disabled="!lesson.total"
                @change="selection = toggleLessons(selection, [lesson])"
              />
              <button
                type="button"
                class="row-toggle"
                :aria-expanded="openLessons.has(lesson.id)"
                :disabled="!lesson.total"
                @click="toggleLesson(lesson.id)"
              >
                <component :is="openLessons.has(lesson.id) ? ChevronDown : ChevronRight" :size="16" />
                <span class="lesson-title">{{ lesson.title }}</span>
                <small>{{ lessonCaption(lesson) }}</small>
              </button>
              <button type="button" class="btn btn-secondary manage-button" @click="openManage(lesson)">
                <PencilLine :size="15" /> Thẻ của tôi
              </button>
            </div>

            <div v-if="openLessons.has(lesson.id)" class="card-picker">
              <p v-if="deckErrors[lesson.id]" class="picker-note" role="alert">
                {{ deckErrors[lesson.id] }}
                <button type="button" class="btn btn-secondary" @click="toggleLesson(lesson.id, true)">Thử lại</button>
              </p>
              <SkeletonLoader v-else-if="!decks[lesson.id]" class="picker-note" :rows="3" label="Đang tải thẻ" />
              <label v-for="card in decks[lesson.id] ?? []" v-else :key="card.id" class="card-option">
                <input
                  type="checkbox"
                  :checked="isCardSelected(selection, lesson.id, card.id)"
                  @change="pickCard(lesson.id, card.id)"
                />
                <span class="card-front">{{ card.front }}</span>
                <span class="card-origin" :class="{ mine: card.mine }">{{ card.mine ? "Thẻ của tôi" : "Thẻ bài học" }}</span>
              </label>
            </div>
          </li>
        </ul>
      </section>
      <button v-if="nextCursor" type="button" class="btn btn-secondary load-more" :disabled="loadingMore" @click="loadMore">
        {{ loadingMore ? "Đang tải..." : "Tải thêm khóa học" }}
      </button>
    </template>

    <AdminModal :open="!!studyCards.length" :title="studyTitle" :width="680" @close="studyCards = []">
      <FlashcardStudy
        v-if="studyCards.length"
        v-model:shuffled="shuffled"
        :cards="studyCards"
        show-lesson
      />
      <template #footer>
        <span class="study-shortcuts">Space lật thẻ · ← → chuyển thẻ · Esc đóng</span>
        <button type="button" class="btn btn-secondary" @click="studyCards = []">Đóng</button>
      </template>
    </AdminModal>

    <AdminModal
      :open="planFormOpen"
      :title="editingPlan ? `Sửa lịch học · ${editingPlan.name}` : 'Đặt lịch học'"
      :width="560"
      @close="planFormOpen = false"
    >
      <FlashcardPlanForm
        v-if="planFormOpen"
        v-model:replace-cards="replacePlanCards"
        form-id="flashcard-plan-form"
        :plan="editingPlan"
        :selected-count="selectedTotal"
        @submit="savePlan"
      />
      <template #footer>
        <button type="button" class="btn btn-secondary" @click="planFormOpen = false">Hủy</button>
        <button type="submit" form="flashcard-plan-form" class="btn btn-primary" :disabled="savingPlan">
          {{ savingPlan ? "Đang lưu..." : "Lưu lịch học" }}
        </button>
      </template>
    </AdminModal>

    <AdminModal
      :open="!!managing"
      :title="managing ? `Thẻ của tôi · ${managing.title}` : ''"
      :width="680"
      fixed-height
      @close="managing = null"
    >
      <PersonalFlashcards
        v-if="managing && decks[managing.id]"
        :lesson-id="managing.id"
        :cards="decks[managing.id].filter((card) => card.mine)"
        @saved="applySaved"
        @removed="applyRemoved"
      />
      <p v-else-if="managing && deckErrors[managing.id]" class="picker-note" role="alert">{{ deckErrors[managing.id] }}</p>
      <SkeletonLoader v-else class="picker-note" :rows="5" label="Đang tải thẻ" />
      <template #footer>
        <button type="button" class="btn btn-secondary" @click="managing = null">Đóng</button>
      </template>
    </AdminModal>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { CalendarClock, ChevronDown, ChevronRight, Layers3, PencilLine } from "lucide-vue-next";
import AdminModal from "../components/AdminModal.vue";
import SkeletonLoader from "../components/SkeletonLoader.vue";
import FlashcardPlanForm from "../components/flashcards/FlashcardPlanForm.vue";
import FlashcardPlanList from "../components/flashcards/FlashcardPlanList.vue";
import FlashcardStudy from "../components/flashcards/FlashcardStudy.vue";
import PersonalFlashcards from "../components/flashcards/PersonalFlashcards.vue";
import {
  apiErrorMessage,
  flashcardApi,
  flashcardPlanApi,
  type Flashcard,
  type FlashcardLibraryCourse,
  type FlashcardLibraryLesson,
  type FlashcardPlan,
  type FlashcardPlanRequest,
} from "../api/flashcardApi";
import {
  dropCard,
  isCardSelected,
  selectedCards,
  selectedInLesson,
  selectionToItems,
  toggleCard,
  toggleLessons,
  upsertCard,
  type CardSelection,
} from "../utils/flashcards";
import { showToast } from "../utils/toast";

// Enough to start quickly without flooding the server when many lessons are selected.
const DECK_REQUESTS_AT_ONCE = 4;

const courses = ref<FlashcardLibraryCourse[]>([]);
const nextCursor = ref<string | null>(null);
const loading = ref(false);
const loadingMore = ref(false);
const error = ref("");
const openCourses = reactive(new Set<string>());
const openLessons = reactive(new Set<string>());
const decks = ref<Record<string, Flashcard[]>>({});
const deckErrors = ref<Record<string, string>>({});
const selection = ref<CardSelection>({});
const starting = ref(false);
const studyCards = ref<Flashcard[]>([]);
const shuffled = ref(false);
const managing = ref<FlashcardLibraryLesson | null>(null);
const route = useRoute();
const router = useRouter();
const plans = ref<FlashcardPlan[]>([]);
const plansError = ref("");
const busyPlanId = ref<number | null>(null);
const planFormOpen = ref(false);
const editingPlan = ref<FlashcardPlan | null>(null);
const replacePlanCards = ref(false);
const savingPlan = ref(false);
const studyName = ref("");
const studyTitle = computed(() =>
  studyName.value ? `${studyName.value} · ${studyCards.value.length} thẻ` : `Học ${studyCards.value.length} thẻ`,
);

const allLessons = computed(() => courses.value.flatMap((course) => course.lessons));
const selectedTotal = computed(() =>
  allLessons.value.reduce((sum, lesson) => sum + selectedInLesson(selection.value, lesson.id, lesson.total), 0),
);

const courseTotal = (course: FlashcardLibraryCourse) => course.lessons.reduce((sum, lesson) => sum + lesson.total, 0);
const lessonCaption = (lesson: FlashcardLibraryLesson) => {
  const chosen = selectedInLesson(selection.value, lesson.id, lesson.total);
  const count = chosen ? `${chosen}/${lesson.total} thẻ` : `${lesson.total} thẻ`;
  return lesson.mine ? `${count} · ${lesson.mine} của tôi` : count;
};

function groupState(lessons: FlashcardLibraryLesson[]): "none" | "some" | "all" {
  const total = lessons.reduce((sum, lesson) => sum + lesson.total, 0);
  const chosen = lessons.reduce((sum, lesson) => sum + selectedInLesson(selection.value, lesson.id, lesson.total), 0);
  if (!chosen) return "none";
  return chosen === total ? "all" : "some";
}

function toggle(set: Set<string>, id: string) {
  if (set.has(id)) set.delete(id);
  else set.add(id);
}

/** A page can come back empty when its courses have no open lesson yet, so keep going until one has some. */
async function loadPage() {
  do {
    const page = await flashcardApi.library(nextCursor.value ?? undefined);
    const known = new Set(courses.value.map((course) => course.id));
    const fresh = page.courses.filter((course) => !known.has(course.id));
    courses.value = [...courses.value, ...fresh];
    nextCursor.value = page.nextCursor;
    if (fresh.length) return;
  } while (nextCursor.value);
}

async function loadMore() {
  loadingMore.value = true;
  try {
    await loadPage();
  } catch (e) {
    showToast(apiErrorMessage(e, "Không tải thêm được khóa học."), "error");
  } finally {
    loadingMore.value = false;
  }
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    courses.value = [];
    nextCursor.value = null;
    await loadPage();
    if (courses.value[0]) openCourses.add(courses.value[0].id);
  } catch (e) {
    error.value = apiErrorMessage(e, "Không tải được flashcard.");
  } finally {
    loading.value = false;
  }
}
onMounted(() => {
  void load();
  void loadPlans();
});

async function loadPlans() {
  plansError.value = "";
  try {
    plans.value = await flashcardPlanApi.list();
  } catch (e) {
    // The rest of the page works without plans.
    plansError.value = apiErrorMessage(e, "Không tải được lịch học.");
  }
}

function openPlanForm(plan: FlashcardPlan | null) {
  editingPlan.value = plan;
  replacePlanCards.value = false;
  planFormOpen.value = true;
}

async function savePlan(request: FlashcardPlanRequest) {
  const editing = editingPlan.value;
  savingPlan.value = true;
  try {
    const items = !editing || replacePlanCards.value ? selectionToItems(selection.value) : undefined;
    const saved = editing
      ? await flashcardPlanApi.update(editing.id, { ...request, items, active: editing.active, version: editing.version })
      : await flashcardPlanApi.create({ ...request, items });
    plans.value = editing ? plans.value.map((plan) => (plan.id === saved.id ? saved : plan)) : [saved, ...plans.value];
    planFormOpen.value = false;
    showToast(editing ? "Đã cập nhật lịch học." : "Đã đặt lịch học.");
  } catch (e) {
    showToast(apiErrorMessage(e, "Không lưu được lịch học."), "error");
  } finally {
    savingPlan.value = false;
  }
}

async function togglePlan(plan: FlashcardPlan) {
  busyPlanId.value = plan.id;
  try {
    const saved = await flashcardPlanApi.update(plan.id, {
      name: plan.name,
      startDate: plan.startDate,
      endDate: plan.endDate,
      weekdays: plan.weekdays,
      reminderTime: plan.reminderTime,
      active: !plan.active,
      version: plan.version,
    });
    plans.value = plans.value.map((item) => (item.id === saved.id ? saved : item));
  } catch (e) {
    showToast(apiErrorMessage(e, "Không cập nhật được lịch học."), "error");
  } finally {
    busyPlanId.value = null;
  }
}

async function removePlan(plan: FlashcardPlan) {
  busyPlanId.value = plan.id;
  try {
    await flashcardPlanApi.remove(plan.id);
    plans.value = plans.value.filter((item) => item.id !== plan.id);
    showToast("Đã xóa lịch học.");
  } catch (e) {
    showToast(apiErrorMessage(e, "Không xóa được lịch học."), "error");
  } finally {
    busyPlanId.value = null;
  }
}

async function openPlan(id: number) {
  try {
    const session = await flashcardPlanApi.session(id);
    if (!session.cards.length) {
      showToast("Lịch học này hiện không còn thẻ nào để học.", "error");
      return;
    }
    studyName.value = session.name;
    studyCards.value = session.cards;
  } catch (e) {
    showToast(apiErrorMessage(e, "Không mở được lịch học."), "error");
  }
}

// A reminder links here with ?plan=; the query is removed so a reload does not reopen it and the same link works twice.
watch(
  () => route.query.plan,
  (plan) => {
    const id = Number(plan);
    if (!plan || !Number.isInteger(id)) return;
    void router.replace({ query: {} });
    void openPlan(id);
  },
  { immediate: true },
);

async function loadDeck(lessonId: string): Promise<Flashcard[]> {
  if (decks.value[lessonId]) return decks.value[lessonId];
  delete deckErrors.value[lessonId];
  try {
    const cards = (await flashcardApi.lessonDeck(lessonId)).cards;
    decks.value[lessonId] = cards;
    return cards;
  } catch (e) {
    deckErrors.value[lessonId] = apiErrorMessage(e, "Không tải được thẻ của bài này.");
    throw e;
  }
}

function toggleLesson(lessonId: string, retry = false) {
  if (!retry) toggle(openLessons, lessonId);
  if (openLessons.has(lessonId)) void loadDeck(lessonId).catch(() => {});
}

function pickCard(lessonId: string, cardId: number) {
  selection.value = toggleCard(selection.value, lessonId, cardId, (decks.value[lessonId] ?? []).map((card) => card.id));
}

async function startStudy() {
  starting.value = true;
  const lessons = allLessons.value.filter((lesson) => selection.value[lesson.id]);
  const failed: string[] = [];
  try {
    for (let index = 0; index < lessons.length; index += DECK_REQUESTS_AT_ONCE) {
      const batch = lessons.slice(index, index + DECK_REQUESTS_AT_ONCE);
      await Promise.all(batch.map((lesson) => loadDeck(lesson.id).catch(() => failed.push(lesson.title))));
    }
    const cards = lessons.flatMap((lesson) => selectedCards(decks.value[lesson.id] ?? [], selection.value[lesson.id]));
    studyName.value = "";
    if (failed.length) showToast(`Không tải được thẻ của ${failed.length} bài, các bài còn lại vẫn học được.`, "error");
    studyCards.value = cards;
  } finally {
    starting.value = false;
  }
}

function openManage(lesson: FlashcardLibraryLesson) {
  managing.value = lesson;
  void loadDeck(lesson.id).catch(() => {});
}

function applySaved(saved: Flashcard) {
  const lesson = managing.value;
  if (!lesson) return;
  const cards = decks.value[lesson.id] ?? [];
  if (!cards.some((card) => card.id === saved.id)) {
    lesson.total += 1;
    lesson.mine += 1;
  }
  decks.value[lesson.id] = upsertCard(cards, saved);
}

function applyRemoved(id: number) {
  const lesson = managing.value;
  if (!lesson) return;
  decks.value[lesson.id] = (decks.value[lesson.id] ?? []).filter((card) => card.id !== id);
  lesson.total -= 1;
  lesson.mine -= 1;
  selection.value = dropCard(selection.value, lesson.id, id, lesson.total);
}
</script>

<style scoped>
.flashcard-library {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  min-width: 0;
}
.load-more {
  align-self: center;
}

.library-header h1 {
  margin: 0 0 0.3rem;
  font-size: 1.5rem;
  letter-spacing: -0.02em;
}
.library-header p {
  margin: 0;
  color: var(--text-muted);
  font-size: 0.9rem;
}
.library-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 2.5rem 1rem;
  text-align: center;
}
.library-state p {
  margin: 0;
  color: var(--text-muted);
}
.selection-bar {
  position: sticky;
  top: 5rem;
  z-index: 5;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding: 0.7rem 1rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  background: var(--surface-ffffff);
  box-shadow: var(--shadow-sm);
}
.selection-count {
  margin-right: auto;
  font-size: 0.9rem;
  font-variant-numeric: tabular-nums;
}
.selection-bar .btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}
.selection-bar .btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.course-group {
  padding: 0;
  overflow: hidden;
}
.course-row,
.lesson-row {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  padding: 0.7rem 1rem;
}
.lesson-list {
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--border-color);
}
.lesson-list li + li {
  border-top: 1px solid var(--border-color);
}
.lesson-row {
  padding-left: 1.5rem;
}
input[type="checkbox"] {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  accent-color: var(--primary-color);
  cursor: pointer;
}
input[type="checkbox"]:disabled {
  cursor: not-allowed;
}
.row-toggle {
  display: flex;
  flex: 1;
  align-items: center;
  gap: 0.4rem;
  min-width: 0;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.row-toggle:disabled {
  color: var(--text-muted);
  cursor: default;
}
.row-toggle > strong,
.lesson-title {
  min-width: 0;
  overflow-wrap: anywhere;
}
.row-toggle small {
  flex-shrink: 0;
  margin-left: auto;
  color: var(--text-muted);
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
}
.manage-button {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0.3rem;
  padding: 0.3rem 0.6rem;
  font-size: 0.8rem;
}
.card-picker {
  display: grid;
  gap: 0.35rem;
  padding: 0 1rem 0.8rem 3.1rem;
}
.picker-note {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  color: var(--text-muted);
  font-size: 0.85rem;
}
.card-option {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.5rem 0.65rem;
  border: 1px solid var(--border-color);
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.card-option:hover {
  border-color: var(--primary-color);
}
.card-front {
  flex: 1;
  min-width: 0;
  font-size: 0.88rem;
  overflow-wrap: anywhere;
}
.card-origin {
  flex-shrink: 0;
  padding: 0.1rem 0.5rem;
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
.study-shortcuts {
  margin-right: auto;
  color: var(--text-muted);
  font-size: 0.75rem;
}
@media (max-width: 600px) {
  /* Fixed slots: wrapping by content width moved the buttons whenever the count or a label changed. */
  .selection-bar {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .selection-count {
    margin-right: 0;
  }
  .selection-bar .btn {
    padding-inline: 0.5rem;
  }
  .lesson-row {
    flex-wrap: wrap;
    padding-left: 1rem;
  }
  .row-toggle {
    flex-basis: 70%;
  }
  .manage-button {
    margin-left: 1.6rem;
  }
  .card-picker {
    padding-left: 1rem;
  }
  .study-shortcuts {
    display: none;
  }
}
</style>
