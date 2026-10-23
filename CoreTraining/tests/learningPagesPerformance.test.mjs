import { test, after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
import { createAsyncResourceCache } from '../src/utils/asyncResourceCache.ts';
import { createLessonProgressWriter } from '../src/utils/lessonProgressWriter.ts';
import * as structure from '../src/utils/learningStructure.ts';
import * as content from '../src/utils/lessonContent.ts';

const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
globalThis.requestAnimationFrame = callback => setTimeout(callback, 0);
const vue = await import('vue');
const icons = await import('lucide-vue-next');
after(() => dom.window.close());
const deferred = () => {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
};
const flush = async () => { for (let i = 0; i < 30; i++) { await Promise.resolve(); await vue.nextTick(); } };
async function compile(path, modules) {
  const { descriptor } = parse(await readFile(new URL(path, import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'learning-page-performance', inlineTemplate: true }).content, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`,
  ).replace('export default', 'return').replaceAll('import.meta.env', '({})');
  return new Function('modules', code)({ vue, 'lucide-vue-next': icons, ...modules });
}
const Stub = { render() { return vue.h('div', this.$slots.default?.()); } };
const Link = { render() { return vue.h('a', this.$slots.default?.()); } };

async function fixture({ save, notes, comments, lessonComments } = {}, view = 'LessonDetail') {
  const state = vue.reactive({ id: 'a', courseId: 'course' });
  const a = { id: 'a', courseId: 'course', chapterId: 'c1', title: 'Bài A', textBody: 'Văn bản A', videoUrl: '/a.mp4', documents: [], comments: [] };
  if (lessonComments) Object.assign(a, lessonComments);
  const b = { ...a, id: 'b', chapterId: 'c2', title: 'Bài B', videoUrl: '/b.mp4' };
  const c = { ...a, id: 'c', title: 'Bài C' };
  const outline = {
    course: { id: 'course', name: 'Khóa học', lessons: [a, b, c] },
    chapters: [
      { id: 'c1', title: 'Chương 1', lessons: [a, { ...a, id: 'gap' }, c], locked: false, completed: false },
      { id: 'c2', title: 'Chương 2', lessons: [b], locked: true, completed: false },
    ],
  };
  const calls = { outlines: 0, saves: [], notes: [] };
  const writer = createLessonProgressWriter(async (id, request) => {
    calls.saves.push({ id, request: { ...request } });
    const result = save ? await save(id, request, calls.saves.length) : { lessonId: id, ...request };
    if (result) user.lessonProgress[id] = result;
    return result;
  });
  const user = vue.reactive({
    username: 'user', permissions: [], systemAdmin: false, completedLessons: ['a', 'c'],
    quizScores: {}, quizTotals: {}, lessonQuizScores: {}, lessonQuizTotals: {},
    registeredCourses: ['course'], mandatoryCourses: [], canLearn: true, progressLoaded: true,
    lessonProgress: {}, fetchProgress: async () => {}, isCourseAccessible: () => true,
    saveLessonProgress: (id, request, touch) => writer.write(id, request, touch),
    getLatestLessonProgress: id => ({ ...user.lessonProgress[id], ...writer.pending(id) }),
  });
  const cache = createAsyncResourceCache();
  const courses = vue.reactive({
    currentLesson: null, currentCourse: outline.course,
    async fetchLessonDetail(id) { courses.currentLesson = id === 'a' ? a : b; return courses.currentLesson; },
    fetchOutline(id, context, force) { return cache.get(id + context, async () => { calls.outlines++; return outline; }, force); },
    addComment: async () => {},
  });
  let mediaEmit;
  let discussionEmit;
  const Discussion = {
    props: ["comments", "hasMore", "loadingMore"], emits: ["load-more"],
    setup(props, { emit }) { discussionEmit = emit; return () => vue.h("div", { id: "discussion", "data-more": String(props.hasMore) }, JSON.stringify(props.comments)); },
  };
  const Media = {
    props: ['lesson', 'initialPositionSeconds'], emits: ['progress-update', 'pause'],
    setup(props, { emit }) { mediaEmit = emit; return () => vue.h('div', { id: 'media', 'data-lesson': props.lesson.id, 'data-position': props.initialPositionSeconds }); },
  };
  const Notes = {
    props: ['notes', 'loading'],
    setup(props) { return () => vue.h('div', { id: 'notes', 'data-loading': String(props.loading) }, JSON.stringify(props.notes)); },
  };
  const router = { push: async path => { if (typeof path === 'string' && path.startsWith('/lesson/')) state.id = path.split('/').at(-1); } };
  const route = vue.reactive({ query: {}, params: { courseId: 'course' } });
  const modules = {
    'vue-router': { useRoute: () => route, useRouter: () => router },
    '../stores/courseStore': { useCourseStore: () => courses },
    '../stores/userStore': { useUserStore: () => user },
    '../utils/scrollWithin': { scrollWithin() {} },
    '../utils/learningStructure': structure,
    '../utils/lessonContent': content,
    '../utils/learning': { resumeRoute: () => '/lesson/a' },
    '../api/axiosClient': { axiosClient: {} },
    '../api/courseApi': { courseApi: { getComments: comments, getVideoNotes(id) { calls.notes.push(id); return notes ? notes(id) : Promise.resolve([]); } } },
    '../components/CourseThumbnail.vue': Stub,
    '../components/CourseRatingForm.vue': Stub,
  };
  for (const name of ['LessonLearningHeader', 'LessonCourseSidebar', 'LessonMediaStage', 'LessonOverview', 'LessonReportForm', 'LessonDiscussion', 'LessonVideoNotes'])
    modules[`../components/lesson/${name}.vue`] = name === 'LessonMediaStage' ? Media : name === 'LessonVideoNotes' ? Notes : name === 'LessonDiscussion' ? Discussion : Stub;
  const Page = await compile(`../src/views/${view}.vue`, modules);
  const app = vue.createApp({ render: () => vue.h(Page, view === 'LessonDetail' ? { id: state.id } : {}) });
  app.component('RouterLink', Link);
  app.mount('#app');
  await flush();
  return { app, state, user, calls, loadMoreComments: () => discussionEmit("load-more"), emit: (...args) => mediaEmit(...args) };
}

test('lesson content does not wait for notes or old progress writes; same-course outline is reused', async () => {
  const oldNotes = deferred();
  const oldSave = deferred();
  const f = await fixture({
    notes: id => id === 'a' ? oldNotes.promise : Promise.resolve([{ id: 2, content: 'Notes B' }]),
    save: (id, request, count) => count === 2 ? oldSave.promise : Promise.resolve({ lessonId: id, ...request }),
  });
  try {
    assert.equal(document.querySelector('#media').dataset.lesson, 'a');
    assert.equal(document.querySelector('#notes').dataset.loading, 'true');
    f.emit('progress-update', { lastPositionSeconds: 12, watchedSeconds: 12, durationSeconds: 100 });
    f.state.id = 'b';
    await flush();
    assert.equal(document.querySelector('#media').dataset.lesson, 'b');
    assert.equal(f.calls.outlines, 1);
    assert.equal(f.calls.saves.filter(call => call.id === 'a').length, 2);
    assert.equal(f.calls.saves.find(call => call.id === 'a' && call.request.lastPositionSeconds === 12).request.contentType, 'VIDEO');
    oldNotes.resolve([{ id: 1, content: 'Notes A' }]);
    await flush();
    assert.match(document.querySelector('#notes').textContent, /Notes B/);
    oldSave.resolve({ lessonId: 'a', lastPositionSeconds: 12 });
    await flush();
  } finally { f.app.unmount(); }
});

test('continuous playback saves every throttle window and pausing flushes immediately', async t => {
  const f = await fixture();
  t.mock.timers.enable({ apis: ['setTimeout'] });
  try {
    const baseline = f.calls.saves.length;
    f.emit('progress-update', { lastPositionSeconds: 1 });
    t.mock.timers.tick(4000);
    f.emit('progress-update', { lastPositionSeconds: 4 });
    t.mock.timers.tick(1000);
    await flush();
    assert.equal(f.calls.saves.length, baseline + 1);
    assert.equal(f.calls.saves.at(-1).request.lastPositionSeconds, 4);
    f.emit('progress-update', { lastPositionSeconds: 7 });
    f.emit('pause');
    await flush();
    assert.equal(f.calls.saves.at(-1).request.lastPositionSeconds, 7);
    t.mock.timers.tick(5000);
    await flush();
    assert.equal(f.calls.saves.length, baseline + 2);
  } finally { f.app.unmount(); }
});

test('course outline mounts only expanded chapters and retains sequential locks/admin bypass', async () => {
  const f = await fixture({}, 'CourseOutline');
  try {
    assert.equal(document.querySelectorAll('.outline-lesson').length, 3);
    assert.equal(document.querySelector('#chapter-c2'), null);
    const buttons = () => [...document.querySelectorAll('#chapter-c1 .outline-lesson')];
    assert.deepEqual(buttons().map(button => button.disabled), [false, false, true]);
    f.user.systemAdmin = true;
    await flush();
    assert.deepEqual(buttons().map(button => button.disabled), [false, false, false]);
    document.querySelectorAll('.chapter-title')[1].click();
    await flush();
    assert.equal(document.querySelectorAll('#chapter-c2 .outline-lesson').length, 1);
    document.querySelectorAll('.chapter-title')[1].click();
    await flush();
    assert.equal(document.querySelector('#chapter-c2'), null);
  } finally { f.app.unmount(); }
});


test('returning to a lesson before its write finishes restores the latest pending position', async () => {
  const waiting = deferred();
  const f = await fixture({
    save: (id, request, count) => count === 2 ? waiting.promise : Promise.resolve({ lessonId: id, ...request }),
  });
  try {
    f.emit('progress-update', { lastPositionSeconds: 23, watchedSeconds: 23, durationSeconds: 100 });
    f.state.id = 'b';
    await flush();
    f.state.id = 'a';
    await flush();
    assert.equal(document.querySelector('#media').dataset.lesson, 'a');
    assert.equal(document.querySelector('#media').dataset.position, '23');
    // Restoring the pending snapshot must not enqueue an obsolete zero-position write.
    assert.equal(f.calls.saves.filter(call => call.id === 'a').length, 2);
    waiting.resolve({ lessonId: 'a', lastPositionSeconds: 23, watchedSeconds: 23, durationSeconds: 100, contentType: 'VIDEO' });
    await flush();
    assert.equal(f.user.lessonProgress.a.lastPositionSeconds, 23);
  } finally { f.app.unmount(); }
});


test('discussion pages append without duplicates and update the remaining count', async () => {
  const f = await fixture({
    lessonComments: { comments: [{ id: 1, content: 'First' }], commentCount: 2 },
    comments: async (id, page) => {
      assert.equal(id, 'a'); assert.equal(page, 1);
      return { content: [{ id: 1 }, { id: 2, content: 'Second' }], page, totalElements: 2, last: true };
    },
  });
  try {
    assert.equal(document.querySelector('#discussion').dataset.more, 'true');
    f.loadMoreComments(); await flush();
    assert.equal(document.querySelector('#discussion').dataset.more, 'false');
    assert.deepEqual(JSON.parse(document.querySelector('#discussion').textContent).map(c => c.id), [1, 2]);
  } finally { f.app.unmount(); }
});

test('late discussion pages cannot append to a different lesson', async () => {
  const pending = deferred();
  const f = await fixture({
    lessonComments: { comments: [{ id: 1 }], commentCount: 2 },
    comments: () => pending.promise,
  });
  try {
    f.loadMoreComments(); await flush();
    f.state.id = 'b'; await flush();
    pending.resolve({ content: [{ id: 99 }], page: 1, totalElements: 2, last: true });
    await flush();
    assert.ok(!JSON.parse(document.querySelector('#discussion').textContent).some(c => c.id === 99));
  } finally { f.app.unmount(); }
});
