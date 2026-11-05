import type { LearnerCourse, LearnerAttempt, ResumeTarget } from "../types";

export const isEnrolled = (item: LearnerCourse) =>
  item.source === "ASSIGNED" || item.source === "REGISTERED";
export const learningStatusLabel = (status: string) =>
  ({
    NOT_STARTED: "Chưa bắt đầu",
    IN_PROGRESS: "Đang học",
    COMPLETED: "Đã hoàn thành",
    AVAILABLE: "Có thể đăng ký",
    PENDING: "Chờ duyệt",
  })[status] || status;
export const normalizeSearch = (text: string) =>
  text
    .toLocaleLowerCase("vi")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .replace(/đ/g, "d");
export function learningSummary(items: LearnerCourse[]) {
  const joined = items.filter(isEnrolled);
  const completed = joined.reduce((n, c) => n + c.completedCount, 0);
  const total = joined.reduce((n, c) => n + c.totalCount, 0);
  return {
    joined,
    completed,
    total,
    percent: total ? Math.round((completed / total) * 100) : null,
    notStarted: joined.filter((c) => c.learningStatus === "NOT_STARTED").length,
    inProgress: joined.filter((c) => c.learningStatus === "IN_PROGRESS").length,
    complete: joined.filter((c) => c.learningStatus === "COMPLETED").length,
    mandatory: joined.filter((c) => c.mandatory).length,
    mandatoryComplete: joined.filter(
      (c) => c.mandatory && c.learningStatus === "COMPLETED",
    ).length,
  };
}
export function groupAttempts(attempts: LearnerAttempt[]) {
  const map = new Map<string, LearnerAttempt[]>();
  for (const attempt of attempts.filter((a) => a.total > 0)) {
    const key = `${attempt.quizId}:${attempt.revision}`;
    map.set(key, [...(map.get(key) || []), attempt]);
  }
  return [...map.values()].map((list) => {
    list.sort((a, b) => b.takenAt.localeCompare(a.takenAt));
    return {
      latest: list[0]!,
      best: list.reduce((a, b) =>
        a.score / a.total >= b.score / b.total ? a : b,
      ),
      attempts: list,
    };
  });
}
export function competencyScores(
  items: LearnerCourse[],
  attempts: LearnerAttempt[],
) {
  const domains = new Map<string, string>();
  items.filter(isEnrolled).forEach((c) => {
    if (c.course.domainId)
      domains.set(c.course.domainId, c.course.domainName || "Chưa phân loại");
  });
  const groups = groupAttempts(attempts);
  // Use the latest evaluated revision per quiz, never count several revisions as several skills.
  const bestByQuiz = new Map<
    string,
    ReturnType<typeof groupAttempts>[number]
  >();
  for (const group of groups) {
    const prev = bestByQuiz.get(group.latest.quizId);
    if (!prev || group.latest.takenAt > prev.latest.takenAt)
      bestByQuiz.set(group.latest.quizId, group);
  }
  return [...domains].map(([id, label]) => {
    const scores = [...bestByQuiz.values()].filter(
      (g) => g.best.domainId === id,
    );
    return {
      id,
      label,
      count: scores.length,
      value: scores.length
        ? Math.round(
            scores.reduce(
              (n, g) => n + (g.best.score / g.best.total) * 100,
              0,
            ) / scores.length,
          )
        : null,
    };
  });
}
export function resumeRoute(target: ResumeTarget) {
  return {
    path: `/lesson/${encodeURIComponent(target.lessonId)}`,
    query: target.documentId ? { documentId: String(target.documentId) } : {},
  };
}
const parseLearningDate = (value: string) =>
  new Date(
    value.length === 10
      ? `${value}T00:00:00+07:00`
      : /Z$|[+-]\d\d:\d\d$/.test(value)
        ? value
        : `${value}+07:00`,
  );
export function formatLearningDate(value?: string | null) {
  if (!value) return "Không quy định";
  const date = parseLearningDate(value);
  return Number.isNaN(date.getTime())
    ? "Chưa xác định"
    : date.toLocaleDateString("vi-VN", { timeZone: "Asia/Saigon" });
}
export function formatLearningDateTime(value?: string | null) {
  if (!value) return "Chưa xác định";
  const date = parseLearningDate(value);
  return Number.isNaN(date.getTime())
    ? "Chưa xác định"
    : date.toLocaleString("vi-VN", {
        timeZone: "Asia/Saigon",
        day: "2-digit",
        month: "2-digit",
        year: "numeric",
        hour: "2-digit",
        minute: "2-digit",
        hour12: false,
      });
}
export function deadlineState(item: LearnerCourse, now = Date.now()) {
  if (!item.dueDate || !isEnrolled(item) || item.learningStatus === "COMPLETED")
    return "";
  const remaining = Date.parse(`${item.dueDate}T23:59:59+07:00`) - now;
  return remaining < 0
    ? "Quá hạn"
    : remaining <= 7 * 86400000
      ? "Sắp đến hạn"
      : "";
}
