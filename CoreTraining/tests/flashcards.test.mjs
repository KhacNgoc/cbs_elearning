import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as flashcards from '../src/utils/flashcards.ts';

const NOW = Date.parse('2026-11-03T09:00:00Z');
const at = (offsetHours) => new Date(NOW + offsetHours * 3_600_000).toISOString();
const deck = [
  { id: 1, type: 'TERM', front: 'CIF', progress: null },
  { id: 2, type: 'TERM', front: 'CASA', progress: { dueAt: at(-1), mastered: false } },
  { id: 3, type: 'CLOZE', front: 'Duyệt trong ____.', progress: { dueAt: at(-48), mastered: true } },
  { id: 4, type: 'TERM', front: 'KYC', progress: { dueAt: at(24 * 30), mastered: true } },
  { id: 5, type: 'TERM', front: 'SWIFT', progress: { dueAt: at(5), mastered: false } },
  { id: 6, type: 'TERM', front: 'POS', progress: null },
];

test('due sessions review the most overdue cards first, then new cards in deck order', () => {
  assert.deepEqual(flashcards.studyQueue(deck, 'due', NOW).map((card) => card.id), [3, 2, 1, 6]);
  assert.deepEqual(flashcards.studyQueue(deck, 'all', NOW).map((card) => card.id), [1, 2, 3, 4, 5, 6]);
});

test('shuffling keeps every card, moves the first card away and leaves the input alone', () => {
  const ids = deck.map((card) => card.id);
  // random() = 0.999 leaves Fisher-Yates a no-op, the case that would otherwise keep the first card.
  for (const random of [Math.random, () => 0, () => 0.999]) {
    const shuffled = flashcards.shuffleCards(deck, random);
    assert.deepEqual(shuffled.map((card) => card.id).sort((a, b) => a - b), ids);
    assert.notEqual(shuffled[0].id, ids[0]);
  }
  assert.deepEqual(deck.map((card) => card.id), ids);
  assert.deepEqual(flashcards.shuffleCards([deck[0]]), [deck[0]]);
  assert.deepEqual(flashcards.shuffleCards([]), []);
});

test('deck stats separate new, due, learning and mastered cards', () => {
  assert.deepEqual(flashcards.deckStats(deck, NOW),
    { total: 6, newCount: 2, dueCount: 2, learningCount: 1, masteredCount: 1 });
});

test('cloze fronts split around the blank and other cards stay whole', () => {
  assert.deepEqual(flashcards.frontParts({ type: 'CLOZE', front: 'Hạn mức ___ cho mỗi giao dịch' }), [
    { text: 'Hạn mức ', blank: false },
    { text: '___', blank: true },
    { text: ' cho mỗi giao dịch', blank: false },
  ]);
  assert.deepEqual(flashcards.frontParts({ type: 'TERM', front: 'A ___ B' }), [{ text: 'A ___ B', blank: false }]);
});

test('draft validation mirrors the server cloze rules', () => {
  assert.equal(flashcards.flashcardDraftError({ type: 'TERM', front: 'CIF', back: 'Mã khách hàng' }), null);
  assert.match(flashcards.flashcardDraftError({ type: 'TERM', front: ' ', back: 'x' }), /đủ mặt trước/);
  assert.match(flashcards.flashcardDraftError({ type: 'CLOZE', front: 'Không có chỗ trống', back: 'x' }), /chỗ trống/);
  assert.match(flashcards.flashcardDraftError({ type: 'CLOZE', front: 'Duyệt trong 2 ngày, tức ____', back: '2 ngày' }), /lộ/);
  assert.equal(flashcards.flashcardDraftError({ type: 'CLOZE', front: 'Bước ____ là kiểm tra hồ sơ', back: '1' }), null);
});

test('source labels use pages for PDF and headings for Word documents', () => {
  assert.equal(flashcards.sourceLabel({ documentName: 'Quy trinh.pdf', page: 4, section: 'Mục 2' }), 'Quy trinh.pdf · trang 4');
  assert.equal(flashcards.sourceLabel({ documentName: 'Quy trinh.docx', page: 1, section: 'Mở CIF' }), 'Quy trinh.docx · Mở CIF');
  assert.equal(flashcards.sourceLabel(null), '');
});

test('saving a personal card replaces it in place or appends a new one', () => {
  const cards = [{ id: 1, front: 'CIF' }, { id: 2, front: 'GL' }];
  assert.deepEqual(flashcards.upsertCard(cards, { id: 2, front: 'Sổ cái' }).map((card) => card.front), ['CIF', 'Sổ cái']);
  assert.deepEqual(flashcards.upsertCard(cards, { id: 3, front: 'KYC' }).map((card) => card.id), [1, 2, 3]);
  assert.equal(cards.length, 2);
});
