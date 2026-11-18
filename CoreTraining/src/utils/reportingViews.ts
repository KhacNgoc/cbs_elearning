export const reportViews = [
  { id: 'learners', label: 'Học viên', caption: 'Theo dõi từng lượt học và các trường hợp cần hỗ trợ' },
  { id: 'courses', label: 'Khóa học', caption: 'Tiến độ triển khai và kết quả theo khóa học' },
  { id: 'programs', label: 'Chương trình', caption: 'Mức độ hoàn thành theo chương trình đào tạo' },
  { id: 'units', label: 'Đơn vị', caption: 'So sánh tiến độ và số lượt quá hạn theo đơn vị' },
  { id: 'completion', label: 'Hoàn thành', caption: 'Chi tiết các trạng thái học tập' },
  { id: 'scores', label: 'Kết quả kiểm tra', caption: 'Điểm gần nhất và kết quả qua các lần thi' },
  { id: 'domains', label: 'Lĩnh vực', caption: 'Tiến độ đào tạo và kết quả kiểm tra theo lĩnh vực' },
] as const;
export type ReportView = typeof reportViews[number]['id'];

// Status detail remains reachable from Dashboard charts, without a separate menu.
export const reportMenuViews = reportViews.filter(view => view.id !== 'completion');

/**
 * The navigation id that grants a report view. Every menu view has its own permission page; the completion
 * detail is opened from the dashboard charts and belongs to the dashboard (admin.overview).
 */
export function reportPageKey(view: string): string {
  return reportMenuViews.some(item => item.id === view) ? `admin.reports.${view}` : 'admin.overview';
}
