import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const icons = await import('lucide-vue-next');
const flush = async () => { for (let i=0; i<20; i++) { await Promise.resolve(); await vue.nextTick(); } };
const deferred = () => { let resolve, reject; const promise = new Promise((yes, no) => { resolve=yes; reject=no; }); return { resolve, reject, promise }; };
after(() => dom.window.close());
const { descriptor } = parse(await readFile(new URL('../src/components/NotificationCenter.vue', import.meta.url), 'utf8'));
let code = ts.transpileModule(compileScript(descriptor, { id: 'notification-test', inlineTemplate: true }).content,
  { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
  `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
const item = id => ({ id, title: `Notice ${id}`, message: 'Message', type: 'COMMENT_REPLY', read: false, createdAt: '2026-10-23T10:00:00' });
const first = () => ({ content: [12,11,10,9,8,7].map(item), hasMore: true, nextBeforeId: 7, unreadCount: 12 });
async function fixture(getNotifications) {
  const calls = []; let receive; let stopped = false;
  const Component = new Function('modules', code)({
    vue: { ...vue, Transition: { props: ['name'], setup(_, { slots }) { return () => slots.default?.(); } } },
    'lucide-vue-next': icons,
    'vue-router': { useRouter: () => ({ push: async () => {} }) },
    '../stores/userStore': { useUserStore: () => ({ fetchProgress: async () => {} }) },
    '../services/notificationSocket': { NotificationSocket: class { constructor(callback) { receive=callback; } start() {} stop() { stopped=true; } } },
    '../api/notificationApi': { notificationApi: {
      getNotifications: before => { calls.push(before); return getNotifications(before, calls.length); },
      markRead: async () => {}, markAllRead: async () => {},
    } },
  });
  const app = vue.createApp(Component); app.mount('#app'); await flush();
  document.querySelector('.notification-trigger').click(); await flush();
  return { app, calls, receive: value => receive(value), stopped: () => stopped };
}

test('starts with six, loads older notifications once and keeps live notifications without duplicates', async () => {
  const pending = deferred();
  const f = await fixture((before, count) => count === 1 ? first() : pending.promise);
  try {
    assert.equal(document.querySelectorAll('.notification-item').length, 6);
    assert.equal(document.querySelector('.notification-badge').textContent, '12');
    f.receive(item(13)); await flush();
    document.querySelector('.notification-load-more').click();
    document.querySelector('.notification-load-more').click(); await flush();
    assert.deepEqual(f.calls, [undefined, 7]);
    assert.equal(document.querySelector('.notification-load-more').disabled, true);
    pending.resolve({ content: [7,6,5,4,3,2,1].map(item), hasMore: false, nextBeforeId: null, unreadCount: 13 }); await flush();
    assert.equal(document.querySelectorAll('.notification-item').length, 13);
    assert.equal(document.querySelector('.notification-load-more'), null);
    assert.equal(document.querySelector('.notification-badge').textContent, '13');
  } finally { f.app.unmount(); }
  assert.equal(f.stopped(), true);
});

test('failed load can retry the same cursor and mark-all covers a page already in flight', async () => {
  const pending = deferred();
  const f = await fixture((before, count) => count === 1 ? first() : count === 2 ? Promise.reject(new Error('offline')) : pending.promise);
  try {
    document.querySelector('.notification-load-more').click(); await flush();
    assert.match(document.querySelector('[role="alert"]').textContent, /Không thể tải/);
    document.querySelector('.notification-load-more').click(); await flush();
    document.querySelector('.notification-panel header button').click(); await flush();
    pending.resolve({ content: [6,5,4,3,2,1].map(item), hasMore: false, nextBeforeId: null, unreadCount: 12 }); await flush();
    assert.deepEqual(f.calls, [undefined, 7, 7]);
    assert.equal(document.querySelectorAll('.notification-item.unread').length, 0);
    assert.equal(document.querySelector('.notification-badge'), null);
  } finally { f.app.unmount(); }
});
