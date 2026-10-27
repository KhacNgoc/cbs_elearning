import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
import { JSDOM } from 'jsdom';

const dom = new JSDOM('<!doctype html><div id="app"></div>', { pretendToBeVisual: true });
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
globalThis.ResizeObserver = class { observe() {} disconnect() {} };
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0);
globalThis.cancelAnimationFrame = clearTimeout;
const vue = await import('vue');
const icons = await import('lucide-vue-next');
after(() => dom.window.close());
async function component(path, modules) {
  const { descriptor } = parse(await readFile(new URL(path, import.meta.url), 'utf8'));
  let source = ts.transpileModule(compileScript(descriptor, { id: 'word-navigation-test', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  source = source.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, name) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(name)}];`)
    .replace(/import\(["']docx-preview["']\)/g, 'Promise.resolve(modules["docx-preview"])').replace('export default', 'return');
  return new Function('modules', source)({ vue, 'lucide-vue-next': icons, ...modules });
}
const Navigation = await component('../src/components/lesson/DocumentPageNavigation.vue', {});
const Zoom = await component('../src/components/lesson/DocumentZoomControls.vue', {});
const Word = await component('../src/components/lesson/WordDocumentPreview.vue', {
  './DocumentPageNavigation.vue': Navigation,
  './DocumentZoomControls.vue': Zoom,
  '../../api/axiosClient': { axiosClient: { get: async () => ({ data: new ArrayBuffer(0) }) } },
  'docx-preview': { renderAsync: async (_data, host) => {
    const scroller = host.closest('.word-preview');
    let scrollTop = 0;
    Object.defineProperty(scroller, 'scrollTop', { configurable: true, get: () => scrollTop, set: value => {
      const scale = parseFloat(host.style.zoom) || 1;
      scrollTop = Math.min(Math.max(0, value), Math.max(0, 3 * 820 * scale - 500));
    } });
    Object.defineProperty(scroller, 'clientWidth', { configurable: true, value: 900 });
    scroller.getBoundingClientRect = () => ({ top: 0, bottom: 500 });
    for (let index = 0; index < 3; index++) {
      const page = document.createElement('section'); page.className = 'docx-page'; page.textContent = `Page ${index + 1}`;
      Object.defineProperty(page, 'offsetWidth', { value: 800 });
      page.getBoundingClientRect = () => {
        const scale = parseFloat(host.style.zoom) || 1;
        const top = index * 820 * scale - scroller.scrollTop;
        return { top, bottom: top + 800 * scale };
      };
      host.append(page);
    }
  } },
});
const flush = async () => { for (let i = 0; i < 30; i++) { await Promise.resolve(); await vue.nextTick(); } };

test('Word restores its page and changes pages with arrows, buttons and manual scrolling', async () => {
  const positions = [];
  const app = vue.createApp({ render: () => vue.h(Word, { url: '/guide.docx', initialPage: 2, 'onPosition-change': position => positions.push(position) }) });
  app.mount('#app'); await flush();
  try {
    const viewer = document.querySelector('.word-preview-shell');
    viewer.getClientRects = () => [{ width: 900, height: 500 }];
    assert.match(document.querySelector('.page-counter')?.textContent || '', /Trang 2 \/ 3/);
    document.body.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true })); await flush();
    assert.match(document.querySelector('.page-counter').textContent, /Trang 3 \/ 3/);
    assert.equal(positions.at(-1).page, 3);
    assert.equal(positions.at(-1).completed, true);
    document.querySelector('[aria-label="Trang trước"]').click(); await flush();
    assert.equal(positions.at(-1).page, 2);
    const scroller = document.querySelector('.word-preview'); scroller.scrollTop = 0;
    scroller.dispatchEvent(new window.Event('scroll')); await flush();
    assert.match(document.querySelector('.page-counter').textContent, /Trang 1 \/ 3/);
    assert.equal(positions.at(-1).scrollOffset, 0);
    document.querySelector('[aria-label="Thu nhỏ"]').click(); await flush();
    document.querySelector('[aria-label="Thu nhỏ"]').click(); await flush();
    for (let i = 0; i < 2; i++) {
      document.body.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true, cancelable: true })); await flush();
    }
    assert.match(document.querySelector('.page-counter').textContent, /Trang 3 \/ 3/);
    // Real browsers fire a scroll event after programmatic scrolling too.
    scroller.dispatchEvent(new window.Event('scroll')); await flush();
    assert.equal(positions.at(-1).page, 3);
  } finally { app.unmount(); }
});
