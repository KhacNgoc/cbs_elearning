# Báo cáo thực thi kiểm thử — CBS E-learning

Theo kịch bản [functional-non-functional-test-scenarios.vi.md](functional-non-functional-test-scenarios.vi.md). Build: commit `484c208` trên nhánh hieuvm_performance. Chạy từ 2026-11-06T04:39:20.713Z đến 2026-11-06T05:50:49.321Z (UTC). Sinh bởi `tests/system/report.mjs`.

## 1. Kết quả tổng

| | Pass | Một phần | Fail | Blocked | N/A | Not Run | Tổng |
|---|---|---|---|---|---|---|---|
| Ca kiểm thử | 60 | 49 | 26 | 3 | 1 | 0 | 139 |
| Trong đó P0 | 35 | 23 | 10 | 0 | 0 | 0 | 68 |

Ở mức biến thể đã chạy: 494 Pass, 44 Fail, 27 Gap (khoảng trống yêu cầu hoặc phạm vi), 6 Blocked, 2 N/A, trên tổng 573 dòng.

"Một phần" nghĩa là các biến thể đã chạy không có Fail, nhưng còn biến thể chưa chạy hoặc còn Gap, nên không tính là Pass theo quy ước mục 1 của kịch bản.

**Kết luận theo exit criteria: chưa đạt.** Còn ca P0 Fail, chưa có ca nào chạy trên staging giống production, và các SLO [ĐX] chưa được chốt.

## 2. Môi trường và cách cô lập

- Toàn bộ chạy trên stack Docker riêng, project `cbstest`: Postgres trên tmpfs (`127.0.0.1:15432`), backend `19000`, frontend `127.0.0.1:15173`, RAG `18001`, Qdrant và SMTP sink riêng. Bộ test tự từ chối mọi đích khác hai cổng 19000 và 15173.
- DB dev `coretraining-db` được đối chiếu trước và sau khi chạy: vẫn 3 user, 3 khoá. Không container dev nào bị dừng hay ghi. Ollama trên máy được dùng chung, chỉ để suy luận.
- Không sửa mã sản phẩm. Mọi thứ mới nằm trong `tests/system/` và báo cáo này.
- Chạy lại: `sh tests/system/reset-stack.sh`, rồi `cd tests/system && node run.mjs`, rồi `node report.mjs`.

## 3. Lỗi cần sửa

Sắp theo mức ảnh hưởng. Mọi lỗi dưới đây đều tái hiện được trong lượt chạy này; riêng các vị trí mã là kết quả đọc code.

### Nghiêm trọng: phân quyền và dữ liệu

> **Cập nhật sau lượt chạy:** lỗi 1–4 đã được sửa trong working tree (chưa commit), đồng thời phân bổ được đồng nhất về cấp khóa: chọn lĩnh vực hoặc chương trình chỉ là chọn nhanh các khóa hiện có; đơn vị của chương trình và lĩnh vực chỉ dùng để quản lý; admin có quyền theo role trên khóa được phân cho đơn vị trong phạm vi của họ. Chạy lại 12 suite, không gồm `resilience`, `nonfunctional`: các dòng ở NF-S03, F-E01, F-E05 chuyển sang Pass; một dòng NF-S05 báo nhầm do báo cáo chậm, đo lại thì payload không chậm hơn giá trị thường. Số liệu và bảng trạng thái trong báo cáo này vẫn là của lượt chạy trước khi sửa (commit `484c208`).

| # | Lỗi | Bằng chứng | Vị trí | Ca |
|---|---|---|---|---|
| 1 | Học viên đọc được changelog bài học của khoá ngoài phạm vi | Học viên đơn vị B gọi `GET /api/lessons/{id}/latest-changelog` của khoá đơn vị A, nhận HTTP 200 kèm nội dung | `LessonChangeServiceImpl.getLatestChangeLog` không kiểm tra quyền | NF-S03 |
| 2 | Phân bổ một khoá làm lộ các khoá khác | Sau `batch-assign` một khoá X cho đơn vị A (API mà `BatchAllocationModal.vue` dùng), học viên A thấy khoá cùng chương trình và khoá ở chương trình khác cùng lĩnh vực, và đăng ký được khoá không được cấp (HTTP 200) | `batchAssignUnitAllocation` thêm đơn vị vào `program.units` và `domain.units`; `AccessPolicy.visible` coi đó là được thấy | F-E01 |
| 3 | Gỡ phân bổ không có tác dụng với danh mục | `batch-sync` REMOVE trả thành công và grant đã xoá, nhưng học viên vẫn thấy khoá | `removeUnitAllocation` không dọn `program.units` và `domain.units` | F-E05 |
| 4 | Chương trình không chọn đơn vị vẫn được tự gán đơn vị | Tạo chương trình với danh sách đơn vị rỗng, phản hồi có `unitIds` chứa một đơn vị; khoá mới kế thừa nên học viên đơn vị đó thấy khoá | `TrainingStructureServiceImpl`: nếu actor không có đơn vị thì lấy đơn vị đầu tiên từ `findAll()`, không xác định thứ tự | F-E01 |

### Cao: toàn vẹn nghiệp vụ

| # | Lỗi | Bằng chứng | Vị trí | Ca |
|---|---|---|---|---|
| 5 | Đăng ký đồng thời tạo yêu cầu trùng | 8 request song song tạo 8 yêu cầu PENDING | Không có unique constraint hay khoá | F-E03 |
| 6 | Xử lý song song một yêu cầu đều thành công | Duyệt và từ chối cùng lúc: cả hai 200. Xử lý 5 lần song song: ba lần 200 | Không có kiểm tra version hay khoá ở bước duyệt | E2E-02, NF-C02 |
| 7 | Có thể lách điều kiện hoàn thành video | Xem 1 giây, khai `durationSeconds=1`, video 100 giây được tính hoàn thành. `watchedSeconds=1e9` được lưu nguyên | Tin giá trị client gửi lên thay vì thời lượng của server | F-L02, F-L05 |
| 8 | Huy hiệu không bao giờ được cấp | Thi đạt 80%, 90%, 95%, 100% đều không có huy hiệu, điểm huy hiệu bằng 0 | `checkAndUnlockBadges` không có nơi gọi; `POST /api/quizzes/{courseId}/submit` luôn 400 | F-Q12 |
| 9 | Lịch sử thi chỉ có một dòng | Học viên thi 7 lượt, `quiz_attempts` có 7 dòng, API lịch sử chỉ trả 1 dòng điểm cao nhất; phân trang và phân tích từng lượt không dùng được | API đọc `user_quiz_scores`, mỗi học viên/bài một dòng | F-R05 |
| 10 | Báo cáo đào tạo không dùng được khi dữ liệu lớn | Xem chi tiết ở mục 4 | `TrainingReportQueryRepositoryImpl` | NF-P02, NF-P08 |

