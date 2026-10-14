# Báo cáo nội dung và ngân hàng câu hỏi

## Báo cáo bài học

- Học viên mở **Báo cáo nội dung không phù hợp** ngay trước phần thông tin bài học.
- Nội dung bắt buộc, tối đa 5.000 ký tự; ảnh không bắt buộc, tối đa 3 PNG/JPEG, 5 MB/ảnh và 20 triệu điểm ảnh. Máy chủ giải mã và mã hóa lại ảnh thành PNG.
- Chỉ học viên có quyền học bài đó mới gửi được báo cáo. Ảnh chỉ được đọc bởi người gửi hoặc người có quyền quản trị khóa liên quan; không cung cấp đường dẫn tệp công khai.
- Quản trị viên toàn hệ thống và người có quyền đọc quản trị khóa liên quan nhận thông báo sau khi transaction lưu thành công. Thông báo mở trực tiếp báo cáo.
- Menu **Báo cáo nội dung**: `/admin/content-reports`, sử dụng quyền `courses:read` và giới hạn phạm vi theo khóa. Xử lý cần `courses:update` trên khóa tương ứng.
- Trạng thái: Mới gửi → Đang xem xét / Đã xử lý / Không ghi nhận. Khi đóng báo cáo phải ghi kết quả. Phiên bản dữ liệu ngăn người xử lý ghi đè thay đổi của nhau.

## Ngân hàng câu hỏi

- Menu **Ngân hàng câu hỏi**: `/admin/question-bank`, dùng quyền `exams:read/create/update` theo phạm vi khóa học.
- Mỗi câu có mã duy nhất, nội dung, loại một/nhiều đáp án, 3–6 lựa chọn, đáp án đúng, giải thích, mức độ Dễ/Trung bình/Khó, bài học liên quan, lĩnh vực và trạng thái.
- Khóa học quản lý xác định phạm vi quyền và lĩnh vực liên quan. Có thể gán thêm một bài học trong khóa, hoặc để trống để phân loại theo lĩnh vực của khóa.
- Trạng thái: Bản nháp, Đang sử dụng, Ngừng sử dụng. Chỉ câu đang sử dụng được thêm vào bài kiểm tra; khóa đích phải ở trạng thái nháp.
- Trong chi tiết bài kiểm tra: **Lưu vào ngân hàng** đưa câu hiện có vào ngân hàng, giữ nội dung và giải thích. Lưu lại cùng câu không tạo mục ngân hàng trùng.
- **Ngân hàng câu hỏi** trong thanh công cụ bài kiểm tra cho phép chọn nhiều câu để thêm. Cần quyền đọc khóa nguồn và tạo câu hỏi tại khóa đích. Không thêm lại cùng mục ngân hàng vào cùng bài kiểm tra.
- Câu được sao chép độc lập, ghi lại ID/mã nguồn ngân hàng. Sửa/ngừng sử dụng câu ngân hàng không sửa nội dung, đáp án hoặc kết quả của bài kiểm tra đã dùng câu đó.
- Câu hỏi cũ vẫn giữ nguyên; chuyển vào ngân hàng bằng thao tác lưu từ bài kiểm tra khi cần dùng lại.

### Chuyển hàng loạt câu hỏi có sẵn

Chỉ cần chạy toàn bộ một file `scripts/sql/backfill-question-bank.sql` bằng PostgreSQL/psql. File này tự bổ sung cấu trúc ngân hàng nếu thiếu, rồi chuyển câu hỏi trong cùng một transaction; không cần chạy migration ngân hàng riêng. Ví dụ:

```powershell
psql -X -U postgres -d coretraining -v ON_ERROR_STOP=1 -f "D:/psnPrj/cbs_elearning/scripts/sql/backfill-question-bank.sql"
```

Thay tài khoản và tên cơ sở dữ liệu cho đúng môi trường. Script tự quản lý transaction và in số câu vừa chuyển/số câu đã có liên kết. Câu mới có mã `MIG-Q-<ID nguồn>`, mức `MEDIUM`, trạng thái `ACTIVE`; lĩnh vực được suy ra qua khóa học, bài học lấy từ câu nguồn hoặc bài kiểm tra. Nội dung, giải thích và thứ tự lựa chọn được giữ nguyên; đáp án lấy từ `question_correct_answers`, dự phòng bằng `questions.correct_answer` cho dữ liệu cũ.

Ngoài việc bổ sung cấu trúc còn thiếu, script chỉ thêm mục ngân hàng và ghi liên kết `bank_question_id/bank_question_code` vào câu nguồn. Câu đã có liên kết hợp lệ không bị ghi đè, kể cả mục ngân hàng đã chỉnh sửa hoặc ngừng sử dụng. Chạy lại không tạo thêm mục. Liên kết hỏng, mã `MIG-Q-*` bị chiếm hoặc dữ liệu câu hỏi không hợp lệ sẽ làm toàn bộ transaction thất bại và nêu ID/mã cần kiểm tra.

Chỉ file script được tạo; chưa chuyển dữ liệu thật. Bộ kiểm thử `scripts/sql/tests/test-question-bank-backfill.ps1` dùng bảng TEMP để kiểm tra PostgreSQL thực mà không thay đổi dữ liệu ứng dụng.

## Cập nhật cơ sở dữ liệu

Migration PostgreSQL: `CoreTrainingAPI/src/main/resources/migrations/20261014_content_reports_question_bank.sql`.

Migration bổ sung các bảng, cột và cập nhật ràng buộc `notifications_type_check` để chấp nhận `LESSON_REPORT`. Hibernate `ddl-auto=update` không bảo đảm cập nhật ràng buộc enum cũ, nên cần chạy script trước khi dùng bản backend mới. Script có transaction, giữ dữ liệu cũ và có thể chạy lại.

Migration đã được áp dụng cho container PostgreSQL cục bộ `coretraining-db` trong phiên triển khai này. Không khởi động lại backend; người dùng tự build/chạy như đã yêu cầu. Với môi trường khác, chạy migration tương ứng trước khi khởi động bản mới.

Ảnh báo cáo lưu tại thư mục con `lesson-reports` của `app.storage.documents-directory`. Đưa thư mục này vào bản sao lưu cùng cơ sở dữ liệu.

## Kiểm chứng

- `npm run build` trong `CoreTraining`: kiểm tra TypeScript và build giao diện.
- `mvn test` với JDK 17 trong `CoreTrainingAPI`: bộ kiểm thử hiện có và `ContentToolsIntegrationTest` (9 kiểm thử mới).
- Trên Windows, khi JDK gặp lỗi socket với đường dẫn tạm dài, dùng `-DargLine=-Djdk.net.unixdomain.tmpdir=D:/psnPrj/cbs_elearning/tmp/java` cho Maven.
- Kiểm thử tích hợp mới không có transaction bao ngoài và tắt Open Session in View: kiểm tra báo cáo kèm ảnh, thông báo, quyền đọc ảnh, dữ liệu ảnh không hợp lệ, dọn tệp khi rollback, giới hạn ảnh, khóa bài học, xử lý ngoài phạm vi, kiểm soát phiên bản, metadata ngân hàng, sao chép độc lập, trạng thái ngừng sử dụng, chống thêm trùng và phân quyền.
- Giao diện đã được thử bằng các component thực với adapter dữ liệu mô phỏng độc lập: biểu mẫu tạo câu, gửi báo cáo và lưu kết quả xử lý. Không gửi báo cáo hoặc thông báo kiểm thử vào dữ liệu người dùng cục bộ.
