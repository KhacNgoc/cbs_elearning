import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as content from '../src/utils/lessonContent.ts';
const supporting = { id: 10, name: 'Supporting', fileUrl: '/legacy.pptx', documentType: 'POWERPOINT' };
const slide = { id: 20, name: 'Slide', fileUrl: '/deck.pptx', documentType: 'POWERPOINT', purpose: 'SLIDE' };
const lesson = { textBody: '# Nội dung', videoUrl: '/video.mp4', documents: [supporting, slide] };
test('normal visits prioritize video then text then required slides', () => {
  assert.equal(content.initialLessonContent(lesson).type, 'VIDEO');
  assert.equal(content.initialLessonContent({ ...lesson, videoUrl: '' }).type, 'TEXT');
  assert.equal(content.initialLessonContent({ ...lesson, videoUrl: '', textBody: '' }).document, slide);
});
test('supporting files never automatically open, including saved PowerPoint bookmarks', () => {
  assert.deepEqual(content.initialLessonContent({ documents: [supporting] }, { contentType: 'DOCUMENT', documentId: 10 }), { type: 'TEXT', document: null });
});
test('explicit supporting file links remain available', () => {
  assert.deepEqual(content.initialLessonContent(lesson, undefined, supporting), { type: 'DOCUMENT', document: supporting });
});
test('only purpose selects slides, and edit selection derives from real content', () => {
  assert.equal(typeof content.lessonContentTypes, 'function');
  assert.deepEqual(content.lessonContentTypes(lesson), ['TEXT', 'VIDEO', 'SLIDE']);
  assert.deepEqual(content.lessonContentTypes({ documents: [supporting] }), []);
});
test('submission removes hidden input without mutating local unsaved values', () => {
  assert.equal(typeof content.selectedLessonContent, 'function');
  const result = content.selectedLessonContent(lesson, ['SLIDE']);
  assert.equal(result.textBody, '');
  assert.equal(result.videoUrl, '');
  assert.deepEqual(result.documents, [supporting, slide]);
  assert.equal(lesson.textBody, '# Nội dung');
  assert.deepEqual(content.selectedLessonContent(lesson, ['TEXT']).documents, [supporting]);
});
test('selected components require real text, video and at least one usable PPTX slide', () => {
  assert.equal(typeof content.validateSelectedLessonContent, 'function');
  assert.throws(() => content.validateSelectedLessonContent({ documents: [] }, ['TEXT']), /văn bản/);
  assert.throws(() => content.validateSelectedLessonContent({ documents: [] }, ['VIDEO']), /video/);
  assert.throws(() => content.validateSelectedLessonContent({ documents: [supporting] }, ['SLIDE']), /Slide/);
  assert.throws(() => content.validateSelectedLessonContent({ documents: [{ ...slide, fileUrl: '/old.ppt' }] }, ['SLIDE']), /pptx/);
  assert.doesNotThrow(() => content.validateSelectedLessonContent(lesson, ['TEXT', 'VIDEO', 'SLIDE']));
});

test('only one required slide file is allowed while supporting PowerPoint files stay unrestricted', () => {
  assert.throws(() => content.validateSelectedLessonContent({ documents: [slide, { ...slide, id: 21 }] }, ['SLIDE']), /một|1/);
  assert.doesNotThrow(() => content.validateSelectedLessonContent({ documents: [slide, supporting, { ...supporting, id: 11 }] }, ['SLIDE']));
});

