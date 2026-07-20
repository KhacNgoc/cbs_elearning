import { axiosClient, isMockApi, mockDelay } from './axiosClient';

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
  accessToken: string;
  tokenType: string;
  role: string;
  username: string;
  fullName: string;
}

export const authApi = {
  async login(data: LoginRequest): Promise<LoginResponse> {
    if (isMockApi()) {
      await mockDelay(350);
      return {
        accessToken: `fake-jwt-token-${data.username}`,
        tokenType: 'Bearer',
        role: 'ROLE_USER',
        username: data.username,
        fullName: data.username === 'quantrh' ? 'Quản Trị Hệ Thống (Bạn)' : data.username
      };
    }
    const response = await axiosClient.post<LoginResponse>('/api/auth/login', data);
    return response.data;
  },

  async register(data: RegisterRequest): Promise<string> {
    if (isMockApi()) {
      await mockDelay(300);
      return 'User registered successfully';
    }
    const response = await axiosClient.post<string>('/api/auth/register', data);
    return response.data;
  }
};
