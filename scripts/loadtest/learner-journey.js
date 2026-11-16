// Load test for the learner side, on the accounts of seed-learning-activity.sql.
//
// Each VU signs in as its own learner (hv00001, hv00002, ...) and repeats a study session: open the
// dashboard, page the course list, open a course outline, resume the current lesson with its comments
// and notes, save progress, and take the lesson quiz when it has one. Between pages it pauses like a
// person reading. Profile, leaderboard and flashcard pages are opened now and then.
//
// Run (BASE_URL is the backend; through the frontend's nginx use http://localhost:5173):
//   k6 run -e BASE_URL=http://localhost:9000 -e PASSWORD=Test@123456 \
//     --summary-export=learner-summary.json scripts/loadtest/learner-journey.js
//
// Options:
//   STAGES   ramp, default 20x2m,100x3m,200x3m (<VUs>x<hold>)
//   LEARNERS number of seeded learners to spread VUs over, default 10000
//   WRITES   0 to skip progress saves and quiz submissions (read-only run), default 1
//   THINK    seconds of reading time between pages, default 3

import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:9000';
const PASSWORD = __ENV.PASSWORD || 'Test@123456';
const LEARNERS = Number(__ENV.LEARNERS || 10000);
const WRITES = __ENV.WRITES !== '0';
const THINK = Number(__ENV.THINK || 3);

const stages = (__ENV.STAGES || '20x2m,100x3m,200x3m')
  .split(',')
  .flatMap((step) => {
    const [target, duration] = step.split('x');
    return [{ target: Number(target), duration: '30s' }, { target: Number(target), duration }];
  });

