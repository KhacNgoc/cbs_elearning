import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { JSDOM } from 'jsdom';
import { parse, compileScript } from '@vue/compiler-sfc';
import ts from 'typescript';

const dom = new JSDOM('<!doctype html><html><body><div id="app"></div></body></html>', { pretendToBeVisual: true });
for (const key of ['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'Event', 'KeyboardEvent']) {
  globalThis[key] = dom.window[key];
}
globalThis.requestAnimationFrame = dom.window.requestAnimationFrame.bind(dom.window);
globalThis.cancelAnimationFrame = dom.window.cancelAnimationFrame.bind(dom.window);
globalThis.getComputedStyle = dom.window.getComputedStyle.bind(dom.window);
const vue = await import('vue');
const { createApp, nextTick, reactive } = vue;
const transpile = source => ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 },
}).outputText;
async function utility(path) {
  let code = transpile(await readFile(new URL(path, import.meta.url), 'utf8'));
  code = code.replace(/from ["']vue["']/g, `from '${import.meta.resolve('vue')}'`);
  return import(`data:text/javascript;base64,${Buffer.from(code).toString('base64')}`);
}
async function component(path, modules) {
  const source = await readFile(new URL(path, import.meta.url), 'utf8');
  const { descriptor } = parse(source);
  let code = transpile(compileScript(descriptor, { id: path, inlineTemplate: true }).content);
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, bindings, module) => {
    const target = bindings.trim().startsWith('{') ? bindings.replace(/\bas\b/g, ':') : bindings;
    const access = bindings.trim().startsWith('{') ? '' : '.default';
    return `const ${target} = modules[${JSON.stringify(module)}]${access};`;
  }).replace('export default', 'return');
  return { default: new Function('modules', code)({ vue, ...modules }) };
}
const blank = { default: { render: () => null } };
const icons = Object.fromEntries(['Search', 'Plus', 'RefreshCw', 'Download', 'Upload', 'ShieldCheck', 'X', 'ChevronLeft', 'ChevronRight'].map(name => [name, blank.default]));
const common = { 'lucide-vue-next': icons };
const modal = await component('../src/components/AdminModal.vue', common);
const pagination = await component('../src/components/TablePagination.vue', common);
const toggle = await component('../src/components/ToggleSwitch.vue', {});
const paging = await utility('../src/composables/useTablePagination.ts');
const tree = await utility('../src/utils/organizationTree.ts');
const route = reactive({ meta: { accessSection: 'groups' }, query: {} });
const people = Array.from({ length: 13 }, (_, i) => ({
  id: i + 1, username: `user${i + 1}`, fullName: `Học viên ${i + 1}`, unitName: 'Đơn vị A', status: 'ACTIVE', selectable: true,
  employeeId: i === 0 ? 'NV0001' : '',
}));
const committed = new Set([1]);
const writes = [];
const group = () => ({ id: 'G1', name: 'Nhóm một', unitId: 'A', mandatoryCourseIds: [], allowedActions: ['read', 'update'], memberCount: committed.size });
const axiosClient = {
  async get(url, { params = {} } = {}) {
    if (url.includes('/options/')) return { data: { units: [{ id: 'A', name: 'Đơn vị A', active: true }], courses: [] } };
    if (url === '/api/admin/access/groups') return { data: [group()] };
    if (url === '/api/admin/users') return { data: [] };
    if (url.endsWith('/members')) {
      const rows = people.filter(p => (!params.membersOnly || committed.has(p.id)) && p.username.includes(params.query || ''));
      return { data: { items: rows.slice((params.page - 1) * params.size, params.page * params.size).map(p => ({ ...p, member: committed.has(p.id) })),
        total: rows.length, page: params.page, pageCount: Math.max(1, Math.ceil(rows.length / params.size)), memberCount: committed.size } };
    }
    throw Error(`Unexpected GET ${url}`);
  },
  async put(url, body) {
    writes.push({ url, body });
    body.addUserIds.forEach(id => committed.add(id));
    body.removeUserIds.forEach(id => committed.delete(id));
  },
};
const view = await component('../src/views/AccessAdmin.vue', {
  ...common,
  '../components/AdminModal.vue': modal,
  '../components/TablePagination.vue': pagination,
  '../components/ToggleSwitch.vue': toggle,
  '../components/LearnerDeadlineEditor.vue': blank,
  '../components/ConfirmModal.vue': blank,
  '../components/OrganizationTreeTable.vue': blank,
  '../components/OrganizationTreeSelect.vue': blank,
  '../composables/useTablePagination': paging,
  '../utils/organizationTree': tree,
  'vue-router': { useRoute: () => route, useRouter: () => ({ replace() {} }) },
  '../api/axiosClient': { axiosClient },
  '../api/adminApi': { adminApi: {} },
  '../utils/toast': { showToast() {} },
  '../stores/userStore': { useUserStore: () => ({ systemAdmin: true, can: () => true, refreshAccess: async () => {} }) },
});
const settle = async (ms = 30) => { await new Promise(resolve => setTimeout(resolve, ms)); await nextTick(); };
const clickText = text => {
  const button = [...document.querySelectorAll('button')].find(button => button.textContent.trim() === text);
  assert.ok(button, `Button ${text} exists`);
  button.click();
};
const select = id => {
  const input = document.querySelector(`input[aria-label="Thành viên nhóm: Học viên ${id}"]`);
  assert.ok(input);
  input.click();
};

