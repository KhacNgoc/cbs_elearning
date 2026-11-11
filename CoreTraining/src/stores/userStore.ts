import { createLessonProgressWriter } from "../utils/lessonProgressWriter";
import { defineStore } from "pinia";
import type { LessonProgressRequest, UserProgress } from "../types";
import { useCourseStore } from "./courseStore";
import { authApi } from "../api/authApi";
import type { LoginRequest } from "../api/authApi";
import { courseApi } from "../api/courseApi";
import { userApi } from "../api/userApi";
import { useNavigationStore } from "./navigationStore";
import { clearAuthSession, markSignedOut, setCachedAuthSession, clearUserDraftsAndTemporaryState } from "../utils/authSession";
import { showToast } from "../utils/toast";
import { useLearningStore } from "./learningStore";

const progressWriters = new WeakMap<object, ReturnType<typeof createLessonProgressWriter>>();
let progressRequest: Promise<void> | null = null;
async function navigate(path: string) {
  const { default: router } = await import("../router");
  return router.push(path);
}

export const useUserStore = defineStore("user", {
  state: (): UserProgress & {
    progressError: string;
    progressLoaded: boolean;
    isAuthenticated: boolean;
    role: string;
    roles: string[];
    permissions: string[];
    roleNames: Record<string, string>;
    systemAdmin: boolean;
    username: string;
    fullName: string;
    mustChangePassword: boolean;
  } => ({
    progressError: "",
    progressLoaded: false,
    completedLessons: [],
    quizScores: {},
    quizTotals: {},
    lessonQuizScores: {},
    lessonQuizTotals: {},
    lessonProgress: {},
    points: 0,
    unlockedBadges: [],
    mandatoryCourses: [],
    registeredCourses: [],
    pendingCourses: [],
    isAuthenticated: !!localStorage.getItem("username"),
    role: localStorage.getItem("userRole") || "",
    roles: [],
    permissions: [],
    roleNames: {},
    systemAdmin: false,
    username: localStorage.getItem("username") || "",
    fullName: localStorage.getItem("fullName") || "",
    mustChangePassword: localStorage.getItem("mustChangePassword") === "true",
  }),

  getters: {
    can:
      (state) =>
      (page: string, action: string = "read") =>
        state.permissions.includes(`${page}:${action}`),
    hasAdminAccess: (state) =>
      state.permissions.some((p) => p.endsWith(":read")),
    canLearn(): boolean {
      return this.permissions.includes("LEARN");
    },
    totalLessonsCount(): number {
      return useCourseStore()
        .courses.filter(
          (course) =>
            this.mandatoryCourses.includes(course.id) ||
            this.registeredCourses.includes(course.id),
        )
        .reduce((acc, course) => acc + course.lessons.length, 0);
    },
    completedLessonsCount(): number {
      return useCourseStore()
        .courses.filter(
          (course) =>
            this.mandatoryCourses.includes(course.id) ||
            this.registeredCourses.includes(course.id),
        )
        .reduce(
          (count, course) =>
            count +
            course.lessons.filter((l) => this.completedLessons.includes(l.id))
              .length,
          0,
        );
    },
    overallProgressPercent(): number {
      const total = this.totalLessonsCount;
      if (total === 0) return 0;
      return Math.round((this.completedLessonsCount / total) * 100);
    },
    getCourseProgressPercent:
      (state) =>
      (courseId: string): number => {
        const course = useCourseStore().courses.find((t) => t.id === courseId);
        if (!course || course.lessons.length === 0) return 0;

        const completedInCourse = course.lessons.filter((lesson) =>
          state.completedLessons.includes(lesson.id),
        ).length;

        return Math.round((completedInCourse / course.lessons.length) * 100);
      },
    getCourseQuizScore:
      (state) =>
      (
        courseId: string,
      ): { score: number; total: number; percent: number } | null => {
        if (state.quizScores[courseId] === undefined) return null;
        const score = state.quizScores[courseId];
        const total = state.quizTotals[courseId];
        if (!total || total <= 0) return null;
        return {
          score,
          total,
          percent: Math.round((score / total) * 100),
        };
      },
    isCourseAccessible:
      (state) =>
      (courseId: string): boolean => {
        if (
          state.systemAdmin ||
          useCourseStore().courses.find((c) => c.id === courseId)?.canEdit
        )
          return true;
        return (
          state.mandatoryCourses.includes(courseId) ||
          state.registeredCourses.includes(courseId)
        );
      },
  },

  actions: {
    async refreshAccess() {
      const session = await authApi.session();
      this.role = session.role;
      this.roles = session.roles || [];
      this.permissions = session.permissions || [];
      this.roleNames = session.roleNames || {};
      this.systemAdmin = !!session.systemAdmin;
      this.mustChangePassword = session.mustChangePassword;
      setCachedAuthSession(session);
      await useNavigationStore().load(true);
    },
    async login(credentials: LoginRequest) {
      try {
        const res = await authApi.login(credentials);
        localStorage.setItem("userRole", res.role);
        localStorage.setItem("username", res.username);
        localStorage.setItem("fullName", res.fullName);
        localStorage.setItem(
          "mustChangePassword",
          res.mustChangePassword ? "true" : "false",
        );

        this.isAuthenticated = true;
        this.role = res.role;
        this.roles = res.roles || [res.role];
        this.permissions = res.permissions || [];
        this.roleNames = res.roleNames || {};
        this.systemAdmin = !!res.systemAdmin;
        this.username = res.username;
        this.fullName = res.fullName;
        this.mustChangePassword = res.mustChangePassword;
        setCachedAuthSession(res);

        if (this.mustChangePassword) {
          await navigate("/change-password");
        } else {
          const navigation = useNavigationStore();
          await navigation.load(true);
          await navigate(navigation.homeRoute);
        }
      } catch (error) {
        console.error("Login failed:", error);
        throw error;
      }
    },

    async logout() {
      let serverSignedOut = false;
      try {
        await authApi.logout();
        serverSignedOut = true;
      } catch (error) {
        console.error("Server logout failed:", error);
      }
      clearAuthSession();
      if (serverSignedOut) {
        markSignedOut();
        // Leave the current page before resetting auth state: flipping isAuthenticated/permissions
        // swaps App.vue's layout wrapper, which would remount the current (admin) page and let it
        // fire API calls with the revoked cookie.
        await navigate("/login");
      }
      clearUserDraftsAndTemporaryState();
      localStorage.removeItem("accessToken");
      localStorage.removeItem("userRole");
      localStorage.removeItem("username");
      localStorage.removeItem("fullName");
      localStorage.removeItem("mustChangePassword");
      // Unsubmitted quiz answers must not outlive the session on a shared machine.
      // Literal mirrors QUIZ_DRAFT_PREFIX in quizStore (importing it would create a cycle).
      Object.keys(localStorage)
        .filter((key) => key.startsWith("quiz-draft:"))
        .forEach((key) => localStorage.removeItem(key));

      this.isAuthenticated = false;
      this.role = "";
      this.username = "";
      this.fullName = "";
      this.mustChangePassword = false;
      this.completedLessons = [];
      this.quizScores = {};
      this.quizTotals = {};
      this.lessonQuizScores = {};
      this.lessonQuizTotals = {};
      this.lessonProgress = {};
      this.points = 0;
      this.unlockedBadges = [];
      this.mandatoryCourses = [];
      this.registeredCourses = [];
      this.pendingCourses = [];

      this.roles = [];
      this.permissions = [];
      this.roleNames = {};
      this.systemAdmin = false;
      useNavigationStore().clear();
      useCourseStore().clearOutlineCache();
      useCourseStore().$reset();
      progressWriters.delete(this);
      this.progressLoaded = false;
      useLearningStore().clear();
      try {
        const { useQuizStore } = await import("./quizStore");
        useQuizStore().$reset();
      } catch {}
      // Server logout failed: the guard re-checks the session and returns the user home if it is still valid.
      if (!serverSignedOut) await navigate("/login");
    },

    async fetchProgress() {
      if (!this.isAuthenticated) return;
      if (progressRequest) return progressRequest;
      progressRequest = (async () => {
        this.progressError = "";
        try {
          const [progress, profile] = await Promise.all([
            courseApi.getMyProgress(),
            userApi.getMyProfile(),
          ]);

          this.progressLoaded = true;
          this.completedLessons = progress.completedLessons || [];
          this.quizScores = progress.quizScores || {};
          this.quizTotals = progress.quizTotals || {};
          this.lessonQuizScores = progress.lessonQuizScores || {};
          this.lessonQuizTotals = progress.lessonQuizTotals || {};
          this.lessonProgress = progress.lessonProgress || {};
          this.points = progress.points || 0;
          this.unlockedBadges = progress.unlockedBadges || [];
          this.mandatoryCourses = progress.mandatoryCourses || [];
          this.registeredCourses = progress.registeredCourses || [];
          this.pendingCourses = progress.pendingCourses || [];
          this.mustChangePassword = progress.mustChangePassword || false;

          this.role = profile.role;
          this.roles = profile.roles || [profile.role];
          this.permissions = profile.permissions || [];
          this.roleNames = profile.roleNames || {};
          this.systemAdmin = !!profile.systemAdmin;
          setCachedAuthSession({
            role: profile.role,
            roles: profile.roles,
            permissions: profile.permissions,
            roleNames: profile.roleNames,
            systemAdmin: profile.systemAdmin,
            username: profile.username,
            fullName: profile.fullName,
            mustChangePassword: this.mustChangePassword,
          });
          this.fullName = profile.fullName || "";
          localStorage.setItem("fullName", this.fullName);
          localStorage.setItem(
            "mustChangePassword",
            this.mustChangePassword ? "true" : "false",
          );

        } catch (error) {
          this.progressError =
            "Không thể tải tiến độ và thành tích. Vui lòng thử lại.";
          console.error("Failed to fetch user progress:", error);
          showToast(
            "Không thể tải thông tin và tiến độ học tập. Vui lòng thử lại.",
            "error",
          );
        }
      })();
      try {
        await progressRequest;
      } finally {
        progressRequest = null;
      }
    },

    async completeLesson(lessonId: string) {
      if (!this.isAuthenticated) return;
      try {
        const updatedProgress = await courseApi.markComplete(lessonId);
        this.completedLessons = updatedProgress.completedLessons;
        this.points = updatedProgress.points;

      } catch (error) {
        console.error("Failed to mark lesson complete:", error);
        throw error;
      }
    },

    async resetLesson(lessonId: string) {
      if (!this.isAuthenticated) return;
      try {
        const updatedProgress = await courseApi.resetLesson(lessonId);
        this.completedLessons = updatedProgress.completedLessons || [];
        progressWriters.delete(this);
        this.lessonProgress = updatedProgress.lessonProgress || {};
        this.points = updatedProgress.points;
      } catch (error) {
        console.error("Failed to reset lesson:", error);
        throw error;
      }
    },

    getLatestLessonProgress(lessonId: string) {
      const pending = progressWriters.get(this)?.pending(lessonId);
      const saved = this.lessonProgress?.[lessonId];
      return pending ? { ...saved, ...pending } : saved;
    },

    async saveLessonProgress(lessonId: string, request: LessonProgressRequest, touch = false) {
      if (!this.canLearn || !this.isAuthenticated) return null;
      let writer = progressWriters.get(this);
      if (!writer) {
        const username = this.username;
        writer = createLessonProgressWriter(async (id, snapshot) => {
          if (!this.isAuthenticated || this.username !== username || progressWriters.get(this) !== writer)
            return null;
          try {
            const progress = await courseApi.saveLessonProgress(id, snapshot);
            if (this.username !== username || progressWriters.get(this) !== writer) return null;
            this.lessonProgress ||= {};
            this.lessonProgress[id] = progress;
            if (progress.lessonCompleted && !this.completedLessons.includes(id)) {
              this.completedLessons.push(id);
              void this.fetchProgress();
            }
            return progress;
          } catch (error) {
            console.error("Failed to save lesson progress:", error);
            return null;
          }
        });
        progressWriters.set(this, writer);
      }
      return writer.write(lessonId, request, touch);
    },

    async registerCourse(courseId: string) {
      if (!this.isAuthenticated) return;
      try {
        const updatedProgress = await courseApi.registerCourse(courseId);
        this.registeredCourses = updatedProgress.registeredCourses || [];
        this.pendingCourses = updatedProgress.pendingCourses || [];
        this.mandatoryCourses = updatedProgress.mandatoryCourses || [];
      } catch (error) {
        console.error("Failed to register course:", error);
        throw error;
      }
    },
  },
});
