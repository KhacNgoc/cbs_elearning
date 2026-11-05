import type { Lesson, LessonDocument, LessonProgress } from "../types";

export type LessonContentType = "TEXT" | "VIDEO" | "SLIDE";
type Content = Partial<
  Pick<Lesson, "videoUrl" | "textBody" | "documents" | "videoSource">
> & { videoFile?: File };
type RequirementFlags = Partial<
  Pick<Lesson, "textRequired" | "videoRequired" | "slideRequired">
>;
export type LessonContentRequirements = Record<LessonContentType, boolean>;

export const lessonContentLabels: Record<LessonContentType, string> = {
  TEXT: "Văn bản",
  VIDEO: "Video",
  SLIDE: "Slide PowerPoint",
};

export const requiredSlides = (lesson: Content) =>
  (lesson.documents || []).filter(document => document.purpose === "SLIDE");

export function lessonContentTypes(lesson: Content): LessonContentType[] {
  return [
    ...(lesson.textBody?.trim() ? ["TEXT" as const] : []),
    ...(lesson.videoUrl?.trim() || lesson.videoFile ? ["VIDEO" as const] : []),
    ...(requiredSlides(lesson).length ? ["SLIDE" as const] : []),
  ];
}

/** A lesson without a saved choice requires everything it contains. */
export function lessonContentRequirements(lesson: RequirementFlags): LessonContentRequirements {
  return {
    TEXT: lesson.textRequired !== false,
    VIDEO: lesson.videoRequired !== false,
    SLIDE: lesson.slideRequired !== false,
  };
}

/** Payload fields for saving; content that is not selected has no choice to keep. */
export function lessonRequirementFields(
  requirements: LessonContentRequirements,
  selected: LessonContentType[],
) {
  return {
    textRequired: !selected.includes("TEXT") || requirements.TEXT,
    videoRequired: !selected.includes("VIDEO") || requirements.VIDEO,
    slideRequired: !selected.includes("SLIDE") || requirements.SLIDE,
  };
}

/** Selected content that existed before and whose requirement choice differs from the saved one. */
export function changedLessonRequirements(
  original: Content & RequirementFlags,
  requirements: LessonContentRequirements,
  selected: LessonContentType[],
) {
  const saved = lessonContentRequirements(original);
  const existing = lessonContentTypes(original);
  return selected.filter(type => existing.includes(type) && saved[type] !== requirements[type]);
}

export function optionalLessonContent(lesson: Content & RequirementFlags) {
  const requirements = lessonContentRequirements(lesson);
  return lessonContentTypes(lesson).filter(type => !requirements[type]);
}

/** What the learner has to do to finish the lesson, in the words the lesson page shows. */
export function lessonCompletionRule(lesson: Content & RequirementFlags, quizRequired: boolean) {
  const requirements = lessonContentRequirements(lesson);
  const required = lessonContentTypes(lesson).filter(type => requirements[type]);
  if (required.length) {
    const content = required.map(type => lessonContentLabels[type]).join(", ");
    return `Học hết ${content}${quizRequired ? " và đạt bài kiểm tra" : ""}`;
  }
  if (quizRequired) return "Đạt bài kiểm tra bắt buộc";
  return "Bấm “Đánh dấu hoàn thành”";
}

export function selectedLessonContent<T extends Content>(
  lesson: T,
  selected: LessonContentType[],
) {
  return {
    ...lesson,
    textBody: selected.includes("TEXT") ? lesson.textBody || "" : "",
    videoUrl: selected.includes("VIDEO") ? lesson.videoUrl || "" : "",
    videoFile: selected.includes("VIDEO") ? lesson.videoFile : undefined,
    documents: (lesson.documents || []).filter(
      document => document.purpose !== "SLIDE" || selected.includes("SLIDE"),
    ),
  };
}

export function validateSelectedLessonContent(
  lesson: Content,
  selected: LessonContentType[],
) {
  if (
    selected.includes("TEXT") &&
    !lesson.textBody?.replace(/<[^>]*>/g, "").replace(/&nbsp;/g, " ").trim()
  ) throw new Error("Vui lòng nhập nội dung văn bản.");
  if (
    selected.includes("VIDEO") &&
    !(lesson.videoSource !== "URL" && lesson.videoFile) &&
    !lesson.videoUrl?.trim()
  ) throw new Error("Vui lòng chọn video hoặc nhập đường link video.");
  if (selected.includes("SLIDE")) {
    const slides = requiredSlides(lesson) as (LessonDocument & { file?: File })[];
    if (slides.length > 1) throw new Error("Mỗi bài học chỉ được có một file slide PowerPoint.");
    if (!slides.length) throw new Error("Vui lòng thêm ít nhất một Slide.");
    if (slides.some(document => !document.name.trim() || !(document.file || document.fileUrl)))
      throw new Error("Mỗi Slide cần có tên và file.");
    if (slides.some(document =>
      !/\.pptx(?:[?#].*)?$/i.test(document.file?.name || document.fileUrl) ||
      document.documentType !== "POWERPOINT",
    )) throw new Error("Slide bắt buộc hỗ trợ .pptx; hãy chuyển .ppt sang .pptx trước khi tải lên.");
  }
}

export function initialLessonContent(
  lesson: Content,
  saved?: Pick<LessonProgress, "contentType" | "documentId">,
  requested?: LessonDocument,
) {
  const documents = lesson.documents || [];
  const explicit = requested && documents.find(document =>
    requested.id != null
      ? document.id === requested.id
      : document.fileUrl === requested.fileUrl,
  );
  if (explicit) return { type: "DOCUMENT" as const, document: explicit };
  if (lesson.videoUrl?.trim()) return { type: "VIDEO" as const, document: null };
  if (lesson.textBody?.trim()) return { type: "TEXT" as const, document: null };
  const slides = requiredSlides(lesson);
  const slide = slides.find(document =>
    saved?.contentType === "DOCUMENT" && document.id === saved.documentId,
  ) || slides[0];
  if (slide) return { type: "DOCUMENT" as const, document: slide };
  return { type: "TEXT" as const, document: null };
}

export function lessonMetadataUpdate(
  lesson: Lesson,
  metadata: Pick<Lesson, "title" | "hint" | "chapterId" | "courseId">,
  version?: number,
): Omit<Lesson, "comments"> {
  const { comments: _comments, ...existing } = lesson;
  return { ...existing, ...metadata, version: version ?? lesson.version };
}
