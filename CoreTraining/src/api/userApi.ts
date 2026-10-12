import { axiosClient, isMockApi, mockDelay } from './axiosClient';
import type { UserProfile } from '../types';

export const userApi = {
  async updateProfile(fields: {fullName:string;email:string}):Promise<UserProfile> {
    return (await axiosClient.patch<UserProfile>('/api/users/me/profile',fields)).data;
  },
  async getMyProfile(): Promise<UserProfile> {
    if (isMockApi()) {
      await mockDelay(200);
      const username = localStorage.getItem('username') || 'quantrh';
      return {
        username,
        fullName: localStorage.getItem('fullName') || 'Quản Trị Hệ Thống',
        email: `${username}@bidv.com.vn`,
        employeeId: 'BIDV001',
        avatarColor: '#005B41',
        role: localStorage.getItem('userRole') || 'ROLE_USER',
        points: 1000,
        groupId: 'GDV',
        groupName: 'Giao dịch viên'
      };
    }

    const response = await axiosClient.get<UserProfile>('/api/users/me/profile');
    return response.data;
  }
};
