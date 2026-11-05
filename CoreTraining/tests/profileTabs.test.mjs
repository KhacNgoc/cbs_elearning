import { test, after, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
import {
  attemptPercent,
  profileMetrics,
  resultEmptyMessage,
  resultPageCount,
  resultRowCount,
} from '../src/utils/profileProgress.ts';
import { formatLearningDateTime } from '../src/utils/learning.ts';

const dom = new JSDOM('<div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0);
const vue = await import('vue');
after(() => dom.window.close());
beforeEach(() => { document.body.innerHTML = '<div id="app"></div>'; });

const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await vue.nextTick(); } };
const slotStub = { render() { return vue.h('div', this.$attrs, this.$slots.default?.()); }, inheritAttrs: false };
const emptyResults = { items: [], total: 0, bestScore: null, legacyTotal: 0, legacyScores: [] };
const emptyStats = { joined: 0, completed: 0, total: 0, percent: null, notStarted: 0, inProgress: 0, complete: 0, mandatory: 0, mandatoryComplete: 0, competencies: [] };
const attempt = (over = {}) => ({ id: 'a', quizId: 'q', courseId: 'c', lessonId: 'l', title: 'Bài thi', revision: 1, score: 4, total: 10, passingScore: 80, passed: false, takenAt: '2026-03-01T09:05:00', ...over });
const group = (latest, best, attemptCount = 3) => ({ latest: attempt(latest), best: attempt(best), attemptCount, courseName: 'Khóa học' });

