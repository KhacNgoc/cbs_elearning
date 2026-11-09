// NF-P01/NF-P03 traffic mix. Runs in the grafana/k6 image on the cbstest network, against the test backend only.
import http from 'k6/http'
import { check, sleep } from 'k6'
import { Rate, Trend } from 'k6/metrics'

const BASE = __ENV.BASE_URL || 'http://test-backend:8080'
const LESSON = __ENV.LESSON_ID
const COURSE = __ENV.COURSE_ID
const PASSWORD = __ENV.PASSWORD
const PREFIX = __ENV.USER_PREFIX
const USERS = Number(__ENV.USER_COUNT)
const STAGES = JSON.parse(__ENV.STAGES)

const unexpected = new Rate('unexpected_errors')
const byKind = {}
for (const k of ['read', 'lesson', 'progress', 'quiz', 'notifications']) byKind[k] = new Trend(`latency_${k}`, true)

export const options = {
  scenarios: { mix: { executor: 'ramping-vus', startVUs: 0, stages: STAGES, gracefulRampDown: '20s' } },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
}

function login(n) {
  const jar = http.cookieJar()
  http.get(`${BASE}/api/auth/csrf`, { tags: { name: 'csrf' } })
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ username: `${PREFIX}${n}`, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': jar.cookiesForURL(BASE)['XSRF-TOKEN']?.[0] }, tags: { name: 'login' },
  })
  return res.status === 200
}
const headers = () => ({ 'Content-Type': 'application/json', 'X-XSRF-TOKEN': http.cookieJar().cookiesForURL(BASE)['XSRF-TOKEN']?.[0] })
function observe(kind, res) {
  byKind[kind].add(res.timings.duration)
  unexpected.add(res.status === 0 || res.status >= 500)
  check(res, { [`${kind} ok`]: (r) => r.status < 400 })
}

let ready = false
export default function () {
  if (!ready) {
    ready = login((__VU - 1) % USERS)
    if (!ready) { unexpected.add(true); sleep(5); return }
  }
  const dice = Math.random()
  if (dice < 0.3) {
    observe('read', http.get(`${BASE}/api/users/me/learning/dashboard/summary?tab=mine`, { tags: { name: 'dashboard' } }))
    observe('read', http.get(`${BASE}/api/users/me/learning/dashboard/courses?tab=mine&page=1&size=6`, { tags: { name: 'catalog' } }))
  } else if (dice < 0.6) {
    observe('lesson', http.get(`${BASE}/api/lessons/${LESSON}`, { tags: { name: 'lesson' } }))
    observe('lesson', http.get(`${BASE}/api/courses/${COURSE}/outline`, { tags: { name: 'outline' } }))
  } else if (dice < 0.8) {
    const seconds = 10 + Math.random() * 80
    observe('progress', http.post(`${BASE}/api/users/me/lessons/${LESSON}/progress`, JSON.stringify({ contentType: 'VIDEO', lastPositionSeconds: seconds, watchedSeconds: seconds, durationSeconds: 100 }), { headers: headers(), tags: { name: 'progress' } }))
  } else if (dice < 0.9) {
    observe('quiz', http.get(`${BASE}/api/quizzes/lessons/${LESSON}/questions`, { tags: { name: 'quiz_questions' } }))
    observe('quiz', http.post(`${BASE}/api/quizzes/lessons/${LESSON}/submit`, JSON.stringify({ selectedAnswers: {} }), { headers: headers(), tags: { name: 'quiz_submit' } }))
  } else {
    observe('notifications', http.get(`${BASE}/api/notifications?size=6`, { tags: { name: 'notifications' } }))
  }
  sleep(3 + Math.random() * 7)
}

export function handleSummary(data) {
  const out = {}
  for (const [k, v] of Object.entries(data.metrics)) if (k.startsWith('latency_') || k === 'unexpected_errors' || k === 'http_reqs' || k === 'http_req_duration' || k === 'vus_max') out[k] = v.values
  return { stdout: JSON.stringify(out) + '\n' }
}
