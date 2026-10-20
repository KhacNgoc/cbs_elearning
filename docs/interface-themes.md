# Giao diện sáng và tối

- Nút mặt trăng/mặt trời trên thanh điều hướng chuyển sáng/tối. Màn hình đăng nhập và đổi mật khẩu có nút ở góc trên phải.
- Nút hình màn hình nhỏ cạnh nút chuyển màu khôi phục chế độ theo thiết bị. Mặc định ứng dụng dùng lựa chọn này.
- Lựa chọn được lưu tại trình duyệt bằng khóa `bidv-color-theme`, đồng bộ giữa các tab. Khi lưu trữ bị chặn, lựa chọn vẫn có hiệu lực trong phiên hiện tại.
- `index.html` áp dụng màu trước khi tải ứng dụng để hạn chế nháy nền trắng. Logic khi chạy nằm trong `src/composables/useTheme.ts`.

## Thêm hoặc sửa giao diện

Dùng các biến `--bg-main`, `--bg-card`, `--bg-subtle`, `--text-main`, `--text-muted`, `--border-color` cho nền, chữ và đường viền. `--primary-color` dùng cho nút có chữ trắng; `--primary-ink` dùng cho chữ hoặc biểu tượng trên nền thông thường. Các bộ `--danger-*`, `--warning-*`, `--success-*`, `--info-*`, `--purple-*` có ba vai trò `ink`, `surface`, `border`.

`theme.css` định nghĩa các màu theo vai trò. `theme-palette.css` ánh xạ những màu cũ trong các thành phần sang bảng màu tối, đồng thời giữ giá trị gốc ở giao diện sáng. Thành phần mới nên dùng trực tiếp biến theo vai trò thay vì bổ sung màu cố định.

Biểu đồ canvas cần nhận màu và tùy chọn phản ứng theo `useTheme()`. Tài liệu Word/PowerPoint, PDF và hình ảnh giữ màu nội dung gốc.

## Kiểm tra

Chạy `npm run test:theme` và `npm run build` trong thư mục `CoreTraining`. Kiểm thử tự động xác minh mặc định theo thiết bị, ưu tiên lựa chọn thủ công, lưu lựa chọn, đồng bộ tab, khôi phục theo thiết bị và trường hợp bộ nhớ bị chặn.
