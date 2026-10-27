import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const flush = async () => { for (let i = 0; i < 15; i++) { await Promise.resolve(); await vue.nextTick(); } };
const pause = () => new Promise(resolve => setTimeout(resolve, 300));
const summary = name => ({ courseId: 'c1', courseName: 'Course', lessonId: name, lessonTitle: name, questionCount: 3, passingScore: 80 });
const result = name => ({ content: [summary(name)], totalElements: 21, totalPages: 3, courses: [{ id: 'c1', name: 'Course' }] });
async function fixture(loader, push = () => {}) {
  const { descriptor } = parse(await readFile(new URL('../src/views/AdminExamList.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'exam-test', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  const calls = [];
  const Component = new Function('modules', code)({
    vue, 'vue-router': { useRouter: () => ({ push }) },
    '../stores/userStore': { useUserStore: () => ({ can: () => true }) },
    '../components/TablePagination.vue': { props: ['page'], emits: ['update:page', 'update:pageSize'], template: '<button class="next-page" @click="$emit(\'update:page\', page + 1)">Next</button>' },
    '../api/quizSummaryApi': { getQuizSummaries: params => { calls.push(params); return loader(params, calls.length); } },
  });
  const app = vue.createApp(Component); app.mount('#app'); await flush();
  return { app, calls };
}

test('one initial summary request; search is debounced and pagination requests only its page', async () => {
  const f = await fixture(() => result('lesson'));
  try {
    assert.equal(f.calls.length, 1);
    assert.equal(document.querySelector('tbody strong').textContent, 'lesson');
    document.querySelector('.next-page').click(); await flush(); await pause(); await flush();
    assert.equal(f.calls[1].page, 1);
    const input = document.querySelector('input');
    input.value = 'a'; input.dispatchEvent(new dom.window.Event('input')); await flush();
    input.value = 'abc'; input.dispatchEvent(new dom.window.Event('input')); await flush();
    await pause(); await flush();
    assert.equal(f.calls.length, 3);
    assert.equal(f.calls[2].page, 0);
    assert.equal(f.calls[2].search, 'abc');
  } finally { f.app.unmount(); }
});

test('late responses cannot replace new filters; failed summary load can retry', async () => {
  let resolveOld;
  const old = new Promise(resolve => { resolveOld = resolve; });
  const f = await fixture((_, count) => count === 1 ? old : count === 2 ? Promise.reject(new Error('offline')) : result('new'));
  try {
    const input = document.querySelector('input');
    input.value = 'new'; input.dispatchEvent(new dom.window.Event('input')); await flush();
    resolveOld(result('old')); await flush();
    assert.equal(document.querySelector('tbody strong'), null);
    await pause(); await flush();
    assert.match(document.querySelector('[role="alert"]').textContent, /Không thể tải/);
    document.querySelector('[role="alert"] button').click(); await flush();
    assert.equal(document.querySelector('tbody strong').textContent, 'new');
  } finally { f.app.unmount(); }
});

test('detail click shows pending navigation immediately and prevents duplicate opens', async () => {
  let finish;
  let navigations = 0;
  const pending = new Promise(resolve => { finish = resolve; });
  const f = await fixture(() => result('lesson'), () => { navigations++; return pending; });
  try {
    document.querySelector('.exam-detail').click(); await flush();
    const button = document.querySelector('.exam-detail');
    assert.equal(button.disabled, true);
    assert.match(button.textContent, /Đang mở/);
    assert.ok(document.querySelector('.exam-opening-status[role="status"]'));
    button.click(); await flush();
    assert.equal(navigations, 1);
    finish(); await flush();
    assert.equal(button.disabled, false);
  } finally { f.app.unmount(); }
});

test('exam listing has a dedicated summary loader, rather than loading question bodies', async () => {
  const view = await readFile(new URL('../src/views/AdminExamList.vue', import.meta.url), 'utf8');
  assert.match(view, /getQuizSummaries/);
  assert.doesNotMatch(view, /getQuizQuestions|fetchCourses/);
  assert.match(view, /requestId/);
  assert.match(view, /clearTimeout/);
});

test('route pages and legacy sections are deferred', async () => {
  const router = await readFile(new URL('../src/router/index.ts', import.meta.url), 'utf8');
  assert.doesNotMatch(router, /import \w+ from ['"]\.\.\/views\//);
  const overview = await readFile(new URL('../src/views/AdminOverview.vue', import.meta.url), 'utf8');
  assert.match(overview, /AdminExamList/);
  assert.doesNotMatch(overview, /import AdminPortal from/);
});