### Trung bình

| # | Lỗi | Bằng chứng | Ca |
|---|---|---|---|
| 11 | Lỗi của client bị trả thành 500 | JSON sai, route không tồn tại, sai method, thiếu tham số, sai content-type, thiếu body, NaN/Infinity, ngày sai định dạng ở báo cáo. Handler `Exception` chung trong `GlobalExceptionHandler` nuốt các lỗi này và làm nhiễu cảnh báo | NF-O01, F-L05, F-R04 |
| 12 | Dữ liệu sai nghiệp vụ cũng trả 500 | Sắp xếp bài với ID trùng/thiếu/của khoá khác; import file không phải Excel; câu hỏi gắn bài ngoài khoá; trả lời bình luận của bài khác | F-C03, F-P07, F-Q13, F-N01 |
| 13 | Upload chỉ kiểm tra phần mở rộng | PDF và PPTX giả được nhận (200); bài học lưu được với URL file không tồn tại | F-C07 |
| 14 | Quá tải job sinh đề trả 503 thay vì 429 | 24 job đồng thời: 202 và 503, RAG trả 429 nhưng `QuizGenerationServiceImpl.startJob` đổi thành 503. Job flashcard thì giữ 429 | NF-P05 |
| 15 | Bình luận không giới hạn độ dài | Lưu được bình luận 3.145.728 ký tự | NF-S07 |
| 16 | Tên đăng nhập `admin` điền sẵn trong bản production | `Login.vue:319`. Lộ tên quản trị và người dùng gõ nối vào thành sai tên | F-A02 |

### Thấp

| # | Lỗi | Ca |
|---|---|---|
| 17 | CSV thiếu header `Content-Type` (đã có BOM và `Content-Disposition`) | F-R06 |
| 18 | Tạo được lịch nhắc không có ngày phù hợp (Thứ 6 trong khoảng Thứ 2–Thứ 3), `nextReminderAt=null` nên không bao giờ nhắc | F-F08 |
| 19 | Trang bài học ở 390px: drawer `.course-sidebar` làm layout giãn lên 733px | NF-U01 |
| 20 | Flashcard và bài học có nút không có tên truy cập | NF-U02 |
| 21 | Range không hợp lệ trả đúng 416 nhưng vẫn gửi cả file trong body | NF-P07 |

## 4. Hiệu năng

**API học và thi ổn trong điều kiện thử.** Mỗi mức giữ 2 phút trên máy trạm, không có lỗi 5xx:

| Mức | Req/s | p95 | p99 |
|---|---|---|---|
| 50 user | 12,1 | 12ms | 55ms |
| 100 user | 24,3 | 10ms | 54ms |
| 200 user | 47,4 | 8ms | 52ms |
| Spike 500 user | 71,0 | 2ms | 49ms |

Tốc độ này gồm cả thời gian nghỉ của user ảo và chạy trên localhost, nên chỉ là cận dưới của chi phí, chưa thay được phép đo trên staging.

**Báo cáo là điểm nghẽn.** Các trang khác vẫn nhanh khi dữ liệu tăng, riêng báo cáo vượt xa trần 3 giây:

| Dữ liệu | Danh sách user trang đầu | Toàn bộ danh sách user | Báo cáo đọc | Xuất CSV |
|---|---|---|---|---|
| 1k user, 100 khoá | 36ms | 645ms | quá 120s | 24s |
| 10k user, 100 khoá | 31ms | 3,4s, 5MB | quá 120s | quá 120s |
| 100k user, 1k khoá | 87ms | 35s, 50MB | quá 120s | quá 120s |

Log backend ghi nhận một lần đọc báo cáo tới 361 giây và lần xuất CSV tới 1.261 giây, vẫn chạy tiếp sau khi client đã ngắt, kèm 118 dòng cảnh báo connection leak của Hikari. Ngay cả CSV không có dòng nào cũng mất 51 giây ở 100k user. Khi đó nộp bài song song vẫn đạt p95 50ms, nhưng vài export cùng lúc có thể giữ hết pool kết nối; điều này chưa được đo. Dữ liệu nạp thử chủ yếu là user, khoá và phân bổ, nên chưa phản ánh progress, attempt, comment với khối lượng lớn.

**Chat AI:** 1, 2, 4, 8 request đồng thời đều thành công, p95 tối đa 21 giây, mỗi mức chỉ một đợt.

## 5. Cần chủ hệ thống quyết định

Đây là các dòng Gap: hành vi hiện tại không rõ đúng hay sai theo kịch bản.

| Vấn đề | Hiện tại | Ca |
|---|---|---|
| Email có phải duy nhất không | Hai tài khoản dùng chung được email, trong khi OTP gửi qua email | F-P06 |
| Có giới hạn tần suất đăng nhập không | 60 lần sai đồng thời đều 401, không khoá tạm | NF-S07 |
| Phân bổ khoá nháp | Tự xuất bản khoá và bài | F-C04 |
| Sửa câu hỏi sau khi có lượt thi | Điểm cũ hiển thị cạnh đề đã sửa; người đang thi bị chấm theo đề mới (3/4 dù trả lời đúng đề đang thấy) | F-Q15 |
| Thêm nội dung bắt buộc sau khi đã hoàn thành | Giữ trạng thái hoàn thành cũ | E2E-05 |
| Version khi sửa bài/khoá | Tuỳ chọn, không gửi là ghi đè | NF-C02 |
| Cách đếm "tối đa 366 ngày" | Chấp nhận chênh 366 ngày, tức 367 ngày lịch | F-F08 |
| Nộp bài khi chưa làm hết | UI khoá nút, chỉ auto-submit mới nộp được một phần | F-Q06 |
| Số slide mỗi bài | Hệ thống chỉ cho 1, dữ liệu kịch bản cần 2 | F-L02 |
| Làm sạch HTML | Backend lưu nguyên script, chỉ frontend chặn (đã kiểm tra an toàn trên trình duyệt) | NF-S05 |
| Cổng RAG | Compose gốc mở cổng 8001, `/chat` không có xác thực riêng | NF-S06 |
| Cookie Secure | `.env.production` đặt `JWT_COOKIE_SECURE=false` | NF-S08 |
| Vận hành | Chưa có luật cảnh báo, job backup định kỳ, runbook rollback migration; chưa chốt SLO, RPO, RTO | NF-O01, NF-R04, NF-D03 |

