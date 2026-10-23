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
const Sidebar = await loadComponent('../src/components/lesson/LessonCourseSidebar.vue');
const Header = await loadComponent('../src/components/lesson/LessonLearningHeader.vue');

test('switches between document, video and text and hides unavailable sections', async () => {
  const { createApp, h, nextTick, reactive } = vue;
  const resource = { id: 10, name: 'Tài liệu mẫu', fileUrl: '/lesson.pdf', documentType: 'PDF' };
  const lesson = { id: 'lesson-1', title: 'Bài học mẫu', videoUrl: '/video.mp4', textBody: 'Nội dung', documents: [resource] };
  const state = reactive({ sidebarOpen: true, activeContent: 'DOCUMENT', selectedDocumentId: 10, course: { lessons: [lesson] } });
  const app = createApp({ render: () => h('div', [h(Header, {
    sidebarOpen: state.sidebarOpen, hasText: Boolean(state.course.lessons[0].textBody?.trim()), hasVideo: Boolean(state.course.lessons[0].videoUrl?.trim()),
    activeContent: state.activeContent, lessonTitle: lesson.title, completedCount: 0, totalLessons: 1, completed: false, canComplete: true,
    'onSelect-content': type => { state.activeContent = type; state.selectedDocumentId = undefined; },
  }), h(Sidebar, {
    ...state, chapters: [{ id: 'chapter-1', title: 'Chương mẫu', lessons: state.course.lessons }], activeLessonId: lesson.id, completedLessons: [], completedCount: 0, progress: 0, open: true,
    lessonQuizScores: {}, lessonQuizTotals: {}, courseQuizScores: {}, courseQuizTotals: {},
    'onSelect-content': type => { state.activeContent = type; state.selectedDocumentId = undefined; },
    'onSelect-document': (id, doc) => { assert.equal(id, lesson.id); state.activeContent = 'DOCUMENT'; state.selectedDocumentId = doc.id; },
  })]) });
  app.component('router-link', { render() { return h('a', this.$slots.default?.()); } });
  app.mount('#app');
  const buttons = () => [...document.querySelectorAll('.lesson-content-switch button')];
  try {
    assert.equal(document.querySelectorAll('.course-sidebar .lesson-content-button').length, 0);
    assert.equal(document.querySelector('.header-completion').getAttribute('aria-hidden'), 'true');
    assert.ok(document.querySelector('.header-completion').hasAttribute('inert'));
    state.sidebarOpen = false;
    await nextTick();
    assert.equal(document.querySelector('.header-completion').getAttribute('aria-hidden'), 'false');
    assert.equal(document.querySelector('.header-completion').hasAttribute('inert'), false);
    assert.match(document.querySelector('.progress-summary').textContent, /0\/1 bài hoàn thành/);
    assert.ok(document.querySelector('.outline-action'));
    assert.equal(buttons().length, 2);
    assert.ok(buttons().every(button => button.getAttribute('aria-pressed') === 'false'));
    buttons()[1].click();
    await nextTick();
    assert.equal(state.activeContent, 'VIDEO');
    assert.equal(state.selectedDocumentId, undefined);
    assert.equal(buttons()[1].getAttribute('aria-pressed'), 'true');
    buttons()[0].click();
    await nextTick();
    assert.equal(state.activeContent, 'TEXT');
    assert.equal(buttons()[1].getAttribute('aria-pressed'), 'false');
    assert.equal(buttons()[0].getAttribute('aria-pressed'), 'true');
    document.querySelector('.lesson-resources-toggle').click();
    await nextTick();
    assert.equal(document.querySelector('.lesson-resources-toggle').getAttribute('aria-expanded'), 'true');
    document.querySelector('.lesson-document-select').click();
    await nextTick();
    assert.equal(state.activeContent, 'DOCUMENT');
    assert.ok(buttons().every(button => button.getAttribute('aria-pressed') === 'false'));
    assert.ok(document.querySelector('.lesson-document-row.selected'));
    state.course.lessons[0].videoUrl = ' ';
    await nextTick();
    assert.equal(buttons().length, 1);
    assert.equal(buttons()[0].textContent, 'Văn bản');
    state.course.lessons[0].textBody = '';
    await nextTick();
    assert.equal(buttons().length, 0);
    assert.ok(document.querySelector('.lesson-resources-toggle'));
  } finally {
    app.unmount();
  }
});


