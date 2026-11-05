import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><html><body></body></html>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) {
  globalThis[key] = dom.window[key];
}
const vue = await import('vue');
const icons = await import('lucide-vue-next');
const calls = [];
const flashcardApi = {
  createMine: async (lessonId, card) => { calls.push(['create', lessonId, card]); return { id: 9, ...card, mine: true, version: 0 }; },
  updateMine: async (id, card) => { calls.push(['update', id, card]); return { id, ...card, mine: true, version: 1 }; },
  removeMine: async () => {},
};

async function loadComponent() {
  const url = new URL('../src/components/flashcards/PersonalFlashcards.vue', import.meta.url);
  const { descriptor } = parse(await readFile(url, 'utf8'));
  const code = ts.transpileModule(compileScript(descriptor, { id: 'personal-flashcards-test', inlineTemplate: true }).content, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`,
  ).replace('export default', 'return');
  return new Function('modules', code)({
    vue,
    'lucide-vue-next': icons,
    '../../api/flashcardApi': {
      flashcardApi, apiErrorMessage: () => '', CLOZE_BLANK: '____', TYPE_LABELS: { TERM: 'Thuật ngữ', CLOZE: 'Điền khuyết' },
    },
    '../../utils/flashcards': await import('../src/utils/flashcards.ts'),
    '../../utils/toast': { showToast: () => {} },
  });
}
const Personal = await loadComponent();
const card = { id: 1, type: 'TERM', front: 'CIF', back: 'Mã khách hàng', mine: true, version: 3 };
const settle = async () => { for (let i = 0; i < 8; i++) { await Promise.resolve(); await vue.nextTick(); } };

async function mount(cards = [card]) {
  const host = document.createElement('div');
  document.body.append(host);
  const app = vue.createApp({ render: () => vue.h(Personal, { lessonId: 'L1', cards }) });
  app.mount(host);
  await settle();
  const button = (label) => [...host.querySelectorAll('button')].find((item) =>
    item.textContent.includes(label) || item.getAttribute('aria-label')?.includes(label));
  const fields = () => [...host.querySelectorAll('textarea')];
  const fill = (field, value) => { field.value = value; field.dispatchEvent(new dom.window.Event('input')); };
  return { host, button, fields, fill, unmount: () => { app.unmount(); host.remove(); } };
}

test('opens on the card list with no form until the learner asks for one', async () => {
  const view = await mount();
  assert.equal(view.host.querySelector('form'), null);
  assert.match(view.host.querySelector('.personal-list').textContent, /CIF/);
  assert.ok(view.button('Thêm thẻ'));
  view.unmount();
});

test('the edit button opens a filled form and saving returns to the list', async () => {
  calls.length = 0;
  const view = await mount();
  view.button('Sửa thẻ CIF').click();
  await settle();
  assert.equal(view.host.querySelector('.personal-list'), null);
  assert.equal(view.fields()[0].value, 'CIF');
  view.fill(view.fields()[1], 'Đã sửa');
  view.button('Lưu thay đổi').click();
  view.host.querySelector('form').dispatchEvent(new dom.window.Event('submit', { cancelable: true }));
  await settle();
  assert.deepEqual(calls[0], ['update', 1, { type: 'TERM', front: 'CIF', back: 'Đã sửa', version: 3 }]);
  assert.equal(view.host.querySelector('form'), null);
  view.unmount();
});

test('adding starts from an empty form, and going back leaves no draft behind', async () => {
  const view = await mount();
  view.button('Sửa thẻ CIF').click();
  await settle();
  view.button('Quay lại').click();
  await settle();
  assert.equal(view.host.querySelector('form'), null);
  view.button('Thêm thẻ').click();
  await settle();
  assert.equal(view.fields()[0].value, '');
  assert.ok(view.button('Thêm thẻ'));
  view.unmount();
});

test('a lesson without cards still offers the add button', async () => {
  const view = await mount([]);
  assert.ok(view.host.querySelector('.list-empty'));
  assert.ok(view.button('Thêm thẻ'));
  view.unmount();
});
