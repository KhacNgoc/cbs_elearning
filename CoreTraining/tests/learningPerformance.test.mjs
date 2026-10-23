import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createAsyncResourceCache } from '../src/utils/asyncResourceCache.ts';
import { createLessonProgressWriter } from '../src/utils/lessonProgressWriter.ts';
import { chapterLearningState, outlineContext } from '../src/utils/learningStructure.ts';
import { createDocumentProgressJournal } from '../src/utils/documentProgressJournal.ts';

test('document journal survives a crash, isolates accounts and retains changes after an older acknowledgement', () => {
  const data = new Map();
  const storage = { getItem: key => data.get(key), setItem: (key, value) => data.set(key, value) };
  const journal = createDocumentProgressJournal('alice', storage);
  const snapshot = { contentType: 'DOCUMENT', documentId: 1, documentPage: 4, scrollOffset: 0 };
  journal.record('lesson', snapshot);
  const recovered = createDocumentProgressJournal('alice', storage);
  assert.equal(recovered.pending('lesson')[0].request.documentPage, 4);
  assert.equal(createDocumentProgressJournal('bob', storage).pending('lesson').length, 0);
  recovered.record('lesson', { ...snapshot, documentPage: 5 });
  recovered.acknowledge('lesson', snapshot);
  assert.equal(recovered.pending('lesson').length, 1);
  recovered.acknowledge('lesson', { ...snapshot, documentPage: 5 });
  assert.equal(createDocumentProgressJournal('alice', storage).pending('lesson').length, 0);
});

test('queued updates to different documents do not overwrite each other', async () => {
  const blocked = deferred();
  const calls = [];
  const writer = createLessonProgressWriter(async (id, request) => {
    calls.push(request); if (calls.length === 1) await blocked.promise;
    return { lessonId: id, ...request };
  });
  const first = writer.write('lesson', { contentType: 'DOCUMENT', documentId: 1, documentPage: 1 });
  const a = writer.write('lesson', { contentType: 'DOCUMENT', documentId: 1, documentPage: 7 });
  const b = writer.write('lesson', { contentType: 'DOCUMENT', documentId: 2, documentPage: 3 });
  blocked.resolve(); await Promise.all([first, a, b]);
  assert.deepEqual(calls.map(r => [r.documentId, r.documentPage]), [[1, 1], [1, 7], [2, 3]]);
  await writer.write('lesson', { contentType: 'DOCUMENT', documentId: 2, documentPage: 3, scrollOffset: 100 });
  assert.equal(calls.length, 3);
});

const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
const progress = (lessonId, request) => ({ lessonId, ...request });

test('outline cache shares requests, expires and isolates account/progress contexts', async t => {
  let now = 100;
  t.mock.method(Date, 'now', () => now);
  const cache = createAsyncResourceCache(30, 2);
  const waiting = deferred();
  let count = 0;
  const load = () => { count++; return waiting.promise; };
  const a = cache.get('course/user-a/rev1', load);
  const b = cache.get('course/user-a/rev1', load);
  await flush();
  assert.equal(count, 1);
  waiting.resolve({ chapters: ['one'] });
  assert.equal(await a, await b);
  await cache.get('course/user-a/rev1', load);
  assert.equal(count, 1);
  now = 131;
  await cache.get('course/user-a/rev1', async () => { count++; return {}; });
  await cache.get('course/user-a/rev2', async () => { count++; return {}; });
  await cache.get('course/user-b/rev2', async () => { count++; return {}; });
  assert.equal(count, 4);
  // Bounded cache evicts the oldest entry.
  await cache.get('course/user-a/rev1', async () => { count++; return {}; });
  assert.equal(count, 5);
});

test('clearing cache prevents old requests repopulating it; failures stay retryable', async () => {
  const cache = createAsyncResourceCache();
  const old = deferred();
  const request = cache.get('course', () => old.promise);
  cache.clear();
  old.resolve('old session');
  await request;
  assert.equal(await cache.get('course', async () => 'new session'), 'new session');
  await assert.rejects(cache.get('failed', async () => { throw Error('network'); }));
  assert.equal(await cache.get('failed', async () => 'retry'), 'retry');
});

