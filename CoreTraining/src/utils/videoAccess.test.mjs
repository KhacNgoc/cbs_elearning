import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

const source = await readFile(new URL('./videoAccess.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 } }).outputText;
const { VideoAccessSession, validateVideoUpload, MAX_VIDEO_UPLOAD_BYTES } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const access = () => ({ streamUrl: '/stream?token=test', expiresAt: Date.now() + 3600_000 });

test('late access response is ignored after changing lesson/unmounting', async () => {
  let resolve;
  let signal;
  const applied = [];
  const session = new VideoAccessSession(s => { signal = s; return new Promise(r => { resolve = r; }); }, a => applied.push(a), () => assert.fail('obsolete request reported an error'));
  const pending = session.load();
  session.stop();
  assert.equal(signal.aborted, true);
  resolve(access());
  await pending;
  assert.deepEqual(applied, []);
});

test('newer access request wins a race', async () => {
  const pending = [];
  const applied = [];
  const session = new VideoAccessSession(() => new Promise(resolve => pending.push(resolve)), a => applied.push(a.streamUrl), () => assert.fail());
  const old = session.load();
  const fresh = session.load();
  pending[1]({ ...access(), streamUrl: '/new' });
  await fresh;
  pending[0]({ ...access(), streamUrl: '/old' });
  await old;
  session.stop();
  assert.deepEqual(applied, ['/new']);
});

test('access renews before expiry and cancels the renewal timer on stop', async t => {
  let callback;
  let delay;
  let requests = 0;
  let cancelled = 0;
  t.mock.method(globalThis, 'setTimeout', (cb, ms) => { callback = cb; delay = ms; return 42; });
  t.mock.method(globalThis, 'clearTimeout', id => { if (id === 42) cancelled++; });
  const session = new VideoAccessSession(async () => { requests++; return access(); }, () => {}, () => assert.fail());
  await session.load();
  assert.ok(delay > 3500_000 && delay < 3600_000);
  callback();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(requests, 2);
  session.stop();
  assert.ok(cancelled >= 2);
});

test('media error recovery is bounded and explicit retry starts another request', async () => {
  let requests = 0;
  let errors = 0;
  const session = new VideoAccessSession(async () => { requests++; return access(); }, () => {}, () => { errors++; });
  await session.load();
  session.recover();
  await new Promise(resolve => setImmediate(resolve));
  session.recover();
  assert.equal(requests, 2);
  assert.equal(errors, 1);
  session.retry();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(requests, 3);
  session.stop();
});

test('expired grants and network failures show an error without renewal loops', async () => {
  let errors = 0;
  const session = new VideoAccessSession(async () => ({ streamUrl: '/stream', expiresAt: Date.now() - 1 }), () => assert.fail(), () => { errors++; });
  await session.load();
  assert.equal(errors, 1);
  session.stop();
  const failed = new VideoAccessSession(async () => { throw new Error('network unavailable'); }, () => assert.fail(), () => { errors++; });
  await failed.load();
  assert.equal(errors, 2);
  failed.stop();
});

test('upload validates size boundaries, empty files and supported containers', () => {
  assert.equal(validateVideoUpload({ name: 'video.MP4', size: MAX_VIDEO_UPLOAD_BYTES }), undefined);
  assert.equal(validateVideoUpload({ name: 'video.webm', size: 1 }), undefined);
  assert.ok(validateVideoUpload({ name: 'video.mp4', size: MAX_VIDEO_UPLOAD_BYTES + 1 }));
  assert.ok(validateVideoUpload({ name: 'video.mp4', size: 0 }));
  assert.ok(validateVideoUpload({ name: 'video.mov', size: 1 }));
  assert.ok(validateVideoUpload({ name: 'video.ogg', size: 1 }));
  assert.ok(validateVideoUpload({ name: 'video.mp4', size: 101 }, 100));
});
