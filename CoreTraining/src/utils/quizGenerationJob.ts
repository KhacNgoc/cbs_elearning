// Drives an asynchronous AI quiz generation job: start it, poll its progress,
// and resolve with the same response shape as the legacy synchronous endpoint.

export type QuizGenerationJobStatus = "queued" | "running" | "completed" | "failed";
export type QuizGenerationJobStage =
  | "queued"
  | "retrieving"
  | "generating"
  | "verifying"
  | "finalizing"
  | "completed"
  | "failed";

export interface QuizGenerationJob<Result = unknown> {
  jobId: string;
  status: QuizGenerationJobStatus;
  stage: QuizGenerationJobStage;
  message?: string;
  percent: number;
  requestedQuestions: number;
  acceptedQuestions: number;
  attempt: number;
  maxAttempts: number;
  /** 1 = generating, 2 = verifying; phaseCount is 1 when verification is off. */
  phase?: number;
  phaseCount?: number;
  /** Progress of the current phase, counted in questions. */
  completedSteps?: number;
  totalSteps?: number;
  error?: string | null;
  result?: Result | null;
}

export type QuizGenerationJobErrorKind = "failed" | "timeout" | "cancelled";

export class QuizGenerationJobError extends Error {
  readonly kind: QuizGenerationJobErrorKind;

  constructor(kind: QuizGenerationJobErrorKind, message: string) {
    super(message);
    this.kind = kind;
    this.name = "QuizGenerationJobError";
  }
}

export interface QuizGenerationJobApi<Request, Result> {
  start(request: Request): Promise<QuizGenerationJob<Result>>;
  status(jobId: string): Promise<QuizGenerationJob<Result>>;
}

export interface QuizGenerationJobOptions<Result> {
  onProgress?: (job: QuizGenerationJob<Result>) => void;
  timeoutMs: number;
  intervalMs?: number;
  /** Consecutive failed status polls tolerated before giving up (network blips). */
  maxPollErrors?: number;
  signal?: AbortSignal;
  now?: () => number;
  sleep?: (ms: number) => Promise<void>;
}

export const QUIZ_TIMEOUT_MESSAGE =
  "Quá thời gian tạo câu hỏi. Hãy giảm số câu hoặc thử lại.";
export const QUIZ_FAILED_MESSAGE = "Không thể tạo câu hỏi. Vui lòng thử lại.";

const STAGE_LABELS: Record<QuizGenerationJobStage, string> = {
  queued: "Đang chờ xử lý",
  retrieving: "Đang truy xuất nội dung tài liệu",
  generating: "Đang sinh câu hỏi",
  verifying: "Đang kiểm định câu hỏi",
  finalizing: "Đang hoàn tất bản nháp",
  completed: "Đã tạo xong",
  failed: "Tạo câu hỏi thất bại",
};

export const clampPercent = (value: unknown): number => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? Math.min(100, Math.max(0, Math.round(parsed))) : 0;
};

export const describeQuizJob = (job: QuizGenerationJob): string => {
  let label = STAGE_LABELS[job.stage] || job.message || STAGE_LABELS.queued;
  if (job.stage === "generating" && job.maxAttempts > 1 && job.attempt > 1) {
    label = `Đang sinh lại câu chưa đạt (lượt ${job.attempt}/${job.maxAttempts})`;
  }
  // The bar is split evenly between generating and verifying; say which half we are in.
  const phaseCount = job.phaseCount ?? 1;
  if (phaseCount > 1 && (job.stage === "generating" || job.stage === "verifying")) {
    label = `Bước ${job.phase}/${phaseCount} · ${label}`;
  }
  return label;
};

/** Counts behind the percent, in the same unit (questions) the bar uses. */
export const describeQuizJobDetail = (job: QuizGenerationJob): string => {
  const done = job.completedSteps ?? 0;
  const total = job.totalSteps ?? 0;
  switch (job.stage) {
    case "retrieving":
      return "Đang tìm các đoạn tài liệu phù hợp";
    case "generating":
      return `Đã tạo ${done}/${total || job.requestedQuestions} câu đạt yêu cầu`;
    case "verifying":
      return `Đã kiểm định ${done}/${total} câu`;
    case "finalizing":
      return `${job.acceptedQuestions}/${job.requestedQuestions} câu đạt yêu cầu`;
    default:
      return "";
  }
};

const defaultSleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

const errorMessage = (error: unknown, fallback: string) =>
  error instanceof Error && error.message ? error.message : fallback;

const isNotFound = (error: unknown) =>
  (error as { response?: { status?: number } })?.response?.status === 404;

export async function runQuizGenerationJob<Request, Result>(
  api: QuizGenerationJobApi<Request, Result>,
  request: Request,
  options: QuizGenerationJobOptions<Result>,
): Promise<Result> {
  const now = options.now ?? Date.now;
  const sleep = options.sleep ?? defaultSleep;
  const intervalMs = options.intervalMs ?? 1500;
  const maxPollErrors = options.maxPollErrors ?? 3;
  const deadline = now() + options.timeoutMs;
  const throwIfCancelled = () => {
    if (options.signal?.aborted) {
      throw new QuizGenerationJobError("cancelled", "Đã hủy tạo câu hỏi");
    }
  };

  let percent = 0;
  const report = (job: QuizGenerationJob<Result>) => {
    // Never let the bar move backwards, even if the server re-plans retries.
    percent = Math.max(percent, clampPercent(job.percent));
    options.onProgress?.({ ...job, percent });
  };

  throwIfCancelled();
  let job = await api.start(request);
  let pollErrors = 0;
  for (;;) {
    throwIfCancelled();
    report(job);
    if (job.status === "completed") {
      if (!job.result) throw new QuizGenerationJobError("failed", QUIZ_FAILED_MESSAGE);
      return job.result;
    }
    if (job.status === "failed") {
      throw new QuizGenerationJobError("failed", job.error || QUIZ_FAILED_MESSAGE);
    }
    if (now() >= deadline) {
      throw new QuizGenerationJobError("timeout", QUIZ_TIMEOUT_MESSAGE);
    }
    await sleep(Math.min(intervalMs, Math.max(0, deadline - now())));
    throwIfCancelled();
    try {
      job = await api.status(job.jobId);
      pollErrors = 0;
    } catch (error) {
      pollErrors += 1;
      if (isNotFound(error) || pollErrors >= maxPollErrors) {
        throw new QuizGenerationJobError("failed", errorMessage(error, QUIZ_FAILED_MESSAGE));
      }
    }
  }
}
