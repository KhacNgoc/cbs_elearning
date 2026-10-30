import { axiosClient } from "./axiosClient";
import type { UserProfile } from "../types";

export const userApi = {
  async updateProfile(fields: {
    fullName: string;
    email: string;
  }): Promise<UserProfile> {
    return (
      await axiosClient.patch<UserProfile>("/api/users/me/profile", fields)
    ).data;
  },
  async getMyProfile(): Promise<UserProfile> {

    const response = await axiosClient.get<UserProfile>(
      "/api/users/me/profile",
    );
    return response.data;
  },
};
