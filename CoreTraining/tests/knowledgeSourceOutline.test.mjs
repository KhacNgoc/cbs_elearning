import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><div id="app"></div>');
for (const key of ['window', 'document', 'Document', 'Element', 'HTMLElement', 'SVGElement', 'Node']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const icons = await import('lucide-vue-next');
const { useTablePagination } = await import('../src/composables/useTablePagination.ts');
const flush = async () => { for (let i = 0; i < 20; i++) { await Promise.resolve(); await vue.nextTick(); } };

async function mount(docs, saved, outline) {
  const { descriptor } = parse(await readFile(new URL('../src/components/LessonKnowledgeSources.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'outline-test', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  const state = { saved: null };
  const modules = {
    vue, 'lucide-vue-next': icons,
    './ToggleSwitch.vue': { props: ['modelValue', 'label'], emits: ['update:modelValue'],
      template: '<input type="checkbox" class="toggle" :data-label="label" :checked="modelValue" @change="$emit(\'update:modelValue\', $event.target.checked)" />' },
    './TablePagination.vue': { template: '<span />' },
    '../composables/useTablePagination': { useTablePagination },
    '../stores/userStore': { useUserStore: () => ({ can: () => true }) },
    '../api/adminApi': { adminApi: {
      getCourseDocuments: async () => docs,
      getLessonKnowledgeSources: async () => saved,
      getDocumentOutline: async () => outline,
      saveLessonKnowledgeSources: async (_, sources) => { state.saved = sources; return sources; },
    } },
  };
  const app = vue.createApp(new Function('modules', code)(modules), { courseId: 'c1', lessonId: 'l1' });
  app.mount('#app'); await flush();
  return { app, state };
}

const toggle = async (label, checked) => {
  const input = [...document.querySelectorAll('.section-choices .toggle')].find(el => el.dataset.label === label);
  assert.ok(input, `missing row ${label}`);
  input.checked = checked; input.dispatchEvent(new dom.window.Event('change')); await flush();
};

test('Word sections render as an ordered chapter tree and a chapter selects all its sections', async () => {
  const sections = ['TÀI LIỆU CIF', 'CHƯƠNG 1 › 1.1. CIF là gì?', 'CHƯƠNG 1 › 1.2. Mục đích', 'CHƯƠNG 2 › 2.1. Định danh'];
  const { app, state } = await mount([{ id: 'w', fileName: 'cif.docx', status: 'INDEXED' }],
    [{ documentId: 'w', section: 'CHƯƠNG 2 › 2.1. Định danh' }],
    { pages: [1], sections, headings: [], excerpts: sections.map(section => ({ page: 1, section, excerpt: `đầu ${section}` })) });
  try {
    const rows = [...document.querySelectorAll('.section-row')];
    assert.deepEqual(rows.map(row => row.querySelector('.toggle').dataset.label),
      ['TÀI LIỆU CIF', 'CHƯƠNG 1', '1.1. CIF là gì?', '1.2. Mục đích', 'CHƯƠNG 2', '2.1. Định danh']);
    assert.equal(rows[2].style.paddingLeft, '1.25rem');
    assert.equal(rows[4].querySelector('.toggle').checked, true);
    await toggle('CHƯƠNG 1', true);
    assert.equal(rows[2].querySelector('.toggle').checked, true);
    await toggle('1.2. Mục đích', false);
    assert.equal(rows[1].querySelector('.toggle').checked, false);
    assert.match(document.querySelector('.source-preview').textContent, /đầu CHƯƠNG 1 › 1\.1/);
    document.querySelector('.save-sources').click(); await flush();
    assert.deepEqual(state.saved.map(source => source.section).sort(),
      ['CHƯƠNG 1 › 1.1. CIF là gì?', 'CHƯƠNG 2 › 2.1. Định danh']);
  } finally { app.unmount(); }
});

test('PDF headings suggest page ranges and label excerpts, while sources stay page ranges', async () => {
  const { app, state } = await mount([{ id: 'p', fileName: 'cif.pdf', status: 'INDEXED' }],
    [{ documentId: 'p', pageFrom: 1, pageTo: 1 }],
    { pages: [1, 2, 3, 4, 5, 6], sections: [], excerpts: [{ page: 5, section: 'Trang 5', heading: 'CHƯƠNG 2', excerpt: 'Cấu trúc' }],
      headings: [{ section: 'CHƯƠNG 1', level: 0, pageFrom: 1, pageTo: 4 }, { section: 'CHƯƠNG 2', level: 0, pageFrom: 5, pageTo: 6 },
        { section: 'CHƯƠNG 2 › 2.1. Định danh', level: 1, pageFrom: 5, pageTo: 5 }] });
  try {
    const buttons = [...document.querySelectorAll('.pdf-headings button')];
    assert.deepEqual(buttons.map(button => button.textContent.replace(/\s+/g, ' ').trim()),
      ['CHƯƠNG 1Trang 1–4', 'CHƯƠNG 2Trang 5–6', '2.1. Định danhTrang 5']);
    buttons[1].click(); await flush();
    buttons[1].click(); await flush();
    assert.match(document.querySelector('.source-preview').textContent, /Trang 5 · CHƯƠNG 2/);
    document.querySelector('.save-sources').click(); await flush();
    assert.deepEqual(state.saved, [{ documentId: 'p', pageFrom: 5, pageTo: 6 }]);
  } finally { app.unmount(); }
});