test('membership choices survive page and search changes, then save only the current group delta', async () => {
  const app = createApp(view.default);
  app.mount('#app');
  try {
    await settle();
    clickText('Thành viên'); await settle();
    assert.equal(document.querySelectorAll('.group-members-table tbody input').length, 1);
    assert.ok([...document.querySelectorAll('.group-members-table th')].some(th => th.textContent === 'Mã nhân viên'));
    const firstRow = document.querySelector('.group-members-table tbody tr');
    assert.equal(firstRow.children[2].textContent, 'NV0001');
    assert.ok(!firstRow.children[1].textContent.includes('NV0001'));
    clickText('Thêm thành viên'); await settle();
    assert.equal(document.querySelectorAll('.group-members-table tbody input').length, 10);
    assert.equal(document.querySelectorAll('.group-members-table tbody tr')[1].children[2].textContent, 'Chưa cập nhật');
    select(2);
    document.querySelector('[aria-label="Phân trang học viên"] button[aria-label="Trang sau"]').click();
    await settle(); select(11);
    const search = document.querySelector('[aria-label="Tìm học viên"]');
    search.value = 'user13'; search.dispatchEvent(new Event('input', { bubbles: true }));
    await settle(300); select(13);
    search.value = ''; search.dispatchEvent(new Event('input', { bubbles: true }));
    await settle(300);
    assert.equal(document.querySelector('input[aria-label="Thành viên nhóm: Học viên 2"]').checked, true);
    clickText('Lưu thành viên'); await settle();
    assert.deepEqual(writes[0], { url: '/api/admin/groups/G1/members', body: { addUserIds: [2, 11, 13], removeUserIds: [] } });
    clickText('Thành viên'); await settle(); select(1); await nextTick();
    clickText('Lưu thành viên'); await settle();
    assert.deepEqual(writes[1].body, { addUserIds: [], removeUserIds: [1] });
    route.meta.accessSection = 'users'; await settle();
    clickText('Thêm tài khoản'); await settle();
    assert.ok(document.querySelector('input[placeholder="Ví dụ: NV0001"]'));
    assert.equal(document.querySelector('select[aria-label="Nhóm học viên"]'), null);
    assert.ok(document.body.textContent.includes('Thêm hoặc bỏ nhóm cho tài khoản tại menu Nhóm học viên'));
  } finally { app.unmount(); await settle(60); dom.window.close(); }
});