test('shows all chapters, expands only the current chapter initially and switches chapters on demand', async () => {
  const { createApp, h, nextTick, reactive } = vue;
  const first = { id: 'first', title: 'Bài chương 1', documents: [] };
  const second = { id: 'second', title: 'Bài chương 2', documents: [{ id: 20, name: 'Tài liệu chương 2', fileUrl: '/two.pdf' }] };
  const state = reactive({
    activeLessonId: second.id,
    chapters: [
      { id: 'c1', title: 'Chương đầu', lessons: [first] },
      { id: 'c2', title: 'Chương đang học', lessons: [second] },
      { id: 'c3', title: 'Chương trống', lessons: [] },
    ],
    completedLessons: [first.id],
  });
  const selected = [];
  const app = createApp({ render: () => h(Sidebar, {
    ...state, course: { id: 'course', lessons: [first, second] },
    completedCount: 1, progress: 50, open: true,
    lessonQuizScores: {}, lessonQuizTotals: {}, courseQuizScores: {}, courseQuizTotals: {},
    onSelect: id => selected.push(id),
  }) });
  app.mount('#app');
  const chapters = () => [...document.querySelectorAll('.chapter-toggle')];
  const visibleTitles = () => [...document.querySelectorAll('.chapter-lessons:not([aria-hidden="true"]) .lesson-row-copy strong')].map(item => item.textContent);
  try {
    assert.equal(chapters().length, 3);
    assert.deepEqual(chapters().map(item => item.getAttribute('aria-expanded')), ['false', 'true', 'false']);
    assert.deepEqual(visibleTitles(), ['1. Bài chương 2']);
    assert.match(chapters()[0].textContent, /1\/1 bài hoàn thành/);
    assert.match(document.querySelector('.sidebar-header').textContent, /1\/2 bài đã hoàn thành/);
    chapters()[0].click();
    await nextTick();
    assert.deepEqual(visibleTitles(), ['1. Bài chương 1']);
    assert.deepEqual(selected, []);
    document.querySelector('.chapter-lessons:not([aria-hidden="true"]) .lesson-row-copy').click();
    assert.deepEqual(selected, [first.id]);
    chapters()[0].click();
    await nextTick();
    assert.deepEqual(visibleTitles(), []);
    chapters()[2].click();
    await nextTick();
    assert.match(document.querySelector('.chapter-empty').textContent, /chưa có bài học/);
    state.activeLessonId = first.id;
    await nextTick();
    assert.equal(chapters()[0].getAttribute('aria-expanded'), 'true');
    assert.deepEqual(visibleTitles(), ['1. Bài chương 1']);
    // Reopening during collapse must not leave the active panel inert.
    chapters()[0].click();
    await nextTick();
    chapters()[0].click();
    await nextTick();
    const activePanel = document.querySelector('.chapter-lessons:not([aria-hidden="true"])');
    assert.ok(activePanel);
    assert.equal(activePanel.hasAttribute('inert'), false);
    state.completedLessons.push(second.id);
    await nextTick();
    assert.match(chapters()[1].textContent, /1\/1 bài hoàn thành/);
  } finally { app.unmount(); }
});

test('opens the active chapter when the outline arrives asynchronously', async () => {
  const state = vue.reactive({ chapters: [] });
  const app = vue.createApp({ render: () => vue.h(Sidebar, {
    chapters: state.chapters, course: { id: 'c', lessons: [] }, activeLessonId: 'l',
    completedLessons: [], completedCount: 0, progress: 0, open: true,
    lessonQuizScores: {}, lessonQuizTotals: {}, courseQuizScores: {}, courseQuizTotals: {},
  }) });
  app.mount('#app');
  try {
    assert.equal(document.querySelector('.chapter-toggle'), null);
    state.chapters = [{ id: 'c1', title: 'Chương', lessons: [{ id: 'l', title: 'Bài học' }] }];
    await vue.nextTick();
    assert.equal(document.querySelector('.chapter-toggle').getAttribute('aria-expanded'), 'true');
    assert.ok(document.querySelector('.lesson-row-copy'));
  } finally { app.unmount(); }
});
