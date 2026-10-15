import { defineStore } from "pinia";
import { axiosClient } from "../api/axiosClient";
import type { LearnerWorkspace } from "../types";
let requestId = 0;

export const useLearningStore = defineStore("learning", {
  state: () => ({
    workspace: null as LearnerWorkspace | null,
    loading: false,
    error: "",
  }),
  actions: {
    clear() {
      requestId++;
      this.$reset();
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