## 6. Phần đã kiểm tra và đạt

OTP và đổi mật khẩu bắt buộc; CSRF, CORS, JWT giả mạo; ma trận phạm vi theo đơn vị (ngoài lỗi 1–4); chấm điểm, deadline, phiên thi, reload không cấp thêm giờ, auto-submit; hoàn thành bài và cộng điểm idempotent khi đồng thời; flashcard và scheduler nhiều instance (500 lịch, không nhắc trùng); AI trả lời đúng nguồn 6/6 và không bịa 4/4 trên mẫu rút gọn, không lộ nguồn của đơn vị khác, chống được prompt injection; XSS trên trình duyệt; đóng WebSocket khoảng 15 giây sau khi khoá tài khoản; mất Qdrant, mất DB 30 giây và restart backend đều phục hồi; backup và khôi phục sang DB riêng trong 9 giây ở cỡ dữ liệu test (261 user).

## 7. Test tự động có sẵn

| Bộ | Kết quả |
|---|---|
| Backend `mvn test` (H2) | 342 chạy, 0 lỗi, 69 bỏ qua |
| Frontend | 213 chạy, 212 đạt, 1 lỗi: `learningPagesPerformance.test.mjs:330`, TypeError khi lấy `trainingApi`, nghi do fixture của test, chưa xác minh |
| `rag-service` pytest | **Chưa chạy:** image không có pytest và pip không dùng được trong container |

## 8. Giới hạn của lượt kiểm thử

- Chạy trên máy trạm, không phải staging giống production; số đo hiệu năng chỉ có giá trị so sánh tương đối.
- Thời lượng tải rút gọn còn 2 phút mỗi mức thay vì 15 phút. Soak 8 giờ, rolling restart, hết dung lượng volume chưa chạy.
- Chưa có HTTPS. Chỉ dùng Chromium; chưa thử Firefox, Safari, Edge, tablet và screen reader.
- E2E chạy chủ yếu qua API. Thời gian được mô phỏng bằng cách sửa deadline và OTP trên DB test.
- Image RAG được gắn tag từ image có sẵn trên máy, không build lại từ mã nguồn, nên có thể lệch với commit đang kiểm thử.
- Mẫu đánh giá AI là 6+4 câu, chấm tự động theo từ khoá, chưa đủ 30+20 câu và chưa có người duyệt.
- Không tắt được Ollama vì dùng chung với máy dev nên chưa thử model timeout thật.
- NF-P07: trong lượt đầy đủ, kết quả là `fetch failed` phía client. Chạy riêng cùng trình tự trên cùng stack ở cỡ 100k user thì đạt (200, 206, 416), nên được ghi Pass kèm chú thích. Nguyên nhân lỗi kết nối chưa xác định, và chưa đo mức dùng RAM.
- Lỗi đồng thời số 6 phụ thuộc thời điểm, có thể không lần nào cũng tái hiện.


## Kết quả theo nhóm

| Nhóm | Pass | Một phần | Fail | Blocked | N/A | Not Run |
|---|---|---|---|---|---|---|
| 4.1 Xác thực | 7 | 1 | 1 | 0 | 0 | 0 |
| 4.2 Phân quyền | 6 | 2 | 1 | 0 | 0 | 0 |
| 4.3 Cấu trúc đào tạo | 3 | 4 | 2 | 0 | 1 | 0 |
| 4.4 Đăng ký | 3 | 1 | 3 | 0 | 0 | 0 |
| 4.5 Học tập | 8 | 2 | 2 | 0 | 0 | 0 |
| 4.6 Thi | 8 | 5 | 2 | 0 | 0 | 0 |
| 4.7 AI | 6 | 4 | 0 | 0 | 0 | 0 |
| 4.8 Flashcard | 7 | 4 | 1 | 0 | 0 | 0 |
| 4.9 Tương tác | 4 | 1 | 1 | 0 | 0 | 0 |
| 4.10 Báo cáo | 2 | 2 | 3 | 0 | 0 | 0 |
| 5.1 Hiệu năng | 0 | 4 | 3 | 1 | 0 | 0 |
| 5.2 Bảo mật | 3 | 4 | 2 | 0 | 0 | 0 |
| 5.3 Tin cậy | 1 | 3 | 0 | 1 | 0 | 0 |
| 5.4 Toàn vẹn | 2 | 3 | 1 | 0 | 0 | 0 |
| 5.5 Khả dụng, vận hành | 0 | 1 | 3 | 1 | 0 | 0 |
| 3. E2E | 0 | 8 | 1 | 0 | 0 | 0 |

## Trạng thái từng ca

