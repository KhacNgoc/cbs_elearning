# Script index PostgreSQL cho production

Script: [`scripts/sql/production-indexes.sql`](../scripts/sql/production-indexes.sql).

Rà soát ngày 23/10/2026: tổng hợp **19 index được khai báo tường minh** trong entity `@Index`, `schema.sql` và các migration hiện có. Không đề xuất thêm index mới dựa trên suy đoán hiệu năng, không bao gồm index PostgreSQL tự tạo cho PRIMARY KEY/UNIQUE. Các migration tiến độ ngày 23/10 chỉ bổ sung cột, không bổ sung index.

## Danh sách

| Bảng | Index | Cột theo thứ tự |
|---|---|---|
| users | idx_users_unit | unit_id |
| courses | idx_courses_owner_unit | owner_unit_id |
| course_unit_grants | idx_course_grants_unit | unit_id |
| course_instructors | idx_instructor_user | user_id |
| bank_questions | idx_bank_questions_course | course_id |
| lesson_reports | idx_lesson_reports_lesson | lesson_id |
| lesson_reports | idx_lesson_reports_created | created_at |
| course_ratings | ix_course_ratings_course_updated | course_id, updated_at DESC |
| notifications | idx_notifications_recipient_created | recipient_id, created_at |
| video_notes | idx_video_notes_lesson_user_time | lesson_id, user_id, timestamp_seconds |
| rag_conversations | idx_rag_conversation_owner_course_updated | user_id, course_id, updated_at |
| rag_conversation_messages | idx_rag_conversation_message_order | conversation_id, id |
| lesson_knowledge_sources | idx_lesson_knowledge_source_lesson | lesson_id |
| lesson_knowledge_sources | idx_lesson_knowledge_source_document | document_id |
| flashcards | idx_flashcards_lesson_order | lesson_id, card_order |
| flashcards | idx_flashcards_course_status | course_id, status |
| flashcards | idx_flashcards_owner_lesson | owner_user_id, lesson_id |
| flashcard_study_plans | idx_flashcard_plans_user | user_id |
| flashcard_study_plans | idx_flashcard_plans_next_reminder | next_reminder_at |

Hai index `lesson_knowledge_sources` có trong entity nhưng chưa có trong bản dump `schema.sql`. Index `course_ratings` có hướng DESC trong migration và ASC trong entity: script chấp nhận cả hai, không thay đổi index hợp lệ đang có. Kiểm tra trước đối chiếu loại btree, bảng, thứ tự cột, tính unique, predicate/expression và INCLUDE; không bắt buộc hướng sắp xếp, collation hoặc operator class.

## Chạy

Áp dụng migration tạo bảng/cột của phiên bản ứng dụng trước. Script dùng schema `public`, không tự bổ sung bảng/cột còn thiếu. Chạy từ thư mục gốc repository bằng tài khoản có quyền tạo index trên các bảng:

```powershell
psql -X -h HOST -p 5432 -U USER -d DATABASE -v ON_ERROR_STOP=1 -f scripts/sql/production-indexes.sql
```

Đổi thông tin kết nối cho môi trường triển khai. Với pgAdmin/DBeaver, bật **autocommit**, dừng khi lỗi, chạy toàn bộ script trên cùng kết nối. Không dùng BEGIN/COMMIT hoặc `--single-transaction`: `CREATE INDEX CONCURRENTLY` không chạy trong transaction block. Chỉ chạy một bản script tại một thời điểm.

Script kiểm tra trước, bổ sung index còn thiếu bằng `CONCURRENTLY IF NOT EXISTS`, rồi trả bảng 21 dòng với `status=OK` và định nghĩa thực tế. Index hợp lệ cùng tên/cấu trúc được giữ nguyên. Xây index vẫn tiêu thụ CPU/I/O; nên chạy vào khung ít tải. Tài liệu PostgreSQL: [CREATE INDEX](https://www.postgresql.org/docs/15/sql-createindex.html).

Nếu index cùng tên khác cấu trúc hoặc bị INVALID, script dừng để xử lý riêng. Không tự drop/rebuild index. Nếu tồn tại index tương đương dưới tên khác, cần đối chiếu trước để tránh tạo bản trùng. Nếu build concurrent thất bại do timeout/ngắt kết nối, kiểm tra trạng thái index; sau khi xác định đúng index lỗi có thể `DROP INDEX CONCURRENTLY public.<ten_index_loi>` ngoài transaction rồi chạy lại script. Các index đã tạo thành công trước lỗi vẫn được giữ lại.

**Khi định nghĩa index thay đổi:** `IF NOT EXISTS` không cập nhật index cũ. Cần migration riêng tạo index mới bằng tên mới, xác minh rồi mới gỡ index cũ. Không chỉ sửa danh sách cột trong script và kỳ vọng index production được thay thế.

## Kiểm chứng

Đã chạy trên database tạm riêng PostgreSQL 15, tự dọn sau kiểm tra: tạo đủ 16 index; chạy lại; chấp nhận biến thể ASC của index ratings; chặn thiếu bảng; chặn sai cấu trúc; chặn index INVALID do concurrent build thất bại; sửa index lỗi và chạy lại thành công. Fixture chỉ gồm bảng/cột tối thiểu để kiểm tra DDL, không phải benchmark dữ liệu production. Chưa thực thi script trên database ứng dụng hoặc production.
