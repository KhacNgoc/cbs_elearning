import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><html><body><div id="app"></div></body></html>', { pretendToBeVisual: true });
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'Event', 'MouseEvent', 'KeyboardEvent']) {
  globalThis[key] = dom.window[key];
}
globalThis.requestAnimationFrame = dom.window.requestAnimationFrame.bind(dom.window);
globalThis.cancelAnimationFrame = dom.window.cancelAnimationFrame.bind(dom.window);
globalThis.getComputedStyle = dom.window.getComputedStyle.bind(dom.window);
const vue = await import('vue');
const source = await readFile(new URL('../src/components/AdminModal.vue', import.meta.url), 'utf8');
const { descriptor } = parse(source);
let code = ts.transpileModule(compileScript(descriptor, { id: 'admin-modal-test', inlineTemplate: true }).content, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
  `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`,
).replace('export default', 'return');
const AdminModal = new Function('modules', code)({ vue, 'lucide-vue-next': { X: { render: () => null } } });

test('fixedHeight keeps the dialog class stable while body content changes', async () => {
  const { createApp, h, nextTick, reactive } = vue;
  const state = reactive({ rows: 0, fixed: false });
  const app = createApp({ render: () => h(AdminModal, {
    open: true, title: 'Thành viên', fixedHeight: state.fixed,
  }, { default: () => Array.from({ length: state.rows }, (_, i) => h('p', { key: i }, String(i))) }) });
  const host = document.createElement('div');
  document.body.append(host);
  app.mount(host);
  try {
    await nextTick();
    const dialog = document.querySelector('.admin-modal-dialog');
    assert.equal(dialog.classList.contains('is-fixed-height'), false);
    state.fixed = true;
    state.rows = 200;
    await nextTick();
    assert.equal(dialog.classList.contains('is-fixed-height'), true);
    const body = dialog.querySelector('.admin-modal-body');
    assert.equal(body.querySelectorAll('p').length, 200);
    assert.equal(dialog.querySelector('.admin-modal-header').contains(body), false);
    assert.equal(dialog.querySelector('.admin-modal-footer').contains(body), false);
    assert.match(source, /\.admin-modal-dialog\.is-fixed-height \{\s*height: min\(760px, var\(--admin-modal-max-height\)\);/);
    assert.match(source, /\.admin-modal-body \{[^}]*min-height: 0;[^}]*overflow-y: auto;/);
  } finally {
    app.unmount();
    host.remove();
    await new Promise(resolve => setTimeout(resolve, 60));
  }
});

test('text selection and background clicks keep entered data; only explicit close actions dismiss the form', async () => {
  const { createApp, h, nextTick, reactive } = vue;
  const state = reactive({ busy: false });
  let closes = 0;
  let submits = 0;
  const app = createApp({ render: () => h(AdminModal, {
    open: true, title: 'Sửa chương trình', busy: state.busy,
    onClose: () => closes++, onSubmit: () => submits++,
  }, { default: () => h('input', { value: 'SAMPLE', 'aria-label': 'Mã' }) }) });
  app.mount('#app');
  try {
    await nextTick();
    const input = document.querySelector('input');
    const overlay = document.querySelector('.admin-modal-overlay');
    input.value = 'SAMPLE-EDITED';
    input.setSelectionRange(0, 6);
    input.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true }));
    overlay.dispatchEvent(new MouseEvent('pointerup', { bubbles: true }));
    // A drag from the input to the backdrop can produce a click on their common ancestor.
    overlay.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await nextTick();
    assert.equal(closes, 0);
    assert.equal(input.value, 'SAMPLE-EDITED');
    overlay.click();
    assert.equal(closes, 0);
    assert.equal(submits, 0);

    document.querySelector('[aria-label="Đóng form"]').click();
    assert.equal(closes, 1);
    document.querySelector('.admin-modal-footer button[type="button"]').click();
    assert.equal(closes, 2);
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    assert.equal(closes, 3);

    state.busy = true;
    await nextTick();
    input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    overlay.click();
    assert.equal(closes, 3);
  } finally {
    app.unmount();
    await new Promise(resolve => setTimeout(resolve, 60));
    dom.window.close();
  }
});
