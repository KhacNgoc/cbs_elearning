import type { LessonProgress, LessonProgressRequest } from "../types";

type Job = {
  request: LessonProgressRequest;
  key: string;
  touch: boolean;
  promise: Promise<LessonProgress | null>;
  resolve: (value: LessonProgress | null) => void;
  followers: Array<(value: LessonProgress | null) => void>;
};
type State = {
  current?: Job;
  pending?: Job;
  savedKey?: string;
  saved?: LessonProgress;
};

/** Coalesce per lesson; serialize writes globally because the server uses updatedAt for resume order. */
export function createLessonProgressWriter(
  save: (
    lessonId: string,
    request: LessonProgressRequest,
  ) => Promise<LessonProgress | null>,
) {
  const states = new Map<string, State>();
  const latest = new Map<string, State>();
  const queue: Array<{ id: string; state: State }> = [];
  let running = false;
  async function drain() {
    if (running) return;
    running = true;
    while (queue.length) {
      const { id, state } = queue.shift()!;
      const job = state.pending;
      if (!job) continue;
      state.pending = undefined;
      state.current = job;
      let result: LessonProgress | null = null;
      if (!job.touch && state.savedKey === job.key)
        result = state.saved || null;
      else {
        try {
          result = await save(id, job.request);
        } catch {
          /* Keep failed snapshots retryable. */
        }
        if (result) {
          state.savedKey = job.key;
          state.saved = result;
        }
      }
      job.resolve(result);
      job.followers.forEach((resolve) => resolve(result));
      state.current = undefined;
    }
    running = false;
  }
  return {
    pending(id: string) {
      const state = latest.get(id);
      return state?.pending?.request || state?.current?.request;
    },
    write(
      id: string,
      request: LessonProgressRequest,
      touch = false,
    ): Promise<LessonProgress | null> {
      const stateKey =
        request.contentType === "DOCUMENT"
          ? JSON.stringify([id, request.documentId])
          : id;
      let state = states.get(stateKey);
      if (!state) {
        state = {};
        states.set(stateKey, state);
      }
      latest.set(id, state);
      const snapshot = { ...request };
      const key = JSON.stringify([
        snapshot.contentType,
        snapshot.documentId,
        snapshot.documentPage,
        snapshot.contentType === "DOCUMENT" ? undefined : snapshot.scrollOffset,
        snapshot.lastPositionSeconds,
        snapshot.watchedSeconds,
        snapshot.durationSeconds,
      ]);
      if (!touch) {
        if (state.pending?.key === key) return state.pending.promise;
        if (!state.pending && state.current?.key === key)
          return state.current.promise;
        if (!state.current && !state.pending && state.savedKey === key)
          return Promise.resolve(state.saved || null);
      }
      let resolve!: Job["resolve"];
      const promise = new Promise<LessonProgress | null>((done) => {
        resolve = done;
      });
      const previous = state.pending;
      state.pending = {
        request: snapshot,
        key,
        touch: touch || !!previous?.touch,
        promise,
        resolve,
        followers: previous ? [...previous.followers, previous.resolve] : [],
      };
      // A newer visit/update belongs after any already queued writes to other lessons.
      const queued = queue.findIndex((item) => item.state === state);
      if (queued >= 0) queue.splice(queued, 1);
      queue.push({ id, state });
      void drain();
      return promise;
    },
  };
}
