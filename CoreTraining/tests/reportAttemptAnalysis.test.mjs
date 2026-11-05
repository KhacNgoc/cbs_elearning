import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const pagination = await import('../src/composables/useTablePagination.ts');
const paginationStub = { props: ['page', 'total'], emits: ['update:page'], setup(props, { emit }) { return () => vue.h('button', { class: 'next', onClick: () => emit('update:page', props.page + 1) }, `${props.total}/${props.page}`); } };
const flush = async () => { for (let i = 0; i < 10; i++) { await Promise.resolve(); await vue.nextTick(); } };

async function mountAnalysis(api) {
  const source = await readFile(new URL('../src/components/ReportAttemptAnalysis.vue', import.meta.url), 'utf8');
  const { descriptor } = parse(source);
  let code = ts.transpileModule(compileScript(descriptor, { id: 'attempt-analysis', inlineTemplate: true }).content, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, id) => `const ${bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : `{default:${bindings}}`}=modules[${JSON.stringify(id)}];`).replace('export default', 'return');
  const component = new Function('modules', code)({ vue, '../api/trainingReportApi': { readReportAttemptAnalysis: api }, './TablePagination.vue': { default: paginationStub }, '../composables/useTablePagination': pagination });
  document.body.innerHTML = '<div id="app"></div>';
  const app = vue.createApp(component, { attemptId: 7 });
  app.mount('#app');
  const details = document.querySelector('details');
  const open = async () => { details.open = true; details.dispatchEvent(new dom.window.Event('toggle')); await flush(); };
  return { app, details, open };
}

const available = {
  attemptId: 7, available: true, source: 'SNAPSHOT', unavailableReason: null, correct: 1, incorrect: 1, unanswered: 1,
  questions: [
    { id: 1, text: 'Câu đúng', options: ['A', 'B'], selectedAnswers: [1], correctAnswers: [1], answered: true, correct: true },
    { id: 2, text: 'Câu sai', options: ['A', 'B'], selectedAnswers: [0], correctAnswers: [1], answered: true, correct: false },
    { id: 3, text: 'Câu trống', options: ['A', 'B'], selectedAnswers: [], correctAnswers: [0], answered: false, correct: false },
  ],
};

test('loads only when opened, once, and marks learner choices against the key', async () => {
  const calls = [];
  const { app, open, details } = await mountAnalysis(async id => { calls.push(id); return available; });
  try {
    await flush();
    assert.deepEqual(calls, []);
    await open();
    assert.deepEqual(calls, [7]);
    assert.match(details.textContent, /1 đúng · 1 sai · 1 bỏ trống \/ 3 câu/);
    const questions = [...details.querySelectorAll('.analysis-questions > li')];
    assert.deepEqual(questions.map(q => q.querySelector('.question-result').textContent), ['Đúng', 'Sai', 'Bỏ trống']);
    assert.match(questions[0].textContent, /Học viên chọn · Đáp án đúng/);
    assert.ok(questions[1].querySelector('.question-options li.is-wrong').textContent.includes('Học viên chọn'));
    assert.ok(questions[1].querySelector('.question-options li.is-correct').textContent.includes('Đáp án đúng'));
    await open();
    assert.equal(calls.length, 1);
  } finally { app.unmount(); }
});

test('shows five questions per page, keeps numbering, and hides paging for short quizzes', async () => {
  const many = Array.from({ length: 12 }, (_, i) => ({ id: i + 1, text: `Câu số ${i + 1}`, options: ['A'], selectedAnswers: [0], correctAnswers: [0], answered: true, correct: true }));
  const { app, open, details } = await mountAnalysis(async () => ({ ...available, correct: 12, incorrect: 0, unanswered: 0, questions: many }));
  try {
    await open();
    const texts = () => [...details.querySelectorAll('.question-text')].map(q => q.textContent);
    assert.deepEqual(texts(), [1, 2, 3, 4, 5].map(n => `Câu số ${n}`));
    assert.match(details.textContent, /12 đúng · 0 sai · 0 bỏ trống \/ 12 câu/);
    details.querySelector('.next').click();
    await flush();
    assert.deepEqual(texts(), [6, 7, 8, 9, 10].map(n => `Câu số ${n}`));
    assert.equal(details.querySelector('.analysis-questions').getAttribute('start'), '6');
    details.querySelector('.next').click();
    await flush();
    assert.deepEqual(texts(), ['Câu số 11', 'Câu số 12']);
  } finally { app.unmount(); }
  const short = await mountAnalysis(async () => available);
  try { await short.open(); assert.equal(short.details.querySelector('.next'), null); } finally { short.app.unmount(); }
});

test('explains why an attempt cannot be analysed', async () => {
  for (const [reason, text] of [['NO_ANSWERS', /chưa lưu đáp án/], ['QUIZ_CHANGED', /đã thay đổi/]]) {
    const { app, open, details } = await mountAnalysis(async () => ({ attemptId: 7, available: false, source: null, unavailableReason: reason, correct: 0, incorrect: 0, unanswered: 0, questions: [] }));
    try { await open(); assert.match(details.textContent, text); } finally { app.unmount(); }
  }
});

test('flags analysis built from the current quiz rather than an exam snapshot', async () => {
  const { app, open, details } = await mountAnalysis(async () => ({ ...available, source: 'CURRENT' }));
  try { await open(); assert.match(details.textContent, /câu hỏi hiện tại/); } finally { app.unmount(); }
});

test('a failed load can be retried', async () => {
  let attempts = 0;
  const { app, open, details } = await mountAnalysis(async () => { if (attempts++ === 0) throw new Error('offline'); return available; });
  try {
    await open();
    assert.match(details.querySelector('[role=alert]').textContent, /Không thể tải phân tích/);
    details.querySelector('.retry').click();
    await flush();
    assert.equal(details.querySelector('[role=alert]'), null);
    assert.match(details.textContent, /Câu đúng/);
  } finally { app.unmount(); }
});
