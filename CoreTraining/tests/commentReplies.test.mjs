import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
import { createPinia, setActivePinia, defineStore } from 'pinia';

async function load(path, modules) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const exports = {};
  new Function('require', 'exports', code)(name => {
    if (!(name in modules)) throw new Error(`Missing test dependency ${name}`);
    return modules[name];
  }, exports);
  return exports;
}
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve = yes; reject = no; }); return { resolve, reject, promise }; };
const reply = (id, createdAt = '2026-10-27T10:00:00') => ({ id, createdAt, date: '27/10/2026', user: 'u', avatar: 'U', content: `${id}` });
async function fixture(api) {
  setActivePinia(createPinia());
  let helper = {};
  try { helper = await load('../src/utils/commentReplies.ts', {}); } catch (e) { if (e.code !== 'ENOENT') throw e; }
  const { useCourseStore } = await load('../src/stores/courseStore.ts', {
    pinia: { defineStore }, '../api/courseApi': { courseApi: api }, '../utils/toast': { showToast() {} },
    '../api/trainingApi': {}, '../utils/asyncResourceCache': {}, '../utils/commentReplies': helper,
  });
  const store = useCourseStore();
  store.currentLessonId = 'lesson';
  store.currentLessonData = { id: 'lesson', comments: [{ ...reply(100), replies: [reply(1), reply(5)], repliesHasMore: true, replyCursor: 'cursor-5' }] };
  return store;
}

test('a pending page cannot restore a reply after successful deletion', async () => {
  const pending = deferred();
  const store = await fixture({ getReplies: async () => pending.promise, deleteComment: async () => {} });
  const loading = store.loadMoreReplies('lesson', 100);
  await store.deleteComment('lesson', 5);
  pending.resolve({ content: [reply(5), reply(7)], nextCursor: 'cursor-7', hasMore: true });
  await loading;
  const root = store.currentLessonData.comments[0];
  assert.equal(root.replies.some(r => r.id === 5), false);
  assert.equal(root.replyCursor, 'cursor-5');
  assert.equal(store.replyPageStates[100].loading, false);
});

test('loads once per root, merges tied timestamps and posted replies without moving the server cursor on post', async () => {
  const pending = deferred(); let calls = 0;
  const store = await fixture({ getReplies: async (...args) => { calls++; assert.deepEqual(args, ['lesson', 100, 'cursor-5']); return pending.promise; }, replyToComment: async () => reply(20, '2026-10-27T12:00:00') });
  assert.equal(typeof store.loadMoreReplies, 'function');
  const loading = store.loadMoreReplies('lesson', 100);
  await store.loadMoreReplies('lesson', 100);
  assert.equal(calls, 1);
  assert.equal(store.replyPageStates[100].loading, true);
  await store.replyToComment('lesson', 100, 100, 'posted');
  assert.equal(store.currentLessonData.comments[0].replyCursor, 'cursor-5');
  pending.resolve({ content: [reply(5), reply(9), reply(7)], nextCursor: 'cursor-9', hasMore: true });
  await loading;
  const root = store.currentLessonData.comments[0];
  assert.deepEqual(root.replies.map(r => r.id), [1, 5, 7, 9, 20]);
  assert.equal(root.replyCursor, 'cursor-9');
  assert.equal(store.replyPageStates[100].loading, false);
});

test('failure keeps cursor and replies for retry; deleting the cursor row still loads the next page', async () => {
  let attempts = 0;
  const store = await fixture({ deleteComment: async () => {}, getReplies: async (_lesson, _root, cursor) => {
    assert.equal(cursor, 'cursor-5'); if (++attempts === 1) throw new Error('offline');
    return { content: [reply(6)], nextCursor: null, hasMore: false };
  } });
  assert.equal(typeof store.loadMoreReplies, 'function');
  await store.loadMoreReplies('lesson', 100);
  assert.ok(store.replyPageStates[100].error);
  assert.deepEqual(store.currentLessonData.comments[0].replies.map(r => r.id), [1, 5]);
  await store.deleteComment('lesson', 5);
  await store.loadMoreReplies('lesson', 100);
  assert.deepEqual(store.currentLessonData.comments[0].replies.map(r => r.id), [1, 6]);
  assert.equal(store.currentLessonData.comments[0].repliesHasMore, false);
  assert.equal(store.replyPageStates[100].error, '');
});

test('ignores an in-flight page after switching lesson instances, including return to the same lesson ID', async () => {
  const pending = deferred();
  const store = await fixture({ getReplies: () => pending.promise });
  assert.equal(typeof store.loadMoreReplies, 'function');
  const loading = store.loadMoreReplies('lesson', 100);
  store.currentLessonData = { id: 'lesson', comments: [{ ...reply(100), replies: [], repliesHasMore: true, replyCursor: 'fresh' }] };
  pending.resolve({ content: [reply(6)], nextCursor: null, hasMore: false });
  await loading;
  assert.deepEqual(store.currentLessonData.comments[0].replies, []);
  assert.equal(store.currentLessonData.comments[0].replyCursor, 'fresh');
});

test('comment API forwards bounded page and reply cursor to the database endpoints', async () => {
  const calls = [];
  const response = { content: [reply(6)], nextCursor: 'next', hasMore: true };
  const { courseApi } = await load('../src/api/courseApi.ts', {
    './axiosClient': { axiosClient: { get: async (...args) => { calls.push(args); return { data: response }; } } },
  });
  assert.equal(await courseApi.getComments('lesson', 3, 10), response);
  assert.deepEqual(calls[0], ['/api/lessons/lesson/comments', { params: { page: 3, size: 10 } }]);
  assert.equal(await courseApi.getReplies('lesson', 100, 'cursor-5'), response);
  assert.deepEqual(calls[1], ['/api/lessons/lesson/comments/100/replies', { params: { after: 'cursor-5' } }]);
});
