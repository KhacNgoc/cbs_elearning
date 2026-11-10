// Chapters, lessons and lesson content are edited only while the course is a draft.
// The backend enforces the same rule (CourseEditPolicy); these helpers only keep the UI in step with it.
export type CourseLike = { status?: string | null } | null | undefined;

export function isCourseContentLocked(course: CourseLike): boolean {
  return course?.status === "PUBLISHED" || course?.status === "ARCHIVED";
}

export function courseLockReason(course: CourseLike): string {
  return course?.status === "ARCHIVED"
    ? "Khóa học đang lưu trữ"
    : "Khóa học đang xuất bản";
}
