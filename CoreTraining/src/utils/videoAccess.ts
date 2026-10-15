export interface VideoAccess {
  streamUrl: string;
  expiresAt: number;
}

// Keep renewal and cancellation independent of the player DOM so late access
// responses cannot replace the source of another lesson.
export class VideoAccessSession {
  private generation = 0;
  private timer: ReturnType<typeof setTimeout> | undefined;
  private controller: AbortController | undefined;
  private recoveries = 0;
  private fetchAccess: (signal: AbortSignal) => Promise<VideoAccess>;
  private applyAccess: (access: VideoAccess) => void;
  private reportError: () => void;

  constructor(
    fetchAccess: (signal: AbortSignal) => Promise<VideoAccess>,
    applyAccess: (access: VideoAccess) => void,
    reportError: () => void,
  ) {
    this.fetchAccess = fetchAccess;
    this.applyAccess = applyAccess;
    this.reportError = reportError;
  }

  stop() {
    this.generation++;
    clearTimeout(this.timer);
    this.controller?.abort();
    this.controller = undefined;
  }

  retry() {
    this.recoveries = 0;
    void this.load();
  }

  recover() {
    if (this.controller) return;
    if (this.recoveries++ >= 1) {
      this.stop();
      this.reportError();
      return;
    }
    void this.load();
  }

  async load() {
    this.stop();
    const generation = this.generation;
    const controller = new AbortController();
    this.controller = controller;
    try {
      const access = await this.fetchAccess(controller.signal);
      if (generation !== this.generation) return;
      if (
        !access.streamUrl ||
        !Number.isFinite(access.expiresAt) ||
        access.expiresAt <= Date.now()
      ) {
        throw new Error("Invalid video access");
      }
      this.applyAccess(access);
      const remaining = access.expiresAt - Date.now();
      const delay = Math.max(250, remaining - Math.min(60_000, remaining / 5));
      this.timer = setTimeout(
        () => void this.load(),
        Math.min(delay, 2_147_483_647),
      );
    } catch {
      if (generation === this.generation) this.reportError();
    } finally {
      if (generation === this.generation) this.controller = undefined;
    }
  }
}

export const MAX_VIDEO_UPLOAD_BYTES = 500 * 1024 * 1024;

export function validateVideoUpload(
  file: { name: string; size: number },
  maxBytes = MAX_VIDEO_UPLOAD_BYTES,
): string | undefined {
  if (!/\.(mp4|m4v|webm)$/i.test(file.name))
    return "Chỉ hỗ trợ MP4/M4V (H.264/AAC) hoặc WebM (VP8/VP9, Opus/Vorbis).";
  if (file.size === 0) return "File video rỗng. Vui lòng chọn file khác.";
  if (file.size > maxBytes)
    return `Video phải có dung lượng tối đa ${Math.floor(maxBytes / 1024 / 1024)} MiB.`;
}
