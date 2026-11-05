import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import ts from 'typescript';
import axios from 'axios';

// Loads the real axios client with a fake transport so CSRF handling can be observed.
async function loadClient({ cookie = '', respond }) {
  globalThis.document = { cookie };
  globalThis.window = { location: { pathname: '/admin', replace() {} } };
  const calls = [];
  const adapter = async (config) => {
    const call = { method: config.method, url: config.url, csrf: config.headers?.['X-XSRF-TOKEN'] };
    calls.push(call);
    const { status = 200, data = {} } = await respond(call, calls);
    const response = { data, status, statusText: '', headers: {}, config, request: {} };
    if (status >= 400) throw new axios.AxiosError('failed', 'ERR', config, {}, response);
    return response;
  };
  axios.defaults.adapter = adapter;
  let code = ts.transpileModule(await readFile(new URL('../src/api/axiosClient.ts', import.meta.url), 'utf8'),
    { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
  code = code.replace(/import\s+([\s\S]*?)\s+from\s+["']([^"']+)["'];/g, (_, b, m) => `const ${b}=modules[${JSON.stringify(m)}];`)
    .replaceAll('import.meta.env', '({})').replace(/export const /g, 'const ');
  const { axiosClient } = new Function('modules', `${code}\nreturn { axiosClient };`)({
    axios, '../utils/authSession': { redirectToLogin() {} },
  });
  axiosClient.defaults.adapter = adapter;
  return { axiosClient, calls };
}
const quiet = () => { const error = console.error; console.error = () => {}; return () => { console.error = error; }; };

test('writes reuse the CSRF cookie without asking the server first', async () => {
  const { axiosClient, calls } = await loadClient({ cookie: 'a=1; XSRF-TOKEN=from-cookie', respond: () => ({}) });
  await axiosClient.post('/api/things', {});
  await axiosClient.get('/api/things');
  assert.deepEqual(calls.map(c => c.url), ['/api/things', '/api/things']);
  assert.equal(calls[0].csrf, 'from-cookie');
  assert.equal(calls[1].csrf, undefined);
});

test('without a cookie, concurrent writes share one token request', async () => {
  const { axiosClient, calls } = await loadClient({
    respond: (call) => call.url.endsWith('/api/auth/csrf') ? { data: { token: 'fetched' } } : {},
  });
  await Promise.all([axiosClient.post('/api/a', {}), axiosClient.put('/api/b', {})]);
  assert.equal(calls.filter(c => c.url.endsWith('/api/auth/csrf')).length, 1);
  assert.deepEqual(calls.filter(c => !c.url.endsWith('/csrf')).map(c => c.csrf), ['fetched', 'fetched']);
});

test('a rejected token is refreshed and the write retried exactly once', async () => {
  const restore = quiet();
  try {
    const { axiosClient, calls } = await loadClient({
      cookie: 'XSRF-TOKEN=stale',
      respond: (call) => call.url.endsWith('/api/auth/csrf') ? { data: { token: 'fresh' } }
        : call.csrf === 'fresh' && call.url === '/api/ok' ? {} : { status: 403 },
    });
    await axiosClient.post('/api/ok', {});
    assert.deepEqual(calls.map(c => [c.url, c.csrf ?? null]),
      [['/api/ok', 'stale'], ['/api/auth/csrf', null], ['/api/ok', 'fresh']]);
    calls.length = 0;
    await assert.rejects(axiosClient.post('/api/denied', {}), (error) => error.response.status === 403);
    assert.equal(calls.filter(c => c.url === '/api/denied').length, 2);
  } finally { restore(); }
});
