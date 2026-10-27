import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<div id="app"></div>', { url: 'https://learning.example.test' });
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const { descriptor } = parse(await readFile(new URL('../src/components/VideoPlayer.vue', import.meta.url), 'utf8'));
let code = ts.transpileModule(compileScript(descriptor, { id: 'video-test', inlineTemplate: true }).content, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText.replaceAll('import.meta.env', '({})');
code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
  `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`,
).replace('export default', 'return');
const VideoPlayer = new Function('modules', code)({
  vue, '../utils/videoAccess': {}, '../api/axiosClient': {},
  '../utils/youtubePlayer': await import('../src/utils/youtubePlayer.ts'),
});
const flush = async () => { await vue.nextTick(); await Promise.resolve(); await vue.nextTick(); };

async function mount(t, overrides = {}) {
  const players = [], ticks = new Map(); let nextTimer = 0;
  // The external player is the boundary; Vue and the progress logic stay real.
  window.YT = { Player: class {
    position = 0; duration = 100; state = -1; destroyed = false; commands = [];
    constructor(element, options) { this.options = options; this.element = element; players.push(this); }
    getCurrentTime() { return this.position; }
    getDuration() { return this.duration; }
    getPlayerState() { return this.state; }
    cueVideoById(options) {
      this.commands.push(['cue', options]);
      queueMicrotask(() => { if (!this.destroyed) { this.position = options.startSeconds; this.change(5); } });
    }
    seekTo(seconds) { this.commands.push(['seek', seconds]); this.position = seconds; if (this.state !== 2) this.change(1); }
    pauseVideo() { this.commands.push(['pause']); this.change(2); }
    destroy() { this.destroyed = true; this.element.remove(); }
    ready() { this.options.events.onReady({ target: this }); }
    change(state) { this.state = state; this.options.events.onStateChange({ target: this, data: state }); }
  } };
  t.mock.method(globalThis, 'setInterval', callback => { const id = ++nextTimer; ticks.set(id, callback); return id; });
  t.mock.method(globalThis, 'clearInterval', id => ticks.delete(id));
  const progress = [], ended = []; let pauses = 0; const component = vue.ref();
  const props = vue.reactive({ lessonId: 'L1', videoUrl: 'https://youtu.be/abcdefghijk', ...overrides });
  const app = vue.createApp({ render: () => vue.h(VideoPlayer, {
    ...props, ref: component, onProgressUpdate: value => progress.push(value),
    onEnded: value => ended.push(value), onPause: () => pauses++,
  }) });
  app.mount('#app');
  t.after(() => app.unmount());
  await flush();
  assert.equal(players.length, 1, 'YouTube iframe must be connected to the Player API');
  const player = players[0]; player.ready(); await flush();
  return { player, players, props, progress, ended, component, app, ticks, pauses: () => pauses,
    tick: position => { player.position = position; for (const callback of ticks.values()) callback(); } };
}

test('YouTube playback reports watched time, duration and pause through lesson progress events', async t => {
  const s = await mount(t, { initialPositionSeconds: 20, initialWatchedSeconds: 15 });
  const url = new URL(document.querySelector('iframe').src);
  assert.equal(url.searchParams.get('enablejsapi'), '1');
  assert.equal(url.searchParams.get('origin'), 'https://learning.example.test');
  assert.equal(s.player.position, 20);
  assert.equal(s.player.state, 5, 'restoring a bookmark must cue a preview without starting playback');
  assert.deepEqual(s.player.commands, [['cue', { videoId: 'abcdefghijk', startSeconds: 20 }]]);
  s.player.change(1); s.tick(21); s.tick(22);
  assert.deepEqual(s.progress.at(-1), { lastPositionSeconds: 22, watchedSeconds: 17, durationSeconds: 100 });
  s.player.position = 22.5; s.player.change(2);
  assert.equal(s.progress.at(-1).watchedSeconds, 17.5);
  assert.equal(s.pauses(), 1);
  s.tick(40);
  assert.equal(s.progress.at(-1).watchedSeconds, 17.5);
});

test('asynchronous initialization preserves a saved bookmark until the user starts playback', async t => {
  const s = await mount(t, { initialPositionSeconds: 80, initialWatchedSeconds: 30 });
  assert.deepEqual(s.player.commands, [['cue', { videoId: 'abcdefghijk', startSeconds: 80 }]]);
  // Real YouTube can briefly report 0 while moving from a preview into playback.
  s.player.position = 0; s.player.change(-1); s.player.change(3);
  assert.equal(s.progress.length, 0, 'initialization must not overwrite the saved bookmark with position zero');
  assert.equal(s.ticks.size, 0);
  s.player.position = 80; s.player.change(1); s.tick(80.5);
  assert.deepEqual(s.progress.at(-1), { lastPositionSeconds: 80.5, watchedSeconds: 30.5, durationSeconds: 100 });
});

