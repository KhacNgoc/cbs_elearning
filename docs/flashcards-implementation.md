# Flashcard bài học (MVP)

Triển khai ngày 03/11/2026 trên nhánh `hieuvm_performance`. Flashcard là công cụ hỗ trợ ghi nhớ:
**không** tính vào điều kiện hoàn thành bài học hay khóa học.

## Phạm vi đã làm

| Nhóm | Nội dung |
|---|---|
| Quản trị | Tab **Flashcard** trong trình soạn bài: thêm/sửa/xóa thẻ, lọc, tìm, phát hành hoặc chuyển về nháp hàng loạt |
| AI | Panel **Tạo bằng AI** dùng nguồn kiến thức đã lưu của bài, sinh thẻ *Thuật ngữ* và *Điền khuyết*, kiểm định bằng LLM, giảng viên duyệt rồi mới lưu |
| Học viên | Nút **Flashcard** kèm tổng số thẻ trên thanh tiêu đề bài học mở modal: lật thẻ, chuyển thẻ trước/tiếp, trộn thẻ, xem vị trí **Thẻ x/n** và **Đã lướt qua y/n thẻ**. Phím tắt Space, ←, →. Trang bài học không có mục flashcard riêng; khi đánh dấu hoàn thành bài có lời nhắc **Củng cố bài vừa học** |

Thẻ AI không tự đến tay học viên: chỉ thẻ `PUBLISHED` thuộc bài, chương và khóa đã xuất bản mới hiển thị.

## Quy tắc nghiệp vụ

- Quyền xem thẻ đi theo quyền học bài (`LearningAccessService.requireLesson`), gồm cả thứ tự mở khóa bài.
- Quản trị dùng quyền trang `courses` (đường dẫn `/api/admin/lessons/{id}/flashcards`). Khóa lưu trữ hoặc đã xóa thì không sửa được thẻ.
- Thẻ điền khuyết phải có đúng chỗ trống `____` (từ 3 dấu gạch dưới) và đáp án không được lộ ở mặt trước.
- Nguồn tài liệu lưu dạng tham chiếu (không FK). Xóa tài liệu khóa học không xóa thẻ đã duyệt; tên tài liệu lấy từ DB, không tin client.
- Không lưu kết quả ghi nhớ: số thẻ đã lướt qua chỉ tồn tại trong phiên đang mở, đóng modal là bắt đầu lại.

## Gỡ lịch ôn SM-2 (04/11/2026)

Tự đánh giá Chưa nhớ/Khó/Nhớ/Dễ không phản ánh được học viên có thật sự nhớ hay không, nên flashcard chỉ còn để lật và
xem nhanh. Đã gỡ `FlashcardSchedulingPolicy`, `FlashcardReview`, API ôn, banner **Ôn tập hôm nay** trên Dashboard và trang
`/flashcards/review`.

Migration (Flyway tự chạy):
`CoreTrainingAPI/src/main/resources/db/migration/V20261104_3__remove_flashcard_reviews.sql`. Hibernate không tự xóa bảng cũ, mà
bảng này có FK tới `flashcards`, nên thiếu script thì xóa thẻ đã từng được ôn sẽ lỗi. Script xóa vĩnh viễn lịch sử ôn.

## Trang Flashcard (04/11/2026)

Trang `/flashcards` (mục **Flashcard** trên thanh điều hướng) cho học viên chọn thẻ từ nhiều bài, nhiều khóa rồi học chung một phiên.

- Chỉ liệt kê khóa học viên được học và bài đã mở khóa, cùng quy tắc với `requireLesson`. `GET /api/users/me/flashcards/library`
  chỉ trả số thẻ theo bài; thẻ được tải khi mở bài hoặc khi bắt đầu học, nên học viên nhiều khóa vẫn tải nhẹ.
- Chọn cả khóa, cả bài hoặc từng thẻ. Lựa chọn chỉ nằm ở frontend, tải lại trang là mất.
- Thêm, sửa, xóa chỉ áp dụng cho **Thẻ của tôi**. Thẻ bài học do quản trị soạn, dùng chung cho mọi học viên nên học viên không sửa được.
- Mục điều hướng được `NavigationDataInitializer` tự thêm khi backend khởi động.

## Lịch học (04/11/2026)

