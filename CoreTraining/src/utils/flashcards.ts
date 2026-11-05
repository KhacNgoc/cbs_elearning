// Pure helpers for learner flashcard sessions; kept framework-free for node tests.

/** Random order that never keeps the first card, so asking for a shuffle always shows a different card. */
export function shuffleCards<T>(cards: readonly T[], random: () => number = Math.random): T[] {
  const shuffled = [...cards];
  for (let index = shuffled.length - 1; index > 0; index--) {
    const other = Math.floor(random() * (index + 1));
    [shuffled[index], shuffled[other]] = [shuffled[other], shuffled[index]];
  }
  if (shuffled.length > 1 && shuffled[0] === cards[0]) {
    const other = 1 + Math.floor(random() * (shuffled.length - 1));
    [shuffled[0], shuffled[other]] = [shuffled[other], shuffled[0]];
  }
  return shuffled;
}

/** Per lesson: every card, including ones not loaded yet, or the chosen card ids. Unlisted lessons have none. */
export type CardSelection = Record<string, "all" | number[]>;

export function selectedInLesson(selection: CardSelection, lessonId: string, total: number): number {
  const chosen = selection[lessonId];
  return chosen === "all" ? total : (chosen?.length ?? 0);
}

export function isCardSelected(selection: CardSelection, lessonId: string, cardId: number): boolean {
  const chosen = selection[lessonId];
  return chosen === "all" || !!chosen?.includes(cardId);
}

/** Selects every given lesson that has cards, or clears them all when they are already fully selected. */
export function toggleLessons(
  selection: CardSelection,
  lessons: readonly { id: string; total: number }[],
): CardSelection {
  const withCards = lessons.filter((lesson) => lesson.total > 0);
  const allChosen = withCards.every((lesson) => selectedInLesson(selection, lesson.id, lesson.total) === lesson.total);
  const next = { ...selection };
  for (const lesson of withCards) {
    if (allChosen) delete next[lesson.id];
    else next[lesson.id] = "all";
  }
  return next;
}

export function toggleCard(
  selection: CardSelection,
  lessonId: string,
  cardId: number,
  lessonCardIds: readonly number[],
): CardSelection {
  const chosen = selection[lessonId];
  const current = chosen === "all" ? [...lessonCardIds] : (chosen ?? []);
  const updated = current.includes(cardId) ? current.filter((id) => id !== cardId) : [...current, cardId];
  const next = { ...selection };
  if (!updated.length) delete next[lessonId];
  else next[lessonId] = updated.length === lessonCardIds.length ? "all" : updated;
  return next;
}

/** Forgets a deleted card, and the lesson once nothing chosen remains in it. */
export function dropCard(selection: CardSelection, lessonId: string, cardId: number, remaining: number): CardSelection {
  const chosen = selection[lessonId];
  if (!chosen) return selection;
  const next = { ...selection };
  const updated = chosen === "all" ? chosen : chosen.filter((id) => id !== cardId);
  if (!remaining || !updated.length) delete next[lessonId];
  else next[lessonId] = updated;
  return next;
}

/** Keeps the deck order of the chosen cards. */
export function selectedCards<T extends { id: number }>(cards: readonly T[], chosen: "all" | number[] | undefined): T[] {
  if (chosen === "all") return [...cards];
  return cards.filter((card) => chosen?.includes(card.id));
}

/** Replaces a saved card in place, or appends it when it is new (the deck lists own cards last). */
export function upsertCard<T extends { id: number }>(cards: readonly T[], saved: T): T[] {
  return cards.some((card) => card.id === saved.id)
    ? cards.map((card) => (card.id === saved.id ? saved : card))
    : [...cards, saved];
}

/** Splits a cloze front into text and blank parts; other cards are one text part. */
export function frontParts(card: { type: "TERM" | "CLOZE"; front: string } | null): { text: string; blank: boolean }[] {
  const text = card?.front ?? "";
  if (card?.type !== "CLOZE") return [{ text, blank: false }];
  return text
    .split(/(_{3,})/)
    .filter(Boolean)
    .map((part) => ({ text: part, blank: /^_{3,}$/.test(part) }));
}

/** PDF chunks carry real page numbers; Word chunks are located by their heading. */
export function sourceLabel(
  source: { documentName: string; page?: number | null; section?: string | null } | null,
): string {
  if (!source) return "";
  const isPdf = source.documentName.toLowerCase().endsWith(".pdf");
  const location = isPdf && source.page ? `trang ${source.page}` : source.section || "";
  return location ? `${source.documentName} · ${location}` : source.documentName;
}