test('reopening at the end of a video prepares a preview without reporting another completion', async t => {
  const s = await mount(t, { initialPositionSeconds: 100, initialWatchedSeconds: 95 });
  assert.deepEqual(s.player.commands, [['cue', { videoId: 'abcdefghijk', startSeconds: 100 }]]);
  assert.equal(s.player.state, 5);
  assert.equal(s.ended.length, 0);
  assert.equal(s.progress.length, 0);
});

test('seeking and buffering do not count as watched time; ending flushes the final fraction', async t => {
  const s = await mount(t);
  s.player.change(1); s.tick(1); s.tick(70);
  assert.equal(s.progress.at(-1).watchedSeconds, 1);
  s.player.change(3); s.tick(75);
  s.player.change(1); s.tick(76);
  assert.equal(s.progress.at(-1).watchedSeconds, 2);
  s.component.value.seekTo(99);
  s.tick(99.5); s.player.position = 100; s.player.change(0);
  assert.deepEqual(s.ended.at(-1), { lastPositionSeconds: 100, watchedSeconds: 3, durationSeconds: 100 });
  assert.equal(s.ticks.size, 0);
});

test('changing lessons destroys the old player and ignores its late events', async t => {
  const s = await mount(t);
  s.player.change(1); s.tick(1);
  s.props.lessonId = 'L2'; s.props.videoUrl = 'https://www.youtube.com/watch?v=12345678901';
  await flush();
  assert.equal(s.player.destroyed, true);
  assert.equal(s.players.length, 2);
  const count = s.progress.length;
  s.player.position = 2; s.player.change(2);
  assert.equal(s.progress.length, count);
  const next = s.players[1]; next.ready(); next.change(1); next.position = 1;
  for (const callback of s.ticks.values()) callback();
  assert.equal(s.progress.at(-1).watchedSeconds, 1);
  s.component.value.resetProgress();
  assert.equal(next.position, 0);
  assert.equal(s.progress.at(-1).watchedSeconds, 0);
});

test('a YouTube error can be retried with a fresh iframe and the current bookmark', async t => {
  const s = await mount(t);
  s.player.change(1); s.tick(1);
  s.player.options.events.onError({ target: s.player, data: 5 });
  await flush();
  assert.match(document.querySelector('[role="status"]').textContent, /Không thể phát video/);
  document.querySelector('[role="status"] button').click();
  await flush();
  assert.equal(s.player.destroyed, true);
  const next = s.players[1];
  assert.ok(next.element.isConnected);
  next.ready(); await flush(); next.change(1); next.position = 2;
  for (const callback of s.ticks.values()) callback();
  assert.equal(s.progress.at(-1).watchedSeconds, 2);
  assert.equal(s.progress.at(-1).lastPositionSeconds, 2);
});

test('removing the YouTube component cancels polling and ignores late callbacks', async t => {
  const s = await mount(t);
  s.player.change(1); s.tick(1);
  s.app.unmount();
  assert.equal(s.ticks.size, 0);
  assert.equal(s.player.destroyed, true);
  const count = s.progress.length;
  s.player.change(0);
  assert.equal(s.progress.length, count);
  assert.equal(s.ended.length, 0);
});

test('direct external video retains native watched-time and resume behavior', async t => {
  const progress = [];
  const app = vue.createApp({ render: () => vue.h(VideoPlayer, {
    lessonId: 'native', videoUrl: 'https://media.example.test/lesson.mp4',
    initialPositionSeconds: 10, initialWatchedSeconds: 7,
    onProgressUpdate: value => progress.push(value),
  }) });
  app.mount('#app'); t.after(() => app.unmount()); await flush();
  const video = document.querySelector('video');
  Object.defineProperty(video, 'duration', { value: 100 });
  video.dispatchEvent(new window.Event('loadedmetadata'));
  assert.equal(video.currentTime, 10);
  video.dispatchEvent(new window.Event('play'));
  video.currentTime = 11; video.dispatchEvent(new window.Event('timeupdate'));
  assert.deepEqual(progress.at(-1), { lastPositionSeconds: 11, watchedSeconds: 8, durationSeconds: 100 });
});
