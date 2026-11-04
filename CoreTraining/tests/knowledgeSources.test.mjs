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

test('failed sources are hidden on load and refresh and cannot remain selected', async () => {
  const { descriptor } = parse(await readFile(new URL('../src/components/LessonKnowledgeSources.vue', import.meta.url), 'utf8'));
  let code = ts.transpileModule(compileScript(descriptor, { id: 'sources-test', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) =>
    `const ${bindings.replace(/\bas\b/g, ':')} = modules[${JSON.stringify(module)}];`).replace('export default', 'return');
  let docs = [{ id: 'ok', fileName: 'ready.docx', status: 'INDEXED' }, { id: 'bad', fileName: 'failed.docx', status: 'FAILED' }];
  let savedSources;
  const modules = {
    vue, 'lucide-vue-next': icons,
    './ToggleSwitch.vue': { template: '<span />' },
    './TablePagination.vue': { props: ['total'], template: '<span class="total">{{ total }}</span>' },
    '../composables/useTablePagination': { useTablePagination },
    '../stores/userStore': { useUserStore: () => ({ can: () => true }) },
    '../api/adminApi': { adminApi: {
      getCourseDocuments: async () => docs,
      getLessonKnowledgeSources: async () => [{ documentId: 'ok' }, { documentId: 'bad' }],
      getDocumentOutline: async () => ({ pages: [1], sections: [], excerpts: [] }),
      saveLessonKnowledgeSources: async (_, sources) => { savedSources = sources; return sources; },
    } },
  };
  const app = vue.createApp(new Function('modules', code)(modules), { courseId: 'c1', lessonId: 'l1' });
  app.mount('#app'); await flush();
  try {
    assert.doesNotMatch(document.body.textContent, /failed.docx/);
    assert.equal(document.querySelector('.total').textContent, '1');
    assert.match(document.querySelector('.source-footer').textContent, /1 tài liệu được chọn/);
    document.querySelector('.save-sources').click(); await flush();
    assert.deepEqual(savedSources, [{ documentId: 'ok' }]);
    docs = docs.map(d => ({ ...d, status: 'FAILED' }));
    document.querySelector('.source-heading button').click(); await flush();
    assert.equal(document.querySelectorAll('tbody tr').length, 0);
    assert.equal(document.querySelector('.total').textContent, '0');
    assert.match(document.querySelector('.source-footer').textContent, /Chưa chọn nguồn/);
  } finally { app.unmount(); }
});
