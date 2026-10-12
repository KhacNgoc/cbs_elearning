export const roleNames: Record<string,string> = {
  ROLE_USER:'Học viên',ROLE_INSTRUCTOR:'Giảng viên / quản lý nội dung',ROLE_UNIT_ADMIN:'Quản trị viên đơn vị',ROLE_REPORT_VIEWER:'Lãnh đạo / người xem báo cáo',ROLE_ADMIN:'Quản trị viên hệ thống'
};
export const roleLabel=(roles: string[])=>roles.map(role=>roleNames[role] || role).join(', ');
