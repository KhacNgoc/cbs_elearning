export interface ProfileSummary {
  joined: number;
  completed: number;
  total: number;
  percent: number | null;
  notStarted: number;
  inProgress: number;
  complete: number;
  mandatory: number;
  mandatoryComplete: number;
}

export interface ResultCounts {
  total: number;
  legacyTotal: number;
}

export const attemptPercent = (score: number, total: number) =>
  total > 0 ? Math.round((score / total) * 100) : 0;

export function profileMetrics(summary: ProfileSummary) {
  return [
    {
      label: "Khóa tham gia",
      value: summary.joined,
      detail: `${summary.inProgress} đang học · ${summary.notStarted} chưa bắt đầu`,
    },
    {
      label: "Hoàn thành khóa",
      value: summary.joined
        ? `${attemptPercent(summary.complete, summary.joined)}%`
        : "—",
      detail: `${summary.complete}/${summary.joined} khóa`,
    },
    {
      label: "Hoàn thành bài",
      value: summary.percent === null ? "—" : `${summary.percent}%`,
      detail: `${summary.completed}/${summary.total} bài`,
    },
    {
      label: "Bắt buộc hoàn thành",
      value: `${summary.mandatoryComplete}/${summary.mandatory}`,
      detail: "khóa học bắt buộc",
    },
  ];
}

// The API pages attempts and legacy scores together but only filters attempts,
// so legacy rows count towards paging only when no search is active.
export const resultRowCount = (counts: ResultCounts, searching: boolean) =>
  searching ? counts.total : Math.max(counts.total, counts.legacyTotal);

export const resultPageCount = (rows: number, pageSize: number) =>
  Math.max(1, Math.ceil(rows / pageSize));

export function resultEmptyMessage(counts: ResultCounts, searching: boolean) {
  if (counts.total > 0) return "";
  if (searching) return "Không có bài kiểm tra phù hợp với từ khóa.";
  return counts.legacyTotal > 0
    ? "Chưa có lịch sử theo từng lần thi."
    : "Chưa có kết quả kiểm tra. Kết quả sẽ xuất hiện sau khi bạn nộp bài kiểm tra đầu tiên.";
}