async function mountProfile(tab, api) {
  const { descriptor } = parse(await readFile(new URL('../src/views/Profile.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'profile-tabs-test', inlineTemplate: true }).content, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) => `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  const route = vue.reactive({ query: { tab } });
  const modules = {
    vue,
    'vue-router': { useRoute: () => route, useRouter: () => ({ replace: async () => {} }) },
    'lucide-vue-next': { Award: slotStub },
    '../stores/userStore': { useUserStore: () => ({}) },
    '../api/userApi': { userApi: { getMyProfile: async () => ({ username: 'learner', fullName: 'Learner', role: 'ROLE_USER' }) } },
    '../api/profileReadApi': { profileReadApi: api },
    '../utils/permissions': { roleLabel: () => '' },
    '../utils/learning': await import('../src/utils/learning.ts'),
    '../utils/profileProgress': await import('../src/utils/profileProgress.ts'),
  };
  for (const name of ['CourseThumbnail', 'RadarChart', 'ProgressBar', 'TablePagination', 'PageLoading']) modules[`../components/${name}.vue`] = { name, render() { return vue.h('div', { class: `stub-${name}` }); } };
  const app = vue.createApp(new Function('modules', code)(modules));
  app.component('RouterLink', slotStub);
  app.mount('#app');
  await flush();
  // The page swaps its loader for the content through an out-in transition.
  await new Promise(resolve => setTimeout(resolve, 100));
  await flush();
  return app;
}
const panel = id => document.querySelector(`#profile-${id}-panel`);

test('panels are real DOM, not children of an inert template element', async () => {
  const api = { stats: async () => emptyStats, courses: async () => ({ items: [], total: 0 }), results: async () => emptyResults };
  for (const tab of ['stats', 'results']) {
    const app = await mountProfile(tab, api);
    try {
      assert.ok(panel(tab), `${tab} panel is rendered`);
      assert.equal(document.querySelector('#app template'), null);
    } finally { app.unmount(); }
  }
});

test('stats tab shows a clear empty state for a learner without courses', async () => {
  const app = await mountProfile('stats', { stats: async () => emptyStats, courses: async () => ({ items: [], total: 0 }) });
  try {
    assert.match(panel('stats').textContent, /Chưa có dữ liệu học tập/);
    assert.equal(panel('stats').querySelector('.summary-grid'), null);
  } finally { app.unmount(); }
});

test('stats tab shows real metrics and only pages when courses overflow', async () => {
  const stats = { ...emptyStats, joined: 12, complete: 3, inProgress: 4, notStarted: 5, completed: 10, total: 40, percent: 25, mandatory: 2, mandatoryComplete: 1 };
  const items = [{ course: { id: 'c1', name: 'Khóa A' }, learningStatus: 'IN_PROGRESS', completedCount: 2, totalCount: 4, percent: 50 }];
  const app = await mountProfile('stats', { stats: async () => stats, courses: async () => ({ items, total: 12 }) });
  try {
    const text = panel('stats').textContent;
    assert.match(text, /3\/12 khóa/);
    assert.match(text, /10\/40 bài/);
    assert.match(text, /Đang học · 2\/4 bài · 50%/);
    assert.ok(panel('stats').querySelector('.stub-TablePagination'));
  } finally { app.unmount(); }
});

test('stats error replaces the zero metrics and retries', async () => {
  let fail = true;
  const app = await mountProfile('stats', { stats: async () => { if (fail) throw new Error('down'); return emptyStats; }, courses: async () => ({ items: [], total: 0 }) });
  try {
    assert.ok(panel('stats').querySelector('[role=alert]'));
    assert.equal(panel('stats').querySelector('.summary-grid'), null);
    fail = false;
    panel('stats').querySelector('[role=alert] button').click();
    await flush();
    assert.equal(panel('stats').querySelector('[role=alert]'), null);
    assert.match(panel('stats').textContent, /Chưa có dữ liệu học tập/);
  } finally { app.unmount(); }
});

test('results tab has loading, empty and error states without misleading text', async () => {
  let release;
  const pending = new Promise(resolve => { release = resolve; });
  // The page holds its full-page loader until the first tab read resolves.
  const loading = mountProfile('results', { results: () => pending });
  await flush();
  release(emptyResults);
  const app = await loading;
  try {
    assert.match(panel('results').textContent, /Chưa có kết quả kiểm tra/);
    assert.equal(panel('results').querySelector('.results-table'), null);
    assert.equal(panel('results').querySelector('.stub-TablePagination'), null);
  } finally { app.unmount(); }
  const failed = await mountProfile('results', { results: async () => { throw new Error('down'); } });
  try {
    assert.ok(panel('results').querySelector('[role=alert]'));
    assert.doesNotMatch(panel('results').textContent, /Chưa có kết quả kiểm tra/);
  } finally { failed.unmount(); }
});

test('results rows show score, time and verdict; review links only for a passed quiz', async () => {
  const items = [
    group({ quizId: 'failed', passed: false }, { quizId: 'failed', score: 5, passed: false }),
    group({ quizId: 'passed', score: 9, passed: true, title: 'Đã đạt' }, { quizId: 'passed', score: 9, passed: true }),
  ];
  const app = await mountProfile('results', { results: async () => ({ ...emptyResults, items, total: 2, bestScore: 90 }) });
  try {
    const rows = [...panel('results').querySelectorAll('tbody tr')];
    assert.equal(rows.length, 2);
    assert.match(rows[0].textContent, /4\/10 \(40%\)/);
    assert.match(rows[0].textContent, /Chưa đạt/);
    assert.match(rows[0].textContent, /09:05/);
    assert.equal(rows[0].querySelector('.review-link'), null);
    assert.match(rows[1].textContent, /Đạt/);
    assert.ok(rows[1].querySelector('.review-link'));
    assert.match(panel('results').textContent, /90\/100/);
  } finally { app.unmount(); }
});

test('legacy scores show without attempts and hide while searching', async () => {
  const legacyScores = [{ courseId: 'c', lessonId: 'l', title: 'Cũ', courseName: 'Khóa', score: 8, total: 10, takenAt: '2025-01-01T08:00:00' }];
  const app = await mountProfile('results', { results: async () => ({ ...emptyResults, legacyTotal: 1, legacyScores }) });
  try {
    assert.match(document.querySelector('[aria-label="Kết quả lưu trước đây"]').textContent, /8\/10/);
    assert.match(panel('results').textContent, /Chưa có lịch sử theo từng lần thi/);
    const search = panel('results').querySelector('input[type=search]');
    search.value = 'x';
    search.dispatchEvent(new dom.window.Event('input'));
    await flush();
    assert.equal(document.querySelector('[aria-label="Kết quả lưu trước đây"]'), null);
    assert.match(panel('results').textContent, /Không có bài kiểm tra phù hợp/);
  } finally { app.unmount(); }
});

test('profile progress helpers stay accurate at the edges', () => {
  assert.equal(attemptPercent(1, 3), 33);
  assert.equal(attemptPercent(0, 0), 0);
  const metrics = profileMetrics(emptyStats);
  assert.equal(metrics[1].value, '—');
  assert.equal(metrics[2].value, '—');
  assert.equal(profileMetrics({ ...emptyStats, joined: 3, complete: 1, percent: 0 })[1].value, '33%');
  assert.equal(profileMetrics({ ...emptyStats, percent: 0 })[2].value, '0%');
  assert.equal(resultRowCount({ total: 2, legacyTotal: 30 }, false), 30);
  assert.equal(resultRowCount({ total: 2, legacyTotal: 30 }, true), 2);
  assert.equal(resultPageCount(0, 10), 1);
  assert.equal(resultPageCount(21, 10), 3);
  assert.equal(resultEmptyMessage({ total: 3, legacyTotal: 0 }, false), '');
  assert.match(resultEmptyMessage({ total: 0, legacyTotal: 0 }, true), /từ khóa/);
  assert.match(resultEmptyMessage({ total: 0, legacyTotal: 2 }, false), /từng lần thi/);
  assert.match(resultEmptyMessage({ total: 0, legacyTotal: 0 }, false), /Chưa có kết quả/);
  assert.match(formatLearningDateTime('2026-03-01T09:05:00'), /09:05.*01\/03\/2026/);
  assert.equal(formatLearningDateTime('nonsense'), 'Chưa xác định');
  assert.equal(formatLearningDateTime(null), 'Chưa xác định');
});
