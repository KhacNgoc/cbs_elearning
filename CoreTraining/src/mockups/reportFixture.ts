// Design fixture only. Never use this module as a fallback for production API data.
export const reportAsOf = '2026-10-15';
export { reportViews } from '../utils/reportingViews';
export type { ReportView } from '../utils/reportingViews';
const people = ['Nguyễn Minh Anh', 'Trần Hoàng Nam', 'Lê Thu Hà', 'Phạm Quang Huy', 'Vũ Ngọc Linh', 'Đỗ Đức Anh', 'Bùi Mai Phương', 'Hoàng Tuấn Kiệt', 'Ngô Bảo Ngọc', 'Đặng Thanh Tùng', 'Đinh Khánh Vy', 'Dương Hải Đăng'];
export const courseFixtures = [
  { courseName: 'Nghiệp vụ tín dụng cơ bản', program: 'Tín dụng & quản trị rủi ro', domain: 'Tín dụng', passingScore: 80 },
  { courseName: 'Kiểm soát rủi ro vận hành', program: 'Tín dụng & quản trị rủi ro', domain: 'Tín dụng', passingScore: 80 },
  { courseName: 'Giao dịch và thanh toán', program: 'Vận hành ngân hàng', domain: 'Vận hành', passingScore: 70 },
  { courseName: 'Tuân thủ & phòng chống rửa tiền', program: 'Vận hành ngân hàng', domain: 'Vận hành', passingScore: 80 },
  { courseName: 'Chăm sóc khách hàng', program: 'Dịch vụ & chuyển đổi số', domain: 'Dịch vụ & ngân hàng số', passingScore: 70 },
  { courseName: 'Ngân hàng số an toàn', program: 'Dịch vụ & chuyển đổi số', domain: 'Dịch vụ & ngân hàng số', passingScore: 80 },
];
export const reportFixture = people.flatMap((name, i) => courseFixtures.filter((_, j) => (i + j) % 3 !== 0).map((course, j) => {
  const seed = i * 4 + j;
  const date = `2026-${String(7 + (i + j) % 4).padStart(2, '0')}-05`;
  const attempts = seed % 5 === 0 ? [] : Array.from({ length: 1 + seed % 3 }, (_, n) => ({ score: 40 + ((seed * 11 + n * 17) % 61), takenAt: `${date.slice(0, 7)}-${String(7 + n * 2).padStart(2, '0')}` }));
  const score = attempts.at(-1)?.score ?? null;
  const passed = attempts.some(a => a.score >= course.passingScore);
  const candidate = [100, 65, 0, 100, 40, 85][seed % 6]!;
  const progress = candidate === 100 && !passed ? 85 : candidate;
  const completedAt = progress === 100 ? `${date.slice(0, 7)}-14` : null;
  // A deadline can never predate enrollment in this fixture.
  const candidateDue = [null, '2026-09-20', '2026-10-12', '2026-10-20', '2026-10-18'][seed % 5] ?? null;
  const dueDate = !attempts.length && candidate === 0 ? '2026-10-18'
    : candidateDue && candidateDue < date ? '2026-10-12' : candidateDue;
  return { id: `${i}-${j}`, learnerId: `HV${String(i + 1).padStart(3, '0')}`, ...course, name,
    unit: ['Hội sở', 'Chi nhánh Hà Nội', 'Chi nhánh TP. HCM'][i % 3]!,
    date, progress, score, attempts, passed, completedAt, dueDate, mandatory: ['Tín dụng', 'Vận hành'].includes(course.domain),
    status: progress === 100 ? 'Hoàn thành' : progress === 0 && !attempts.length ? 'Chưa bắt đầu' : 'Đang học',
  };
}));
export type ReportRow = typeof reportFixture[number];