export const options = {
  // Each VU stays signed in across iterations, like a learner keeping the tab open.
  noCookiesReset: true,
  scenarios: { study: { executor: 'ramping-vus', startVUs: 1, stages, gracefulRampDown: '20s' } },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    'http_req_duration{kind:dashboard}': ['p(95)<800'],
    'http_req_duration{kind:course}': ['p(95)<500'],
    'http_req_duration{kind:lesson}': ['p(95)<500'],
    'http_req_duration{kind:write}': ['p(95)<800'],
    'http_req_duration{kind:quiz}': ['p(95)<800'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const dashboardLoad = new Trend('dashboard_page_load_ms', true);
const loginFailures = new Counter('login_failures');

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const think = () => sleep(THINK * (0.5 + Math.random()));

let signedIn = false;
let xsrf = '';

function headers() {
  return { 'Content-Type': 'application/json', 'X-XSRF-TOKEN': xsrf };
}

// Like the web client: the token comes from /api/auth/csrf, and login rotates it, so it is fetched
// again after login and once more when a write is rejected with 403.
function fetchXsrf() {
  const res = http.get(`${BASE}/api/auth/csrf`, { tags: { kind: 'auth', name: 'csrf' } });
  xsrf = res.status === 200 ? res.json('token') || '' : '';
}

function get(path, kind, name) {
  const res = http.get(`${BASE}${path}`, { tags: { kind, name: name || path } });
  check(res, { [`${name || path} 200`]: (r) => r.status === 200 });
  return res;
}

function post(path, body, kind, name) {
  const send = () => http.post(`${BASE}${path}`, body === undefined ? null : JSON.stringify(body),
    { headers: headers(), tags: { kind, name: name || path } });
  let res = send();
  if (res.status === 403) {
    fetchXsrf();
    res = send();
  }
  check(res, { [`${name || path} 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  return res;
}

function signIn() {
  const username = `hv${String(((__VU - 1) % LEARNERS) + 1).padStart(5, '0')}`;
  fetchXsrf();
  const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ username, password: PASSWORD }),
    { headers: headers(), tags: { kind: 'auth', name: 'login' } });
  if (res.status !== 200) {
    loginFailures.add(1);
    return false;
  }
  fetchXsrf();
  return true;
}

export default function () {
  if (!signedIn) {
    signedIn = signIn();
    if (!signedIn) {
      sleep(5);
      return;
    }
  }

  let courses = [];
  group('dashboard', () => {
    const start = Date.now();
    const responses = http.batch([
      ['GET', `${BASE}/api/auth/session`, null, { tags: { kind: 'dashboard', name: 'session' } }],
      ['GET', `${BASE}/api/users/me`, null, { tags: { kind: 'dashboard', name: 'me' } }],
      ['GET', `${BASE}/api/notifications?size=6`, null, { tags: { kind: 'dashboard', name: 'notifications' } }],
      ['GET', `${BASE}/api/users/me/learning/dashboard/summary?tab=mine`, null, { tags: { kind: 'dashboard', name: 'summary' } }],
      ['GET', `${BASE}/api/users/me/learning/dashboard/courses?tab=mine&page=1&size=6&sort=deadline`, null,
        { tags: { kind: 'dashboard', name: 'courses' } }],
    ]);
    responses.forEach((r, i) => check(r, { [`dashboard ${i} 200`]: (x) => x.status === 200 }));
    dashboardLoad.add(Date.now() - start);
    if (responses[4].status === 200) courses = responses[4].json('items') || [];
  });
  think();

  if (Math.random() < 0.3) {
    get('/api/users/me/learning/dashboard/courses?tab=discover&page=1&size=6&sort=name', 'dashboard', 'discover');
    think();
  }

  if (!courses.length) return;
  const courseId = pick(courses).course.id;

  let lessonId = null;
  group('course', () => {
    get(`/api/courses/${courseId}/outline`, 'course', 'outline');
    get(`/api/courses/${courseId}/rating`, 'course', 'rating');
    const resume = get(`/api/users/me/learning/dashboard/courses/${courseId}/resume`, 'course', 'resume');
    if (resume.status === 200 && resume.body) lessonId = resume.json('lessonId');
  });
  think();
  if (!lessonId) return;

  let lesson = null;
  group('lesson', () => {
    const res = get(`/api/lessons/${lessonId}`, 'lesson', 'lesson');
    if (res.status === 200) lesson = res.json();
    get(`/api/lessons/${lessonId}/comments`, 'lesson', 'comments');
    get(`/api/lessons/${lessonId}/notes`, 'lesson', 'notes');
  });
  think();

  if (WRITES && lesson) {
    group('progress', () => {
      const video = !!(lesson.content && lesson.content.videoUrl) || !!lesson.videoUrl;
      post(`/api/users/me/lessons/${lessonId}/progress`, video
        ? { contentType: 'VIDEO', lastPositionSeconds: 120 + Math.random() * 300, watchedSeconds: 300, durationSeconds: 600 }
        : { contentType: 'TEXT', textCompleted: true }, 'write', 'progress');
    });
    think();
  }

  if (lesson && lesson.hasQuiz) {
    group('quiz', () => {
      const questions = get(`/api/quizzes/lessons/${lessonId}/questions`, 'quiz', 'quiz questions');
      const session = post(`/api/quizzes/lessons/${lessonId}/start`, undefined, 'quiz', 'quiz start');
      if (!WRITES || questions.status !== 200 || session.status !== 200) return;
      sleep(THINK * 3);
      const selectedAnswers = {};
      for (const q of questions.json()) {
        const indexes = q.optionIndexes || q.options.map((_, i) => i);
        selectedAnswers[q.id] = [pick(indexes)];
      }
      post(`/api/quizzes/lessons/${lessonId}/submit`,
        { selectedAnswers, timeElapsed: 60, sessionId: session.json('sessionId') }, 'quiz', 'quiz submit');
    });
    think();
  }

  if (Math.random() < 0.15) {
    group('profile and leaderboard', () => {
      get('/api/users/me/assessment-statistics', 'profile', 'assessment statistics');
      get('/api/users/me/awards', 'profile', 'awards');
      get('/api/leaderboard/page', 'profile', 'leaderboard');
    });
    think();
  }
  if (Math.random() < 0.1) {
    get('/api/users/me/flashcards/library', 'profile', 'flashcard library');
    think();
  }
}
