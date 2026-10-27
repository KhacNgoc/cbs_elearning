import test from 'node:test';
import assert from 'node:assert/strict';
import { JSDOM } from 'jsdom';

async function setup(t) {
  const dom = new JSDOM('', { url: 'https://learning.example.test' });
  globalThis.window = dom.window;
  globalThis.document = dom.window.document;
  t.after(() => dom.window.close());
  return import(`../src/utils/youtubePlayer.ts?test=${encodeURIComponent(t.name)}`);
}

test('API loading is shared and preserves an existing ready callback', async t => {
  const { loadYouTubeApi } = await setup(t);
  let previousCalls = 0;
  window.onYouTubeIframeAPIReady = () => previousCalls++;
  const first = loadYouTubeApi(), second = loadYouTubeApi();
  assert.equal(first, second);
  assert.equal(document.querySelectorAll('script').length, 1);
  assert.equal(document.querySelector('script').src, 'https://www.youtube.com/iframe_api');
  const api = { Player: class {} };
  window.YT = api;
  window.onYouTubeIframeAPIReady();
  assert.equal(await first, api);
  assert.equal(previousCalls, 1);
  assert.equal(await loadYouTubeApi(), api);
  assert.equal(document.querySelectorAll('script').length, 1);
});

test('a failed API download is removed and can be retried', async t => {
  const { loadYouTubeApi } = await setup(t);
  const pending = loadYouTubeApi();
  const rejected = assert.rejects(pending, /Không thể tải YouTube Player API/);
  document.querySelector('script').dispatchEvent(new window.Event('error'));
  await rejected;
  assert.equal(document.querySelector('script'), null);
  const retry = loadYouTubeApi();
  window.YT = { Player: class {} };
  window.onYouTubeIframeAPIReady();
  assert.equal(await retry, window.YT);
});

test('a blocked API download times out and allows a new attempt', async t => {
  const { loadYouTubeApi } = await setup(t);
  let timeout;
  t.mock.method(globalThis, 'setTimeout', (callback, milliseconds) => {
    assert.equal(milliseconds, 15000); timeout = callback; return 1;
  });
  t.mock.method(globalThis, 'clearTimeout', () => {});
  const rejected = assert.rejects(loadYouTubeApi(), /Không thể tải YouTube Player API/);
  timeout(); await rejected;
  assert.equal(document.querySelector('script'), null);
  const retry = loadYouTubeApi();
  window.YT = { Player: class {} };
  window.onYouTubeIframeAPIReady();
  assert.equal(await retry, window.YT);
});
