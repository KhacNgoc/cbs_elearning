import { axiosClient } from './axiosClient';
import type { BankQuestion, LessonReport } from './contentToolsApi';
import type { AdminUser, EnrollmentRequest } from './adminApi';
import type { AccessRole, RoleMember } from './roleApi';
export interface CursorPage<T> { items: T[]; nextCursor: string | null; hasMore: boolean }
export interface NumberedPage<T> { items: T[]; total: number; page: number; pageCount: number }
export interface AdminGroup { id: string; name: string; unitId: string; mandatoryCourseIds: string[]; allowedActions?: string[]; memberCount?: number }
export const adminListApi = {
  async enrollments(params: { query?: string; status?: string; size: number; cursor?: string }) {
    return (await axiosClient.get<CursorPage<EnrollmentRequest>>('/api/admin/enrollment-requests/page', { params })).data;
  },
  async bank(params: { query?: string; status?: string; difficulty?: string; size: number; cursor?: string }) {
    return (await axiosClient.get<CursorPage<BankQuestion>>('/api/admin/question-bank/page', { params })).data;
  },
  async reports(params: { query?: string; status?: string; size: number; cursor?: string }) {
    return (await axiosClient.get<CursorPage<LessonReport> & { newCount: number }>('/api/admin/content-reports/page', { params })).data;
  },
  async report(id: number) { return (await axiosClient.get<LessonReport>(`/api/admin/content-reports/${id}`)).data; },
  async users(params: { query?: string; unitId?: string; status?: string; page: number; size: number }) {
    return (await axiosClient.get<NumberedPage<AdminUser>>('/api/admin/users/page', { params })).data;
  },
  async groups(params: { query?: string; unitId?: string; page: number; size: number }) {
    return (await axiosClient.get<NumberedPage<AdminGroup>>('/api/admin/access/groups/page', { params })).data;
  },
  async groupCourses(params: { query?: string; page: number; size: number }) {
    return (await axiosClient.get<NumberedPage<{ id: string; name: string; code?: string }>>('/api/admin/access/groups/course-options', { params })).data;
  },
  async roleOption(id: string) {
    return (await axiosClient.get<AccessRole & { memberCount: number }>(`/api/admin/rbac/role-options/item/${encodeURIComponent(id)}`)).data;
  },
  async roles(params: { query?: string; status?: string; page: number; size: number; managementOnly?: boolean }) {
    return (await axiosClient.get<NumberedPage<AccessRole & { memberCount: number }>>(params.managementOnly ? '/api/admin/rbac/role-options/page' : '/api/admin/rbac/roles/page', { params })).data;
  },
  async assignRoleChanges(roleId: string, changes: { addUserIds: number[]; removeUserIds: number[]; version: number }) {
    await axiosClient.put(`/api/admin/rbac/roles/${encodeURIComponent(roleId)}/member-changes`, changes);
  },
  async roleMembers(params: { roleId: string; query?: string; page: number; size: number }) {
    return (await axiosClient.get<NumberedPage<RoleMember & { member: boolean; selectable: boolean }>>('/api/admin/rbac/roles/member-options/page', { params })).data;
  },
};
