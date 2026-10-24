import type { LessonProgressRequest } from '../types';

type Entry = { request: LessonProgressRequest; dirty: boolean };
const signature = (r: LessonProgressRequest) => JSON.stringify([r.lastPositionSeconds, r.watchedSeconds, r.durationSeconds]);

/** Keep the latest snapshot in memory; persist at most once/second, or on flush. */
export function createVideoProgressJournal(username: string, storage?: Pick<Storage, 'getItem' | 'setItem'>) {
  const key = `video-progress:v1:${username}`;
  let entries: Record<string, Entry> = {};
  let lastWrite = -Infinity;
  try {
    storage ??= window.localStorage;
    for (const [id, entry] of Object.entries(JSON.parse(storage.getItem(key) || '{}')) as [string, Entry][]) {
      if (entry?.dirty && entry.request?.contentType === 'VIDEO'
          && [entry.request.lastPositionSeconds, entry.request.watchedSeconds, entry.request.durationSeconds]
            .every(v => typeof v === 'number' && Number.isFinite(v) && v >= 0)) entries[id] = entry;
    }
  } catch { /* Disabled or corrupt storage must not stop playback. */ }
  function persist() {
    try {
      storage?.setItem(key, JSON.stringify(Object.fromEntries(Object.entries(entries).filter(([, entry]) => entry.dirty))));
      lastWrite = Date.now();
    } catch { /* Best effort. */ }
  }
  return {
    record(id: string, request: LessonProgressRequest, force = false) {
      const previous = entries[id];
      const changed = !previous || signature(previous.request) !== signature(request);
      if (changed) entries[id] = { request: { ...request, contentType: 'VIDEO' }, dirty: true };
      if ((changed || previous?.dirty) && (force || Date.now() - lastWrite >= 1000)) persist();
    },
    pending(id: string) { return entries[id]?.dirty ? entries[id]!.request : undefined; },
    acknowledge(id: string, request: LessonProgressRequest) {
      const entry = entries[id];
      if (entry?.dirty && signature(entry.request) === signature(request)) { entry.dirty = false; persist(); }
    },
  };
}