Trên trang Flashcard, học viên chọn thẻ rồi **Đặt lịch học**: một ngày hoặc khoảng ngày, các thứ trong tuần, giờ nhắc hoặc không nhắc.
Mỗi học viên tối đa 20 lịch, mỗi lịch kéo dài tối đa 366 ngày. Lịch có thể học ngay, tạm dừng, sửa, xóa.

- Giờ và ngày tính theo `Asia/Ho_Chi_Minh` (`FlashcardPlanSchedule`), không phụ thuộc múi giờ máy chủ hay trình duyệt.
- Nhắc qua thông báo trong app (chuông và WebSocket), loại `FLASHCARD_STUDY_REMINDER`, mở `/flashcards?plan={id}`. Chưa có email.
- `next_reminder_at` có index nên tác vụ mỗi phút chỉ đọc lịch đã đến giờ. Mỗi lần xử lý 200 lịch trong một transaction, tối đa 10 lần mỗi lượt;
  `FOR UPDATE SKIP LOCKED` để nhiều backend cùng chạy không gửi trùng.
- Bỏ lỡ không dồn: backend chậm quá 2 giờ thì bỏ lần nhắc đó và chuyển sang lần sau.
- Chọn cả bài thì thẻ mới của bài tự vào lịch; chọn từng thẻ thì giữ đúng các thẻ đó. Thẻ đã xóa và bài không còn quyền học được bỏ qua khi vào học.
- API: `GET/POST /api/users/me/flashcard-plans`, `PUT/DELETE .../{id}`, `GET .../{id}/cards`.
- Migration (Flyway tự chạy): `CoreTrainingAPI/src/main/resources/db/migration/V20261104_1__flashcard_study_plans.sql` (idempotent).
  Hibernate tự tạo bảng nhưng không nới `notifications_type_check`, nên thiếu script thì lưu nhắc học bị lỗi và tác vụ nhắc thất bại.

`GET /api/users/me/flashcards/library` kiểm tra quyền, tiến độ và thứ tự chương theo lô (`LearningAccessService.learnableCourses`,
`@BatchSize` trên khóa, chương trình, lĩnh vực, đơn vị). `FlashcardPlanQueryAuditTest` đo được 16 câu lệnh với 2 khóa và 17 câu lệnh với 32 khóa trong DB.

## Hiệu năng trang Flashcard và nhắc học (05/11/2026)

- `library` lọc khóa ngay trong SQL (`LearnerDashboardSqlRelations.learnable`), trả về scalar và phân trang theo khóa: `GET /api/users/me/flashcards/library?after={courseId}&size=30`, tối đa 50 khóa mỗi trang. `FlashcardAccessParityTest` giữ kết quả khớp `requireCourse` và `requireLesson`.
- Phiên học đọc thẻ bằng projection; tạo, sửa lịch và mở phiên chỉ đọc các khóa của bài trong lịch.
- `job.FlashcardPlanReminderJob` gửi nhắc theo batch JDBC. `FlashcardReminderConcurrencyTest` kiểm tra hai transaction cùng chạy trên PostgreSQL không gửi trùng (cần `-Dcbs.postgres.tests=true`).
- Scheduler có 2 luồng để job không chặn bước quét WebSocket.

## AI và tải hệ thống

- RAG: `POST /flashcards/jobs`, `GET /flashcards/jobs/{id}`. Dùng chung truy xuất (`retrieve_grounded_sources`), semaphore Ollama
  và trần `QUIZ_JOB_MAX_ACTIVE` với sinh đề, nên tổng tải AI không tăng thêm. Vượt trần trả 429, backend báo "Hệ thống AI đang bận".
- Kiểm tra rẻ trước khi gọi LLM kiểm định: đáp án điền khuyết phải có nguyên văn trong nguồn, thuật ngữ phải xuất hiện trong nguồn.
- Sinh và kiểm định theo pipeline như sinh đề: mỗi job chỉ xếp tối đa `QUIZ_GENERATION_CONCURRENCY` lượt sinh vào hàng đợi chung, nên các job chia đều lượt gọi model. Thẻ bị loại, kể cả ở bước kiểm định, được thay thế và ưu tiên loại thẻ còn thiếu để giữ tỉ lệ Thuật ngữ/Điền khuyết.
- Dùng chung `QUIZ_CANDIDATE_BUDGET_FACTOR` và `QUIZ_GENERATION_TIME_BUDGET_SECONDS`: hết giờ thì trả về các thẻ đã đạt kèm cảnh báo thiếu thẻ, thay vì lỗi 504.
- Đo trên máy dev (qwen3:8b): job 4 thẻ mất 40–85 giây.

