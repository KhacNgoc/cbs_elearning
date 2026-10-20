import type { Lesson, LessonDocument, LessonProgress } from "../types";

export function initialLessonContent(
  lesson: Pick<Lesson, "videoUrl" | "textBody" | "documents">,
  saved?: Pick<LessonProgress, "contentType" | "documentId">,
  requested?: LessonDocument,
) {
  const documents = lesson.documents || [];
  const explicitDocument =
    requested &&
    documents.find((document) =>
      requested.id != null
        ? document.id === requested.id
        : document.fileUrl === requested.fileUrl,
    );
  if (explicitDocument)
    return { type: "DOCUMENT" as const, document: explicitDocument };
  if (saved?.contentType === "DOCUMENT") {
    const document = documents.find((item) => item.id === saved.documentId);
    if (document) return { type: "DOCUMENT" as const, document };
  }
  if (saved?.contentType === "TEXT" && lesson.textBody?.trim())
    return { type: "TEXT" as const, document: null };
  if (lesson.videoUrl?.trim())
    return { type: "VIDEO" as const, document: null };
  if (documents[0])
    return { type: "DOCUMENT" as const, document: documents[0] };
  return { type: "TEXT" as const, document: null };
}
