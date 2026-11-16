// Load test for the training catalog page (curriculum tree + table view).
//
// Each VU behaves like an administrator browsing the catalog: open the tree page, expand a program
// and a course, sometimes page through a large program, search, or switch to the table view, with
// a short pause between actions.
//
// Prepare sample ids (run against the same database):
//   docker exec coretraining-db psql -U postgres -d coretraining -At -c "select json_build_object(
//     'domains',(select json_agg(id order by code) from training_domains),
//     'programs',(select json_agg(id) from (select id from training_programs order by random() limit 200) x),
//     'bigPrograms',(select json_agg(program_id) from (select program_id from courses group by program_id order by count(*) desc limit 40) x),
//     'courses',(select json_agg(id) from (select id from courses where status<>'DELETED' order by random() limit 500) x),
//     'searchTerms',json_build_array('cho vay','thanh toán','thẻ','rủi ro','Core Banking','kho quỹ','tiền gửi','eKYC'))" > /tmp/catalog-ids.json
//
// Run:
//   k6 run -e BASE_URL=http://localhost:9000 -e ADMIN_USERNAME=admin -e ADMIN_PASSWORD=*** \
//     -e IDS_FILE=/tmp/catalog-ids.json --summary-export=catalog-summary.json scripts/loadtest/catalog-tree.js
//
// STAGES (optional) overrides the ramp, e.g. -e STAGES=20x1m,50x1m

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:9000';
const ids = JSON.parse(open(__ENV.IDS_FILE || '/tmp/catalog-ids.json'));

const stages = (__ENV.STAGES || '10x1m,50x1m,100x1m')
  .split(',')
  .flatMap((step) => {
    const [target, duration] = step.split('x');
    return [{ target: Number(target), duration: '15s' }, { target: Number(target), duration }];
  });

export const options = {
  scenarios: { browse: { executor: 'ramping-vus', startVUs: 1, stages, gracefulRampDown: '10s' } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:tree}': ['p(95)<500'],
    'http_req_duration{kind:expand}': ['p(95)<500'],
    'http_req_duration{kind:search}': ['p(95)<800'],
    'http_req_duration{kind:table}': ['p(95)<800'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const pageLoad = new Trend('tree_page_load_ms', true);

const pick = (list) => list[Math.floor(Math.random() * list.length)];

function get(path, params, kind, name) {
  const query = params
    ? '?' + Object.entries(params).map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&')
    : '';
  const res = http.get(`${BASE}${path}${query}`, { tags: { kind, name: name || path } });
  check(res, { [`${name || path} 200`]: (r) => r.status === 200 });
  return res;
}

export function setup() {
  http.get(`${BASE}/api/auth/csrf`);
  const xsrf = http.cookieJar().cookiesForURL(BASE)['XSRF-TOKEN'];
  const res = http.post(
    `${BASE}/api/auth/login`,
    JSON.stringify({ username: __ENV.ADMIN_USERNAME || 'admin', password: __ENV.ADMIN_PASSWORD }),
    { headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf ? xsrf[0] : '' } },
  );
  if (res.status !== 200) throw new Error(`login failed: HTTP ${res.status}`);
  const cookies = {};
  for (const [name, values] of Object.entries(http.cookieJar().cookiesForURL(BASE))) cookies[name] = values[0];
  return { cookies };
}

export default function (data) {
  const jar = http.cookieJar();
  for (const [name, value] of Object.entries(data.cookies)) jar.set(BASE, name, value);

  group('open tree page', () => {
    const start = Date.now();
    const page = Math.floor(Math.random() * 4);
    const domains = get('/api/admin/domains/paged', { page, size: 10 }, 'tree', 'domains/paged');
    const domainIds = (domains.json('items') || []).map((d) => d.id);
    const responses = http.batch([
      ['GET', `${BASE}/api/admin/programs/by-domains?domainIds=${domainIds.join(',')}`, null, { tags: { kind: 'tree', name: 'programs/by-domains' } }],
      ['GET', `${BASE}/api/admin/options/catalog?parts=units`, null, { tags: { kind: 'tree', name: 'options/catalog?parts=units' } }],
    ]);
    responses.forEach((r) => check(r, { 'tree batch 200': (x) => x.status === 200 }));
    pageLoad.add(Date.now() - start);
  });
  sleep(0.5 + Math.random());

  group('expand program and course', () => {
    const program = Math.random() < 0.3 ? pick(ids.bigPrograms) : pick(ids.programs);
    get(`/api/admin/courses/by-program/${program}`, { page: 0, size: 6 }, 'expand', 'courses/by-program');
    if (Math.random() < 0.3)
      get(`/api/admin/courses/by-program/${program}`, { page: 1 + Math.floor(Math.random() * 10), size: 6 }, 'expand', 'courses/by-program (more)');
    sleep(0.3 + Math.random() * 0.7);
    get(`/api/admin/courses/${pick(ids.courses)}/chapters`, { view: 'outline' }, 'expand', 'chapters?view=outline');
  });
  sleep(0.5 + Math.random());

  if (Math.random() < 0.25) {
    group('search', () => {
      const term = pick(ids.searchTerms);
      const found = get('/api/admin/domains/paged', { page: 0, size: 10, search: term }, 'search', 'domains/paged?search');
      const domainIds = (found.json('items') || []).map((d) => d.id);
      if (domainIds.length)
        get('/api/admin/programs/by-domains', { domainIds: domainIds.join(','), search: term }, 'search', 'programs/by-domains?search');
    });
    sleep(0.5 + Math.random());
  }

  if (Math.random() < 0.2) {
    group('table view', () => {
      get('/api/admin/options/catalog', { parts: 'units,domains' }, 'table', 'options/catalog?parts=units,domains');
      get('/api/admin/domains/paged', { page: 0, size: 10 }, 'table', 'domains/paged');
      get('/api/admin/programs/paged', { domainId: pick(ids.domains), page: 0, size: 10 }, 'table', 'programs/paged');
      if (Math.random() < 0.5)
        get('/api/admin/courses/paged', { page: Math.floor(Math.random() * 50), size: 10 }, 'table', 'courses/paged');
    });
    sleep(0.5 + Math.random());
  }
}
