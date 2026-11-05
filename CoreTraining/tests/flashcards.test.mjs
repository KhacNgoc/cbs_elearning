import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as flashcards from '../src/utils/flashcards.ts';

const deck = [1, 2, 3, 4, 5, 6].map((id) => ({ id, type: 'TERM', front: `Thẻ ${id}` }));

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

test('selecting a whole lesson counts cards that are not loaded yet', () => {
  const lessons = [{ id: 'A', total: 3 }, { id: 'B', total: 2 }, { id: 'EMPTY', total: 0 }];
  const selected = flashcards.toggleLessons({}, lessons);
  assert.deepEqual(selected, { A: 'all', B: 'all' });
  assert.equal(flashcards.selectedInLesson(selected, 'A', 3), 3);
  assert.equal(flashcards.selectedInLesson(selected, 'EMPTY', 0), 0);
  assert.deepEqual(flashcards.toggleLessons(selected, lessons), {});
  assert.deepEqual(flashcards.toggleLessons({ A: [1] }, lessons), { A: 'all', B: 'all' });
});

test('picking single cards moves between all, some and none', () => {
  const ids = [1, 2];
  let selected = flashcards.toggleCard({ A: 'all' }, 'A', 1, ids);
  assert.deepEqual(selected, { A: [2] });
  assert.equal(flashcards.isCardSelected(selected, 'A', 1), false);
  selected = flashcards.toggleCard(selected, 'A', 1, ids);
  assert.deepEqual(selected, { A: 'all' });
  assert.equal(flashcards.isCardSelected(selected, 'A', 1), true);
  selected = flashcards.toggleCard({ A: [1] }, 'A', 1, ids);
  assert.deepEqual(selected, {});
});

test('deleting a card forgets it and study keeps the deck order', () => {
  assert.deepEqual(flashcards.dropCard({ A: [1, 2] }, 'A', 1, 3), { A: [2] });
  assert.deepEqual(flashcards.dropCard({ A: [1] }, 'A', 1, 3), {});
  assert.deepEqual(flashcards.dropCard({ A: 'all' }, 'A', 1, 2), { A: 'all' });
  assert.deepEqual(flashcards.dropCard({ A: 'all' }, 'A', 1, 0), {});
  const cards = [{ id: 1 }, { id: 2 }, { id: 3 }];
  assert.deepEqual(flashcards.selectedCards(cards, [3, 1]).map((card) => card.id), [1, 3]);
  assert.equal(flashcards.selectedCards(cards, 'all').length, 3);
  assert.deepEqual(flashcards.selectedCards(cards, undefined), []);
});
