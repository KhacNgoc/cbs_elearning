# Báo cáo đào tạo từ cơ sở dữ liệu

Từ phiên bản này, Dashboard `/admin`, tám báo cáo chi tiết và đường dẫn development `/mockups/reports` chỉ gọi dữ liệu thực. Các file fixture còn lưu phục vụ lịch sử thiết kế/kiểm thử, không được nhập vào trang báo cáo. Không thay số liệu bằng fixture khi API lỗi. Tài liệu này thay thế phần mô tả nguồn dữ liệu mockup trong `admin-reporting-mockup.md`.

## API và nguồn dữ liệu

`GET /api/admin/reports/training` trả snapshot chỉ đọc, gồm ngày hiện tại của server và các cặp học viên–khóa được ghi danh/giao bắt buộc. Chỉ lấy khóa đã xuất bản, tương tự workspace học viên. Nguồn là `users`, khóa/chương/bài học, quan hệ ghi danh/giao khóa theo nhóm/đơn vị, `user_completed_lessons`, `lesson_progress`, `user_quiz_scores`, `user_completed_courses`, hạn cá nhân và hạn khóa học. Chỉ trả DTO cần cho báo cáo; không trả mật khẩu, quyền hay đáp án.

Yêu cầu `overview:read`. Phạm vi học viên theo đơn vị của chính sách có quyền này, bao gồm đơn vị con. Chính sách giới hạn khóa được phân công chỉ cho phép các khóa giảng viên được giao. Các vai trò xem báo cáo không cần quyền sửa khóa để xem người học trong phạm vi. Không ghi hay bổ sung dữ liệu vào DB khi đọc báo cáo.

## Quy tắc

- Một dòng gốc là một học viên–khóa hiện được giao/ghi danh, không nhân lên theo số lần thi.
- Hoàn thành dùng chung `LearningPolicy.courseCompleted` với workspace học viên và đánh giá khóa: mọi chương/bài đã xuất bản hoàn thành và các bài kiểm tra bắt buộc đã đạt. Khóa không có bài kiểm tra vẫn có thể hoàn thành. Khóa rỗng chưa được coi hoàn thành.
- Điểm đại diện mỗi lượt học: trung bình điểm lần gần nhất của từng bài kiểm tra đã thi trong khóa, quy đổi thang 100. Chưa thi không tính điểm 0. Lịch sử trả từng lần thi, tên bài và ngưỡng đạt riêng. Trường hợp dữ liệu legacy chưa gắn bài học được nhóm thành bài kiểm tra cấp khóa.
- Đạt kiểm tra: mỗi bài đã thi có ít nhất một lần đạt, đồng thời các bài bắt buộc đều đạt. Đạt lần đầu yêu cầu lần đầu của từng bài đã thi đạt và không thiếu bài bắt buộc. Áp dụng cùng phép làm tròn khi xét đạt như `LearningPolicy.quizPassed`.
- Hoàn thành, trạng thái, điểm, quá hạn và tổng hợp các chiều trên frontend đều dùng `reportingMetrics.ts`; không lấy trung bình các tỷ lệ nhóm đã làm tròn, người học được đếm distinct.
- Hạn cá nhân ưu tiên hơn hạn mặc định của khóa. Không có hạn thì không đưa vào mẫu số quá hạn. Đã hoàn thành thì không quá hạn.
- Chưa lưu ngày ghi danh nên không cung cấp bộ lọc ngày ghi danh. Bộ lọc ngày mang nhãn **Hoàn thành từ/đến ngày**, mặc định bỏ trống để lấy toàn bộ. Khi chọn sẽ giới hạn vào những lượt có ngày hoàn thành đã lưu trong khoảng; không tự tạo ngày cho lịch sử còn thiếu.
- Xu hướng chỉ dùng ngày hoàn thành đã ghi nhận trong `user_completed_courses`. Người đã hoàn thành nhưng thiếu ngày vẫn nằm trong KPI không lọc ngày, không được gán vào tháng giả định.
- Đánh giá khóa vẫn gọi API riêng, có phạm vi khóa và bộ lọc riêng. Các bộ lọc tiến độ không tự áp dụng vào khối đánh giá. Báo cáo sao giữ quy tắc một đánh giá hiện hành/học viên–khóa.
- Chưa có mô hình mục tiêu và ánh xạ năng lực: không tạo điểm năng lực/khoảng cách. Chưa có dữ liệu bỏ dở thì hiển thị chưa có dữ liệu.

Các đoạn giới thiệu, banner minh họa, nhãn MOCKUP và ghi chú trạng thái lịch sử đã bỏ khỏi trang; CSV không còn nhãn dữ liệu mẫu. Giữ thông báo tải/lỗi/trống và đơn vị/mẫu số cần thiết để đọc đúng số liệu.

## Kiểm tra và chạy

Build frontend đã đạt. 56 kiểm thử backend (4 dịch vụ báo cáo, 6 đánh giá, 38 bảo mật HTTP, 8 principal detached) và 10 kiểm thử tiện ích frontend đều đạt. Kiểm thử HTTP xác nhận dữ liệu hoàn thành/hạn học đọc từ H2, viewer được xem, ngoài phạm vi không lộ dữ liệu, người học và request chưa đăng nhập bị từ chối. Chưa kiểm thử giao diện trên DB triển khai.

Cần chạy/khởi động lại backend có controller mới; frontend dùng `VITE_API_URL` hiện có. Không cần thêm bảng cho báo cáo tiến độ. Migration đánh giá khóa của bước trước vẫn áp dụng nếu chưa chạy. API snapshot hiện trả toàn bộ lượt học được phép xem; tập dữ liệu lớn cần chuyển bộ lọc/phân trang/tổng hợp xuống truy vấn SQL trước khi vận hành ở quy mô lớn.
