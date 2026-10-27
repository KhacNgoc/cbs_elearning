import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const { useTablePagination } = await import('../src/composables/useTablePagination.ts');
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await vue.nextTick(); } };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
async function fixture(loader) {
  const { descriptor } = parse(await readFile(new URL('../src/components/QuestionBankPanel.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'bank-context-test', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  const calls = [];
  const modules = {
    vue,
    '../api/quizContextApi': { getQuizCourses: async () => ['c1', 'c2'].map(id => ({ id, name: id, lessons: [], adminActions: { exams: ['create', 'update'] } })),
      getQuizContext: params => { calls.push(params); return loader(params); } },
    '../api/contentToolsApi': { contentToolsApi: { bank: async () => [] }, contentToolsError: () => 'error', bankStatus: {}, difficultyLabel: {} },
    './AdminModal.vue': { props: ['open', 'busy'], emits: ['close'], setup(props, { slots, emit }) { return () => props.open ? vue.h('div', { class: 'test-modal' }, [slots.default?.(), vue.h('button', { class: 'close', onClick: () => emit('close') }, 'Close')]) : null; } },
    './TablePagination.vue': { template: '<div />' },
    '../composables/useTablePagination': { useTablePagination }, '../utils/toast': { showToast() {} },
  };
  const app = vue.createApp(new Function('modules', code)(modules)); app.mount('#app'); await flush();
  return { app, calls };
}

test('bank initially loads course options only; course switches ignore stale lesson options', async () => {
  const old = deferred();
  const f = await fixture(params => params.courseId === 'c1' ? old.promise : Promise.resolve({ lessons: [{ id: 'l2', title: 'Second lesson' }] }));
  try {
    assert.equal(f.calls.length, 0);
    document.querySelector('.bank-heading button').click(); await flush();
    const course = document.querySelector('.bank-form-grid select');
    course.value = 'c1'; course.dispatchEvent(new dom.window.Event('change')); await flush();
    assert.equal(document.querySelectorAll('.bank-form-grid select')[1].disabled, true);
    course.value = 'c2'; course.dispatchEvent(new dom.window.Event('change')); await flush();
    old.resolve({ lessons: [{ id: 'l1', title: 'Old lesson' }] }); await flush();
    const lesson = document.querySelectorAll('.bank-form-grid select')[1];
    assert.match(lesson.textContent, /Second lesson/);
    assert.doesNotMatch(lesson.textContent, /Old lesson/);
    assert.equal(lesson.disabled, false);
  } finally { f.app.unmount(); }
});

test('failed lesson options can retry without silently clearing the selected lesson', async () => {
  let attempts = 0;
  const f = await fixture(async () => { if (++attempts === 1) throw new Error('offline'); return { lessons: [{ id: 'l1', title: 'Lesson' }] }; });
  try {
    document.querySelector('.bank-heading button').click(); await flush();
    const course = document.querySelector('.bank-form-grid select');
    course.value = 'c1'; course.dispatchEvent(new dom.window.Event('change')); await flush();
    assert.match(document.querySelector('.bank-form-grid [role="alert"]').textContent, /Không thể tải/);
    document.querySelector('.bank-form-grid [role="alert"] button').click(); await flush();
    assert.equal(attempts, 2);
    assert.equal(document.querySelector('.bank-form-grid [role="alert"]'), null);
    assert.match(document.querySelectorAll('.bank-form-grid select')[1].textContent, /Lesson/);
  } finally { f.app.unmount(); }
});
