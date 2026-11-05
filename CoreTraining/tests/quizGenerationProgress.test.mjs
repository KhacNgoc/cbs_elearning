import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
import { runQuizGenerationJob, describeQuizJob, describeQuizJobDetail, QuizGenerationJobError, QUIZ_TIMEOUT_MESSAGE } from '../src/utils/quizGenerationJob.ts';

const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const icons = await import('lucide-vue-next');
const { useTablePagination } = await import('../src/composables/useTablePagination.ts');
const jobModule = await import('../src/utils/quizGenerationJob.ts');
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await vue.nextTick(); } };

const snapshot = (fields) => ({ jobId: 'j1', status: 'running', stage: 'generating', percent: 0,
  requestedQuestions: 5, acceptedQuestions: 0, attempt: 1, maxAttempts: 2, ...fields });
const result = { questions: [{ type: 'SINGLE_CHOICE', text: 'Q1', options: ['A', 'B', 'C', 'D'], correctAnswers: [0], explanation: '', sourceIds: ['SOURCE_1'] }], sources: [], warnings: [] };
const fakeClock = () => { let t = 0; return { now: () => t, sleep: async ms => { t += ms; } }; };

test('job runner reports monotonic progress and resolves with the final result', async () => {
  const polls = [snapshot({ stage: 'retrieving', percent: 5 }), snapshot({ percent: 40, acceptedQuestions: 2 }),
    snapshot({ percent: 30, attempt: 2 }), snapshot({ status: 'completed', stage: 'completed', percent: 100, acceptedQuestions: 5, result })];
  const seen = [];
  const output = await runQuizGenerationJob({
    start: async request => { assert.deepEqual(request, { courseId: 'c1' }); return snapshot({ status: 'queued', stage: 'queued' }); },
    status: async jobId => { assert.equal(jobId, 'j1'); return polls.shift(); },
  }, { courseId: 'c1' }, { timeoutMs: 60_000, onProgress: job => seen.push(job.percent), ...fakeClock() });
  assert.equal(output, result);
  assert.deepEqual(seen, [0, 5, 40, 40, 100]);
});

test('job runner surfaces server failure, timeout, missing job and cancellation', async () => {
  await assert.rejects(runQuizGenerationJob({ start: async () => snapshot({}), status: async () => snapshot({ status: 'failed', stage: 'failed', error: 'Dịch vụ tạo câu hỏi tạm thời không khả dụng' }) },
    {}, { timeoutMs: 60_000, ...fakeClock() }), e => e instanceof QuizGenerationJobError && e.kind === 'failed' && /không khả dụng/.test(e.message));

  let polls = 0;
  await assert.rejects(runQuizGenerationJob({ start: async () => snapshot({}), status: async () => { polls++; return snapshot({ percent: 50 }); } },
    {}, { timeoutMs: 6_000, intervalMs: 1_500, ...fakeClock() }), e => e.kind === 'timeout' && e.message === QUIZ_TIMEOUT_MESSAGE);
  assert.equal(polls, 4);

  const notFound = Object.assign(new Error('Tiến trình tạo câu hỏi đã hết hạn'), { response: { status: 404 } });
  await assert.rejects(runQuizGenerationJob({ start: async () => snapshot({}), status: async () => { throw notFound; } },
    {}, { timeoutMs: 60_000, ...fakeClock() }), e => e.kind === 'failed' && /hết hạn/.test(e.message));

  let flaky = 0;
  const recovered = await runQuizGenerationJob({ start: async () => snapshot({}), status: async () => {
    if (++flaky < 3) throw new Error('network');
    return snapshot({ status: 'completed', stage: 'completed', percent: 100, result });
  } }, {}, { timeoutMs: 60_000, ...fakeClock() });
  assert.equal(recovered, result);

  const abort = new AbortController();
  await assert.rejects(runQuizGenerationJob({ start: async () => snapshot({}), status: async () => { abort.abort(); return snapshot({}); } },
    {}, { timeoutMs: 60_000, signal: abort.signal, ...fakeClock() }), e => e.kind === 'cancelled');
});

test('labels name the phase and retry round, details count questions like the bar', () => {
  const twoPhases = { phaseCount: 2 };
  assert.equal(describeQuizJob(snapshot({ stage: 'retrieving', ...twoPhases })), 'Đang truy xuất nội dung tài liệu');
  assert.equal(describeQuizJob(snapshot({ phase: 1, ...twoPhases })), 'Bước 1/2 · Đang sinh câu hỏi');
  assert.equal(describeQuizJob(snapshot({ phase: 1, attempt: 2, ...twoPhases })), 'Bước 1/2 · Đang sinh lại câu chưa đạt (lượt 2/2)');
  assert.equal(describeQuizJob(snapshot({ stage: 'verifying', phase: 2, ...twoPhases })), 'Bước 2/2 · Đang kiểm định câu hỏi');
  assert.equal(describeQuizJob(snapshot({ phase: 1, phaseCount: 1 })), 'Đang sinh câu hỏi');
  assert.equal(describeQuizJobDetail(snapshot({ completedSteps: 0, totalSteps: 5 })), 'Đã tạo 0/5 câu đạt yêu cầu');
  assert.equal(describeQuizJobDetail(snapshot({ stage: 'verifying', completedSteps: 2, totalSteps: 5 })), 'Đã kiểm định 2/5 câu');
  assert.equal(describeQuizJobDetail(snapshot({ stage: 'finalizing', acceptedQuestions: 4 })), '4/5 câu đạt yêu cầu');
});

