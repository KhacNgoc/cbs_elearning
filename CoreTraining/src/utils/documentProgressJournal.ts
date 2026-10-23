import type { LessonProgressRequest } from "../types";

type Entry = {
  lessonId: string;
  request: LessonProgressRequest;
  dirty: boolean;
};

/** Synchronous crash recovery, isolated by account. Storage failure must not stop learning. */
export function createDocumentProgressJournal(
  username: string,
  storage?: Pick<Storage, "getItem" | "setItem">,
) {
  const key = `document-progress:v1:${username}`;
  let entries: Record<string, Entry> = {};
  try {
    storage ??= window.localStorage;
    const parsed = JSON.parse(storage.getItem(key) || "{}");
    for (const [id, entry] of Object.entries(parsed) as [string, Entry][]) {
      if (
        entry?.dirty &&
        typeof entry?.lessonId === "string" &&
        entry.request?.contentType === "DOCUMENT" &&
        Number.isFinite(entry.request.documentId) &&
        Number.isFinite(entry.request.documentPage) &&
        entry.request.documentPage! >= 1
      )
        entries[id] = entry;
    }
  } catch {
    /* Storage can be unavailable or corrupt. */
  }
  const id = (lesson: string, document: number) =>
    JSON.stringify([lesson, document]);
  const persist = () => {
    try {
      storage?.setItem(key, JSON.stringify(entries));
    } catch {
      /* Best effort. */
    }
  };
  return {
    position(lesson: string, document: number) {
      return entries[id(lesson, document)]?.request;
    },
    record(lessonId: string, request: LessonProgressRequest) {
      if (!request.documentId) return;
      const entryKey = id(lessonId, request.documentId);
      const previous = entries[entryKey];
      // Reading position is page-based: do not write storage on every scroll pixel.
      if (previous?.request.documentPage === request.documentPage) return;
      delete entries[entryKey];
      entries[entryKey] = {
        lessonId,
        request: { ...request },
        dirty:
          previous?.dirty ||
          previous?.request.documentPage !== request.documentPage,
      };
      persist();
    },
    acknowledge(lesson: string, request: LessonProgressRequest) {
      if (!request.documentId) return;
      const entry = entries[id(lesson, request.documentId)];
      if (entry?.dirty && entry.request.documentPage === request.documentPage) {
        entry.dirty = false;
        persist();
      }
    },
    pending(lesson: string) {
      return Object.values(entries).filter(
        (entry) => entry.lessonId === lesson && entry.dirty,
      );
    },
  };
}