| Ca | Loại/Ưu tiên | Trạng thái | Biến thể Pass/đã chạy | Phần chưa chạy |
|---|---|---|---|---|
| E2E-01 | H, P0 | Một phần | 9/10 | chạy qua API, phần AI tách riêng |
| E2E-02 | N → H, P1 | Fail | 4/5 | chạy qua API |
| E2E-03 | W, P0 | Một phần | 4/5 | chạy qua API, chưa ngắt mạng thật trên UI |
| E2E-04 | W, P0 | Một phần | 5/5 | chạy qua API; UI kiểm tra riêng reload và auto-submit |
| E2E-05 | N/W, P0 | Một phần | 4/5 | chưa thay video; chạy qua API |
| E2E-06 | W, P0 | Một phần | 5/7 | chưa thử lịch sử chat, export |
| E2E-07 | H/B/W, P1 | Một phần | 1/1 | chưa thêm thẻ AI, scheduler kiểm tra riêng ở F-F11/12 |
| E2E-08 | W, P0 | Một phần | 1/1 | chưa giả lập model timeout và xoá file lúc ingest |
| E2E-09 | W, P0 | Một phần | 1/1 | chưa mô phỏng mất từng dịch vụ; chưa kiểm tra lịch sử chat và nhắc học sau phục hồi |
| F-A01 | H/P0 | Pass | 2/2 |  |
| F-A02 | N/B/P0 | Fail | 8/9 |  |
| F-A03 | H/N/P0 | Pass | 2/2 |  |
| F-A04 | H/N/P0 | Pass | 1/1 |  |
| F-A05 | W/P0 | Pass | 3/3 |  |
| F-A06 | B/N/P0 | Pass | 4/4 |  |
| F-A07 | N/W/P1 | Một phần | 2/2 | thông báo trên UI |
| F-A08 | N/P0 | Pass | 4/4 |  |
| F-A09 | B/N/P1 | Pass | 5/5 |  |
| F-P01 | H/N/P0 | Pass | 8/8 |  |
| F-P02 | H/N/P0 | Pass | 5/5 |  |
| F-P03 | W/P0 | Pass | 3/3 |  |
| F-P04 | H/N/P1 | Pass | 2/2 |  |
| F-P05 | H/B/N/P1 | Pass | 5/5 |  |
| F-P06 | H/N/P1 | Một phần | 4/5 |  |
| F-P07 | H/N/P1 | Fail | 1/2 | chỉ thử template và file sai định dạng; chưa import file hợp lệ, thiếu cột, trùng dòng, lỗi giữa file |
| F-P08 | H/N/P0 | Pass | 4/4 |  |
| F-P09 | H/W/P1 | Một phần | 2/2 | chưa theo dõi menu/route qua chu kỳ revalidate 30s trên UI |
| F-C01 | H/P1 | Pass | 2/2 |  |
| F-C02 | N/B/P1 | Pass | 11/11 |  |
| F-C03 | H/N/P1 | Fail | 5/8 |  |
| F-C04 | H/N/P0 | Một phần | 3/4 |  |
| F-C05 | B/N/P0 | Pass | 5/5 |  |
| F-C06 | H/W/P1 | Một phần | 1/1 | chưa thử sinh tóm tắt AI |
| F-C07 | H/B/N/P1 | Fail | 10/13 |  |
| F-C08 | H/N/P1 | Một phần | 2/2 | chưa thử chọn mục outline |
| F-C09 | H/W/P1 | Một phần | 1/1 | chưa kiểm tra thẻ đã duyệt còn lại sau khi xoá tài liệu |
| F-C10 | H/N/P1 | N/A | 0/1 |  |
| F-E01 | H/P0 | Fail | 3/5 |  |
| F-E02 | H/N/P0 | Pass | 1/1 |  |
| F-E03 | N/W/P1 | Fail | 0/1 |  |
| F-E04 | N/H/P1 | Pass | 3/3 |  |
| F-E05 | H/B/N/P1 | Fail | 1/2 | chưa thử chọn cả chương trình |
| F-E06 | W/P0 | Pass | 3/3 |  |
| F-E07 | H/B/P1 | Một phần | 2/2 | chưa đối chiếu quanh 00:00 UTC+7 trên dashboard/báo cáo |
| F-L01 | H/N/P0 | Pass | 2/2 |  |
| F-L02 | H/B/P0 | Fail | 5/7 |  |
| F-L03 | H/P1 | Pass | 2/2 |  |
| F-L04 | H/N/P1 | Một phần | 3/3 | chưa phát video thật trên UI |
| F-L05 | N/B/P0 | Fail | 11/15 |  |
| F-L06 | H/N/P0 | Pass | 3/3 |  |
| F-L07 | H/B/P1 | Pass | 4/4 |  |
| F-L08 | W/P0 | Pass | 3/3 |  |
| F-L09 | H/N/P1 | Pass | 1/1 |  |
| F-L10 | W/P0 | Một phần | 3/3 | chưa kiểm tra thông báo trạng thái lưu trên UI |
| F-L11 | W/P0 | Pass | 3/3 |  |
| F-L12 | H/B/P1 | Pass | 3/3 |  |
| F-Q01 | H/N/P0 | Pass | 4/4 |  |
| F-Q02 | H/B/P0 | Pass | 8/8 |  |
| F-Q03 | B/P0 | Pass | 7/7 |  |
| F-Q04 | N/W/P0 | Pass | 5/5 |  |
| F-Q05 | H/B/P0 | Pass | 10/10 |  |
| F-Q06 | W/P0 | Một phần | 4/5 |  |
| F-Q07 | B/W/P0 | Một phần | 5/5 | mốc thời gian dựng bằng cách đổi deadline trên DB test, không phải đồng hồ kiểm soát; chưa thử sửa giờ client |
| F-Q08 | N/W/P0 | Pass | 5/5 |  |
| F-Q09 | H/N/P0 | Pass | 3/3 |  |
| F-Q10 | H/N/P0 | Pass | 4/4 |  |
| F-Q11 | W/P0 | Một phần | 1/1 | mất response được mô phỏng ở tầng API |
| F-Q12 | H/B/P1 | Fail | 0/5 |  |
| F-Q13 | H/N/P1 | Fail | 9/10 |  |
| F-Q14 | H/B/N/P1 | Một phần | 8/8 | chưa thử add đúng 100 câu |
| F-Q15 | W/P0 | Một phần | 0/2 |  |
| F-I01 | H/P1 | Pass | 7/7 |  |
| F-I02 | N/P0 | Pass | 5/5 |  |
| F-I03 | H/N/P0 | Pass | 3/3 |  |
| F-I04 | H/N/P0 | Pass | 3/3 |  |
| F-I05 | H/P1 | Một phần | 1/1 | chỉ sinh 2 câu; chưa theo bài và nguồn đã chọn |
| F-I06 | B/N/P1 | Pass | 5/5 |  |
| F-I07 | W/P1 | Một phần | 1/1 | chưa dựng ca toàn bộ bị loại hoặc hết budget |
| F-I08 | W/P0 | Một phần | 3/3 | chưa thử model timeout |
| F-I09 | H/N/P1 | Pass | 2/2 |  |
| F-I10 | W/P1 | Một phần | 1/1 | chưa thử mất mạng và bấm lưu hai lần |
| F-F01 | H/N/P1 | Pass | 3/3 |  |
| F-F02 | H/N/P0 | Pass | 2/2 |  |
| F-F03 | B/N/P1 | Pass | 2/2 |  |
| F-F04 | W/P1 | Pass | 1/1 |  |
| F-F05 | H/B/P2 | Một phần | 1/2 | chưa thử shuffle khi đang giữa bộ và bộ 0/1 thẻ |
| F-F06 | H/N/P1 | Một phần | 1/1 | chưa thử trên 50 khoá trên UI |
| F-F07 | H/B/P1 | Pass | 3/3 |  |
| F-F08 | B/N/P1 | Fail | 6/8 |  |
| F-F09 | H/W/P1 | Pass | 1/1 |  |
| F-F10 | W/P0 | Pass | 3/3 |  |
| F-F11 | B/W/P1 | Một phần | 2/2 | chưa thử sát nửa đêm và lặp nhiều thứ |
| F-F12 | W/P1 | Một phần | 1/2 | chưa thử restart giữa batch |
| F-N01 | H/N/P1 | Fail | 3/4 |  |
| F-N02 | H/N/P1 | Pass | 2/2 |  |
| F-N03 | H/B/N/P1 | Pass | 2/2 |  |
| F-N04 | H/W/P1 | Pass | 4/4 |  |
| F-N05 | W/P1 | Một phần | 2/2 | chưa thử đổi user trên cùng tab và reconnect nhân đôi |
| F-N06 | H/N/P1 | Pass | 4/4 |  |
| F-R01 | H/B/P1 | Pass | 3/3 |  |
| F-R02 | H/P1 | Pass | 1/1 |  |
| F-R03 | H/B/P1 | Một phần | 2/2 | chưa thử đổi bộ lọc đơn vị |
| F-R04 | H/N/P0 | Fail | 8/9 |  |
| F-R05 | H/B/P0 | Fail | 1/2 |  |
| F-R06 | H/B/N/P0 | Fail | 3/4 | chưa thử số dòng lớn ở ca này (xem NF-P08) |
| F-R07 | B/W/P1 | Một phần | 3/3 | chưa kiểm tra khoá archive |
| NF-P01 | H/P1 | Một phần | 3/3 | thời lượng rút gọn |
| NF-P02 | B/P1 | Fail | 0/3 | chưa có EXPLAIN và đếm query; progress/attempt/comment chưa được nạp khối lượng lớn |
| NF-P03 | W/P1 | Một phần | 1/1 | thời lượng rút gọn |
| NF-P04 | B/W/P1 | Một phần | 0/1 |  |
| NF-P05 | W/P0 | Fail | 0/1 |  |
| NF-P06 | W/P1 | Blocked | 0/1 |  |
| NF-P07 | B/P1 | Một phần | 1/1 | chưa giả lập mạng và reverse proxy HTTPS |
| NF-P08 | B/W/P1 | Fail | 0/1 | dung lượng theo dữ liệu nạp thử, chưa phải dung lượng production |
| NF-S01 | N/P0 | Pass | 12/12 |  |
| NF-S02 | N/P0 | Pass | 5/5 |  |
| NF-S03 | W/P0 | Fail | 31/32 |  |
| NF-S04 | N/W/P0 | Một phần | 7/7 | chưa kiểm tra token hết hạn tự nhiên |
| NF-S05 | N/P0 | Một phần | 35/36 |  |
| NF-S06 | W/P0 | Một phần | 2/3 |  |
| NF-S07 | N/W/P1 | Fail | 4/6 |  |
| NF-S08 | H/N/P0 | Một phần | 2/3 | chưa có HTTPS |
| NF-S09 | N/P1 | Pass | 4/4 |  |
| NF-R01 | W/P0 | Pass | 3/3 |  |
| NF-R02 | W/P0 | Một phần | 2/3 |  |
| NF-R03 | W/P0 | Một phần | 1/3 |  |
| NF-R04 | W/P0 | Một phần | 0/1 |  |
| NF-R05 | W/P1 | Blocked | 0/1 |  |
| NF-C01 | W/P0 | Pass | 3/3 |  |
| NF-C02 | W/P0 | Fail | 2/4 |  |
| NF-C03 | W/P1 | Một phần | 1/1 | chưa chạy mức >2000 lịch |
| NF-D01 | H/W/P0 | Pass | 3/3 |  |
| NF-D02 | B/W/P0 | Một phần | 3/4 |  |
| NF-D03 | W/P0 | Một phần | 1/2 |  |
| NF-U01 | H/B/P1 | Fail | 1/3 |  |
| NF-U02 | H/N/P1 | Fail | 0/2 |  |
| NF-U03 | W/P1 | Blocked | 0/1 |  |
| NF-O01 | W/P1 | Fail | 4/11 |  |
| NF-O02 | H/B/P1 | Một phần | 0/1 |  |

