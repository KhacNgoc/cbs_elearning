// Load test đo số request đồng thời mà chatbot và sinh đề chịu được.
// Cách chạy và đọc kết quả: xem scripts/loadtest/README.md.
import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Rate, Trend } from "k6/metrics";

const BASE_URL = (__ENV.BASE_URL || "http://localhost:8080").replace(/\/$/, "");
const SCENARIO = __ENV.SCENARIO || "chat"; // chat | quiz | mixed
const COURSE_ID = __ENV.COURSE_ID || "";

// Mỗi bậc "<số VU>x<thời gian giữ>": mỗi VU là một người dùng gửi liên tục,
// nên số VU chính là số request đồng thời tại bậc đó.
const CHAT_STEPS = __ENV.CHAT_STEPS || "1x2m,2x2m,4x2m,8x2m,12x2m,16x2m";
const QUIZ_STEPS = __ENV.QUIZ_STEPS || "1x5m,2x5m,3x5m,5x5m";

const CHAT_TIMEOUT = __ENV.CHAT_TIMEOUT || "120s";
const CHAT_TURNS_PER_CONVERSATION = Number(__ENV.CHAT_TURNS || 4);
const THINK_TIME_SECONDS = Number(__ENV.THINK_TIME || 0);

const QUIZ_SINGLE = Number(__ENV.QUIZ_SINGLE || 5);
const QUIZ_MULTIPLE = Number(__ENV.QUIZ_MULTIPLE || 5);
const QUIZ_POLL_INTERVAL_SECONDS = Number(__ENV.QUIZ_POLL_INTERVAL || 1.5);
const QUIZ_MAX_WAIT_SECONDS = Number(__ENV.QUIZ_MAX_WAIT || 330);

const DEFAULT_QUESTIONS = [
  "Tóm tắt các nội dung chính của khóa học này",
  "Quy trình mở tài khoản cho khách hàng cá nhân gồm những bước nào?",
  "Những lưu ý quan trọng khi thẩm định hồ sơ là gì?",
  "Giải thích lại ý vừa rồi một cách ngắn gọn hơn",
  "Có ví dụ minh họa nào cho nội dung này không?",
  "Các quy định liên quan đến hạn mức giao dịch là gì?",
  "Điểm khác nhau giữa hai sản phẩm được nêu trong tài liệu là gì?",
  "Khi nào cần báo cáo lên cấp quản lý?",
];
const QUESTIONS = __ENV.QUESTIONS_FILE
  ? JSON.parse(open(__ENV.QUESTIONS_FILE))
  : DEFAULT_QUESTIONS;

const chatDuration = new Trend("chat_duration", true);
const chatSuccess = new Rate("chat_success");
const chatErrors = new Counter("chat_errors");
const quizJobDuration = new Trend("quiz_job_duration", true);
const quizJobSuccess = new Rate("quiz_job_success");
const quizAcceptedRatio = new Trend("quiz_accepted_ratio");
const quizErrors = new Counter("quiz_errors");

function parseSteps(spec) {
  const stages = [];
  for (const step of spec.split(",")) {
    const [target, hold] = step.trim().split("x");
    // Tăng nhanh lên bậc mới rồi giữ nguyên để đo ở mức đồng thời cố định.
    stages.push({ target: Number(target), duration: "10s" });
    stages.push({ target: Number(target), duration: hold });
  }
  return stages;
}

const scenarios = {};
if (SCENARIO === "chat" || SCENARIO === "mixed") {
  scenarios.chat = {
    executor: "ramping-vus",
    exec: "chat",
    startVUs: 0,
    stages: parseSteps(CHAT_STEPS),
    gracefulRampDown: "2m",
    gracefulStop: "2m",
  };
}
if (SCENARIO === "quiz" || SCENARIO === "mixed") {
  scenarios.quiz = {
    executor: "ramping-vus",
    exec: "quiz",
    startVUs: 0,
    stages: parseSteps(QUIZ_STEPS),
    gracefulRampDown: "6m",
    gracefulStop: "6m",
  };
}

export const options = {
  scenarios,
  // Chỉ để đánh dấu đạt/không đạt trong báo cáo, không dừng test giữa chừng.
  thresholds: {
    chat_success: ["rate>0.95"],
    chat_duration: ["p(95)<50000"],
    quiz_job_success: ["rate>0.9"],
  },
  summaryTrendStats: ["avg", "min", "med", "p(90)", "p(95)", "max"],
};

// Mỗi VU đăng nhập một lần ở lượt đầu. Cookie jar của k6 làm mất access_token
// khi server xóa cookie XSRF-TOKEN (Max-Age=0), nên tự giữ token và gửi kèm.
let accessToken = null;
let conversationId = null;
let turnsInConversation = 0;
let loggedChatErrors = 0;

