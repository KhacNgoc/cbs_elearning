# Kết quả load test chatbot và sinh đề (02/11/2026)

Đo bằng [scripts/loadtest/ai-concurrency.js](../scripts/loadtest/ai-concurrency.js) trên máy dev.

## Môi trường

- MacBook M4 Pro, 12 nhân, 24GB RAM. Ollama 0.40.13 chạy trên host, dùng GPU Metal. Không đặt `OLLAMA_NUM_PARALLEL`.
- Docker compose local, cấu hình model giống `.env.production`: chat `qwen3:4b`, viết lại câu hỏi `qwen3:1.7b`, sinh đề `qwen3:8b`, kiểm định `qwen3:4b`.
- Khóa "CIF cơ bản" (91 đoạn tài liệu), tài khoản `admin`. Bộ câu hỏi chat ở [questions-cif.json](../scripts/loadtest/questions-cif.json).
- Mỗi VU gửi request liên tục, không nghỉ. Hội thoại 4 lượt rồi mở hội thoại mới.

## Chatbot

| Người dùng đồng thời | Số câu | Thành công | p50 | p95 | Max |
|---|---|---|---|---|---|
| 1 | 45 | 100% | 1,4s | 3,4s | 3,7s |
| 2 | 68 | 100% | 2,6s | 6,1s | 7,8s |
| 4 | 66 | 100% | 5,9s | 9,9s | 11,9s |
| 8 | 87 | 100% | 11,3s | 19,0s | 21,1s |
| 12 | 80 | 100% | 19,3s | 24,4s | 30,7s |
| 16 | 85 | 100% | 21,1s | 25,2s | 28,7s |
| 24 | 89 | 100% | 29,1s | 37,1s | 40,6s |

- Ollama trả lời tuần tự. Năng lực tối đa khoảng **0,8 câu/giây (~48 câu/phút)**: thời gian chờ tăng tuyến tính theo số người dùng.
- Không có lỗi 504 nào tới 24 người dùng. Theo đà tăng, timeout 50s của bước sinh câu trả lời sẽ bắt đầu bị vượt ở khoảng **35–40 người dùng đồng thời**. Mức này chưa đo trực tiếp.
- **Nút thắt ở pool kết nối DB, xảy ra trước nút thắt của Ollama:**
  - Thread dump lúc có 16 người dùng cho thấy 10 thread đang chờ rag-service, và chính 10 thread này giữ hết 10 kết nối Hikari (do `spring.jpa.open-in-view` mặc định bật). 6 thread khác phải chờ `HikariPool.getConnection`, 5 trong số đó đang ở bước `UserDetailsServiceImpl.loadUserById` của bộ lọc JWT.
  - Hệ quả: API nhẹ `/api/auth/session` mất **1,4–5,8s** khi đang tải, so với **4ms** lúc không tải. Từ khoảng 10 cuộc chat đồng thời trở lên, **mọi API khác của hệ thống đều chậm theo**. Nếu thời gian chờ kết nối vượt 30s, các API đó sẽ lỗi.
- Hàng đợi tóm tắt hội thoại (1 thread, chứa tối đa 100 task) bị đầy: **15 bản tóm tắt bị bỏ** (`TaskRejectedException`). Chat vẫn trả lời bình thường, nhưng bản tóm tắt của các hội thoại đó không được cập nhật.

### Sau khi sửa nghẽn pool kết nối DB

[WebConfig.java](../CoreTrainingAPI/src/main/java/com/bidv/coretraining/config/WebConfig.java) khai báo bean OSIV riêng, loại trừ `/api/rag/chat` và `/api/admin/quizzes/generate`. Các API khác vẫn dùng OSIV như cũ. Đo lại với 16 người dùng chat trong 90s:

