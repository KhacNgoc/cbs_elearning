import { axiosClient } from "./axiosClient";

export interface NavigationItem {
  id: string;
  label: string;
  route: string;
  icon: string;
  area: "MAIN" | "ADMIN";
  sortOrder: number;
  visible: boolean;
}

export const navigationApi = {
  async getMyNavigation(): Promise<NavigationItem[]> {
    const response =
      await axiosClient.get<NavigationItem[]>("/api/navigation/me");
    return response.data;
  },
};
