<template>
  <form :id="formId" class="plan-form" novalidate @submit.prevent="submit">
    <label class="field">
      <span>Tên lịch học</span>
      <input v-model="draft.name" type="text" maxlength="120" placeholder="VD: Ôn CIF trước kỳ kiểm tra" data-autofocus />
    </label>

    <fieldset class="field">
      <legend>Thời gian học</legend>
      <div class="segment" role="radiogroup" aria-label="Thời gian học">
        <button type="button" role="radio" :aria-checked="draft.singleDay" @click="draft.singleDay = true">Một ngày</button>
        <button type="button" role="radio" :aria-checked="!draft.singleDay" @click="draft.singleDay = false">Khoảng ngày</button>
      </div>
      <div class="date-row">
        <label>
          <small>{{ draft.singleDay ? "Ngày học" : "Từ ngày" }}</small>
          <input v-model="draft.startDate" type="date" />
        </label>
        <label v-if="!draft.singleDay">
          <small>Đến ngày</small>
          <input v-model="draft.endDate" type="date" :min="draft.startDate" />
        </label>
      </div>
    </fieldset>

    <fieldset v-if="!draft.singleDay" class="field">
      <legend>Tần suất</legend>
      <div class="weekday-row">
        <button
          v-for="day in WEEKDAYS"
          :key="day.value"
          type="button"
          class="weekday"
          :aria-pressed="chosenDays.includes(day.value)"
          :disabled="!availableDays.includes(day.value)"
          :title="availableDays.includes(day.value) ? undefined : 'Không có trong khoảng ngày đã chọn'"
          @click="toggleDay(day.value)"
        >
          {{ day.label }}
        </button>
        <button type="button" class="link-button" @click="draft.weekdays = allDays()">Hằng ngày</button>
      </div>
    </fieldset>

    <fieldset class="field">
      <legend>Nhắc học</legend>
      <label class="check">
        <input v-model="draft.remind" type="checkbox" />
        <span>Nhắc tôi trong app vào giờ này</span>
        <input v-model="draft.reminderTime" type="time" :disabled="!draft.remind" aria-label="Giờ nhắc" />
      </label>
      <small class="hint">Theo giờ Việt Nam. Thông báo nằm ở chuông, bấm vào là mở đúng các thẻ của lịch.</small>
    </fieldset>

    <p v-if="!plan" class="cards-note">Lịch gồm <strong>{{ selectedCount }}</strong> thẻ đang chọn.</p>
    <label v-else-if="selectedCount" class="check">
      <input :checked="replaceCards" type="checkbox" @change="replaceCards = ($event.target as HTMLInputElement).checked" />
      <span>Thay thẻ trong lịch bằng {{ selectedCount }} thẻ đang chọn</span>
    </label>

    <p v-if="touched && error" class="field-error" role="alert">{{ error }}</p>
  </form>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from "vue";
import type { FlashcardPlan, FlashcardPlanRequest } from "../../api/flashcardApi";
import { planDraftError, planWeekdays, vietnamToday, WEEKDAYS, weekdaysInRange, type PlanDraft } from "../../utils/flashcards";

const props = defineProps<{ formId: string; plan: FlashcardPlan | null; selectedCount: number }>();
const emit = defineEmits<{ submit: [request: FlashcardPlanRequest] }>();
const replaceCards = defineModel<boolean>("replaceCards", { default: false });

const allDays = () => WEEKDAYS.map((day) => day.value);
const today = vietnamToday();
const plan = props.plan;
const draft = reactive<PlanDraft>({
  name: plan?.name ?? "",
  singleDay: plan ? plan.startDate === plan.endDate : true,
  startDate: plan?.startDate ?? today,
  endDate: plan?.endDate ?? today,
  // A single-day plan stores every weekday, which is also the right start if it becomes a range.
  weekdays: plan?.weekdays ?? allDays(),
  remind: plan ? !!plan.reminderTime : true,
  reminderTime: plan?.reminderTime ?? "20:00",
});
const availableDays = computed(() => weekdaysInRange(draft.startDate, draft.endDate));
const chosenDays = computed(() => planWeekdays(draft));
const touched = ref(false);
const error = computed(() => planDraftError(draft, today, !plan));

function toggleDay(day: number) {
  draft.weekdays = draft.weekdays.includes(day)
    ? draft.weekdays.filter((item) => item !== day)
    : [...draft.weekdays, day].sort((a, b) => a - b);
}

function submit() {
  touched.value = true;
  if (error.value) return;
  emit("submit", {
    name: draft.name.trim(),
    startDate: draft.startDate,
    endDate: draft.singleDay ? draft.startDate : draft.endDate,
    weekdays: draft.singleDay ? allDays() : chosenDays.value,
    reminderTime: draft.remind ? draft.reminderTime : null,
    active: true,
  });
}
</script>

<style scoped>
.plan-form { display: grid; gap: 1rem; }
.field { display: grid; gap: .45rem; margin: 0; padding: 0; border: 0; }
.field > span, legend { padding: 0; font-size: var(--admin-text-label, .85rem); font-weight: 700; }
legend { margin-bottom: .45rem; }
input[type="text"], input[type="date"], input[type="time"] { width: 100%; padding: .55rem .7rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); background: var(--surface-ffffff); color: var(--text-color); font-family: var(--font-family); color-scheme: light dark; }
input[type="time"] { width: auto; }
input[type="time"]:disabled { opacity: .55; }
.segment { display: inline-flex; width: fit-content; margin-bottom: .55rem; padding: .2rem; border: 1px solid var(--border-color); border-radius: 999px; background: var(--surface-ffffff); }
.segment button { padding: .3rem .85rem; border: 0; border-radius: 999px; background: transparent; color: var(--text-muted); font-weight: 700; cursor: pointer; }
.segment button[aria-checked="true"] { background: var(--primary-light); color: var(--primary-ink); }
.date-row { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: .6rem; }
.date-row label { display: grid; gap: .25rem; }
.date-row small, .hint { color: var(--text-muted); font-size: var(--admin-text-caption, .75rem); }
.weekday-row { display: flex; flex-wrap: wrap; align-items: center; gap: .35rem; }
.weekday { min-width: 2.5rem; padding: .35rem .5rem; border: 1px solid var(--border-color); border-radius: 8px; background: var(--surface-ffffff); color: var(--text-muted); font-weight: 700; cursor: pointer; }
.weekday[aria-pressed="true"] { border-color: var(--primary-color); background: var(--primary-light); color: var(--primary-ink); }
.weekday:disabled { opacity: .4; cursor: not-allowed; }
.link-button { margin-left: .3rem; padding: .3rem .5rem; border: 0; background: transparent; color: var(--primary-ink); font-weight: 700; cursor: pointer; }
.check { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; font-size: .88rem; }
.check input[type="checkbox"] { width: 16px; height: 16px; accent-color: var(--primary-color); }
.cards-note { margin: 0; color: var(--text-muted); font-size: .88rem; }
.field-error { margin: 0; color: var(--danger-ink); font-size: var(--admin-text-label, .85rem); }
</style>
