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
| `quiz_errors{status:...}` | `429` là vượt `QUIZ_JOB_MAX_ACTIVE`, `504` là job vượt 300s |

**Năng lực chịu tải** là bậc cao nhất mà `chat_success` vẫn > 95% và p95 của `chat_duration` < 50s. Với sinh đề, là bậc cao nhất mà `quiz_job_success` > 90%.

Trong lúc chạy, nên theo dõi thêm:

- `docker stats`: CPU và RAM của `ollama` và `rag-service`.
- `docker compose logs -f rag-service | grep "Ollama generation"`: thời gian của từng lượt gọi model (`total_ms`). Nếu `load_ms` lớn và lặp lại nhiều lần, model đang bị nạp đi nạp lại.
- Grafana/Loki: lỗi của backend, đặc biệt là lỗi hết kết nối DB (`Connection is not available`). Lỗi này xuất hiện khi các request chat giữ kết nối DB trong lúc chờ rag-service.