/** Mirrors the server rule so the editor can warn before saving. */
export function flashcardDraftError(card: { type: "TERM" | "CLOZE"; front: string; back: string }): string | null {
  const front = card.front.trim();
  const back = card.back.trim();
  if (!front || !back) return "Nhập đủ mặt trước và mặt sau của thẻ.";
  if (front.length > 1000 || back.length > 4000) return "Nội dung thẻ quá dài.";
  if (card.type === "CLOZE") {
    if (!/_{3,}/.test(front)) return "Thẻ điền khuyết cần có chỗ trống ____ ở mặt trước.";
    if (back.length >= 4 && front.toLowerCase().includes(back.toLowerCase()))
      return "Đáp án của thẻ điền khuyết đang lộ ở mặt trước.";
  }
  return null;
}

export interface PlanDraft {
  name: string;
  singleDay: boolean;
  startDate: string;
  endDate: string;
  weekdays: number[];
  remind: boolean;
  reminderTime: string;
}

interface PlanLike {
  startDate: string;
  endDate: string;
  weekdays: number[];
  reminderTime: string | null;
  active: boolean;
}

const WEEKDAY_LABELS: Record<number, string> = { 1: "T2", 2: "T3", 3: "T4", 4: "T5", 5: "T6", 6: "T7", 7: "CN" };
export const WEEKDAYS = Object.entries(WEEKDAY_LABELS).map(([value, label]) => ({ value: Number(value), label }));

/** Plans run on Vietnam dates, whichever zone the browser is in. */
export function vietnamToday(now: Date = new Date()): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Ho_Chi_Minh" }).format(now);
}

/** Mirrors the server rules so the form can warn before saving. */
export function planDraftError(draft: PlanDraft, today: string, isNew: boolean): string | null {
  if (!draft.name.trim()) return "Nhập tên lịch học.";
  if (!draft.startDate) return "Chọn ngày học.";
  if (!draft.singleDay) {
    if (!draft.endDate) return "Chọn ngày kết thúc.";
    if (draft.endDate < draft.startDate) return "Ngày kết thúc phải sau ngày bắt đầu.";
    if (!draft.weekdays.length) return "Chọn ít nhất một ngày trong tuần.";
  }
  if (isNew && (draft.singleDay ? draft.startDate : draft.endDate) < today) return "Ngày học đã qua.";
  if (draft.remind && !draft.reminderTime) return "Chọn giờ nhắc.";
  return null;
}

export function selectionToItems(selection: CardSelection): { lessonId: string; cardIds: number[] | null }[] {
  return Object.entries(selection).map(([lessonId, chosen]) => ({
    lessonId,
    cardIds: chosen === "all" ? null : chosen,
  }));
}

/** Lessons of courses not loaded yet look like no cards, so such a plan is described by its lessons instead. */
export function planSizeLabel(
  items: readonly { lessonId: string; cardIds: number[] | null }[],
  lessons: readonly { id: string; total: number }[],
  allLoaded: boolean,
): string {
  const known = new Set(lessons.map((lesson) => lesson.id));
  if (!allLoaded && items.some((item) => !known.has(item.lessonId))) return `${items.length} bài`;
  return `${planCardCount(items, lessons)} thẻ`;
}

/** Lessons the learner cannot open any more count as empty, as they do when studying. */
export function planCardCount(
  items: readonly { lessonId: string; cardIds: number[] | null }[],
  lessons: readonly { id: string; total: number }[],
): number {
  const totals = new Map(lessons.map((lesson) => [lesson.id, lesson.total]));
  return items.reduce((sum, item) => {
    const total = totals.get(item.lessonId) ?? 0;
    return sum + (item.cardIds ? Math.min(item.cardIds.length, total) : total);
  }, 0);
}

const displayDate = (iso: string) => iso.split("-").reverse().join("/");

export function planSchedule(plan: PlanLike): string {
  const single = plan.startDate === plan.endDate;
  const parts = [single ? displayDate(plan.startDate) : `${displayDate(plan.startDate)} – ${displayDate(plan.endDate)}`];
  if (!single) {
    parts.push(plan.weekdays.length === 7 ? "Hằng ngày" : plan.weekdays.map((day) => WEEKDAY_LABELS[day]).join(", "));
  }
  parts.push(plan.reminderTime ? `Nhắc lúc ${plan.reminderTime}` : "Không nhắc");
  return parts.join(" · ");
}

export function planStatus(plan: PlanLike, today: string): "running" | "paused" | "ended" {
  if (plan.endDate < today) return "ended";
  return plan.active ? "running" : "paused";
}
