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

async function loadStudy() {
  const url = new URL('../src/components/flashcards/FlashcardStudy.vue', import.meta.url);
  const { descriptor } = parse(await readFile(url, 'utf8'));
  const code = ts.transpileModule(compileScript(descriptor, { id: 'flashcard-study-test', inlineTemplate: true }).content, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`,
  ).replace('export default', 'return');
  return new Function('modules', code)({
    vue,
    'lucide-vue-next': icons,
    '../../api/flashcardApi': { TYPE_LABELS: { TERM: 'Thuật ngữ', CLOZE: 'Điền khuyết' } },
    '../../utils/flashcards': await import('../src/utils/flashcards.ts'),
  });
}
const Study = await loadStudy();
const cards = [1, 2, 3].map((id) => ({
  id, type: 'TERM', front: `Mặt trước ${id}`, back: `Mặt sau ${id}`, source: null, mine: false, version: null,
}));

async function mount(props = {}) {
  const host = document.createElement('div');
  document.body.append(host);
  const app = vue.createApp({ render: () => vue.h(Study, { cards, ...props }) });
  app.mount(host);
  await vue.nextTick();
  const text = (selector) => host.querySelector(selector)?.textContent.trim() ?? '';
  const button = (label) => [...host.querySelectorAll('button')].find((item) => item.textContent.includes(label));
  const click = async (label) => { button(label).click(); await vue.nextTick(); await vue.nextTick(); };
  const press = async (key) => {
    host.querySelector('.flashcard-study').dispatchEvent(new dom.window.KeyboardEvent('keydown', { key, bubbles: true }));
    await vue.nextTick();
    await vue.nextTick();
  };
  return { host, text, button, click, press, unmount: () => { app.unmount(); host.remove(); } };
}

test('shows the position and counts each card once however often the learner returns to it', async () => {
  const view = await mount();
  assert.equal(view.text('.study-count'), 'Thẻ 1/3');
  assert.equal(view.text('.study-seen'), 'Đã lướt qua 1/3 thẻ');
  assert.equal(view.button('Thẻ trước').disabled, true);

  await view.click('Thẻ tiếp');
  assert.equal(view.text('.study-count'), 'Thẻ 2/3');
  assert.equal(view.text('.study-seen'), 'Đã lướt qua 2/3 thẻ');

  await view.click('Thẻ trước');
  assert.equal(view.text('.study-count'), 'Thẻ 1/3');
  assert.equal(view.text('.study-seen'), 'Đã lướt qua 2/3 thẻ');
  view.unmount();
});

test('flipping does not move on, and moving on always shows the next card face up', async () => {
  const view = await mount();
  await view.press(' ');
  assert.ok(view.host.querySelector('.flip-card.flipped'));
  assert.equal(view.text('.study-count'), 'Thẻ 1/3');

  await view.press('ArrowRight');
  assert.equal(view.text('.study-count'), 'Thẻ 2/3');
  assert.equal(view.host.querySelector('.flip-card.flipped'), null);

  await view.press('ArrowLeft');
  assert.equal(view.text('.study-count'), 'Thẻ 1/3');
  await view.press('ArrowLeft');
  assert.equal(view.text('.study-count'), 'Thẻ 1/3');
  view.unmount();
});

test('asks for no rating and finishes after the last card, which can be restarted', async () => {
  const view = await mount();
  assert.equal(view.host.querySelector('.rating-button'), null);
  await view.press('ArrowRight');
  await view.press('ArrowRight');
  assert.ok(view.button('Hoàn thành'));
  await view.click('Hoàn thành');
  assert.match(view.text('.study-finished'), /lướt qua hết 3 thẻ/);

  await view.click('Xem lại từ đầu');
  assert.equal(view.text('.study-count'), 'Thẻ 1/3');
  assert.equal(view.text('.study-seen'), 'Đã lướt qua 1/3 thẻ');
  view.unmount();
});

test('shuffling keeps the cards already passed and the seen count', async () => {
  const view = await mount();
  await view.click('Thẻ tiếp');
  await view.click('Trộn thẻ');
  assert.equal(view.text('.study-count'), 'Thẻ 2/3');
  assert.equal(view.text('.flip-face.front .card-text'), 'Mặt trước 3');
  assert.equal(view.text('.study-seen'), 'Đã lướt qua 3/3 thẻ');
  await view.click('Thẻ trước');
  assert.equal(view.text('.flip-face.front .card-text'), 'Mặt trước 1');
  view.unmount();
});