## Chi tiết các dòng không Pass

| Case/biến thể | Status | Actual result |
|---|---|---|
| NF-S08 / cờ Secure trên HTTPS production-like | Gap | Stack test chạy HTTP với JWT_COOKIE_SECURE=false; Secure chỉ kiểm được khi có HTTPS (cần staging). Giá trị mặc định trong .env.production là false. |
| F-P06 / email trùng theo constraint | Gap | Không có unique constraint trên users.email: hai tài khoản được dùng chung email. Kịch bản ghi "theo constraint" nên đây là quyết định nghiệp vụ, cần chốt vì OTP gửi qua email. |
| F-P07 / file không phải Excel báo lỗi, không tạo user, không 500 | Fail | expected 4xx got 500: {"timestamp":"2026-11-06T11:39:46.047544533","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/admin/users/upload","traceId":"e6da2168-0c2d-4e46-bf18-2ae |
| NF-S03 / học viên B đọc changelog đã có của khoá A | Fail | changelog of another unit leaked: HTTP 200 |
| F-C03 / order bài cấp khoá: ID trùng | Fail | ID trùng expected 4xx got 500: {"timestamp":"2026-11-06T11:39:50.31526772","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/admin/courses/286707d4-8a63-4010-b48d-36a2e5b26f05/lessons/ |
| F-C03 / order bài cấp khoá: thiếu ID | Fail | thiếu ID expected 4xx got 500: {"timestamp":"2026-11-06T11:39:50.903175368","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/admin/courses/286707d4-8a63-4010-b48d-36a2e5b26f05/lessons |
| F-C03 / order bài cấp khoá: ID khoá khác | Fail | ID khoá khác expected 4xx got 500: {"timestamp":"2026-11-06T11:39:51.488079793","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/admin/courses/286707d4-8a63-4010-b48d-36a2e5b26f05/lessons |
| F-C04 / phân bổ khoá nháp tự chuyển sang PUBLISHED | Gap | Phân bổ một khoá DRAFT tự xuất bản khoá và bài (ensureCourseLearnableWhenAllocated). Cần chốt: kịch bản coi nháp không được học. |
| F-C07 / PDF hỏng/giả extension bị chặn | Fail | corrupt/fake PDF accepted with HTTP 200 |
| F-C07 / PPTX không phải zip bị chặn khi làm slide | Fail | fake PPTX accepted with HTTP 200 |
| F-C07 / bài không được trỏ tới file không tồn tại | Fail | lesson saved with a dangling file URL (HTTP 200) |
| F-C10 / màn hình nhập nhanh | N/A | Không có endpoint nhập nhanh riêng; backend chỉ có API CRUD từng cấp, UI gọi tuần tự. |
| F-E01 / chương trình không chọn đơn vị thì không cấp cho đơn vị nào | Fail | chương trình tạo không chọn đơn vị nhưng được tự gán UNIT_08C3D1CA: TrainingStructureServiceImpl lấy đơn vị đầu tiên trong DB khi actor không có đơn vị, khoá mới kế thừa nên người học đơn vị đó thấy và đăng ký được + actual - expected + [ + 'UNIT_08C3D1CA' + ] - [] |
| F-E01 / phân bổ một khoá không làm lộ khoá khác cùng chương trình/lĩnh vực | Fail | Chỉ phân bổ khoá X cho A nhưng A thấy khoá cùng chương trình=true, khác chương trình cùng lĩnh vực=true, đăng ký khoá không được cấp trả HTTP 200. batch-assign thêm đơn vị vào program.units và domain.units, AccessPolicy.visible coi đó là được thấy mọi khoá |
| F-E03 / gửi trùng tuần tự và đồng thời: chỉ 1 yêu cầu | Fail | 8 PENDING requests after 8 concurrent submits 8 !== 1 |
| F-E05 / batch-assign và batch-sync hợp lệ | Fail | Gỡ phân bổ khoá xong (grant đã xoá) học viên vẫn thấy khoá: removeUnitAllocation không dọn đơn vị khỏi program.units và domain.units |
| F-L02 / client tự khai durationSeconds nhỏ để lách 90% | Fail | 1-second watch completed a 100s video via client-supplied duration (save=200, complete=200) |
| F-L02 / slide bắt buộc: chưa đọc đến cuối thì chưa đạt | Gap | Hệ thống chỉ cho 1 slide mỗi bài ("Mỗi bài học chỉ được có một file slide PowerPoint"), trong khi dữ liệu L1 của kịch bản yêu cầu 2 slide bắt buộc. Đã kiểm tra với 1 slide; kịch bản cần cập nhật. |
| F-L05 / parser NaN | Fail | NaN expected 4xx got 500: {"timestamp":"2026-11-06T11:40:11.276339452","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/users/me/lessons/LSmw4hkb1na3/progress","traceId":"fb97034 |
| F-L05 / parser Infinity | Fail | Infinity expected 4xx got 500: {"timestamp":"2026-11-06T11:40:11.281449313","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/users/me/lessons/LSmw4hkb1na3/progress","traceId":"1406675 |
| F-L05 / parser chuỗi | Fail | chuỗi expected 4xx got 500: {"timestamp":"2026-11-06T11:40:11.287008108","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/users/me/lessons/LSmw4hkb1na3/progress","traceId":"76887c7 |
| F-L05 / watchedSeconds vượt duration không tự hoàn thành sai | Fail | stored watchedSeconds=1000000000 beyond duration 100 |
| F-Q12 / API quiz cấp khoá có nộp được không | Gap | POST /api/quizzes/{courseId}/submit luôn trả 400 ("Bài kiểm tra thuộc bài học"); quiz cấp khoá không còn đường nộp. |
| F-Q12 / đạt 80% → huy hiệu bronze +100 | Fail | không có huy hiệu sau quiz (none), points=0. checkAndUnlockBadges trong QuizServiceImpl không có nơi gọi và API quiz cấp khoá luôn 400 '' !== 'bronze' |
| F-Q12 / đạt 90% → huy hiệu bronze +100 | Fail | không có huy hiệu sau quiz (none), points=0. checkAndUnlockBadges trong QuizServiceImpl không có nơi gọi và API quiz cấp khoá luôn 400 '' !== 'bronze' |
| F-Q12 / đạt 95% → huy hiệu silver +200 | Fail | không có huy hiệu sau quiz (none), points=0. checkAndUnlockBadges trong QuizServiceImpl không có nơi gọi và API quiz cấp khoá luôn 400 '' !== 'silver' |
| F-Q12 / đạt 100% → huy hiệu gold +300 | Fail | không có huy hiệu sau quiz (none), points=0. checkAndUnlockBadges trong QuizServiceImpl không có nơi gọi và API quiz cấp khoá luôn 400 '' !== 'gold' |
| F-Q13 / bài không thuộc khoá | Fail | bài không thuộc khoá expected 4xx got 500: {"timestamp":"2026-11-06T11:40:47.263098677","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/admin/questions","traceId":"fd1112c0-27de-4ea2-b903-db5789 |
| F-Q15 / sửa đáp án sau khi đã nộp: lịch sử giữ snapshot | Gap | Lượt thi lưu snapshot (quiz_attempts.questions_json), nhưng màn review của học viên đọc câu hiện tại: điểm cũ 2/2 hiển thị cạnh đề đã sửa. Cần chốt yêu cầu theo F-Q15. |
| F-Q15 / sửa câu trong lúc làm: chấm theo bộ câu hiện tại | Gap | Học viên trả lời đúng theo đề đang thấy nhưng bị chấm theo đề đã sửa: 3/4. Phiên không chụp bộ câu, cần chốt theo [ĐX]. |
| F-F05 / lật/phím tắt/shuffle | Gap | Thao tác thuần UI; xem phần kiểm tra trình duyệt trong báo cáo. |
| F-F08 / không có ngày phù hợp | Fail | expected 4xx got 200: {"id":24,"name":"Lịch","startDate":"2026-12-07","endDate":"2026-12-08","weekdays":[5],"reminderTime":"08:00","active":true,"nextReminderAt":null,"items":[{"lessonId":"LSmw4hl4aaij","cardIds":null}],"version":0} |
| F-F08 / chênh 366 ngày (367 ngày tính cả hai đầu) | Gap | Từ ngày đầu đến ngày cuối chênh 366 ngày (367 ngày lịch tính cả hai đầu) vẫn được chấp nhận. Cần chốt cách đếm của "tối đa 366 ngày". |
| F-F12 / hai scheduler thật chạy đồng thời | N/A | Chạy ở NF-C03 (hai backend trên cùng DB test). |
| F-N01 / reply vào comment của bài khác bị chặn | Fail | expected 4xx got 500: {"timestamp":"2026-11-06T11:41:16.730861994","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp sự cố. Vui lòng thử lại sau.","path":"/api/lessons/LSmw4hlq4zjf/comments/1/replies","traceId":"f39dd9 |
| F-R04 / tham số sai: ngày sai định dạng | Fail | HTTP 500: {"timestamp":"2026-11-06T11:46:38.914555964","status":500,"error":"Internal Server Error","message":"Hệ thống đang gặp s |
| F-R05 / nhiều lượt thi: phân trang, phân tích đúng lượt | Fail | Có 7 lượt trong quiz_attempts nhưng API lịch sử chỉ trả 1 dòng: user_quiz_scores chỉ giữ một dòng điểm cao nhất mỗi học viên/bài, không xem được các lượt còn lại 1 !== 7 |
| F-R06 / CSV có Content-Type text/csv và BOM UTF-8 cho Excel | Fail | response không có Content-Type |
| NF-S06 / RAG service không mở ra host | Gap | Trong compose gốc rag-service publish cổng 8001 và API /chat không có xác thực riêng; ai truy cập được cổng này có thể bỏ qua kiểm quyền của backend. Production nên chỉ mở trong mạng nội bộ. |
| NF-O02 / mẫu rút gọn 6+4 câu | Gap | Trong nguồn đúng 6/6, có dẫn chứng 6/6; ngoài nguồn không bịa 4/4. Chấm tự động theo từ khoá, chưa đủ cỡ mẫu 30+20 và chưa có người duyệt nên chưa kết luận tiêu chí [ĐX]. |
| NF-S05 / HTML trong textBody được làm sạch ở backend | Gap | Backend lưu nguyên script trong textBody; lớp bảo vệ duy nhất là sanitize ở frontend (utils/lessonText.ts). Đã kiểm tra render trên trình duyệt, xem báo cáo. |
| NF-S07 / flood đăng nhập sai có hạn chế tần suất | Gap | 60 lần đăng nhập sai đồng thời đều trả 401: không có rate limit/khoá tạm. Ghi nhận gap theo kịch bản. |
| NF-S07 / JSON cực lớn/lồng sâu không làm sập | Fail | stored a 3145728-character comment; no length limit |
| NF-O01 / JSON sai cú pháp → 400, không 500 | Fail | HTTP 500 (mong đợi 400), lỗi client bị GlobalExceptionHandler xếp thành 500 và làm nhiễu cảnh báo |
| NF-O01 / route không tồn tại → 404, không 500 | Fail | HTTP 500 (mong đợi 404), lỗi client bị GlobalExceptionHandler xếp thành 500 và làm nhiễu cảnh báo |
| NF-O01 / sai method → 405, không 500 | Fail | HTTP 500 (mong đợi 405), lỗi client bị GlobalExceptionHandler xếp thành 500 và làm nhiễu cảnh báo |
| NF-O01 / thiếu query param → 400, không 500 | Fail | HTTP 500 (mong đợi 400), lỗi client bị GlobalExceptionHandler xếp thành 500 và làm nhiễu cảnh báo |
| NF-O01 / content-type sai → 415, không 500 | Fail | HTTP 500 (mong đợi 415), lỗi client bị GlobalExceptionHandler xếp thành 500 và làm nhiễu cảnh báo |
| NF-O01 / request bị thiếu body → 400, không 500 | Fail | HTTP 500 (mong đợi 400), lỗi client bị GlobalExceptionHandler xếp thành 500 và làm nhiễu cảnh báo |
| NF-O01 / cảnh báo error/latency/DB pool/scheduler lag | Gap | Stack test không chạy Grafana/Loki; repo chỉ có dashboard log (monitoring/), không thấy luật cảnh báo latency, DB pool hay scheduler lag. Cần bổ sung nếu áp dụng [ĐX]. |
| E2E-01 / nguồn AI đã ingest và duyệt nội dung AI | Gap | Ingest, gắn nguồn và duyệt AI kiểm tra riêng ở F-I01/F-I05/F-I09, không chạy lại trong luồng này do chi phí suy luận. |
| E2E-02 / hai quản trị duyệt/từ chối cùng lúc: trạng thái cuối duy nhất | Fail | statuses 200,200 2 !== 1 |
| E2E-03 / trạng thái đồng bộ offline trên UI | Gap | Cần kiểm tra UI khi mất mạng; [ĐX], xem phần trình duyệt của báo cáo. |
| E2E-05 / giữ hay reset completion lịch sử | Gap | Hiện tại giữ completion cũ dù thêm slide bắt buộc. Kịch bản yêu cầu chốt policy [ĐX] trước khi nghiệm thu. |
| E2E-06 / WebSocket cũ bị đóng sau khoá tài khoản | Gap | Runtime kiểm thử không gửi được cookie qua WebSocket; cần kiểm tra trên trình duyệt. Code có quét định kỳ 15 giây (NotificationWebSocketHandler). |
| E2E-06 / SLA thu hồi token stream | Gap | Token video kiểm tra lại quyền ở mỗi request nên thu hồi có hiệu lực ngay; riêng cache permission và socket 15 giây. [ĐX] cần chốt ngưỡng chấp nhận. |
| NF-R03 / hết dung lượng volume | Blocked | Volume test dùng ổ Docker chung; không lấp đầy để tránh ảnh hưởng stack dev. Cần môi trường riêng có quota. |
| NF-R03 / model timeout | Gap | Không tắt được Ollama vì đang dùng chung với máy dev. Timeout có unit test ở rag-service/tests/test_chat_resilience.py. |
| NF-R02 / job AI đang chạy khi restart backend | Gap | Job nằm trong bộ nhớ RAG service; restart RAG thì polling nhận 404 "không tồn tại", UI phải thoát loading. Chưa kiểm tra UI ở tình huống này. |
| NF-R05 / rolling restart hai backend, reload frontend, job AI còn chạy | Blocked | Stack test không có load balancer trước hai backend nên không đo được "không mất request". Phần scheduler nhiều instance đã kiểm ở NF-C03. |
| NF-P04 / chat 1→2→4→8 đồng thời (rút gọn, mỗi mức một đợt) | Gap | 1: success 1/1, p95 18s \| 2: success 2/2, p95 9s \| 4: success 4/4, p95 11s \| 8: success 8/8, p95 21s. Đợt ngắn, chưa giữ mỗi mức ≥2 phút và chưa tới 12/16; cold/warm chưa tách. Chạy đầy đủ bằng scripts/loadtest/ai-concurrency.js trên staging. |
| NF-P05 / vượt trần job trả 429 có kiểm soát | Fail | 24 job đồng thời trả 202,503: RAG trả 429 nhưng QuizGenerationServiceImpl.startJob đổi mọi RestClientException thành 503 (job thẻ thì giữ 429) |
| NF-R04 / RPO/RTO | Gap | Quy trình pg_dump + tar khôi phục được trong 9s ở cỡ dữ liệu test, dưới mức RTO 4h [ĐX]. Không thấy job backup tự động trong repo nên chưa chứng minh RPO ≤24h; chủ hệ thống cần chốt. |
| NF-C02 / không gửi version: ghi đè âm thầm? | Gap | Version là tuỳ chọn ở API bài học/khoá: client không gửi version vẫn ghi đè. UI có gửi nhưng API không bắt buộc. [ĐX] cần chốt. |
| NF-C02 / yêu cầu đăng ký xử lý song song 5 lần | Fail | statuses 409,200,409,200,200 3 !== 1 |
| NF-D02 / client ở múi giờ khác | Gap | Server trả ngày thuần và Instant có Z; hiển thị phía client cần kiểm tra trên trình duyệt với timezone khác, chưa chạy. |
| NF-D03 / kế hoạch phục hồi khi migration thất bại | Gap | Repo có script migrate nhưng không có Flyway undo/runbook phục hồi; DEPLOYMENT.md chỉ dặn sao lưu trước. Cần tài liệu rollback. |
| NF-P06 / soak 8 giờ | Blocked | Không chạy trên máy trạm dùng chung; cần staging. Kịch bản có thể chạy bằng tests/system/k6/load.js với STAGES 8h. |
| NF-P02 / DB 1k user / 100 khoá | Fail | trang đầu vượt 3s (report): users/page 36ms/9KB, users search 11ms/0KB, leaderboard 11ms/3KB, dashboard 19ms/5KB, users (full list) 645ms/804KB, report read timeout>120s, CSV export 23983ms/23761KB 1 !== 0 |
| NF-P02 / DB 10k user / 100 khoá | Fail | trang đầu vượt 3s (report): users/page 31ms/9KB, users search 27ms/0KB, leaderboard 19ms/3KB, dashboard 24ms/5KB, users (full list) 3420ms/5225KB, report read timeout>120s, CSV export timeout>120s 1 !== 0 |
| NF-P02 / DB 100k user / 1k khoá | Fail | trang đầu vượt 3s (report): users/page 87ms/9KB, users search 99ms/5KB, leaderboard 139ms/3KB, dashboard 31ms/5KB, users (full list) 35051ms/49920KB, report read timeout>120s, CSV export timeout>120s 1 !== 0 |
| NF-P08 / CSV lớn đồng thời với nộp bài | Fail | Export không xong trong 180s (HTTP timeout>180s) ở dữ liệu 100k user/1k khoá; nộp bài song song vẫn p95=50ms + actual - expected + 'timeout>180s' - 200 |
| F-A02 / UI: ô tên đăng nhập điền sẵn admin | Fail | Login.vue:319 khởi tạo username = ref("admin") trong bản build production: lộ tên tài khoản quản trị và người dùng gõ tiếp thành adminui_learner rồi đăng nhập thất bại. |
| F-Q06 / UI: nộp thủ công khi chưa trả lời hết | Gap | Nút Nộp bài bị khoá đến khi làm đủ câu ("Hoàn thành tất cả để nộp bài"); chỉ auto-submit mới nộp được một phần. Cần xác nhận đúng ý đồ. |
| NF-U01 / Chromium 390×844: trang bài học | Fail | Drawer .course-sidebar position:fixed đặt ở x=390 làm layout viewport giãn lên 733px và lộ bóng ở mép phải màn hình. Mức thấp, nội dung vẫn đọc được. |
| NF-U01 / Firefox, Safari, Edge, tablet | Blocked | Môi trường chỉ có Chromium của app; chưa chạy các trình duyệt còn lại. |
| NF-U02 / Tên truy cập của nút | Fail | Trang flashcard có 2 nút mở rộng khoá/bài không có accessible name; trang bài học có 2 nút không tên. Trình đọc màn hình đọc thành "button". Form đăng nhập có label đúng. |
| NF-U02 / Screen reader, zoom 200%, đo tương phản, trap modal | Blocked | Cần kiểm tra thủ công với screen reader và công cụ đo; mới kiểm tra được label, tên nút và điều khiển flashcard bằng bàn phím. |
| NF-U03 / Double click, back/forward, API chậm trong modal | Blocked | Chưa chạy trên UI. Phía server các request lặp đã được kiểm tra ở F-L08, F-Q08 và NF-R01. |

Toàn bộ dòng, kể cả Pass, nằm trong `tests/system/results/all.json` và `tests/system/ui-results.json`. Hội thoại AI nằm trong `tests/system/results/ai-transcript.json`; số đo tải trong `tests/system/results/load-metrics.json`.