test('progress writes serialize, coalesce waiting snapshots and deduplicate acknowledgements', async () => {
  const first = deferred();
  const calls = [];
  const writer = createLessonProgressWriter(async (id, request) => {
    calls.push({ id, request });
    if (calls.length === 1) return first.promise;
    return progress(id, request);
  });
  const initial = writer.write('a', { contentType: 'VIDEO', lastPositionSeconds: 1 });
  const duplicate = writer.write('a', { contentType: 'VIDEO', lastPositionSeconds: 1 });
  assert.equal(initial, duplicate);
  const intermediate = writer.write('a', { contentType: 'VIDEO', lastPositionSeconds: 2 });
  const latest = writer.write('a', { contentType: 'VIDEO', lastPositionSeconds: 3 });
  assert.equal(calls.length, 1);
  assert.equal(writer.pending('a').lastPositionSeconds, 3);
  first.resolve(progress('a', { lastPositionSeconds: 1 }));
  const results = await Promise.all([initial, intermediate, latest]);
  assert.equal(results[1].lastPositionSeconds, 3);
  assert.equal(results[2].lastPositionSeconds, 3);
  assert.deepEqual(calls.map(c => c.request.lastPositionSeconds), [1, 3]);
  await writer.write('a', { contentType: 'VIDEO', lastPositionSeconds: 3 });
  assert.equal(calls.length, 2);
});

test('progress snapshots stay attached to their lesson and failed writes can retry', async () => {
  const waiting = deferred();
  const calls = [];
  const writer = createLessonProgressWriter(async (id, request) => {
    calls.push({ id, request });
    if (id === 'a') return waiting.promise;
    return calls.filter(c => c.id === 'b').length === 1 ? null : progress(id, request);
  });
  const snapshot = { contentType: 'DOCUMENT', documentId: 7, documentPage: 3 };
  const a = writer.write('a', snapshot);
  snapshot.documentPage = 99;
  const b = writer.write('b', { contentType: 'TEXT' });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].request.documentPage, 3);
  waiting.resolve(progress('a', calls[0].request));
  assert.equal((await a).lessonId, 'a');
  assert.equal(await b, null);
  assert.ok(await writer.write('b', { contentType: 'TEXT' }));
});

test('chapter state preserves sequential locks including completed lessons after a gap', () => {
  const chapters = [{ id: 'c', lessons: ['a', 'b', 'c', 'd'].map(id => ({ id })) }, { id: 'empty', lessons: [] }];
  const state = chapterLearningState(chapters, new Set(['a', 'c']));
  assert.deepEqual(state.get('c'), { completedCount: 2, firstIncomplete: 1 });
  assert.deepEqual(state.get('empty'), { completedCount: 0, firstIncomplete: 0 });
  assert.equal(chapterLearningState(chapters, new Set(['a', 'b', 'c', 'd'])).get('c').firstIncomplete, 4);
});

test('outline context changes when completion or permissions change', () => {
  const user = { username: 'a', permissions: [], systemAdmin: false, completedLessons: [], quizScores: {}, lessonQuizScores: {}, registeredCourses: [], mandatoryCourses: [] };
  const base = outlineContext(user);
  assert.notEqual(outlineContext({ ...user, completedLessons: ['a'] }), base);
  assert.notEqual(outlineContext({ ...user, permissions: ['courses:read'] }), base);
  assert.notEqual(outlineContext({ ...user, username: 'b' }), base);
});


test('revisiting unchanged content touches its access time after older writes to other lessons', async () => {
  const waiting = deferred();
  const calls = [];
  const writer = createLessonProgressWriter(async (id, request) => {
    calls.push(id);
    if (calls.length === 1) await waiting.promise;
    return progress(id, request);
  });
  const a = writer.write('a', { contentType: 'TEXT' }, true);
  const b = writer.write('b', { contentType: 'TEXT' }, true);
  const revisit = writer.write('a', { contentType: 'TEXT' }, true);
  assert.deepEqual(calls, ['a']);
  waiting.resolve();
  await Promise.all([a, b, revisit]);
  assert.deepEqual(calls, ['a', 'b', 'a']);
  await writer.write('a', { contentType: 'TEXT' });
  assert.equal(calls.length, 3);
});


test('seeking back to an acknowledged position replaces a different pending position', async () => {
  const waiting = deferred();
  const calls = [];
  const writer = createLessonProgressWriter(async (id, request) => {
    calls.push({ id, position: request.lastPositionSeconds });
    if (id === 'b') await waiting.promise;
    return progress(id, request);
  });
  await writer.write('a', { lastPositionSeconds: 1 });
  const b = writer.write('b', { lastPositionSeconds: 0 });
  const forward = writer.write('a', { lastPositionSeconds: 2 });
  const back = writer.write('a', { lastPositionSeconds: 1 });
  assert.equal(writer.pending('a').lastPositionSeconds, 1);
  waiting.resolve();
  await Promise.all([b, forward, back]);
  assert.ok(!calls.some(call => call.id === 'a' && call.position === 2));
});
