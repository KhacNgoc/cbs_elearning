// Pure helpers for learner flashcard sessions; kept framework-free for node tests.

export interface StudyCard {
  id: number;
  type: "TERM" | "CLOZE";
  front: string;
  progress: { dueAt: string; mastered: boolean } | null;
}

export interface DeckStats {
  total: number;
  newCount: number;
  dueCount: number;
  learningCount: number;
  masteredCount: number;
}

const isDue = (card: StudyCard, now: number) =>
  !!card.progress && new Date(card.progress.dueAt).getTime() <= now;

/**
 * "due" studies overdue cards first (oldest first), then cards never seen, in deck
 * order. "all" keeps the deck order for browsing every card.
 */
export function studyQueue<T extends StudyCard>(cards: readonly T[], mode: "due" | "all", now = Date.now()): T[] {
  if (mode === "all") return [...cards];
  const due = cards
    .filter((card) => isDue(card, now))
    .sort((a, b) => new Date(a.progress!.dueAt).getTime() - new Date(b.progress!.dueAt).getTime());
  return [...due, ...cards.filter((card) => !card.progress)];
}

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

export function deckStats(cards: readonly StudyCard[], now = Date.now()): DeckStats {
  const stats: DeckStats = { total: cards.length, newCount: 0, dueCount: 0, learningCount: 0, masteredCount: 0 };
  for (const card of cards) {
    if (!card.progress) stats.newCount++;
    else if (isDue(card, now)) stats.dueCount++;
    else if (card.progress.mastered) stats.masteredCount++;
    else stats.learningCount++;
  }
  return stats;
}

/** Replaces a saved card in place, or appends it when it is new (the deck lists own cards last). */
export function upsertCard<T extends { id: number }>(cards: readonly T[], saved: T): T[] {
  return cards.some((card) => card.id === saved.id)
    ? cards.map((card) => (card.id === saved.id ? saved : card))
    : [...cards, saved];
}

/** Splits a cloze front into text and blank parts; other cards are one text part. */
export function frontParts(card: Pick<StudyCard, "type" | "front"> | null): { text: string; blank: boolean }[] {
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
