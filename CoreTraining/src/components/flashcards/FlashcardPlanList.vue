<template>
  <section class="card plan-list" aria-labelledby="flashcard-plans-title">
    <h2 id="flashcard-plans-title"><CalendarClock :size="18" /> Lịch học của tôi</h2>
    <p v-if="error" class="plan-note" role="alert">
      {{ error }}
      <button type="button" class="btn btn-secondary" @click="$emit('retry')">Thử lại</button>
    </p>
    <p v-else-if="!plans.length" class="plan-note">
      Chưa có lịch nào. Chọn thẻ bên dưới rồi bấm <strong>Đặt lịch học</strong> để được nhắc học theo giờ bạn muốn.
    </p>
    <ul v-else>
      <li v-for="plan in plans" :key="plan.id">
        <div class="plan-copy">
          <div class="plan-title">
            <strong>{{ plan.name }}</strong>
            <span class="plan-status" :class="status(plan)">{{ STATUS_LABELS[status(plan)] }}</span>
          </div>
          <small>{{ planSchedule(plan) }} · {{ planSizeLabel(plan.items, lessons, allLoaded) }}</small>
          <small v-if="plan.nextReminderAt">Lần nhắc tới: {{ formatReminder(plan.nextReminderAt) }}</small>
        </div>
        <div v-if="confirmingId === plan.id" class="plan-actions">
          <span>Xóa lịch này?</span>
          <button type="button" class="btn btn-secondary" @click="confirmingId = null">Không</button>
          <button type="button" class="btn btn-danger" :disabled="busyId === plan.id" @click="$emit('remove', plan)">Xóa</button>
        </div>
        <div v-else class="plan-actions">
          <button type="button" class="btn btn-primary" @click="$emit('study', plan)">Học ngay</button>
          <button
            v-if="status(plan) !== 'ended'"
            type="button"
            class="btn btn-secondary"
            :disabled="busyId === plan.id"
            @click="$emit('toggle', plan)"
          >
            {{ plan.active ? "Tạm dừng" : "Tiếp tục" }}
          </button>
          <button type="button" class="icon-button" :aria-label="`Sửa lịch ${plan.name}`" @click="$emit('edit', plan)">
            <Edit :size="15" />
          </button>
          <button type="button" class="icon-button danger" :aria-label="`Xóa lịch ${plan.name}`" @click="confirmingId = plan.id">
            <Trash2 :size="15" />
          </button>
        </div>
      </li>
    </ul>
  </section>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { CalendarClock, Edit, Trash2 } from "lucide-vue-next";
import type { FlashcardLibraryLesson, FlashcardPlan } from "../../api/flashcardApi";
import { planSchedule, planSizeLabel, planStatus, vietnamToday } from "../../utils/flashcards";

defineProps<{
  plans: FlashcardPlan[];
  lessons: FlashcardLibraryLesson[];
  allLoaded: boolean;
  error?: string;
  busyId?: number | null;
}>();
defineEmits<{
  study: [plan: FlashcardPlan];
  edit: [plan: FlashcardPlan];
  toggle: [plan: FlashcardPlan];
  remove: [plan: FlashcardPlan];
  retry: [];
}>();

const STATUS_LABELS = { running: "Đang chạy", paused: "Tạm dừng", ended: "Đã kết thúc" };
const confirmingId = ref<number | null>(null);
const status = (plan: FlashcardPlan) => planStatus(plan, vietnamToday());
const formatReminder = (iso: string) =>
  new Intl.DateTimeFormat("vi-VN", {
    timeZone: "Asia/Ho_Chi_Minh",
    hour: "2-digit",
    minute: "2-digit",
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(new Date(iso));
</script>

<style scoped>
.plan-list { display: grid; gap: .7rem; padding: 1rem; }
h2 { display: flex; align-items: center; gap: .4rem; margin: 0; font-size: 1rem; }
.plan-note { display: flex; flex-wrap: wrap; align-items: center; gap: .3rem; margin: 0; color: var(--text-muted); font-size: .88rem; }
ul { display: grid; gap: .5rem; margin: 0; padding: 0; list-style: none; }
li { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .6rem; padding: .7rem .85rem; border: 1px solid var(--border-color); border-radius: var(--radius-sm); }
.plan-copy { display: flex; flex: 1 1 280px; flex-direction: column; gap: .15rem; min-width: 0; }
.plan-copy small { color: var(--text-muted); font-size: .8rem; font-variant-numeric: tabular-nums; }
.plan-title { display: flex; flex-wrap: wrap; align-items: center; gap: .45rem; }
.plan-title strong { overflow-wrap: anywhere; }
.plan-status { padding: .1rem .5rem; border-radius: 9999px; background: var(--surface-f1f5f9); color: var(--text-muted); font-size: .72rem; font-weight: 700; }
.plan-status.running { background: var(--primary-light); color: var(--primary-ink); }
.plan-status.paused { background: var(--warning-surface); color: var(--warning-ink); }
.plan-actions { display: flex; flex-wrap: wrap; align-items: center; gap: .35rem; font-size: .85rem; }
.plan-actions .btn { padding: .3rem .7rem; font-size: .82rem; }
.btn-danger { border: 1px solid var(--danger-ink); background: transparent; color: var(--danger-ink); }
.icon-button { display: inline-flex; padding: .4rem; border: 1px solid var(--border-color); border-radius: 6px; background: var(--surface-ffffff); color: var(--text-muted); cursor: pointer; }
.icon-button:hover { color: var(--primary-ink); }
.icon-button.danger:hover { color: var(--danger-ink); }
</style>
