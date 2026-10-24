# Phân trang thông báo

Ngày 23/10/2026. Trước đây API trả tối đa 50 thông báo và giao diện hiển thị toàn bộ, không tải tiếp lịch sử cũ hơn.

- `GET /api/notifications?size=6&beforeId=<id>` trả `{content,nextBeforeId,hasMore,unreadCount}`. Mặc định 6, size giới hạn 1–50. Frontend và backend cần cập nhật cùng nhau vì response đã đổi từ array sang object.
- Thứ tự `createdAt DESC, id DESC`; cursor là ID cuối trang trước, backend lấy thời gian của thông báo thuộc đúng tài khoản để tải những bản ghi cũ hơn. Dùng limit + 1 để xác định còn dữ liệu, không tải toàn bộ rồi cắt ở frontend.
- Giao diện ban đầu tải 6, nút **Tải thêm** lấy tiếp 6. Giữ thông báo realtime và lọc ID trùng; không đổi cursor theo thông báo mới. Nút khóa khi đang tải, ẩn khi hết; lỗi cho phép thử lại đúng cursor.
- Badge đếm chưa đọc của toàn bộ lịch sử. **Đã đọc tất cả** dùng bulk update theo tài khoản, gồm các thông báo chưa tải và hơn 50 bản ghi. Response trang cũ đang chờ không làm các thông báo đã đọc tất cả trở lại trạng thái chưa đọc.
- Kiểm chứng: 3 kiểm thử backend H2 và 2 kiểm thử giao diện JSDOM đạt; TypeScript/Vite build đạt. Bao gồm timestamp trùng, thêm thông báo giữa các trang, cursor của tài khoản khác, bulk read hơn 50, tải lỗi/thử lại và chống bấm tải thêm lặp. Chưa kiểm tra trực quan trình duyệt thật hoặc PostgreSQL production.

Chạy kiểm thử giao diện từ `CoreTraining`: `node --test tests/notificationCenter.test.mjs`.
