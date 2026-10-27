import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
import { JSDOM } from 'jsdom';

const dom = new JSDOM('<!doctype html><div id="app"></div><textarea id="comment"></textarea>', { pretendToBeVisual: true });
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const icons = await import('lucide-vue-next');
after(() => dom.window.close());
const { descriptor } = parse(await readFile(new URL('../src/components/lesson/DocumentPageNavigation.vue', import.meta.url), 'utf8'));
let source = ts.transpileModule(compileScript(descriptor, { id: 'page-navigation-test', inlineTemplate: true }).content, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
source = source.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, name) =>
  `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(name)}];`).replace('export default', 'return');
const Component = new Function('modules', source)({ vue, 'lucide-vue-next': icons });

function mount(props = {}) {
  document.activeElement?.blur();
  const state = vue.reactive({ page: 2, total: 3, disabled: false, ...props });
  const changes = [];
  const app = vue.createApp({ render: () => vue.h('div', { class: 'pdf-preview' }, [vue.h(Component, { ...state, onGo: page => { changes.push(page); state.page = page; } })]) });
  app.mount('#app');
  const viewer = document.querySelector('.pdf-preview');
  viewer.getClientRects = () => [{ width: 600, height: 400 }];
  return { state, changes, viewer, close: () => app.unmount() };
}
async function press(key, target = document.body, options = {}) {
  const event = new window.KeyboardEvent('keydown', { key, bubbles: true, cancelable: true, ...options });
  target.dispatchEvent(event); await vue.nextTick(); return event;
}

test('left and right arrows use the same page-change event as the buttons', async () => {
  const f = mount();
  try {
    assert.equal((await press('ArrowRight')).defaultPrevented, true);
    assert.equal(f.state.page, 3);
    await press('ArrowLeft');
    assert.deepEqual(f.changes, [3, 2]);
    document.querySelector('[aria-label="Trang trước"]').click(); await vue.nextTick();
    assert.deepEqual(f.changes, [3, 2, 1]);
  } finally { f.close(); }
});

test('does not turn beyond document boundaries or while disabled', async () => {
  const f = mount({ page: 1 });
  try {
    await press('ArrowLeft'); assert.deepEqual(f.changes, []);
    f.state.page = 3; await vue.nextTick(); await press('ArrowRight'); assert.deepEqual(f.changes, []);
    f.state.page = 2; f.state.disabled = true; await vue.nextTick();
    await press('ArrowLeft'); await press('ArrowRight'); assert.deepEqual(f.changes, []);
  } finally { f.close(); }
});

test('preserves typing, selection shortcuts and already-handled keyboard events', async () => {
  const f = mount();
  try {
    const input = document.querySelector('#comment'); input.focus();
    assert.equal((await press('ArrowRight', input)).defaultPrevented, false);
    await press('ArrowRight', document.body); // Editable focus still wins.
    input.blur();
    for (const modifier of ['ctrlKey', 'altKey', 'metaKey', 'shiftKey']) await press('ArrowRight', document.body, { [modifier]: true });
    const editable = document.createElement('div'); editable.setAttribute('contenteditable', 'true'); f.viewer.append(editable);
    await press('ArrowRight', editable);
    const handled = new window.KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true });
    handled.preventDefault(); document.body.dispatchEvent(handled); await vue.nextTick();
    assert.deepEqual(f.changes, []);
  } finally { f.close(); }
});

test('outside dialogs and hidden document viewers do not consume arrow keys', async () => {
  const f = mount();
  const dialog = document.createElement('div'); dialog.setAttribute('role', 'dialog'); dialog.setAttribute('aria-modal', 'true'); document.body.append(dialog);
  try {
    await press('ArrowRight'); assert.deepEqual(f.changes, []);
    dialog.remove();
    f.viewer.getClientRects = () => [];
    await press('ArrowRight'); assert.deepEqual(f.changes, []);
  } finally { dialog.remove(); f.close(); }
});

test('listener is removed when navigation unmounts', async () => {
  const f = mount(); f.close();
  const event = await press('ArrowRight');
  assert.equal(event.defaultPrevented, false); assert.deepEqual(f.changes, []);
});
