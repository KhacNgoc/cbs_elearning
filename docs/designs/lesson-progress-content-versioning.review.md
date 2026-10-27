# Rà soát thiết kế phiên bản tiến độ

- Ngày: 2026-10-26, Asia/Saigon.
- Tài liệu: [Giải pháp đầy đủ](lesson-progress-content-versioning.md).
- Phương pháp: hai reviewer EGM ở ngữ cảnh độc lập, một người đánh giá thiết kế theo feature-spec-standard và một người đánh giá khả năng triển khai theo implementation-readiness-standard. Mỗi reviewer xác nhận đọc đủ persona, tiêu chuẩn và tài liệu; chỉ đọc mã liên quan, không sửa mã hoặc chạy build/test.
- Đây là hai rà soát có trọng tâm, không phải tuyên bố đã chạy toàn bộ pipeline năm giai đoạn của EGM.

## Các phát hiện và cách giải quyết

| Reviewer | Mức độ ban đầu | Phát hiện | Thay đổi trong bản cuối |
|---|---|---|---|
| Thiết kế và khả năng triển khai | Major | Transaction đọc ở READ COMMITTED chưa đảm bảo session quiz cùng snapshot | Khóa chia sẻ lesson trước khi đọc phiên bản, cấu hình và câu hỏi, giữ đến khi dựng DTO; writer khóa ghi tương ứng; thêm kiểm thử PostgreSQL race |
| Thiết kế | Major | Xem lại điểm cũ với câu hỏi/điểm đạt hiện tại làm sai lịch sử | Review dùng snapshot của QuizAttempt và quyền sở hữu, attemptId/version/revision/historical; legacy thiếu snapshot trả 422 |
| Khả năng triển khai | Major | Chưa phân loại thay đổi nội dung quy trình đang hiển thị | Thêm tiêu đề mục và bước theo thứ tự vào so sánh/snapshot; sửa/thêm/xóa/reorder đều tăng phiên bản; có test no-op |
| Khả năng triển khai | Major | Chưa có lifecycle cho Quiz.revision | Quiz mới revision 1; semantic quiz update tăng một lần cùng transaction; no-op không tăng; giữ Quiz rỗng để không mất attempts |
| Khả năng triển khai | Major | Thao tác tăng version thủ công chưa có contract và thiếu file | Chốt POST admin content-version, body version kỹ thuật, response LessonDto, quyền sửa, stale409, editor không có thay đổi chưa lưu; thêm AdminService/adminApi/DTO vào phạm vi |

Các điểm bổ sung khi đối chiếu mã: dùng currentQuizPassed thay điểm aggregate lịch sử cho điều hướng quiz; cập nhật quizStore; không bỏ qua mọi thay đổi cấu trúc khóa học chỉ vì có timestamp hoàn thành; migration dùng PostgreSQL test riêng và profile rõ ràng.

## Kết quả tái kiểm tra

- Reviewer thiết kế: hai Major ban đầu đã được giải quyết; không còn Blocker/Major trong phạm vi tái kiểm tra.
- Reviewer khả năng triển khai: bốn Major ban đầu đã được giải quyết; không còn Blocker/Major trong phạm vi tái kiểm tra.
- Một lần gọi reviewer ban đầu gặp lỗi năng lực mô hình trước khi đánh giá; đã gọi lại và nhận được báo cáo hoàn chỉnh. Không tính lần lỗi là một lượt review sạch.
- Bản cuối vẫn là đề xuất cần duyệt; kết quả review không chứng minh mã triển khai đúng.

## Những giới hạn vẫn giữ rõ trong thiết kế

1. Migration bảo thủ giữ tiến độ hiện có ở version 0; người chưa hoàn thành bắt đầu lại ở version 1. Không thể chứng minh dữ liệu cũ thuộc video nào chỉ từ completionVersion cũ.
2. Thành tích đã cấp vẫn giữ nguyên, nhưng lịch sử nội dung cũ có thể không có đủ snapshot hoặc ngày cấp. Không tự tạo dữ kiện lịch sử.
3. Versioning không tự phát hiện nhà cung cấp thay byte nội dung sau cùng một URL; có thao tác đánh dấu thay đổi do quản trị viên thực hiện.
4. Bộ đếm giây, mức tin cậy metadata từ client và giới hạn anti-cheat không được tuyên bố đã giải quyết chỉ bằng versioning.
5. Lượt này chỉ rà soát mã và thiết kế; không chạy lại bộ kiểm thử ứng dụng. Các kết quả test YouTube trước đây được tài liệu ghi rõ là baseline từ lượt sửa trước.
