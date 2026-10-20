# Triển khai trang chủ học viên, hồ sơ và ảnh khóa học

Đã triển khai trong source ngày 14/10/2026. Header hiện tại được giữ nguyên: bấm avatar/tên để tới `/profile`. Không thêm menu hồ sơ riêng.

## Giao diện và dữ liệu

- Trang chủ: lời chào, tiếp tục/bắt đầu học, tổng tiến độ trên các khóa đang tham gia; hai tab Khóa học của tôi / Khám phá & đăng ký. Thẻ có ảnh, trạng thái, nguồn tham gia, bắt buộc, số bài/%, hạn và hành động phù hợp. Catalog và chờ duyệt không nằm trong mẫu số tiến độ.
- Tìm tên/mã/mô tả/từ khóa không phân biệt dấu; lọc AND theo lĩnh vực, phân hệ, chương trình, mức độ, nguồn tham gia, bắt buộc, trạng thái. Lĩnh vực/chương trình dùng ID thật. Phân trang 6/9/12 khóa; sắp theo hạn, gần đây hoặc tên. Tổng tiến độ không đổi khi lọc.
- Hồ sơ: các tab Thông tin người dùng / Thống kê học tập / Kết quả kiểm tra / Thành tích. Thông tin gồm đơn vị. Điểm lần gần nhất và cao nhất tách biệt theo phiên bản đề; lịch sử lấy QuizAttempt của chính người dùng, không trả đáp án. Các điểm UserQuizScore cũ chưa có attempt được giữ riêng dưới Kết quả lưu trước đây.
- Radar lấy lĩnh vực từ khóa tham gia và điểm từ attempt thật: bình quân điểm tốt nhất trên từng bài đánh giá ở phiên bản đã thi gần nhất. Không suy năng lực từ tiến độ. Chưa thi hiển thị Chưa đánh giá; dưới ba lĩnh vực có điểm thì hiển thị danh sách thay radar.
- Dashboard, trang khóa học dùng chung bộ phân giải resume máy chủ, kiểm tra thứ tự mở khóa. Bài chưa hoàn thành gần nhất → kiểm tra bắt buộc còn thiếu nếu nội dung đã xong → bài kế tiếp được mở khóa. Khi không có lịch sử bắt đầu từ bài đầu hợp lệ. Toàn khóa hoàn thành hiển thị Xem lại.
- Bookmark lưu máy chủ gồm contentType, documentId, documentPage, scrollOffset, vị trí video và updatedAt theo người dùng/bài. PDF dùng PDF.js với điều hướng trang; Word khôi phục trang/scroll, PowerPoint khôi phục slide. Vị trí video giữ nguyên khi chuyển đọc tài liệu. Định dạng DOC/PPT cũ vẫn có giới hạn preview như trước.

## Quản trị và giảng viên

Trong form Tạo khóa học / Thông tin khóa đã bổ sung:

1. Phân hệ nghiệp vụ, mức độ, từ khóa.
2. Hạn hoàn thành mặc định; để trống nếu không quy định.
3. Tải ảnh JPEG/PNG tối đa 5 MB, xem trước, thay hoặc bỏ ảnh. Chọn chương trình trước khi tải ảnh cho khóa mới. Quyền tạo/sửa và phạm vi chương trình/khóa được giữ theo RBAC hiện có; giảng viên cần quyền tương ứng.

Ảnh được kiểm tra bằng bộ giải mã, giới hạn 20 triệu pixel, rồi lưu lại thành PNG tên UUID. Không tin phần mở rộng/MIME do client gửi. Đọc ảnh cần đăng nhập và quyền xem khóa hoặc là người đã tải ảnh. Ảnh lưu tại `<DOCUMENT_STORAGE_PATH>/course-images`, nằm trong volume tài liệu hiện có. Khóa không có ảnh hoặc ảnh không tải được dùng biểu tượng/tên khóa làm placeholder.

Trong Tài khoản → Sửa học viên đã bổ sung Hạn hoàn thành theo học viên. Chỉ các khóa đã tham gia xuất hiện; quản trị viên trong phạm vi được phép có thể lưu hạn riêng hoặc trở về hạn mặc định. Hạn riêng ưu tiên hơn hạn mặc định; không tự sinh hạn cho dữ liệu cũ.

## API và schema