| | Trước | Sau |
|---|---|---|
| `/api/auth/session` khi đang tải chat | 1,4–5,8s | 8–16ms |
| Thread chờ rag-service | 10 (bị chặn ở mức pool) | 16 |
| Thread chờ kết nối DB | 6 | 0 |
| Chat thành công | 100% | 100% (71/71) |
| Thông lượng chat | ~0,8 câu/s | ~0,8 câu/s |

- Thời gian trả lời chat gần như không đổi (p50 24s, p95 33s), vì nút thắt của chat vẫn là Ollama. Khác biệt là giờ các request xếp hàng ở Ollama, không còn chiếm pool kết nối DB, nên **các API khác không bị chậm theo**.
- Không có `LazyInitializationException` nào trong log backend.

## Sinh đề (mỗi job 10 câu: 5 một đáp án + 5 nhiều đáp án)

| Job đồng thời | Số job | Hoàn thành | Thời gian job (min / trung vị / max) |
|---|---|---|---|
| 1 | 5 | 4 | 44 / 47 / 73s |
| 2 | 7 | 2 | 79 / 91 / 133s |
| 3 | 8 | 4 | 130 / 186 / 203s |
| 5 | 5 | 3 | 230 / 235 / 238s |

- Mỗi job tốn khoảng 45s khi chạy một mình. Thời gian job tăng tuyến tính theo số job chạy song song, vì giới hạn `QUIZ_GENERATION_CONCURRENCY=2` áp dụng cho cả hệ thống.
- Với 5 job đồng thời, mỗi job mất khoảng 235s, đã sát giới hạn 300s. **Trên máy này, trần vào khoảng 5–6 job 10 câu cùng lúc.** Job thứ 7 trở đi sẽ bị lỗi 504 hàng loạt. Năng lực tối đa khoảng 1,3 job/phút.
- **Khoảng một nửa số job lỗi ở mọi mức tải, kể cả khi chỉ có 1 job.** Lỗi này không do quá tải:
  - Tất cả 14 job lỗi đều cùng một nguyên nhân: `Quiz verification failed ... error_type=ModelOutputTruncated`.
  - Bước kiểm định giới hạn `num_predict=256`. Khoảng 5% lượt kiểm định (14/287) bị cắt vì model hết token đầu ra.
  - `asyncio.gather` trong `verify_one` không dùng `return_exceptions`, nên chỉ cần **một câu** bị cắt là **cả job** trả về 503. Với 10 câu mỗi job, xác suất job lỗi là khoảng 1 − 0,95¹⁰ ≈ 40%.
- Job hoàn thành cũng chỉ đạt trung bình 4,7/10 câu, do bước kiểm định loại khoảng một nửa số câu đã sinh.

### Sau khi sửa bước kiểm định

Hai thay đổi:

- Tăng giới hạn token của bước kiểm định lên 512, cấu hình qua `QUIZ_VERIFICATION_NUM_PREDICT`.
- Lỗi khi kiểm định một câu chỉ loại câu đó. Job chỉ trả về 503 khi bước kiểm định lỗi với **tất cả** các câu.

| Job đồng thời | Số job | Hoàn thành | Thời gian job (trung vị / max) |
|---|---|---|---|
| 1 | 8 | 8 | 44 / 104s |
| 3 | 6 | 6 | 124 / 128s |

- **15/15 job hoàn thành (100%)**, trước khi sửa là 48%. Toàn bộ 150 lượt kiểm định kết thúc bình thường, không lượt nào bị cắt.
- 6 job có 1 câu bị loại vì model trả JSON sai schema (`ValidationError`). Trước khi sửa, lỗi này cũng làm hỏng cả job.
- Mỗi job vẫn chỉ giữ lại trung bình 4,4/10 câu. Đây là vấn đề chất lượng của bước kiểm định (loại khoảng một nửa số câu), cần xử lý riêng.

## Chưa đo

- Chatbot và sinh đề chạy cùng lúc (`SCENARIO=mixed`).
- Chatbot trên 24 người dùng đồng thời.
- Máy production (CPU/GPU khác thì con số khác hoàn toàn).
