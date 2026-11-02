import { defineStore } from "pinia";
import { axiosClient } from "../api/axiosClient";
import type { LearnerWorkspace } from "../types";
import { getDashboardSummary, getDashboardCourses, type DashboardOverview, type DashboardPage, type DashboardParams } from "../api/learnerDashboardApi";
let requestId = 0;
let dashboardRequestId = 0;

export const useLearningStore = defineStore("learning", {
  state: () => ({
    workspace: null as LearnerWorkspace | null,
    dashboard: null as DashboardOverview | null,
    dashboardPage: null as DashboardPage | null,
    dashboardLoading: false,
    dashboardTab: "",
    dashboardError: "",
    loading: false,
    error: "",
  }),
  actions: {
    clear() {
      requestId++;
      dashboardRequestId++;
      this.$reset();
    },
    invalidateDashboard() { dashboardRequestId++; },
    async loadDashboard(params: DashboardParams, refreshSummary = true) {
      const current = ++dashboardRequestId;
      this.dashboardLoading = true;
      this.dashboardError = "";
      try {
        const [page, overview] = await Promise.all([
          getDashboardCourses(params),
          refreshSummary || !this.dashboard || this.dashboardTab !== String(params.tab) ? getDashboardSummary(String(params.tab || "mine")) : Promise.resolve(null),
        ]);
        if (current !== dashboardRequestId) return;
        this.dashboardPage = page;
        if (overview) { this.dashboard = overview; this.dashboardTab = String(params.tab); }
      } catch {
        if (current === dashboardRequestId) this.dashboardError = "Không thể tải dữ liệu học tập. Vui lòng thử lại.";
      } finally {
        if (current === dashboardRequestId) this.dashboardLoading = false;
      }
    },
    async load() {
      const current = ++requestId;
      this.loading = true;
      this.error = "";
      try {
        const data = (
          await axiosClient.get<LearnerWorkspace>("/api/users/me/learning")
        ).data;
        if (current === requestId) this.workspace = data;
      } catch {
        if (current === requestId) {
          this.workspace = null;
          this.error = "Không thể tải dữ liệu học tập. Vui lòng thử lại.";
        }
      } finally {
        if (current === requestId) this.loading = false;
      }
    },
  },
});
