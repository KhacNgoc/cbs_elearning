import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
after(() => dom.window.close());
const flush = async () => { for (let i=0; i<20; i++) { await Promise.resolve(); await vue.nextTick(); } };
async function fixture(report) {
  const { descriptor } = parse(await readFile(new URL('../src/views/LessonReportDetail.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'report-test', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  const route = vue.reactive({ params: { id: '42' } });
  const ids = [];
  const Component = new Function('modules', code)({
    vue,
    'vue-router': { useRoute: () => route },
    '../components/ProtectedImage.vue': { default: { template: '<img />' } },
    '../api/contentToolsApi': {
      contentToolsApi: { report: async id => { ids.push(id); return report(id); } },
      contentToolsError: () => 'Không thể xem báo cáo.',
      reportStatus: { RESOLVED: 'Đã xử lý', NEW: 'Mới gửi' },
    },
  });
  const app = vue.createApp(Component);
  app.component('RouterLink', { props: ['to'], template: '<a :href="to"><slot /></a>' });
  app.mount('#app'); await flush();
  return { app, route, ids };
}
const value = { id: 42, lessonId: 'lesson', lessonTitle: 'Bài học', courseName: 'Khóa học', content: 'Nội dung báo cáo',
  images: [], status: 'RESOLVED', resolution: '<img src=x onerror=alert(1)>\nĐã sửa', reviewerName: 'Quản trị viên', createdAt: '2026-10-27T10:00:00' };

test('owner detail displays status and reply as plain text', async () => {
  const f = await fixture(() => value);
  try {
    assert.deepEqual(f.ids, [42]);
    assert.match(document.body.textContent, /Đã xử lý/);
    assert.match(document.querySelector('.report-resolution').textContent, /<img src=x onerror=alert\(1\)>/);
    assert.equal(document.querySelector('.report-resolution img'), null);
  } finally { f.app.unmount(); }
});

test('changing report clears old private detail when the new request fails', async () => {
  const f = await fixture(id => id === 42 ? value : Promise.reject(new Error('Forbidden')));
  try {
    f.route.params.id = '43'; await flush();
    assert.deepEqual(f.ids, [42, 43]);
    assert.match(document.querySelector('[role="alert"]').textContent, /Không thể xem/);
    assert.equal(document.querySelector('.report-resolution'), null);
  } finally { f.app.unmount(); }
});

test('pending report shows its empty reply state and keeps the lesson return link', async () => {
  const f = await fixture(() => ({ ...value, lessonId: 'lesson/one', status: 'NEW', resolution: '', reviewerName: undefined }));
  try {
    assert.match(document.querySelector('.status-badge').textContent, /Mới gửi/);
    assert.match(document.querySelector('.awaiting-response').textContent, /Báo cáo chưa có phản hồi/);
    assert.equal(document.querySelector('.reviewer-details'), null);
    assert.equal(document.querySelector('a').getAttribute('href'), '/lesson/lesson%2Fone');
    assert.equal(document.querySelectorAll('figure').length, 0);
  } finally { f.app.unmount(); }
});

test('report displays all evidence and the last review information', async () => {
  const reviewedAt = '2026-10-27T10:30:00';
  const f = await fixture(() => ({ ...value, reviewedAt, images: ['/api/lesson-reports/42/images/0', '/api/lesson-reports/42/images/1'] }));
  try {
    assert.equal(document.querySelectorAll('figure').length, 2);
    assert.match(document.querySelector('figcaption').textContent, /Ảnh minh chứng 1/);
    assert.match(document.querySelector('.reviewer-details').textContent, /Quản trị viên/);
    assert.equal(document.querySelector('.reviewer-details time').getAttribute('datetime'), reviewedAt);
  } finally { f.app.unmount(); }
});
