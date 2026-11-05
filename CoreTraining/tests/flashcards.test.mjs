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

const draft = { name: 'Ôn tối', singleDay: false, startDate: '2026-11-10', endDate: '2026-11-20', weekdays: [1], remind: true, reminderTime: '20:00' };

test('plan drafts follow the server rules', () => {
  const today = '2026-11-04';
  assert.equal(flashcards.planDraftError(draft, today, true), null);
  assert.match(flashcards.planDraftError({ ...draft, name: ' ' }, today, true), /tên/);
  assert.match(flashcards.planDraftError({ ...draft, endDate: '2026-11-01' }, today, true), /sau ngày/);
  assert.match(flashcards.planDraftError({ ...draft, weekdays: [] }, today, true), /trong tuần/);
  assert.match(flashcards.planDraftError({ ...draft, reminderTime: '' }, today, true), /giờ nhắc/);
  assert.equal(flashcards.planDraftError({ ...draft, remind: false, reminderTime: '' }, today, true), null);
  // A single day needs no end date or weekdays, but must not have passed for a new plan.
  const day = { ...draft, singleDay: true, endDate: '', weekdays: [] };
  assert.equal(flashcards.planDraftError(day, today, true), null);
  assert.match(flashcards.planDraftError({ ...day, startDate: '2026-11-01' }, today, true), /đã qua/);
  assert.equal(flashcards.planDraftError({ ...day, startDate: '2026-11-01' }, today, false), null);
});

test('a plan only counts the weekdays its date range contains', () => {
  // 5 and 6 November 2026 are a Thursday and a Friday.
  assert.deepEqual(flashcards.weekdaysInRange('2026-11-05', '2026-11-06'), [4, 5]);
  assert.deepEqual(flashcards.weekdaysInRange('2026-11-08', '2026-11-09'), [1, 7]);
  assert.deepEqual(flashcards.weekdaysInRange('2026-11-05', '2026-11-05'), [4]);
  assert.deepEqual(flashcards.weekdaysInRange('2026-11-05', '2026-11-11'), [1, 2, 3, 4, 5, 6, 7]);
  assert.deepEqual(flashcards.weekdaysInRange('2026-11-05', '2026-12-30'), [1, 2, 3, 4, 5, 6, 7]);
  // Until the range is valid nothing is ruled out, and the date error is the one to show.
  assert.equal(flashcards.weekdaysInRange('2026-11-05', '').length, 7);
  assert.equal(flashcards.weekdaysInRange('2026-11-06', '2026-11-05').length, 7);

  const short = { ...draft, startDate: '2026-11-05', endDate: '2026-11-06' };
  assert.deepEqual(flashcards.planWeekdays({ ...short, weekdays: [1, 2, 3, 4, 5, 6, 7] }), [4, 5]);
  assert.match(flashcards.planDraftError({ ...short, weekdays: [1] }, '2026-11-04', true), /trong tuần/);
  assert.equal(flashcards.planDraftError({ ...short, weekdays: [1, 5] }, '2026-11-04', true), null);
});

test('the Vietnam date decides today whatever the browser zone', () => {
  assert.equal(flashcards.vietnamToday(new Date('2026-11-04T17:30:00Z')), '2026-11-05');
  assert.equal(flashcards.vietnamToday(new Date('2026-11-04T16:30:00Z')), '2026-11-04');
});

test('plans describe their schedule and count only cards still open', () => {
  const plan = { startDate: '2026-11-10', endDate: '2026-11-20', weekdays: [1, 3, 5], reminderTime: '20:00', active: true };
  assert.equal(flashcards.planSchedule(plan), '10/11/2026 – 20/11/2026 · T2, T4, T6 · Nhắc lúc 20:00');
  assert.equal(flashcards.planSchedule({ ...plan, weekdays: [1, 2, 3, 4, 5, 6, 7], reminderTime: null }), '10/11/2026 – 20/11/2026 · Hằng ngày · Không nhắc');
  assert.equal(flashcards.planSchedule({ ...plan, endDate: plan.startDate }), '10/11/2026 · Nhắc lúc 20:00');
  assert.equal(flashcards.planStatus(plan, '2026-11-21'), 'ended');
  assert.equal(flashcards.planStatus({ ...plan, active: false }, '2026-11-11'), 'paused');
  assert.equal(flashcards.planStatus(plan, '2026-11-20'), 'running');

  assert.deepEqual(flashcards.selectionToItems({ A: 'all', B: [1, 2] }), [
    { lessonId: 'A', cardIds: null }, { lessonId: 'B', cardIds: [1, 2] },
  ]);
  const items = [{ lessonId: 'A', cardIds: null }, { lessonId: 'B', cardIds: [1, 2] }, { lessonId: 'LOCKED', cardIds: null }];
  assert.equal(flashcards.planCardCount(items, [{ id: 'A', total: 5 }, { id: 'B', total: 8 }]), 7);
  assert.equal(flashcards.planSizeLabel(items, [{ id: 'A', total: 5 }, { id: 'B', total: 8 }], true), '7 thẻ');
  assert.equal(flashcards.planSizeLabel(items, [{ id: 'A', total: 5 }, { id: 'B', total: 8 }], false), '3 bài');
});
