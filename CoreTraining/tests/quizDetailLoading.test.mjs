import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const icons = await import('lucide-vue-next');
const { useTablePagination } = await import('../src/composables/useTablePagination.ts');
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await vue.nextTick(); } };
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r; }); return { promise, resolve }; };
const course = { id: 'c1', name: 'Course', status: 'DRAFT', adminActions: { exams: ['read', 'update'] }, lessons: [{ id: 'l1', title: 'Lesson', quizPassingScore: 80 }] };
async function fixture(getCourses, getQuestions, options = {}) {
  const { descriptor } = parse(await readFile(new URL('../src/views/AdminQuizDetail.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'quiz-loading-test', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  const stub = { template: '<div />' };
  const store = vue.reactive({ courses: [] });
  const modules = new Proxy({
    vue, 'lucide-vue-next': icons,
    'vue-router': { useRoute: () => options.route || ({ name: 'AdminQuizDetail', params: { scopeType: 'lesson', scopeId: 'l1' }, query: {} }), useRouter: () => ({ push() {}, replace: options.replace || (() => {}) }) },
    '../stores/courseStore': { useCourseStore: () => store },
    '../api/quizContextApi': { getQuizContext: async params => (await getCourses(params)).data[0], getQuizCourses: async () => (await getCourses({ options: true })).data },
    '../api/adminApi': { adminApi: { getQuizQuestions: getQuestions } },
    '../composables/useTablePagination': { useTablePagination },
    '../utils/toast': { showToast() {} },
    '../api/contentToolsApi': { contentToolsApi: {}, contentToolsError: () => '' },
  }, { get: (target, key) => target[key] || stub });
  if (options.toggle) modules['../components/ToggleSwitch.vue'] = { props: ['modelValue'], emits: ['update:modelValue'], template: '<input class="required-toggle" type="checkbox" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' };
  const app = vue.createApp(new Function('modules', code)(modules)); app.mount('#app'); await flush();
  return app;
}

test('detail shows loading throughout course and question requests, then reveals controls', async () => {
  const courses = deferred(), questions = deferred();
  const app = await fixture(() => courses.promise, () => questions.promise);
  try {
    assert.ok(document.querySelector('.quiz-detail-loading[role="status"]'));
    assert.equal(document.querySelector('.settings-card'), null);
    courses.resolve({ data: [course] }); await flush();
    assert.ok(document.querySelector('.quiz-detail-loading'));
    questions.resolve([]); await flush();
    assert.equal(document.querySelector('.quiz-detail-loading'), null);
    assert.ok(document.querySelector('.settings-card'));
    assert.match(document.querySelector('h1').textContent, /Lesson/);
  } finally { app.unmount(); }
});

test('confirming creation preserves unsaved threshold and required setting across route initialization', async () => {
  const writable = { ...course, adminActions: { exams: ['read', 'create', 'update'] } };
  const route = vue.reactive({ name: 'AdminQuizCreate', params: {}, query: {} });
  let attempts = 0;
  const app = await fixture(async () => ({ data: [writable] }), async () => {
    if (++attempts === 1) throw new Error('offline');
    return [];
  }, { route, toggle: true,
    replace() { route.name = 'AdminQuizDetail'; route.params = { scopeType: 'lesson', scopeId: 'l1' }; } });
  try {
    const lessonSelect = document.querySelectorAll('.settings-grid select')[1];
    lessonSelect.value = 'l1'; lessonSelect.dispatchEvent(new dom.window.Event('change'));
    const score = document.querySelector('input[type="number"]');
    score.value = '65'; score.dispatchEvent(new dom.window.Event('input'));
    const toggle = document.querySelector('.required-toggle');
    toggle.checked = false; toggle.dispatchEvent(new dom.window.Event('change')); await flush();
    document.querySelector('.settings-action .primary-button').click(); await flush();
    assert.ok(document.querySelector('.quiz-detail-error'));
    document.querySelector('.quiz-detail-error button').click(); await flush();
    assert.equal(document.querySelector('input[type="number"]').value, '65');
    assert.equal(document.querySelector('.required-toggle').checked, false);
    assert.equal(document.querySelectorAll('.settings-grid select')[1].disabled, true);
  } finally { app.unmount(); }
});

test('creation loads lesson options on course selection and ignores late results; detail reloads on reused route', async () => {
  const second = { ...course, id: 'c2', name: 'Second', lessons: [{ id: 'l2', title: 'Second lesson', courseId: 'c2', quizRequired: false }] };
  const late = deferred();
  const calls = [];
  const route = vue.reactive({ name: 'AdminQuizCreate', params: {}, query: {} });
  const app = await fixture(async params => {
    calls.push(params);
    if (params.options) return { data: [course, second].map(c => ({ ...c, lessons: [] })) };
    if (params.courseId === 'c2') return late.promise;
    return { data: [course] };
  }, async () => [], { route });
  try {
    assert.deepEqual(calls, [{ options: true }, { courseId: 'c1' }]);
    const courseSelect = document.querySelector('.settings-grid select');
    courseSelect.value = 'c2'; courseSelect.dispatchEvent(new dom.window.Event('change')); await flush();
    assert.equal(document.querySelectorAll('.settings-grid select')[1].disabled, true);
    courseSelect.value = 'c1'; courseSelect.dispatchEvent(new dom.window.Event('change')); await flush();
    late.resolve({ data: [second] }); await flush();
    assert.match(document.querySelectorAll('.settings-grid select')[1].textContent, /Lesson/);
    assert.doesNotMatch(document.querySelectorAll('.settings-grid select')[1].textContent, /Second lesson/);
    route.name = 'AdminQuizDetail'; route.params = { scopeType: 'lesson', scopeId: 'l1' }; await flush();
    assert.deepEqual(calls.at(-1), { lessonId: 'l1' });
    assert.equal(document.querySelector('.quiz-detail-loading'), null);
    assert.match(document.querySelector('h1').textContent, /Lesson/);
  } finally { app.unmount(); }
});

test('quiz editor and question bank no longer fetch full admin course payloads or mutate the course store', async () => {
  for (const path of ['../src/views/AdminQuizDetail.vue', '../src/components/QuestionBankPanel.vue']) {
    const source = await readFile(new URL(path, import.meta.url), 'utf8');
    assert.doesNotMatch(source, /\/api\/admin\/courses/);
    assert.doesNotMatch(source, /courseStore\.courses\s*=/);
    assert.match(source, /getQuizContext/);
  }
});

test('failed loading shows retry instead of a misleading empty editor', async () => {
  let attempts = 0;
  const app = await fixture(async () => ({ data: [course] }), async () => {
    if (++attempts === 1) throw new Error('offline');
    return [];
  });
  try {
    assert.ok(document.querySelector('.quiz-detail-error[role="alert"]'));
    assert.equal(document.querySelector('.settings-card'), null);
    document.querySelector('.quiz-detail-error button').click(); await flush();
    assert.equal(attempts, 2);
    assert.equal(document.querySelector('.quiz-detail-error'), null);
    assert.ok(document.querySelector('.settings-card'));
  } finally { app.unmount(); }
});
