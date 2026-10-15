import { axiosClient } from "./axiosClient";
import type { OrganizationUnit } from "../types/organization";
export interface AccessRole {
  id: string;
  code: string;
  name: string;
  description: string;
  active: boolean;
  protectedRole: boolean;
  learningEnabled: boolean;
  assignedCoursesOnly: boolean;
  version: number;
  permissions: string[];
  unitIds: string[];
  userIds: number[];
}
export interface AdminPage {
  id: string;
  name: string;
  route: string;
  actions: string[];
  sortOrder: number;
}
export interface RoleMember {
  id: number;
  username: string;
  fullName: string;
  unitId?: string;
  roles: string[];
}
export const roleApi = {
  async list() {
    return (await axiosClient.get<AccessRole[]>("/api/admin/rbac/roles")).data;
  },
  async options() {
    return (await axiosClient.get<AccessRole[]>("/api/admin/rbac/role-options"))
      .data;
  },
  async members() {
    return (
      await axiosClient.get<RoleMember[]>(
        "/api/admin/rbac/roles/member-options",
      )
    ).data;
  },
  async pages() {
    return (await axiosClient.get<AdminPage[]>("/api/admin/rbac/pages")).data;
  },
  async units() {
    return (await axiosClient.get<OrganizationUnit[]>("/api/admin/rbac/units"))
      .data;
  },
  async save(r: Partial<AccessRole>) {
    return (
      await axiosClient.request<AccessRole>({
        method: r.id ? "put" : "post",
        url: `/api/admin/rbac/roles${r.id ? "/" + encodeURIComponent(r.id) : ""}`,
        data: r,
      })
    ).data;
  },
  async remove(id: string) {
    await axiosClient.delete(`/api/admin/rbac/roles/${encodeURIComponent(id)}`);
  },
  async assign(r: AccessRole, userIds: number[]) {
    return (
      await axiosClient.put<AccessRole>(
        `/api/admin/rbac/roles/${encodeURIComponent(r.id)}/members`,
        { userIds, version: r.version },
      )
    ).data;
  },
  async grants(r: AccessRole, permissions: string[], unitIds: string[]) {
    return (
      await axiosClient.put<AccessRole>(
        `/api/admin/rbac/permissions/${encodeURIComponent(r.id)}`,
        { permissions, unitIds, version: r.version },
      )
    ).data;
  },
};