function login(username, password) {
  const csrf = http.get(`${BASE_URL}/api/auth/csrf`, { tags: { name: "csrf" } });
  if (!check(csrf, { "lấy được CSRF token": (r) => r.status === 200 })) {
    throw new Error(`GET /api/auth/csrf trả về ${csrf.status}`);
  }
  const response = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username, password }),
    {
      headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": csrf.json("token") },
      tags: { name: "login" },
    },
  );
  if (!check(response, { "đăng nhập thành công": (r) => r.status === 200 })) {
    throw new Error(`Đăng nhập ${username} thất bại: HTTP ${response.status}`);
  }
  accessToken = response.cookies.access_token[0].value;
}

function authParams(params) {
  return { ...params, cookies: { access_token: accessToken } };
}

// Token CSRF đổi sau mỗi request ghi, nên lấy token mới trước mỗi POST như frontend.
function jsonHeaders() {
  const csrfToken = http
    .get(`${BASE_URL}/api/auth/csrf`, authParams({ tags: { name: "csrf" } }))
    .json("token");
  return { "Content-Type": "application/json", "X-XSRF-TOKEN": csrfToken };
}

function requireEnv(name) {
  const value = __ENV[name];
  if (!value) throw new Error(`Thiếu biến môi trường ${name}`);
  return value;
}

export function chat() {
  if (!accessToken) login(requireEnv("CHAT_USERNAME"), requireEnv("CHAT_PASSWORD"));

  // Hội thoại nhiều lượt giống thực tế: có lịch sử nên bước viết lại câu hỏi
  // và tóm tắt hội thoại cũng chạy.
  if (turnsInConversation >= CHAT_TURNS_PER_CONVERSATION) {
    conversationId = null;
    turnsInConversation = 0;
  }
  const question = QUESTIONS[(__VU * 7 + __ITER) % QUESTIONS.length];
  const body = { question };
  if (COURSE_ID) body.courseId = COURSE_ID;
  if (conversationId) body.conversationId = conversationId;

  const response = http.post(`${BASE_URL}/api/rag/chat`, JSON.stringify(body), authParams({
    headers: jsonHeaders(),
    timeout: CHAT_TIMEOUT,
    tags: { name: "rag_chat" },
  }));
  const ok = response.status === 200 && Boolean(response.json("answer"));
  chatSuccess.add(ok);
  chatDuration.add(response.timings.duration, { status: String(response.status) });
  if (ok) {
    conversationId = response.json("conversationId");
    turnsInConversation += 1;
  } else {
    chatErrors.add(1, { status: String(response.status) });
    if (loggedChatErrors < 3) {
      loggedChatErrors += 1;
      console.error(`chat HTTP ${response.status}: ${String(response.body).slice(0, 300)}`);
    }
    conversationId = null;
    turnsInConversation = 0;
    // Lỗi trả về ngay (403, 429...) không được làm VU gửi dồn dập hàng nghìn request.
    sleep(1);
  }
  check(response, { "chat trả về 200": (r) => r.status === 200 });
  if (THINK_TIME_SECONDS > 0) sleep(THINK_TIME_SECONDS);
}

export function quiz() {
  if (!accessToken) login(requireEnv("ADMIN_USERNAME"), requireEnv("ADMIN_PASSWORD"));
  const courseId = requireEnv("COURSE_ID");
  const requested = QUIZ_SINGLE + QUIZ_MULTIPLE;
  const body = {
    courseId,
    singleChoiceCount: QUIZ_SINGLE,
    multipleChoiceCount: QUIZ_MULTIPLE,
    optionCount: 4,
  };
  if (__ENV.LESSON_ID) body.lessonId = __ENV.LESSON_ID;

  const startedAt = Date.now();
  const created = http.post(`${BASE_URL}/api/admin/quizzes/generate/jobs`, JSON.stringify(body), authParams({
    headers: jsonHeaders(),
    timeout: "30s",
    tags: { name: "quiz_job_create" },
  }));
  if (!check(created, { "tạo job trả về 202": (r) => r.status === 202 })) {
    quizJobSuccess.add(false);
    quizErrors.add(1, { status: String(created.status), stage: "create" });
    sleep(5);
    return;
  }

  const jobId = created.json("jobId");
  let job = created.json();
  while (job.status !== "completed" && job.status !== "failed") {
    if ((Date.now() - startedAt) / 1000 > QUIZ_MAX_WAIT_SECONDS) {
      job = { status: "failed", errorStatus: "client_timeout" };
      break;
    }
    sleep(QUIZ_POLL_INTERVAL_SECONDS);
    const polled = http.get(`${BASE_URL}/api/admin/quizzes/generate/jobs/${jobId}`, authParams({
      timeout: "30s",
      tags: { name: "quiz_job_poll" },
    }));
    if (polled.status !== 200) {
      job = { status: "failed", errorStatus: `poll_${polled.status}` };
      break;
    }
    job = polled.json();
  }

  const ok = job.status === "completed";
  quizJobSuccess.add(ok);
  quizJobDuration.add(Date.now() - startedAt, { status: job.status });
  if (ok) {
    quizAcceptedRatio.add(job.acceptedQuestions / requested);
  } else {
    quizErrors.add(1, { status: String(job.errorStatus), stage: "job" });
  }
}