const course = { id: 'c1', name: 'Course', status: 'DRAFT', adminActions: { exams: ['read', 'create', 'update'] }, lessons: [] };
async function fixture(generateQuizWithProgress) {
  const { descriptor } = parse(await readFile(new URL('../src/views/AdminQuizDetail.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'quiz-progress-test', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  const stub = { template: '<div />' };
  const toasts = [];
  const modules = new Proxy({
    vue, 'lucide-vue-next': icons,
    'vue-router': { useRoute: () => ({ name: 'AdminQuizDetail', params: { scopeType: 'course', scopeId: 'c1' }, query: {} }), useRouter: () => ({ push() {}, replace() {} }) },
    '../stores/courseStore': { useCourseStore: () => vue.reactive({ courses: [] }) },
    '../api/quizContextApi': { getQuizContext: async () => course, getQuizCourses: async () => [course] },
    '../api/adminApi': { adminApi: { getQuizQuestions: async () => [], generateQuizWithProgress }, QUIZ_MAX_QUESTIONS: 10 },
    '../utils/quizGenerationJob': jobModule,
    '../composables/useTablePagination': { useTablePagination },
    '../utils/toast': { showToast: (message, type) => toasts.push({ message, type }) },
    '../api/contentToolsApi': { contentToolsApi: {}, contentToolsError: () => '' },
  }, { get: (target, key) => target[key] || stub });
  const app = vue.createApp(new Function('modules', code)(modules)); app.mount('#app'); await flush();
  document.querySelector('.ai-button').click(); await flush();
  return { app, toasts };
}

test('AI panel shows a live progress bar, then the drafts', async () => {
  let report, finish, options;
  const { app } = await fixture((request, opts) => { options = opts; report = opts.onProgress; return new Promise(resolve => { finish = resolve; }); });
  try {
    document.querySelector('.ai-settings .primary-button').click(); await flush();
    assert.equal(options.signal.aborted, false);
    assert.ok(document.querySelector('.ai-progress[role="status"]'));
    report(snapshot({ percent: 40, acceptedQuestions: 4, attempt: 2, phase: 1, phaseCount: 2, completedSteps: 4, totalSteps: 5 })); await flush();
    const bar = document.querySelector('.ai-progress-track[role="progressbar"]');
    assert.equal(bar.getAttribute('aria-valuenow'), '40');
    assert.equal(document.querySelector('.ai-progress-fill').style.width, '40%');
    assert.match(document.querySelector('.ai-progress-header').textContent, /Bước 1\/2 · Đang sinh lại câu chưa đạt \(lượt 2\/2\)\s*40%/);
    assert.equal(document.querySelector('.ai-progress small').textContent, 'Đã tạo 4/5 câu đạt yêu cầu');
    assert.match(document.querySelector('.ai-settings .primary-button').textContent, /40%/);
    finish(result); await flush();
    assert.equal(document.querySelector('.ai-progress'), null);
    assert.match(document.querySelector('.ai-drafts').textContent, /Q1/);
  } finally { app.unmount(); }
});

test('AI panel keeps a failed progress bar with the error message and aborts on unmount', async () => {
  const { app, toasts } = await fixture(async (request, opts) => {
    opts.onProgress(snapshot({ percent: 60 }));
    throw new QuizGenerationJobError('timeout', QUIZ_TIMEOUT_MESSAGE);
  });
  try {
    document.querySelector('.ai-settings .primary-button').click(); await flush();
    const progress = document.querySelector('.ai-progress.failed');
    assert.ok(progress);
    assert.match(progress.textContent, /Quá thời gian tạo câu hỏi/);
    assert.equal(document.querySelector('.ai-progress-fill').style.width, '60%');
    assert.deepEqual(toasts.at(-1), { message: QUIZ_TIMEOUT_MESSAGE, type: 'error' });
    assert.equal(document.querySelector('.ai-settings .primary-button').disabled, false);
  } finally { app.unmount(); }

  let signal;
  const pending = await fixture((request, opts) => { signal = opts.signal; return new Promise(() => {}); });
  document.querySelector('.ai-settings .primary-button').click(); await flush();
  pending.app.unmount();
  assert.equal(signal.aborted, true);
});