## API

| API | Chức năng |
|---|---|
| GET `/api/lessons/{id}/flashcards` | Bộ thẻ đã phát hành của bài và thẻ của học viên |
| GET/POST `/api/admin/lessons/{id}/flashcards`, POST `.../batch` | Danh sách, tạo một thẻ, tạo nhiều thẻ từ bản nháp AI |
| PUT/DELETE `.../flashcards/{cardId}`, PUT `.../flashcards/status` | Sửa (khóa lạc quan theo `version`), xóa, đổi trạng thái hàng loạt |
| POST `.../flashcards/generate/jobs`, GET `.../generate/jobs/{jobId}` | Sinh bản nháp AI có tiến độ |

## Schema

Flyway tự tạo bảng `flashcards` bằng
`CoreTrainingAPI/src/main/resources/db/migration/V20261103_1__flashcards.sql` (idempotent). Cần deploy đồng thời backend, frontend và RAG service.

## Kiểm chứng

- Backend: toàn bộ 285 test qua (71 test chỉ chạy với Postgres vẫn bỏ qua như trước); thêm `FlashcardControllerContractTest`.
- RAG: 67 test qua, thêm `tests/test_flashcards.py`.
- Frontend: `vue-tsc`, `vite build`, 174 test node qua, thêm `tests/flashcards.test.mjs`.
- Chạy thật trên Docker local: sinh thẻ AI, duyệt và phát hành, thêm thẻ thủ công, học thẻ trong bài học.

## Thẻ cá nhân của học viên (04/11/2026)

Học viên tự viết thẻ cho từng bài: nút **Flashcard** trên thanh tiêu đề luôn hiện (kể cả bài chưa có thẻ), modal có màn
**Thêm / sửa thẻ của tôi** để thêm/sửa/xóa thẻ thuật ngữ hoặc điền khuyết. Bài chưa có thẻ nào thì modal mở thẳng vào màn này.

- Modal không chia tab: thẻ bài học và thẻ của học viên học chung một phiên, mỗi thẻ có nhãn **Thẻ bài học** hoặc
  **Thẻ của tôi** trên cả hai mặt.
- Nút **Trộn thẻ** chỉ đảo các thẻ phía sau thẻ đang xem, tắt thì trở lại thứ tự của bộ thẻ. Thứ tự chỉ nằm ở frontend.
  Lựa chọn được giữ trong modal đến khi rời trang bài học.

- Dữ liệu: dùng chung bảng `flashcards`, thêm cột `owner_user_id` (NULL = thẻ của bài) và origin `PERSONAL`. Thẻ cá nhân
  luôn `PUBLISHED`, không cần duyệt.
- Mọi truy vấn phía quản trị lọc `owner IS NULL`; admin không thấy, không sửa và không phát hành được thẻ của học viên,
  `maxCardOrder` không bị ảnh hưởng. Admin gửi origin `PERSONAL` bị từ chối.
- Bộ thẻ của bài = thẻ của bài theo thứ tự soạn, sau đó là thẻ của học viên theo thứ tự tạo. `Card` có thêm `mine`
  và `version` (chỉ có trên thẻ của mình).
- Thẻ của người khác trả 404 khi sửa hoặc xóa. Tối đa 200 thẻ cá nhân mỗi bài. Xóa thẻ không cần còn quyền vào bài.
- API: `POST /api/lessons/{id}/flashcards/mine`, `PUT`/`DELETE /api/flashcards/mine/{cardId}` (sửa có khóa lạc quan theo `version`).
- Migration (Flyway tự chạy):
  `CoreTrainingAPI/src/main/resources/db/migration/V20261104_2__personal_flashcards.sql` (idempotent). Hibernate tự thêm cột
  nhưng không nới ràng buộc `flashcards_origin_check` nó đã sinh, nên thiếu script này thì tạo thẻ cá nhân lỗi 409.
- Test: `PersonalFlashcardTest`, bổ sung `FlashcardControllerContractTest`, `upsertCard` trong `tests/flashcards.test.mjs`.

## Chưa làm (giai đoạn sau)

Thẻ từ câu làm sai, thẻ quy trình từ `ProcedureSection`, tạo thẻ cá nhân từ ghi chú video, bôi đen văn bản, chatbot,
đánh dấu thẻ lỗi thời khi tài liệu đổi, báo lỗi thẻ, thống kê thẻ khó, nhắc học qua email.
