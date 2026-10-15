import { axiosClient, isMockApi, mockDelay } from "./axiosClient";

export interface LoginRequest {
  username: string;
  password?: string;
}

export interface RegisterRequest {
  username: string;
  password?: string;
  fullName: string;
  employeeId: string;
  email: string;
  avatarColor?: string;
  userGroupId?: string;
}

export interface LoginResponse {
  accessToken?: string;
  tokenType?: string;
  role: string;
  roles?: string[];
  roleNames?: Record<string, string>;
  systemAdmin?: boolean;
  permissionRevision?: number;
  permissions?: string[];
  unitId?: string;
  scopeUnitIds?: string[];
  status?: string;
  learnerType?: string;
  username: string;
  fullName: string;
  mustChangePassword: boolean;
}

export const authApi = {
  async login(data: LoginRequest): Promise<LoginResponse> {
    if (isMockApi()) {
      await mockDelay(350);
      return {
        accessToken: `fake-jwt-token-${data.username}`,
        tokenType: "Bearer",
        role: "ROLE_USER",
        username: data.username,
        fullName:
          data.username === "quantrh"
            ? "Quản Trị Hệ Thống (Bạn)"
            : data.username,
        mustChangePassword: false,
      };
    }
    const response = await axiosClient.post<LoginResponse>(
      "/api/auth/login",
      data,
    );
    return response.data;
  },

  async session(): Promise<LoginResponse> {
    const response = await axiosClient.get<LoginResponse>("/api/auth/session");
    return response.data;
  },

  async logout(): Promise<void> {
    if (!isMockApi()) await axiosClient.post("/api/auth/logout");
  },

  async register(data: RegisterRequest): Promise<string> {
    if (isMockApi()) {
      await mockDelay(300);
      return "User registered successfully";
    }
    const response = await axiosClient.post<string>("/api/auth/register", data);
    return response.data;
  },

  async sendResetOtp(username: string, email: string): Promise<string> {
    if (isMockApi()) {
      await mockDelay(300);
      return "OTP sent successfully";
    }
    const response = await axiosClient.post<string>(
      "/api/auth/forgot-password-otp",
      { username, email },
    );
    return response.data;
  },

  async resetPasswordWithOtp(
    username: string,
    otp: string,
    newPassword: string,
  ): Promise<string> {
    if (isMockApi()) {
      await mockDelay(300);
      return "Password reset successfully";
    }
    const response = await axiosClient.post<string>(
      "/api/auth/reset-password-otp",
      { username, otp, newPassword },
    );
    return response.data;
  },
};
