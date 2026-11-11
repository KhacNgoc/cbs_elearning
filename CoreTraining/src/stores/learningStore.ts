import { defineStore } from "pinia";
import { getDashboardSummary, getDashboardCourses, type DashboardOverview, type DashboardPage, type DashboardParams } from "../api/learnerDashboardApi";
let dashboardRequestId = 0;

export const useLearningStore = defineStore("learning", {
  state: () => ({
    dashboard: null as DashboardOverview | null,
    dashboardPage: null as DashboardPage | null,
    dashboardLoading: false,
    dashboardTab: "",
    dashboardError: "",
  }),
  actions: {
    clear() {
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
  },
});