// Mount the actual selector to verify its v-model and upload classification.
import { readFile } from 'node:fs/promises';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
import { JSDOM } from 'jsdom';
const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const icons = await import('lucide-vue-next');
async function selectorFixture(selected = [], documents = [], readOnly = false, requirements = { TEXT: true, VIDEO: true, SLIDE: true }) {
  const { descriptor } = parse(await readFile(new URL('../src/components/lesson/LessonContentSelection.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'content-selector', inlineTemplate: true }).content, {
    compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) => `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  const messages = [];
  const component = new Function('modules', code)({ vue, 'lucide-vue-next': icons, '../../utils/lessonContent': content, '../../utils/toast': { showToast: message => messages.push(message) } });
  const state = vue.reactive({ selected, documents, requirements });
  const app = vue.createApp({ render: () => vue.h(component, { readOnly, modelValue: state.selected, 'onUpdate:modelValue': value => state.selected = value, documents: state.documents, 'onUpdate:documents': value => state.documents = value, requirements: state.requirements, 'onUpdate:requirements': value => state.requirements = value }) });
  app.mount('#app');
  return { state, messages, close: () => app.unmount() };
}
test('real dropdown toggles editors and preserves unsaved slide values while hidden', async () => {
  const f = await selectorFixture(['SLIDE'], [{ ...slide, file: { name: 'draft.pptx' } }]);
  try {
    assert.ok(document.querySelector('.slide-editor'));
    const input = document.querySelector('input[value="SLIDE"]');
    input.checked = false; input.dispatchEvent(new dom.window.Event('change', { bubbles: true })); await vue.nextTick();
    assert.equal(document.querySelector('.slide-editor'), null);
    assert.equal(f.state.documents[0].file.name, 'draft.pptx');
    input.checked = true; input.dispatchEvent(new dom.window.Event('change', { bubbles: true })); await vue.nextTick();
    assert.match(document.querySelector('.slide-editor').textContent, /draft.pptx/);
  } finally { f.close(); }
});
test('slide upload uses SLIDE purpose, rejects unsupported PPT and retains supporting PowerPoint', async () => {
  const f = await selectorFixture(['SLIDE'], [{ ...supporting }]);
  try {
    await vue.nextTick();
    assert.equal(f.state.documents[1].purpose, 'SLIDE');
    const input = document.querySelector('input[type="file"]');
    Object.defineProperty(input, 'files', { configurable: true, value: [new dom.window.File(['old'], 'old.ppt')] });
    input.dispatchEvent(new dom.window.Event('change', { bubbles: true })); await vue.nextTick();
    assert.equal(f.state.documents[1].file, undefined);
    assert.match(f.messages[0], /pptx/);
    Object.defineProperty(input, 'files', { configurable: true, value: [new dom.window.File(['deck'], 'deck.pptx')] });
    input.dispatchEvent(new dom.window.Event('change', { bubbles: true })); await vue.nextTick();
    assert.equal(f.state.documents[1].file.name, 'deck.pptx');
    assert.equal(f.state.documents[1].documentType, 'POWERPOINT');
    assert.equal(f.state.documents[0].purpose, undefined);
  } finally { f.close(); }
});


test('requirement choice appears only for ticked content and updates the model', async () => {
  const f = await selectorFixture(['TEXT', 'VIDEO']);
  try {
    assert.ok(document.querySelector('[data-required-for="TEXT"]'));
    assert.ok(document.querySelector('[data-required-for="VIDEO"]'));
    assert.equal(document.querySelector('[data-required-for="SLIDE"]'), null);
    const video = document.querySelector('[data-required-for="VIDEO"]');
    assert.equal(video.checked, true);
    video.checked = false; video.dispatchEvent(new dom.window.Event('change', { bubbles: true })); await vue.nextTick();
    assert.equal(f.state.requirements.VIDEO, false);
    assert.equal(f.state.requirements.TEXT, true);
    assert.ok(document.querySelector('.content-selection-value').textContent.includes('Video (không bắt buộc)'));
    assert.equal(document.querySelector('.content-selection-note'), null);
    const tick = document.querySelector('input[value="VIDEO"]');
    tick.checked = false; tick.dispatchEvent(new dom.window.Event('change', { bubbles: true })); await vue.nextTick();
    assert.equal(document.querySelector('[data-required-for="VIDEO"]'), null);
  } finally { f.close(); }
});
test('form warns when ticked content has nothing required', async () => {
  const f = await selectorFixture(['TEXT'], [], false, { TEXT: false, VIDEO: true, SLIDE: true });
  try { assert.match(document.querySelector('.content-selection-note').textContent, /Đánh dấu hoàn thành/); }
  finally { f.close(); }
});
test('requirements default to required and unticked content does not keep a choice', () => {
  assert.deepEqual(content.lessonContentRequirements({}), { TEXT: true, VIDEO: true, SLIDE: true });
  assert.deepEqual(content.lessonContentRequirements({ videoRequired: false }), { TEXT: true, VIDEO: false, SLIDE: true });
  assert.deepEqual(
    content.lessonRequirementFields({ TEXT: false, VIDEO: false, SLIDE: false }, ['VIDEO']),
    { textRequired: true, videoRequired: false, slideRequired: true },
  );
});
test('only a changed choice on content that already existed counts as a requirement change', () => {
  const saved = { ...lesson, videoRequired: false };
  const current = { TEXT: true, VIDEO: false, SLIDE: true };
  assert.deepEqual(content.changedLessonRequirements(saved, current, ['TEXT', 'VIDEO', 'SLIDE']), []);
  assert.deepEqual(content.changedLessonRequirements(saved, { ...current, VIDEO: true }, ['TEXT', 'VIDEO']), ['VIDEO']);
  assert.deepEqual(content.changedLessonRequirements({ documents: [] }, { ...current, TEXT: false }, ['TEXT']), []);
});
test('learner sees optional content and the completion rule it leaves', () => {
  const mixed = { ...lesson, documents: [supporting, slide], videoRequired: false, slideRequired: false };
  assert.deepEqual(content.optionalLessonContent(mixed), ['VIDEO', 'SLIDE']);
  assert.deepEqual(content.optionalLessonContent({ textBody: '', textRequired: false }), []);
  assert.equal(content.lessonCompletionRule(mixed, false), 'Học hết Văn bản');
  assert.equal(content.lessonCompletionRule(mixed, true), 'Học hết Văn bản và đạt bài kiểm tra');
  const none = { textBody: 'Text', textRequired: false };
  assert.equal(content.lessonCompletionRule(none, true), 'Đạt bài kiểm tra bắt buộc');
  assert.match(content.lessonCompletionRule(none, false), /Đánh dấu hoàn thành/);
});

test('metadata edits preserve fetched learning content, document identities and optimistic lock', () => {
  assert.equal(typeof content.lessonMetadataUpdate, 'function');
  const existing = { ...lesson, id: 'L1', title: 'Old', hint: 'Hint', version: 7, procedureSteps: [{ title: 'Process', steps: ['A'] }] };
  const result = content.lessonMetadataUpdate(existing, { title: 'New', hint: 'New hint', chapterId: 'chapter', courseId: 'course' }, 6);
  assert.equal(result.title, 'New');
  assert.equal(result.videoUrl, '/video.mp4');
  assert.equal(result.textBody, '# Nội dung');
  assert.equal(result.documents[1].id, 20);
  assert.equal(result.documents[1].purpose, 'SLIDE');
  assert.equal(result.procedureSteps, existing.procedureSteps);
  assert.equal(result.version, 6);
});

test('URL video selection cannot validate through a stale hidden upload', () => {
  assert.throws(() => content.validateSelectedLessonContent({ videoSource: 'URL', videoUrl: '', videoFile: { name: 'old.mp4' } }, ['VIDEO']), /video/);
});

test('slide editor offers one styled file picker and replaces the selected file in place', async () => {
  const f = await selectorFixture(['SLIDE'], [{ ...supporting }]);
  try {
    assert.equal([...document.querySelectorAll('button')].some(button => button.textContent.trim() === 'Thêm Slide'), false);
    assert.ok(document.querySelector('.file-picker input[type="file"]'));
    assert.equal(document.querySelectorAll('.document-form-card').length, 1);
    const input = document.querySelector('input[type="file"]');
    for (const name of ['first.pptx', 'replacement.pptx']) {
      Object.defineProperty(input, 'files', { configurable: true, value: [new dom.window.File(['deck'], name)] });
      input.dispatchEvent(new dom.window.Event('change', { bubbles: true })); await vue.nextTick();
      const slides = f.state.documents.filter(document => document.purpose === 'SLIDE');
      assert.equal(slides.length, 1);
      assert.equal(slides[0].file.name, name);
    }
    assert.equal(f.state.documents[0].id, supporting.id);
    document.querySelector('button[aria-label="Gỡ file slide"]').click(); await vue.nextTick();
    const cleared = f.state.documents.find(document => document.purpose === 'SLIDE');
    assert.equal(cleared.file, undefined);
    assert.equal(cleared.fileUrl, '');
    assert.equal(input.required, true);
  } finally { f.close(); }
});

test('legacy extra slides require explicit removal and do not discard supporting files', async () => {
  const f = await selectorFixture(['SLIDE'], [{ ...supporting }, { ...slide }, { ...slide, id: 21, name: 'Extra' }]);
  try {
    assert.ok(document.querySelector('[role="alert"]'));
    document.querySelector('button[aria-label="Xóa slide dư Extra"]').click(); await vue.nextTick();
    assert.equal(f.state.documents.length, 2);
    assert.equal(f.state.documents[0].id, supporting.id);
    assert.equal(document.querySelector('[role="alert"]'), null);
    assert.equal(document.querySelectorAll('input[type="file"]').length, 1);
  } finally { f.close(); }
});

test('read-only slide editor does not create or change lesson documents', async () => {
  const f = await selectorFixture(['SLIDE'], [{ ...supporting }], true);
  try {
    assert.equal(f.state.documents.length, 1);
    assert.equal(document.querySelector('input[type="file"]'), null);
  } finally { f.close(); }
});
