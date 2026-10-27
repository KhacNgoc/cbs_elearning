import test, { after } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><div id="app"></div>', { url: 'http://localhost' });
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'localStorage']) globalThis[key] = dom.window[key];
const vue = await import('vue');
const icons = await import('lucide-vue-next');
after(() => dom.window.close());
const { descriptor } = parse(await readFile(new URL('../src/components/lesson/LessonDiscussion.vue', import.meta.url), 'utf8'));
let code = ts.transpileModule(compileScript(descriptor, { id: 'reply-test', inlineTemplate: true }).content,
  { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) => {
  const expression = bindings.startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings;
  return `const ${expression} = modules[${JSON.stringify(module)}];`;
}).replace('export default', 'return');
const empty = { render: () => null };
const Discussion = new Function('modules', code)({ vue, 'lucide-vue-next': icons, '../ConfirmModal.vue': empty, '../StarRating.vue': empty });

test('each thread can load replies independently, shows busy/retry states, and hides the button after the final page', async () => {
  const comment = id => ({ id, user: 'u', avatar: 'U', date: '27/10/2026', content: 'root', replies: [], repliesHasMore: true });
  const state = vue.reactive({ comments: [comment(1), comment(2)], replyPageStates: {} });
  const requested = [];
  const app = vue.createApp({ render: () => vue.h(Discussion, { ...state, comment: '', currentUsername: 'u', isAdmin: false,
    'onLoad-more-replies': id => { requested.push(id); state.replyPageStates[id] = { loading: true, error: '' }; },
  }) });
  app.mount('#app');
  try {
    const buttons = () => [...document.querySelectorAll('.reply-load-more')];
    assert.equal(buttons().length, 2);
    assert.match(buttons()[0].textContent, /Xem thêm phản hồi/);
    buttons()[0].click(); await vue.nextTick();
    assert.equal(buttons()[0].disabled, true);
    assert.equal(buttons()[0].getAttribute('aria-busy'), 'true');
    assert.equal(buttons()[1].disabled, false);
    buttons()[0].click(); buttons()[1].click(); await vue.nextTick();
    assert.deepEqual(requested, [1, 2]);
    state.replyPageStates[1] = { loading: false, error: 'Không thể tải phản hồi. Vui lòng thử lại.' }; await vue.nextTick();
    assert.match(document.querySelector('[role="alert"]').textContent, /Vui lòng thử lại/);
    assert.equal(buttons()[0].disabled, false);
    buttons()[0].click(); await vue.nextTick();
    assert.deepEqual(requested, [1, 2, 1]);
    state.comments[0].repliesHasMore = false; await vue.nextTick();
    assert.equal(buttons().length, 1);
    assert.match(document.querySelector('.comment-meta').textContent, /27\/10\/2026/);
  } finally { app.unmount(); }
});
