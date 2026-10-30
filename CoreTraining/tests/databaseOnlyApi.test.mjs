import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';

async function loadApi(file) {
  const calls = [];
  const database = { source: 'database' };
  const axiosClient = Object.fromEntries(['get', 'post', 'patch', 'put', 'delete'].map(method => [method, async (...args) => {
    calls.push([method, ...args]); return { data: database };
  }]));
  const source = (await readFile(new URL(`../src/api/${file}.ts`, import.meta.url), 'utf8'))
    .replaceAll('import.meta.env', '({ VITE_USE_MOCK_API: "true" })');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText;
  const exports = {};
  const require = id => {
    if (id === './axiosClient') return { axiosClient, isMockApi: () => true, mockDelay: async () => {} };
    if (id.includes('mockData')) throw new Error('Production API must not import mock data');
    if (id === './courseApi') return { mockDbCourses: [], setMockDbCourses() {} };
    if (id === './quizApi') return { mockDbQuestions: [], setMockDbQuestions() {} };
    if (id.includes('videoAccess')) return { MAX_VIDEO_UPLOAD_BYTES: 1000 };
    return {};
  };
  new Function('require', 'exports', 'localStorage', code)(require, exports, { getItem: () => null, setItem() {} });
  return { exports, calls, database };
}

for (const [file, name, method, args, path] of [
  ['authApi', 'authApi', 'login', [{ username: 'learner', password: 'secret' }], '/api/auth/login'],
  ['userApi', 'userApi', 'getMyProfile', [], '/api/users/me/profile'],
  ['courseApi', 'courseApi', 'getAllCourses', [], '/api/courses'],
  ['quizApi', 'quizApi', 'getQuizQuestions', ['course', 'lesson'], '/api/quizzes/lessons/lesson/questions'],
  ['adminApi', 'adminApi', 'getAllUsers', [], '/api/admin/users'],
  ['leaderboardApi', 'leaderboardApi', 'getLeaderboard', [], '/api/leaderboard'],
]) {
  test(`${name}.${method} always uses database API even with obsolete mock flag`, async () => {
    const f = await loadApi(file);
    const value = await f.exports[name][method](...args);
    assert.deepEqual(value, f.database);
    assert.equal(f.calls.length, 1);
    assert.equal(f.calls[0][1], path);
  });
}

test('logout always clears the server session', async () => {
  const f = await loadApi('authApi');
  await f.exports.authApi.logout();
  assert.equal(f.calls[0]?.[1], '/api/auth/logout');
});
