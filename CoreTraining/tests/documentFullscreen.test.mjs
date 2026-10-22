import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><html><body><div id="app"></div></body></html>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) {
  globalThis[key] = dom.window[key];
}
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0);
const vue = await import('vue');
const icons = await import('lucide-vue-next');
async function loadComponent(path) {
const source = await readFile(new URL(path, import.meta.url), 'utf8');
const { descriptor } = parse(source);
let code = ts.transpileModule(compileScript(descriptor, { id: 'lesson-navigation-test', inlineTemplate: true }).content, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
  `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`,
).replace('export default', 'return');
return new Function('modules', code)({ vue, 'lucide-vue-next': icons });
}
const Fullscreen = await loadComponent('../src/components/lesson/DocumentFullscreen.vue');
Object.defineProperty(document, 'fullscreenElement', { configurable: true, writable: true, value: null });
Object.defineProperty(document, 'fullscreenEnabled', { configurable: true, writable: true, value: true });

const flush = async () => { await new Promise(resolve => setTimeout(resolve, 0)); await vue.nextTick(); };
function mount() {
  const app = vue.createApp({ render: () => vue.h(Fullscreen, null, {
    default: () => vue.h('input', { class: 'document-state', value: 'Trang 3' }),
  }) });
  app.mount('#app');
  return app;
}

test('enters and exits native fullscreen without recreating the document', async () => {
  document.fullscreenEnabled = true;
  HTMLElement.prototype.requestFullscreen = async function () {
    document.fullscreenElement = this;
    document.dispatchEvent(new window.Event('fullscreenchange'));
  };
  document.exitFullscreen = async () => {
    document.fullscreenElement = null;
    document.dispatchEvent(new window.Event('fullscreenchange'));
  };
  const app = mount();
  try {
    const content = document.querySelector('.document-state');
    document.querySelector('.document-fullscreen-button').click();
    await flush();
    assert.equal(document.fullscreenElement, document.querySelector('.document-fullscreen'));
    assert.equal(document.querySelector('.document-fullscreen-button').getAttribute('aria-pressed'), 'true');
    document.querySelector('.document-fullscreen-button').click();
    await flush();
    assert.equal(document.fullscreenElement, null);
    assert.equal(document.querySelector('.document-state'), content);
  } finally { app.unmount(); }
});

test('falls back to the viewport after rejection and restores focus and scrolling on Escape', async () => {
  HTMLElement.prototype.requestFullscreen = async () => { throw new Error('Unsupported'); };
  document.body.style.overflow = 'auto';
  const app = mount();
  try {
    const content = document.querySelector('.document-state');
    const button = document.querySelector('.document-fullscreen-button');
    button.focus();
    button.click();
    await flush();
    const dialog = document.querySelector('.document-fullscreen');
    assert.equal(dialog.parentElement, document.body);
    assert.equal(dialog.getAttribute('role'), 'dialog');
    assert.equal(document.body.style.overflow, 'hidden');
    assert.equal(document.querySelector('.document-state'), content);
    document.dispatchEvent(new window.KeyboardEvent('keydown', { key: 'Escape' }));
    await flush();
    assert.ok(document.querySelector('#app .document-fullscreen'));
    assert.equal(document.body.style.overflow, 'auto');
    assert.equal(document.activeElement, button);
    assert.equal(document.querySelector('.document-state'), content);
  } finally { app.unmount(); }
});

test('cleans up viewport fullscreen when the document viewer is removed', async () => {
  document.fullscreenEnabled = false;
  document.body.style.overflow = 'scroll';
  const app = mount();
  document.querySelector('.document-fullscreen-button').click();
  await flush();
  assert.equal(document.body.style.overflow, 'hidden');
  app.unmount();
  await flush();
  assert.equal(document.body.style.overflow, 'scroll');
  assert.equal(document.querySelector('.document-fullscreen'), null);
});
