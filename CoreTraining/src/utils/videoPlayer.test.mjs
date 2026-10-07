import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

// Execute the actual compiled component setup with small media/API adapters.
// This exercises its lifecycle handlers without installing a browser framework.
const componentSource = await readFile(new URL('../components/VideoPlayer.vue', import.meta.url), 'utf8');
const script = compileScript(parse(componentSource).descriptor, { id: 'video-test' }).content.replaceAll('import.meta.env', '({})');
const transpile = source => ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
const accessExports = {};
new Function('exports', transpile(await readFile(new URL('./videoAccess.ts', import.meta.url), 'utf8')))(accessExports);

function mount(fetchAccess, overrides = {}) {
  const events = [];
  let changed;
  let unmount;
  const vue = {
    defineComponent: value => value,
    ref: value => ({ value }),
    computed: getter => ({ get value() { return getter(); } }),
    watch: (_getter, callback) => { changed = callback; callback(); },
    onBeforeUnmount: callback => { unmount = callback; },
  };
  const exports = {};
  new Function('require', 'exports', transpile(script))(name => {
    if (name === 'vue') return vue;
    if (name.includes('videoAccess')) return accessExports;
    if (name.includes('axiosClient')) return { axiosClient: { get: fetchAccess } };
    throw new Error(`Unexpected dependency: ${name}`);
  }, exports);
  const props = { lessonId: 'lesson-1', videoUrl: '/uploads/videos/file.mp4', title: '', initialPositionSeconds: 10, initialWatchedSeconds: 20, ...overrides };
  const player = exports.default.setup(props, { expose: () => {}, emit: (...event) => events.push(event) });
  const video = {
    currentTime: 0, duration: 100, volume: 1, muted: false, paused: true, ended: false,
    seekable: { length: 0 }, playCount: 0,
    play() { this.paused = false; this.playCount++; return Promise.resolve(); },
    pause() { this.paused = true; },
  };
  player.videoElement.value = video;
  return { player, props, events, video, changed: () => changed(), unmount: () => unmount() };
}

const flush = () => new Promise(resolve => setImmediate(resolve));
const grant = token => ({ data: { streamUrl: `/api/videos/stream?token=${token}`, expiresAt: Date.now() + 3600_000 } });

test('actual player renewal preserves playing/paused position and watched progress', async () => {
  let requests = 0;
  const { player, video, events, unmount } = mount(async () => grant(++requests));
  await flush();
  player.onLoadedMetadata();
  assert.equal(video.currentTime, 10);
  assert.equal(player.watchedSeconds.value, 20);
  video.paused = false;
  player.isPlaying.value = true;
  video.currentTime = 11;
  player.onTimeUpdate();
  assert.equal(player.watchedSeconds.value, 21);

  await player.accessSession.load();
  assert.deepEqual(player.pendingRestore, { position: 11, playing: true });
  // Changing src resets browser state before loadedmetadata arrives.
  video.currentTime = 0;
  video.paused = true;
  player.isPlaying.value = false;
  const emittedBeforeMetadata = events.length;
  player.onTimeUpdate();
  assert.equal(events.length, emittedBeforeMetadata);
  player.onLoadedMetadata();
  assert.equal(video.currentTime, 11);
  assert.equal(video.paused, false);
  assert.equal(video.playCount, 1);
  assert.equal(player.watchedSeconds.value, 21);
  assert.equal(player.lastPlaybackPosition.value, 11);

  video.currentTime = 33;
  video.paused = true;
  player.isPlaying.value = false;
  await player.accessSession.load();
  video.currentTime = 0;
  player.onLoadedMetadata();
  assert.equal(video.currentTime, 33);
  assert.equal(video.paused, true);
  assert.equal(video.playCount, 1);
  assert.equal(player.watchedSeconds.value, 21);
  unmount();
});

test('actual player ignores stale source after lesson switch and response after unmount', async () => {
  const pending = [];
  const mounted = mount((url, config) => new Promise(resolve => pending.push({ url, signal: config.signal, resolve })));
  mounted.props.lessonId = 'lesson-2';
  mounted.props.videoUrl = '/uploads/videos/second.mp4';
  mounted.props.initialPositionSeconds = 7;
  mounted.props.initialWatchedSeconds = 8;
  mounted.changed();
  assert.equal(pending[0].signal.aborted, true);
  assert.ok(pending[1].url.endsWith('lesson-2'));
  pending[1].resolve(grant('new'));
  await flush();
  pending[0].resolve(grant('old'));
  await flush();
  assert.ok(mounted.player.authorizedStreamUrl.value.endsWith('token=new'));
  mounted.player.onLoadedMetadata();
  assert.equal(mounted.video.currentTime, 7);
  assert.equal(mounted.player.watchedSeconds.value, 8);

  const renewal = mounted.player.accessSession.load();
  mounted.unmount();
  assert.equal(pending[2].signal.aborted, true);
  pending[2].resolve(grant('after-unmount'));
  await renewal;
  assert.ok(mounted.player.authorizedStreamUrl.value.endsWith('token=new'));
});

test('actual player never sets a source for an empty URL', () => {
  const mounted = mount(() => assert.fail('empty URL requested access'), { videoUrl: '' });
  assert.equal(mounted.player.resolvedVideoUrl.value, '');
  mounted.unmount();
});
