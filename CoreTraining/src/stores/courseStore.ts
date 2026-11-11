import { defineStore } from "pinia";
import type { Course, Lesson, CourseOutline, ReplyPageState } from "../types";
import { mergeReplies } from "../utils/commentReplies";
import { courseApi } from "../api/courseApi";
import { showToast } from "../utils/toast";
import { trainingApi } from "../api/trainingApi";
import { createAsyncResourceCache } from "../utils/asyncResourceCache";

const outlineCaches = new WeakMap<object, ReturnType<typeof createAsyncResourceCache<CourseOutline>>>();

let lessonRequestId = 0;

export const useCourseStore = defineStore("course", {
  state: () => ({
    courses: [] as Course[],
    currentLessonId: null as string | null,
    currentLessonData: null as Lesson | null,
    isLoading: false,
    replyPageStates: {} as Record<number, ReplyPageState>,
  }),

  getters: {
    allLessons(): Lesson[] {
      return this.courses.flatMap((course) => course.lessons);
    },
    currentLesson(): Lesson | null {
      return this.currentLessonData;
    },
    currentCourse(): Course | null {
      const lesson = this.currentLesson;
      if (!lesson) return null;
      return (
        this.courses.find((course) => course.id === lesson.courseId) || null
      );
    },
  },

  actions: {
    async fetchOutline(courseId: string, context: string, force = false) {
      let cache = outlineCaches.get(this);
      if (!cache) {
        cache = createAsyncResourceCache<CourseOutline>();
        outlineCaches.set(this, cache);
      }
      return cache.get(JSON.stringify([courseId, context]), () => trainingApi.outline(courseId), force);
    },
    clearOutlineCache() { outlineCaches.get(this)?.clear(); },
    async fetchLessonDetail(lessonId: string) {
      const requestId = ++lessonRequestId;
      this.isLoading = true;
      this.currentLessonId = lessonId;
      this.replyPageStates = {};
      try {
        const lesson = await courseApi.getLessonById(lessonId);
        if (requestId !== lessonRequestId) return;
        this.currentLessonData = lesson;
        // Keep the course outline in sync with the detailed lesson response.
        // The sidebar reads from `courses`, while the learning page reads from
        // `currentLessonData`; without this merge a newly-created quiz can
        // appear in one place but not the other until a full page refresh.
        if (this.currentLessonData) {
          const course = this.courses.find(
            (item) => item.id === this.currentLessonData?.courseId,
          );
          const lessonIndex =
            course?.lessons.findIndex((item) => item.id === lessonId) ?? -1;
          if (course && lessonIndex >= 0) {
            course.lessons[lessonIndex] = {
              ...course.lessons[lessonIndex],
              ...this.currentLessonData,
            };
          }
        }
        return lesson;
      } catch (error) {
        console.error(`Failed to load lesson detail for ${lessonId}:`, error);
        if (requestId === lessonRequestId) {
          this.currentLessonData = null;
          showToast(
            "Không thể tải nội dung bài học. Vui lòng thử lại.",
            "error",
          );
        }
        return null;
      } finally {
        if (requestId === lessonRequestId) this.isLoading = false;
      }
    },

    async addComment(lessonId: string, rating: number | null, content: string) {
      try {
        const newComment = await courseApi.addComment(
          lessonId,
          rating,
          content,
        );
        if (this.currentLessonData && this.currentLessonData.id === lessonId) {
          this.currentLessonData.comments.unshift(newComment);
          if (this.currentLessonData.commentCount != null) this.currentLessonData.commentCount++;
        }
      } catch (error) {
        console.error("Failed to add comment:", error);
      }
    },

    async loadMoreReplies(lessonId: string, rootCommentId: number) {
      const lesson = this.currentLessonData;
      if (!lesson || lesson.id !== lessonId || this.currentLessonId !== lessonId) return;
      const root = lesson.comments.find((comment) => comment.id === rootCommentId);
      if (!root?.repliesHasMore || this.replyPageStates[rootCommentId]?.loading) return;
      this.replyPageStates[rootCommentId] = { loading: true, error: "" };
      const state = this.replyPageStates[rootCommentId]!;
      const isCurrent = () => this.currentLessonData === lesson && this.currentLessonId === lessonId
        && this.replyPageStates[rootCommentId] === state && lesson.comments.includes(root);
      try {
        const page = await courseApi.getReplies(lessonId, rootCommentId, root.replyCursor);
        if (!isCurrent()) return;
        root.replies = mergeReplies(root.replies || [], page.content);
        root.replyCursor = page.nextCursor;
        root.repliesHasMore = page.hasMore;
      } catch {
        if (isCurrent()) state.error = "Không thể tải phản hồi. Vui lòng thử lại.";
      } finally {
        state.loading = false;
      }
    },

    async replyToComment(
      lessonId: string,
      rootCommentId: number,
      replyToCommentId: number,
      content: string,
    ) {
      const reply = await courseApi.replyToComment(
        lessonId,
        rootCommentId,
        replyToCommentId,
        content,
      );
      if (this.currentLessonData?.id === lessonId) {
        const root = this.currentLessonData.comments.find(
          (comment) => comment.id === rootCommentId,
        );
        if (
          root &&
          !(root.replies || []).some((item) => item.id === reply.id)
        ) {
          root.replies = mergeReplies(root.replies || [], [reply]);
        }
      }
    },

    async deleteComment(lessonId: string, commentId: number) {
      await courseApi.deleteComment(lessonId, commentId);
      if (this.currentLessonData?.id !== lessonId) return;

      const isRootComment = this.currentLessonData.comments.some(
        (comment) => comment.id === commentId,
      );
      if (isRootComment) {
        if (this.currentLessonData.commentCount != null) this.currentLessonData.commentCount--;
        this.currentLessonData.comments =
          this.currentLessonData.comments.filter(
            (comment) => comment.id !== commentId,
          );
        return;
      }
      this.currentLessonData.comments.forEach((comment) => {
        if ((comment.replies || []).some((reply) => reply.id === commentId)
          && this.replyPageStates[comment.id]?.loading) {
          // Discard the older in-flight page so it cannot restore this reply.
          // Keep its cursor; a retry still reads from the same ordering key.
          this.replyPageStates[comment.id] = { loading: false, error: "" };
        }
        comment.replies = (comment.replies || []).filter(
          (reply) => reply.id !== commentId,
        );
      });
    },
  },
});
