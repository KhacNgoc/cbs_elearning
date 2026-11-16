# Load test chatbot và sinh đề

Script [ai-concurrency.js](ai-concurrency.js) dùng [k6](https://k6.io) để tăng dần số request đồng thời vào backend Java, từ đó tìm mức mà chatbot và sinh đề bắt đầu lỗi hoặc quá chậm.

Mỗi VU (virtual user) gửi request liên tục, không nghỉ giữa các lượt, nên **số VU ở mỗi bậc chính là số request đồng thời**.

## Chuẩn bị

- Cài k6: `brew install k6`.
- Chạy trên **staging hoặc máy có cấu hình giống production**. Chatbot ghi hội thoại vào DB, nên dùng tài khoản test riêng.
- Chuẩn bị một tài khoản học viên đã được ghi danh vào khóa có tài liệu đã ingest, và một tài khoản admin để sinh đề.
- Lần đầu Ollama phải nạp model nên rất chậm. Gửi thử 1–2 câu chat trước khi đo, hoặc bỏ qua kết quả bậc đầu tiên.

## Chạy

Chatbot, tăng dần 1 → 16 request đồng thời, mỗi bậc giữ 2 phút:

```bash
k6 run -e SCENARIO=chat -e BASE_URL=http://localhost:8080 -e CHAT_USERNAME=learner01 -e CHAT_PASSWORD=*** -e COURSE_ID=<courseId> --summary-export=chat-summary.json scripts/loadtest/ai-concurrency.js
```

Sinh đề, tăng dần 1 → 5 job đồng thời, mỗi job 10 câu:

```bash
k6 run -e SCENARIO=quiz -e BASE_URL=http://localhost:8080 -e ADMIN_USERNAME=admin -e ADMIN_PASSWORD=*** -e COURSE_ID=<courseId> --summary-export=quiz-summary.json scripts/loadtest/ai-concurrency.js
```

`SCENARIO=mixed` chạy cả hai cùng lúc, để đo ảnh hưởng của sinh đề lên chatbot (cả hai dùng chung Ollama). Kịch bản này cần đủ cả hai bộ tài khoản.

Mỗi lần chỉ nên đo một bậc tải để dễ đọc kết quả, ví dụ: `-e CHAT_STEPS=4x5m`.

## Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `BASE_URL` | `http://localhost:8080` | Địa chỉ backend Java |
| `SCENARIO` | `chat` | `chat`, `quiz` hoặc `mixed` |
| `CHAT_STEPS` | `1x2m,2x2m,4x2m,8x2m,12x2m,16x2m` | Các bậc `<số VU>x<thời gian giữ>` |
| `QUIZ_STEPS` | `1x5m,2x5m,3x5m,5x5m` | Như trên, cho sinh đề |
| `COURSE_ID` | | Khóa học dùng làm ngữ cảnh chat; bắt buộc với sinh đề |
| `LESSON_ID` | | Chỉ sinh đề từ một bài học |
| `CHAT_TURNS` | `4` | Số lượt hỏi trong một hội thoại trước khi mở hội thoại mới |
| `THINK_TIME` | `0` | Số giây nghỉ giữa hai câu hỏi của một VU |
| `QUESTIONS_FILE` | | File JSON chứa mảng câu hỏi; nên dùng câu hỏi đúng nội dung khóa học |
| `QUIZ_SINGLE` / `QUIZ_MULTIPLE` | `5` / `5` | Số câu một lựa chọn / nhiều lựa chọn mỗi job |

## Đọc kết quả

| Metric | Ý nghĩa |
|---|---|
| `chat_duration` | Thời gian một câu chat, đo từ phía client |
| `chat_success` | Tỷ lệ câu chat trả lời thành công |
| `chat_errors{status:...}` | Số lỗi theo mã: `504` là Ollama quá thời gian (hết 50s), `503` là rag-service không phản hồi, `0` là k6 tự hết thời gian chờ |
| `quiz_job_duration` | Thời gian từ lúc tạo job đến khi job xong |
| `quiz_job_success` | Tỷ lệ job sinh đề hoàn thành |
| `quiz_accepted_ratio` | Số câu đạt chia cho số câu yêu cầu. Tỷ lệ này giảm khi quá tải vì các lượt sinh bị timeout |
| `quiz_errors{status:...}` | `429` là vượt `QUIZ_JOB_MAX_ACTIVE`, `504` là job vượt 420s |

**Năng lực chịu tải** là bậc cao nhất mà `chat_success` vẫn > 95% và p95 của `chat_duration` < 50s. Với sinh đề, là bậc cao nhất mà `quiz_job_success` > 90%.

Trong lúc chạy, nên theo dõi thêm:

- `docker stats`: CPU và RAM của `ollama` và `rag-service`.
- `docker compose logs -f rag-service | grep "Ollama generation"`: thời gian của từng lượt gọi model (`total_ms`). Nếu `load_ms` lớn và lặp lại nhiều lần, model đang bị nạp đi nạp lại.
- Grafana/Loki: lỗi của backend, đặc biệt là lỗi hết kết nối DB (`Connection is not available`). Lỗi này xuất hiện khi các request chat giữ kết nối DB trong lúc chờ rag-service.

# Load test danh mục đào tạo (cây cấu trúc và bảng quản lý)

Bộ dữ liệu lớn [seed-large-catalog.sql](seed-large-catalog.sql) **xóa toàn bộ danh mục hiện tại** (lĩnh vực, chương trình, khóa học, bài học, quiz và mọi dữ liệu học tập phụ thuộc), giữ nguyên người dùng và bổ sung đơn vị Hội sở, ban và chi nhánh. Kết quả khoảng 40 lĩnh vực, 670 chương trình, 17 500 khóa học, 96 000 chương và 480 000 bài học; mỗi lĩnh vực có một chương trình lớn (150–250 khóa) để thử phân trang trong nhánh.

Chỉ chạy trên database local hoặc staging, sau khi đã sao lưu:

```bash
docker exec coretraining-db pg_dump -U postgres -d coretraining -Fc > coretraining-backup.dump
docker exec -i coretraining-db psql -U postgres -d coretraining -v ON_ERROR_STOP=1 < scripts/loadtest/seed-large-catalog.sql
```

Kịch bản [catalog-tree.js](catalog-tree.js) mô phỏng quản trị viên mở cây, mở chương trình và khóa học, tìm kiếm và chuyển sang bảng. Cách xuất file id mẫu và lệnh chạy nằm ở đầu file. Mặc định tăng 10 → 50 → 100 VU, đổi bằng `-e STAGES=50x1m,100x1m,200x1m,300x1m`.

# Dữ liệu người dùng và hoạt động học tập

[seed-learning-activity.sql](seed-learning-activity.sql) chạy **sau** `seed-large-catalog.sql` và bổ sung mọi loại dữ liệu phía người dùng mà bộ danh mục chưa có:

| Nhóm | Dữ liệu |
|---|---|
| Tài khoản | 10 000 học viên `hv00001`…, 200 giảng viên `gv001`…, 1 quản trị chi nhánh cho mỗi chi nhánh `qtcn001`… (vai trò giới hạn theo chi nhánh). Khoảng 8% là nhân viên mới, khoảng 1,5% bị khóa |
| Ghi danh | Khóa bắt buộc theo đơn vị, khóa tự đăng ký, yêu cầu ghi danh đã duyệt / chờ duyệt / bị từ chối, hạn hoàn thành cá nhân (có khóa quá hạn) |
| Học tập | Tiến độ bài học (video, văn bản), bài và khóa đã hoàn thành theo đúng thứ tự mở khóa; khóa chưa bắt đầu / đang học / đã xong |
| Kiểm tra | Phiên làm bài có giới hạn thời gian (cả phiên đang mở), lượt làm bài kèm snapshot đề, điểm cao nhất và đáp án đã chọn; có lượt trượt trước khi đạt và bài bắt buộc đang trượt |
| Thành tích | Huy hiệu đồng/bạc/vàng và điểm tính đúng quy tắc của ứng dụng (100 điểm/bài, cộng điểm huy hiệu), bảng xếp hạng |
| Cộng đồng | Đánh giá khóa học, bình luận và trả lời của giảng viên, ghi chú video |
| Flashcard | Thẻ của bài học (thủ công, AI), thẻ cá nhân, lịch ôn tập |
| Khác | Thông báo các loại, báo lỗi bài học ở mọi trạng thái, nhật ký thay đổi bài học, ngân hàng câu hỏi, lịch sử hội thoại trợ lý, giảng viên phụ trách khóa |

Mọi tài khoản dùng mật khẩu **`Test@123456`** và không bị buộc đổi mật khẩu. Script chạy lại được: lần chạy sau xóa các tài khoản `hv*`, `gv*`, `qtcn*` cùng dữ liệu của chúng rồi tạo lại; người dùng khác và danh mục được giữ nguyên.

```bash
docker exec -i coretraining-db psql -U postgres -d coretraining -v ON_ERROR_STOP=1 < scripts/loadtest/seed-learning-activity.sql
```

Tăng quy mô bằng biến psql, ví dụ `-v learners=50000 -v instructors=500` (đặt trước dấu `<`).

## Tìm tài khoản theo tình huống cần test

```sql
-- Học viên có khóa quá hạn chưa xong
SELECT u.username, d.course_id, d.due_date FROM user_course_deadlines d JOIN users u ON u.id = d.user_id
WHERE d.due_date < current_date AND NOT EXISTS (SELECT 1 FROM user_completed_courses c WHERE c.user_id = d.user_id AND c.course_id = d.course_id) LIMIT 5;
-- Học viên đang trượt bài kiểm tra bắt buộc
SELECT u.username, s.lesson_id, s.score, s.total FROM user_quiz_scores s JOIN users u ON u.id = s.user_id
JOIN quizzes q ON q.lesson_id = s.lesson_id AND q.required_for_progress WHERE s.score * 100 < 80 * s.total LIMIT 5;
-- Học viên có nhiều huy hiệu nhất
SELECT u.username, count(*) FROM user_badges b JOIN users u ON u.id = b.user_id GROUP BY u.username ORDER BY 2 DESC LIMIT 5;
-- Nhân viên mới chưa học gì
SELECT username FROM users WHERE username LIKE 'hv%' AND learner_type = 'NEW_HIRE' LIMIT 5;
-- Quản trị chi nhánh có yêu cầu ghi danh chờ duyệt
SELECT u.username, count(*) FROM notifications n JOIN users u ON u.id = n.recipient_id
WHERE n.type = 'ENROLLMENT_REQUEST' GROUP BY u.username ORDER BY 2 DESC LIMIT 5;
-- Giảng viên và số khóa phụ trách
SELECT u.username, count(*) FROM course_instructors ci JOIN users u ON u.id = ci.user_id GROUP BY u.username ORDER BY 2 DESC LIMIT 5;
```

## Load test luồng học viên

[learner-journey.js](learner-journey.js): mỗi VU đăng nhập bằng một học viên riêng (`hv00001`, `hv00002`, …) rồi lặp một buổi học: trang tổng quan, danh sách khóa, đề cương khóa học, học tiếp bài hiện tại (kèm bình luận, ghi chú), lưu tiến độ, làm và nộp bài kiểm tra, thỉnh thoảng mở hồ sơ, bảng xếp hạng và flashcard. Giữa các trang có thời gian đọc (`THINK`, mặc định 3 giây).

```bash
k6 run -e BASE_URL=http://localhost:9000 -e PASSWORD=Test@123456 --summary-export=learner-summary.json scripts/loadtest/learner-journey.js
```

Mặc định tăng 20 → 100 → 200 VU; đổi bằng `-e STAGES=200x5m,500x5m`. `-e WRITES=0` chỉ đọc (không lưu tiến độ, không nộp bài), dùng khi không muốn thay đổi dữ liệu. Lượt chạy có ghi làm thay đổi tiến độ và điểm của học viên mẫu; chạy lại `seed-learning-activity.sql` để đưa về trạng thái ban đầu.
