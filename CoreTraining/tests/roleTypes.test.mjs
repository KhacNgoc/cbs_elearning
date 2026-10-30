import test, { after } from 'node:test';
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
after(() => dom.window.close());
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
const icons = Object.fromEntries(['Search', 'Plus', 'RefreshCw', 'Download', 'Upload', 'ShieldCheck', 'Users', 'X', 'ChevronLeft', 'ChevronRight'].map(name => [name, blank.default]));
const common = { 'lucide-vue-next': icons };
const modal = await component('../src/components/AdminModal.vue', common);
const pagination = await component('../src/components/TablePagination.vue', common);
const toggle = await component('../src/components/ToggleSwitch.vue', {});
const paging = await utility('../src/composables/useTablePagination.ts');
const fixture = (id, learnerRole) => ({
  id, code: id, name: learnerRole ? 'Học viên dùng chung' : 'Quản lý nội dung',
  description: '', active: true, protectedRole: false, learnerRole, learningEnabled: true,
  assignedCoursesOnly: false, version: 0, permissions: learnerRole ? [] : ['courses:read'],
  unitIds: learnerRole ? [] : ['A'], userIds: [],
});
const roles = [fixture('LEARNER', true), fixture('MANAGER', false)];
const saved = [];
const rolePageReads = [];
const memberReads = [], memberWrites = [];
const people = Array.from({length:13},(_,i)=>({id:i+1,username:`person${i+1}`,fullName:`Person ${i+1}`,roles:[],unitId:"A",member:i<12,selectable:true}));
const dependencies = {
  ...common,
  '../components/AdminModal.vue': modal,
  '../components/TablePagination.vue': pagination,
  '../components/ToggleSwitch.vue': toggle,
  '../components/ConfirmModal.vue': blank,
  '../components/OrganizationCheckTree.vue': blank,
  '../composables/useTablePagination': paging,
  '../api/roleApi': { roleApi: {
    list: async () => roles, options: async () => roles, pages: async () => [], units: async () => [],
    save: async role => { saved.push(role); return role; },
  } },
  '../api/adminListApi': { adminListApi: {
    roles: async params => { rolePageReads.push({ ...params }); return { items: roles.filter(r => !params.managementOnly || !r.learnerRole).map(r=>({...r,memberCount:0})), total: params.managementOnly ? 1 : 2, page: 1, pageCount: 1 }; },
    roleMembers: async params => { memberReads.push({...params}); const rows = people.filter(m => m.username.includes(params.query)); return { items: rows.slice((params.page-1)*params.size,params.page*params.size), total: rows.length, page: params.page, pageCount: Math.max(1, Math.ceil(rows.length/params.size)) }; },
    assignRoleChanges: async (roleId, changes) => { memberWrites.push({roleId,changes}); },
  } },
  '../api/axiosClient': { axiosClient: { get: async () => ({ data: { units: [] } }) } },
  '../stores/userStore': { useUserStore: () => ({ systemAdmin: true, canLearn: true, can: () => true, refreshAccess: async () => {} }) },
  '../utils/toast': { showToast() {} },
  'vue-router': { useRoute: () => ({ query: {} }), useRouter: () => ({ push() {} }) },
};
const roleView = await component('../src/views/RoleAdmin.vue', dependencies);
const permissionView = await component('../src/views/PermissionAdmin.vue', dependencies);
const settle = async () => { await new Promise(resolve => setTimeout(resolve, 60)); await nextTick(); };
const click = (scope, text) => {
  const button = [...scope.querySelectorAll('button')].find(b => b.textContent.trim() === text);
  assert.ok(button, text);
  button.click();
};

test('learner role hides detailed permissions and saves the learner toggle; management roles keep configuration', async () => {
  let app = createApp(roleView.default);
  app.mount('#app');
  try {
    await settle();
    assert.equal(rolePageReads.length, 1);
    assert.equal(rolePageReads[0].size, 10);
    const rows = document.querySelectorAll('.admin-data-table tbody tr');
    assert.ok(!rows[0].textContent.includes('Phân quyền'));
    assert.ok(rows[0].textContent.includes('Theo đơn vị của tài khoản'));
    assert.ok(rows[1].textContent.includes('Phân quyền'));
    click(document, 'Thêm role'); await settle();
    assert.equal(document.querySelector('[aria-label="Là học viên"]').getAttribute('aria-checked'), 'true');
    assert.equal(document.querySelector('[aria-label="Cho phép tham gia học tập"]'), null);
    document.querySelector('[aria-label="Là học viên"]').click(); await nextTick();
    assert.ok(document.querySelector('[aria-label="Cho phép tham gia học tập"]'));
    document.querySelector('[aria-label="Là học viên"]').click(); await nextTick();
    const form = document.querySelector('.admin-modal-dialog');
    const nameInput = form.querySelector('input');
    nameInput.value = 'Học viên mới';
    nameInput.dispatchEvent(new Event('input', { bubbles: true }));
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await settle();
    assert.equal(saved[0].learnerRole, true);
    assert.equal(saved[0].learningEnabled, true);
    click(rows[1], 'Sửa'); await settle();
    document.querySelector('[aria-label="Là học viên"]').click(); await nextTick();
    assert.ok(document.body.textContent.includes('toàn bộ quyền quản trị và phạm vi đơn vị của role này sẽ được gỡ'));
    app.unmount(); await settle();
    app = createApp(permissionView.default);
    app.mount('#app'); await settle();
    const choices = [...document.querySelectorAll('.role-choice')];
    assert.equal(choices.length, 1);
    assert.ok(choices[0].textContent.includes('Quản lý nội dung'));
    assert.ok(document.body.textContent.includes('Phạm vi quản trị dữ liệu'));
  } finally { app.unmount(); await settle(); }
});


test('role member choices are lazy, bounded and retain edit deltas across pages and searches', async () => {
  const app = createApp(roleView.default); app.mount('#app');
  try {
    await settle(); assert.equal(memberReads.length, 0);
    click(document.querySelector('.admin-data-table tbody tr'), 'Gán tài khoản'); await settle();
    assert.equal(memberReads.length, 1); assert.equal(memberReads[0].size, 10);
    document.querySelector('input[aria-label="Gán Person 2"]').click(); await nextTick();
    document.querySelector('[aria-label="Phân trang tài khoản"] button[aria-label="Trang sau"]').click(); await settle();
    document.querySelector('input[aria-label="Gán Person 13"]').click(); await nextTick();
    const search = document.querySelector('input[placeholder="Tìm họ tên hoặc tài khoản…"]');
    search.value='person11'; search.dispatchEvent(new Event('input',{bubbles:true})); await new Promise(r=>setTimeout(r,300)); await settle();
    document.querySelector('input[aria-label="Gán Person 11"]').click(); await nextTick();
    search.value=''; search.dispatchEvent(new Event('input',{bubbles:true})); await new Promise(r=>setTimeout(r,300)); await settle();
    assert.equal(document.querySelector('input[aria-label="Gán Person 2"]').checked,false);
    document.querySelector('.admin-modal-dialog').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})); await settle();
    assert.deepEqual(memberWrites[0],{roleId:'LEARNER',changes:{addUserIds:[13],removeUserIds:[2,11],version:0}});
  } finally { app.unmount(); await settle(); }
});
