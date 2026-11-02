import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';
const dom = new JSDOM('<div id="app"></div>');
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'localStorage']) {
  if (key !== 'localStorage') globalThis[key] = dom.window[key];
}
globalThis.localStorage = { getItem: () => null, setItem() {} };
const vue = await import('vue');
const icons = await import('lucide-vue-next');
test('opening an admin page does not request learner progress or the full course catalog', async () => {
  const calls = [];
  const user = { isAuthenticated: true, hasAdminAccess: true, fullName: 'Admin', fetchProgress: async () => calls.push('progress') };
  const modules = {
    vue, 'lucide-vue-next': icons,
    'vue-router': { useRoute: () => ({ name: 'AdminUsers', fullPath: '/admin/users' }) },
    './stores/userStore': { useUserStore: () => user },
    './stores/courseStore': { useCourseStore: () => ({ fetchCourses: async () => calls.push('courses') }) },
    './stores/navigationStore': { useNavigationStore: () => ({ mainItems: [], homeRoute: '/admin', load: async () => calls.push('navigation') }) },
  };
  const { descriptor } = parse(await readFile(new URL('../src/App.vue', import.meta.url), 'utf8'));
  let source = ts.transpileModule(compileScript(descriptor, { id: 'startup', inlineTemplate: true }).content,
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  source = source.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, id) => {
    const names = bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : `{ default: ${bindings} }`;
    return `const ${names} = modules[${JSON.stringify(id)}];`;
  }).replace('export default', 'return');
  const Stub = { render: () => vue.h('div') };
  const Component = new Function('modules', source)(new Proxy(modules, { get: (target, key) => target[key] || { default: Stub } }));
  const app = vue.createApp(Component);
  app.component('router-link', Stub); app.component('router-view', Stub);
  try {
    app.mount('#app'); await vue.nextTick(); await Promise.resolve();
    assert.equal(calls.includes('courses'), false);
    assert.equal(calls.includes('progress'), false);
    assert.equal(calls.includes('navigation'), true);
  } finally { app.unmount(); dom.window.close(); }
});