| API | Chức năng |
|---|---|
| GET `/api/users/me/learning` | Catalog trong phạm vi, trạng thái/tiến độ theo học viên, bookmark resume, attempt và điểm cũ |
| GET `/api/courses/{id}/outline` | Bổ sung resume đầy đủ bên cạnh resumeLessonId |
| POST `/api/users/me/lessons/{id}/progress` | Bổ sung bookmark tài liệu, loại nội dung; giữ các giá trị video không được gửi lên |
| POST `/api/admin/courses/images` | Multipart file; courseId cho sửa hoặc programId cho tạo |
| GET `/api/course-images/{uuid}.png` | Phục vụ ảnh có kiểm tra quyền |
| GET/PUT `/api/admin/users/{id}/course-deadlines` | Đọc và lưu hạn riêng; dueDate=null khôi phục mặc định |

Hibernate hiện dùng ddl-auto=update ở profile PostgreSQL nên thêm cột/bảng khi backend khởi động lại. Với môi trường quản lý schema thủ công, có script idempotent `CoreTrainingAPI/src/main/resources/migrations/20261014_learner_dashboard.sql`. Chưa chạy migration trên production, chưa deploy hoặc khởi động lại dịch vụ thực tế trong đợt này. Cần build/cập nhật cả frontend và backend cùng nhau vì frontend sử dụng API học tập mới. Dashboard mới dùng API thật; không sử dụng mockData làm nguồn số liệu.

## Kiểm chứng

- Frontend: TypeScript + Vite production build thành công; PDF viewer được tách thành chunk tải khi cần.
- Frontend logic: 5 kiểm thử học tập mới và 9 kiểm thử cây tổ chức thành công.
- Backend: 56 kiểm thử trong các nhóm AccessSecurityIntegrationTest, LearnerWorkspaceServiceTest, EmptyQuizPolicyTest, DocumentDeletionTest, PermissionCacheTest, QuizGenerationSourceTest, RagChatErrorTest thành công (37 + 4 + 3 + 5 + 2 + 4 + 1). Sau khi nối resume vào trang khóa học, đã chạy lại 41 kiểm thử quyền truy cập/resume và đều thành công.
- Phủ kiểm thử mới: catalog theo phạm vi; đang học ở 0% khi có hoạt động; bookmark tài liệu và bảo toàn video; hạn riêng/mặc định và scope; upload ảnh thật/ảnh giả/quyền đọc; tạo khóa lưu ảnh và metadata; resume bỏ qua bài hoàn thành, tránh bài khóa, chuyển quiz bắt buộc và khóa rỗng; điểm tốt nhất theo tỷ lệ/phiên bản và năng lực chưa đánh giá.
- Toàn bộ suite còn 8 lỗi ở LessonKnowledgeSourceServiceTest do Java không tạo được máy chủ HttpServer loopback (`Unable to establish loopback connection`) trong môi trường thực thi, kể cả sau khi thử cấu hình IPv4. Các lỗi không phải assertion của phần triển khai mới.
- Chưa kiểm tra hình ảnh/luồng tương tác bằng trình duyệt thực tế: trình duyệt được cung cấp không truy cập được localhost của workspace. Build và kiểm thử API/logic không thay thế kiểm thử trực quan.

## Bản sửa lỗi tải dashboard ngày 14/10/2026

Log lúc 10:44:46 của tài khoản test01 cho thấy GET `/api/users/me/learning` trả HTTP 500 vì `LazyInitializationException` ở `User.registeredCourses`. Đối tượng người dùng trong authentication principal được tải ở transaction của security filter; các collection học tập chưa được khởi tạo và đối tượng đã detached khi service đọc chúng. Transaction của service không tự gắn lại principal đó.

Đã sửa LearnerWorkspaceService để lấy ID từ principal rồi đọc lại UserRepository trong transaction hiện tại trước khi đọc registeredCourses, completedLessons, quizScores và courseDeadlines. Không chuyển các quan hệ sang EAGER hoặc dựa vào Open Session in View để che lỗi.

Đã chạy lại 37 kiểm thử quyền truy cập và 4 kiểm thử workspace thành công. Thêm LearnerWorkspaceDetachedUserTest không có transaction bao quanh phương thức kiểm thử, tắt Open Session in View: xác nhận principal có các collection chưa khởi tạo, truy cập ngoài session thực sự gây LazyInitializationException, rồi xác nhận service trả được khóa đăng ký, hạn riêng và điểm tiếp tục. Kiểm thử hồi quy này đã thành công. Cần build/cập nhật và khởi động lại backend đang chạy để áp dụng bản sửa.
