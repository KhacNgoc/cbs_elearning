import { axiosClient } from "./axiosClient";
import type { LeaderboardUser } from "../types";

export const leaderboardApi = {
  async getLeaderboard(): Promise<LeaderboardUser[]> {
    const response =
      await axiosClient.get<LeaderboardUser[]>("/api/leaderboard");
    return response.data;
  },
};
