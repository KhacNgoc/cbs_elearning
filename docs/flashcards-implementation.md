# Flashcard bài học (MVP)

Triển khai ngày 03/11/2026 trên nhánh `hieuvm_performance`. Flashcard là công cụ hỗ trợ ghi nhớ:
**không** tính vào điều kiện hoàn thành bài học hay khóa học.

## Phạm vi đã làm

| Nhóm | Nội dung |
|---|---|
| Quản trị | Tab **Flashcard** trong trình soạn bài: thêm/sửa/xóa thẻ, lọc, tìm, phát hành hoặc chuyển về nháp hàng loạt |
| AI | Panel **Tạo bằng AI** dùng nguồn kiến thức đã lưu của bài, sinh thẻ *Thuật ngữ* và *Điền khuyết*, kiểm định bằng LLM, giảng viên duyệt rồi mới lưu |
| Học viên | Nút **Flashcard** kèm số thẻ cần ôn trên thanh tiêu đề bài học mở modal ôn ngay tại chỗ: lật thẻ, tự đánh giá Chưa nhớ/Khó/Nhớ/Dễ, phím tắt Space và 1–4. Trang bài học không có mục flashcard riêng; khi đánh dấu hoàn thành bài có lời nhắc **Củng cố bài vừa học** |
| Ôn tập | Lặp lại ngắt quãng kiểu SM-2; banner **Ôn tập hôm nay** trên Dashboard; trang `/flashcards/review?courseId=` ôn thẻ đến hạn theo khóa |

Thẻ AI không tự đến tay học viên: chỉ thẻ `PUBLISHED` thuộc bài, chương và khóa đã xuất bản mới hiển thị.

## Quy tắc nghiệp vụ

- Quyền xem thẻ đi theo quyền học bài (`LearningAccessService.requireLesson`), gồm cả thứ tự mở khóa bài.
- Quản trị dùng quyền trang `courses` (đường dẫn `/api/admin/lessons/{id}/flashcards`). Khóa lưu trữ hoặc đã xóa thì không sửa được thẻ.
- Thẻ điền khuyết phải có đúng chỗ trống `____` (từ 3 dấu gạch dưới) và đáp án không được lộ ở mặt trước.
- Nguồn tài liệu lưu dạng tham chiếu (không FK). Xóa tài liệu khóa học không xóa thẻ đã duyệt; tên tài liệu lấy từ DB, không tin client.
- Xóa thẻ sẽ xóa luôn lịch ôn của học viên với thẻ đó.

## Lịch ôn (SM-2)

`FlashcardSchedulingPolicy`: *Chưa nhớ* → ôn lại sau 10 phút, giảm hệ số dễ; *Khó/Nhớ/Dễ* → 1, 3 ngày rồi nhân theo hệ số dễ, tối đa 365 ngày.
Đánh giá tốt hơn không bao giờ cho lịch sớm hơn. Thẻ có khoảng ôn từ 21 ngày được tính là **Đã thuộc**.
`dueAt` trả về dạng instant (có `Z`) để trình duyệt ở múi giờ khác máy chủ so sánh đúng.

## AI và tải hệ thống

- RAG: `POST /flashcards/jobs`, `GET /flashcards/jobs/{id}`. Dùng chung truy xuất (`retrieve_grounded_sources`), semaphore Ollama
  và trần `QUIZ_JOB_MAX_ACTIVE` với sinh đề, nên tổng tải AI không tăng thêm. Vượt trần trả 429, backend báo "Hệ thống AI đang bận".
- Kiểm tra rẻ trước khi gọi LLM kiểm định: đáp án điền khuyết phải có nguyên văn trong nguồn, thuật ngữ phải xuất hiện trong nguồn.
- Lần sinh lại ưu tiên loại thẻ còn thiếu để giữ tỉ lệ Thuật ngữ/Điền khuyết.
- Đo trên máy dev (qwen3:8b): job 4 thẻ mất 40–85 giây.

## API

| API | Chức năng |
|---|---|
| GET `/api/lessons/{id}/flashcards` | Bộ thẻ đã phát hành của bài kèm lịch ôn và thống kê của học viên |
| POST `/api/flashcards/{id}/reviews` | Ghi nhận đánh giá `{rating}` và trả lịch ôn mới |
| GET `/api/users/me/flashcards/summary` | Số thẻ đến hạn theo khóa (cho Dashboard) |
| GET `/api/users/me/flashcards/due?courseId=&limit=` | Hàng đợi thẻ đến hạn của một khóa (tối đa 50) |
| GET/POST `/api/admin/lessons/{id}/flashcards`, POST `.../batch` | Danh sách, tạo một thẻ, tạo nhiều thẻ từ bản nháp AI |
| PUT/DELETE `.../flashcards/{cardId}`, PUT `.../flashcards/status` | Sửa (khóa lạc quan theo `version`), xóa, đổi trạng thái hàng loạt |
| POST `.../flashcards/generate/jobs`, GET `.../generate/jobs/{jobId}` | Sinh bản nháp AI có tiến độ |

## Schema

Hibernate `ddl-auto=update` tự tạo bảng `flashcards` và `flashcard_reviews`. Môi trường quản lý schema thủ công dùng
`CoreTrainingAPI/src/main/resources/migrations/20261103_flashcards.sql` (idempotent). Cần deploy đồng thời backend, frontend và RAG service.

## Kiểm chứng

- Backend: toàn bộ 285 test qua (71 test chỉ chạy với Postgres vẫn bỏ qua như trước); thêm `FlashcardSchedulingPolicyTest`, `FlashcardControllerContractTest`.
- RAG: 67 test qua, thêm `tests/test_flashcards.py`.
- Frontend: `vue-tsc`, `vite build`, 174 test node qua, thêm `tests/flashcards.test.mjs`.
- Chạy thật trên Docker local: sinh thẻ AI, duyệt và phát hành, thêm thẻ thủ công, ôn thẻ trong bài học, banner Dashboard và trang ôn theo khóa.

## Chưa làm (giai đoạn sau)

Thẻ từ câu làm sai, thẻ quy trình từ `ProcedureSection`, thẻ cá nhân (ghi chú video, bôi đen văn bản, chatbot),
đánh dấu thẻ lỗi thời khi tài liệu đổi, báo lỗi thẻ, thống kê thẻ khó, nhắc ôn qua thông báo, chuyển sang FSRS.
