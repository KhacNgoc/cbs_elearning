# Kịch bản kiểm thử Functional và Non-functional — CBS E-learning

Ngày lập: 06/11/2026. Cơ sở: mã nguồn trong workspace tại thời điểm khảo sát.

Đây là bộ kịch bản để thực thi kiểm thử, **chưa phải kết quả chạy kiểm thử**. Phạm vi bao gồm giao diện Vue, API Java, RAG service, PostgreSQL, lưu trữ tài liệu/video, email OTP, WebSocket và tác vụ nhắc học. Không dùng trang mockup báo cáo làm bằng chứng nghiệm thu.

## 1. Quy ước và phạm vi

- **F**: functional; **NF**: non-functional; **E2E**: luồng xuyên hệ thống, tham chiếu cả F và NF.
- **H**: happy case; **N**: negative case; **B**: boundary; **W**: worst case.
- **P0**: bảo mật, mất/sai dữ liệu, luồng học/thi chính; **P1**: chức năng quan trọng; **P2**: trải nghiệm và trường hợp ít gặp.
- Mỗi dòng là một ca có thể thực thi. Các giá trị liệt kê trong một dòng phải chạy thành các biến thể riêng; không đánh Pass cả dòng khi mới thử một giá trị.
- “Bị chặn”: kiểm tra cả thông báo UI, HTTP response và dữ liệu không bị thay đổi. Với request ghi, phải lấy CSRF hợp lệ trừ ca chủ động kiểm tra CSRF; tránh nhầm lỗi CSRF với lỗi nghiệp vụ.
- Các tiêu chí gắn **[ĐX]** là đề xuất nghiệm thu, cần chốt theo SLA và nghiệp vụ. Chênh lệch với tiêu chí này được ghi nhận là khoảng trống/yêu cầu cần quyết định; không mặc định mọi khác biệt đều là lỗi đã xác nhận.
- Kết quả không suy ra từ tên test hay README. Đặc biệt, endpoint tồn tại không đồng nghĩa được phép truy cập.

### Các quy tắc đã đối chiếu code

1. `/api/auth/register` và `/api/auth/reset-password-direct` bị `denyAll`; tạo người dùng qua quản trị, đặt lại mật khẩu qua OTP.
2. OTP có hiệu lực 5 phút, tối đa 5 lần thử; reset thành công hủy OTP và tăng phiên bản token.
3. Hoàn thành nội dung xét văn bản, video, tài liệu có mục đích `SLIDE` và cờ bắt buộc. Tài liệu phụ trợ không chặn hoàn thành. Video bắt buộc cần xem ít nhất 90%; mọi slide bắt buộc phải được đánh dấu đã đọc đến cuối.
4. Hoàn thành bài còn phụ thuộc quiz bắt buộc. Mở bài có nội dung nhưng không có thành phần bắt buộc không được tự thưởng hoàn thành chỉ vì truy cập.
5. Lần đầu hoàn thành bài được cộng 100 điểm; gọi lặp phải không cộng lại.
6. Quiz bài học: ngưỡng đạt cấu hình 1–100; mặc định 80. Thời gian null hoặc 1–300 phút. Quiz có giờ dùng phiên server; có khoảng đệm nộp 30 giây sau deadline. Không áp dụng suy diễn cơ chế phiên này cho mọi API quiz cấp khóa.
7. Huy hiệu khóa theo tỷ lệ đúng: 100% vàng +300; >90% và <100% bạc +200; từ 80% đến 90% đồng +100. Ngưỡng đạt quiz là quy tắc khác với ngưỡng huy hiệu.
8. Flashcard không tính vào hoàn thành bài/khóa; không có lịch ôn SM-2 hoặc API tự chấm mức nhớ hiện hành. Thẻ cá nhân tối đa 200/bài; người khác không được sửa/xóa; quản trị không sửa thẻ cá nhân qua công cụ thẻ dùng chung.
9. Mỗi người tối đa 20 lịch flashcard; khoảng lịch tối đa 366 ngày; múi giờ `Asia/Ho_Chi_Minh`; nhắc trong app/WebSocket, không phải email. Lần nhắc trễ quá 2 giờ bị bỏ qua. Chọn cả bài khác với chọn cố định từng thẻ.
10. Quyền thao tác và phạm vi đơn vị phải được giữ theo từng vai trò; không ghép quyền từ vai trò A với phạm vi của vai trò B để mở rộng quyền.

## 2. Chuẩn bị môi trường và dữ liệu

Thực thi trên staging cô lập, có đủ frontend/backend/RAG/model, PostgreSQL, mail sandbox và lưu trữ giống production. Dùng PostgreSQL cho kiểm thử khóa, transaction, scheduler nhiều instance và migration. Ghi lại commit, cấu hình triển khai, phiên bản trình duyệt, model AI và cấu hình tải; không sao chép mật khẩu thật vào báo cáo.

| Mã | Dữ liệu chuẩn bị |
|---|---|
| U0 | Quản trị hệ thống; U1 quản trị đơn vị A; U2 giảng viên được giao C1; U3 chỉ đọc báo cáo A |
| U4–U7 | Học viên A mới; học viên A đang học; học viên B; học viên bị khóa. Mỗi luồng E2E dùng tài khoản riêng |
| U8 | Người có hai vai trò: quyền sửa khóa trong A; quyền chỉ đọc trong B |
| O1 | Cây A → A1 → A11; nhánh độc lập B → B1; một đơn vị inactive |
| C1 | Khóa đã xuất bản, 2 chương, ít nhất 3 bài, có thứ tự mở khóa; phân bổ cho A; đăng ký cần duyệt |
| C2–C4 | Khóa nháp; khóa lưu trữ; khóa xuất bản chỉ cho B. Thêm khóa có cơ chế đăng ký khác nếu cấu hình hỗ trợ |
| L1 | Văn bản + video 100 giây + 2 slide bắt buộc + tài liệu phụ trợ + quiz bắt buộc |
| L2–L4 | Bài chỉ văn bản; bài chỉ video; bài có nội dung nhưng tất cả nội dung không bắt buộc, không quiz bắt buộc |
| Q1 | Quiz có 20 câu đã biết đáp án, gồm một/nhiều lựa chọn; ngưỡng đạt 80; thời gian 1 phút |
| Q2 | Quiz không giới hạn giờ; quiz rỗng; bộ 100 câu để kiểm tra sát ngưỡng làm tròn khi cần |
| D1 | PDF/Word/slide hợp lệ theo định dạng hệ thống hỗ trợ; file hỏng, rỗng, sai MIME, tên tiếng Việt, tên có ký tự đặc biệt |
| D2 | Dung lượng M−1, M, M+1 theo giới hạn upload thực tế; file nhiều trang; video ngắn/dài và nguồn YouTube |
| AI1 | Tài liệu có nội dung kiểm chứng được: ít nhất 30 câu hỏi có đáp án và nguồn chuẩn; thêm tài liệu thiếu text, chưa ingest, và có câu lệnh prompt injection giả lập |
| FC1 | Thẻ chung nháp/đã xuất bản; thẻ cá nhân của hai người; thẻ thuật ngữ và điền khuyết; lịch active/paused/không nhắc |
| T1 | Dữ liệu báo cáo có học viên chưa học/đang học/đạt/trượt/quá hạn; ngày ở 23:59 và 00:00 UTC+7; học viên thi nhiều lần |

**Bằng chứng chung:** ảnh UI, request/response đã che dữ liệu nhạy cảm, correlation ID, trạng thái DB trước/sau (chỉ đọc), log liên quan. Dùng đồng hồ kiểm thử hoặc môi trường có thể điều khiển thời gian cho OTP/deadline/scheduler; không đổi giờ hệ thống dùng chung.

## 3. Các luồng E2E bắt buộc

### E2E-01 — Từ thiết lập đến hoàn thành đào tạo (H, P0)

**Tiền điều kiện:** staging sạch cho bộ dữ liệu riêng; U0 có quyền quản trị; người học chưa có tiến độ.

1. U0 tạo cây đơn vị A/A1, vai trò và người học; đặt yêu cầu đổi mật khẩu lần đầu.
2. U0 tạo lĩnh vực → chương trình → C1 → 2 chương → L1/L2/L3; lưu thứ tự.
3. U2 tải nội dung, slide/video; gắn nguồn kiến thức đã ingest; tạo quiz và flashcard; duyệt nội dung AI trước khi phát hành.
4. Xuất bản các thành phần cần thiết, phân bổ C1 cho A, gán hạn học.
5. Người học đăng nhập, đổi mật khẩu, tìm C1, gửi đăng ký. Khi chờ duyệt, thử mở bài/API trực tiếp.
6. U1 duyệt; người học nhận thông báo, mở outline và bài đầu. Thử nhảy đến bài/chương chưa mở khóa.
7. Học đủ văn bản, video ≥90%, mọi slide bắt buộc; tạo ghi chú/bình luận, hỏi chatbot có nguồn.
8. Làm quiz: một lượt trượt rồi một lượt đạt; lần lượt học các bài/chương còn lại và hoàn thành khóa.
9. Đánh giá khóa, mở hồ sơ, dashboard và leaderboard; U3 mở báo cáo, chi tiết lượt thi và xuất CSV.
10. Đăng xuất, đăng nhập lại để đối chiếu tiến độ và quyền.

**Mong đợi:** chỉ truy cập sau khi đủ quyền/điều kiện; thứ tự mở khóa chính xác; điểm, lượt thi, huy hiệu và trạng thái hoàn thành nhất quán giữa UI/API/DB/báo cáo; không cộng trùng. CSV khớp bộ lọc và phạm vi. Luồng đạt cả F-A01…F-R06 liên quan và NF-S01/NF-S03/NF-D01, không chỉ UI chạy hết màn hình.

### E2E-02 — Bị từ chối, sửa điều kiện và đăng ký lại (N → H, P1)

**Tiền điều kiện:** người học A chưa có enrollment C1; quản trị có quyền duyệt.

1. Gửi đăng ký hai lần, tải lại, gửi lại request cũ.
2. Quản trị từ chối; người học đọc thông báo và thử truy cập bài, tài liệu, quiz, chat của khóa.
3. Thực hiện lại đăng ký theo chính sách hiện hành; nếu cho phép, quản trị duyệt, học viên học bình thường.
4. Trong một biến thể, khóa bị archive trước khi duyệt; trong biến thể khác, hai quản trị duyệt/từ chối cùng lúc.

**Mong đợi:** không có yêu cầu active trùng; từ chối không mở quyền; trạng thái cuối duy nhất, có thông báo phù hợp. Việc đăng ký lại phải được xác nhận theo chính sách thực tế; archive không bị vượt qua bằng request cũ. Áp dụng F-E02…F-E06, NF-C02.

### E2E-03 — Học gián đoạn, đổi thiết bị, request đến sai thứ tự (W, P0)

**Tiền điều kiện:** người học đã được duyệt; mở L1 ở hai tab/thiết bị.

1. Đọc slide thứ nhất đến trang giữa; chuyển slide thứ hai, chuyển video; lưu mốc.
2. Ngắt mạng khi tiến độ đang lưu; tiếp tục thao tác; chuyển route rồi đóng/mở lại trình duyệt.
3. Kết nối lại; phát lại request cũ, cho request cũ đến sau request mới.
4. Mở thiết bị thứ hai, hoàn thành nội dung; đồng thời hai tab gọi hoàn thành bài.

**Mong đợi:** tiến độ đã được server xác nhận còn nguyên; không gán bookmark của tài liệu này sang tài liệu khác; watchedSeconds không bị giảm do request cũ; lastPosition là bookmark, có thể lùi khi người học chủ động tua lại. Hoàn thành/điểm không lặp. **[ĐX]** Đồng bộ phần chưa được server xác nhận phải có trạng thái rõ ràng; không mặc định hỗ trợ học offline. Áp dụng F-L03…F-L10, NF-C01/NF-R01.

### E2E-04 — Thi hết giờ, mất phản hồi, nộp trùng (W, P0)

**Tiền điều kiện:** Q1 giới hạn 1 phút, phiên mới cho mỗi biến thể.

1. Start; sau 20 giây reload/mở tab thứ hai; sửa giờ client.
2. Trả lời một phần, để hết giờ và quan sát auto-submit.
3. Chặn response của request nộp nhưng để server xử lý; bấm nộp lại và gửi hai request đồng thời.
4. Ở các phiên độc lập, nộp tại deadline−1s, deadline+29s, deadline+31s; thử sessionId của người khác/bài khác.
5. Đối chiếu điểm, lịch sử lượt thi, tiến độ, huy hiệu và báo cáo sau reconnect.

**Mong đợi:** reload không cấp thêm thời gian cho phiên đang còn hiệu lực; server quyết định deadline; trong khoảng đệm xử lý đúng, quá 30s bị từ chối; một phiên không được tiêu thụ hai lần. Kết quả đã lưu có thể tra cứu lại; không cộng điểm hai lần. Áp dụng F-Q05…F-Q11, NF-C01/NF-D01. Kiểm thử thời điểm sát đúng +30s bằng clock kiểm soát, không dựa vào độ trễ mạng thủ công.

### E2E-05 — Thay nội dung sau khi đã có người hoàn thành (N/W, P0)

**Tiền điều kiện:** một người hoàn thành L1, một người học dở; lưu snapshot version và điểm.

1. U2 sửa văn bản, thay video, thêm/xóa slide bắt buộc; lưu phiên bản mới.
2. Xem số người đã hoàn thành, tạo/tinh chỉnh tóm tắt thay đổi và gửi thông báo.
3. Người học mở thông báo, đọc thay đổi, vào lại bài; kiểm tra bookmark và completion theo policy version hiện hành.
4. Phát lại progress của version cũ; chạy biến thể đổi slide thành tài liệu phụ trợ và ngược lại.
5. Kiểm tra dashboard, outline, báo cáo; không học lại chỉ để đạt trạng thái UI giả.

**Mong đợi:** thông báo đúng đối tượng; nội dung hiện tại được dùng khi đánh giá yêu cầu bắt buộc; không chặn hoàn thành vì tài liệu phụ trợ; không thưởng lại do lưu nội dung. **[ĐX]** Quyết định giữ hay reset completion lịch sử cần được chốt theo thiết kế/version thực tế trước khi nghiệm thu; không tự giả định mọi cập nhật đều buộc học lại. Áp dụng F-L11/F-C06/F-N04, NF-D02.

### E2E-06 — Thu hồi quyền trong phiên đang hoạt động (W, P0)

**Tiền điều kiện:** người học A đang mở bài/chat/video; U8 đang sửa khóa A và chỉ đọc B.

1. Thu hồi role/ghi danh/phân bổ hoặc khóa tài khoản; lần lượt chạy từng biến thể.
2. Giữ tab cũ, gọi lại API bài, tiến độ, quiz, tài liệu, flashcard, lịch, lịch sử chat và export.
3. Thử request sửa khóa B bằng U8; thay ID tài nguyên sang nhánh B.
4. Kiểm tra video token còn hạn/hết hạn, kết nối WebSocket hiện hữu và sau reconnect.
5. Khôi phục quyền và xác nhận dữ liệu học trước đó không bị mất.

**Mong đợi:** backend chặn hành động không còn quyền theo cơ chế hiệu lực/cache đã xác minh; UI cập nhật quyền, không là lớp bảo vệ duy nhất. Không trộn quyền/phạm vi giữa role; không lộ tài nguyên của B. **[ĐX]** Ghi rõ thời gian thu hồi tối đa chấp nhận được với token stream đã cấp, cache và socket. Áp dụng F-A05/F-P03/F-E06, NF-S03…NF-S06.

### E2E-07 — Chu trình flashcard và lịch nhắc (H/B/W, P1)

**Tiền điều kiện:** người học có quyền nhiều bài/khóa; có thẻ dùng chung, thẻ riêng và scheduler.

1. U2 tạo thẻ thủ công và AI, duyệt rồi publish; người học mở thẻ trong bài.
2. Người học tạo/sửa thẻ riêng, chọn thẻ từ nhiều bài/khóa và học; lật, chuyển, trộn, dùng phím tắt.
3. Tạo một lịch chọn cả bài và một lịch chọn cố định thẻ; thêm thẻ mới vào bài.
4. Đến giờ nhắc, mở thông báo → đúng lịch; pause/resume/sửa/xóa lịch.
5. Xóa một thẻ, thu hồi quyền một bài, chạy hai scheduler cùng lúc; thử nhắc trễ quá 2 giờ.

**Mong đợi:** thẻ riêng chỉ chủ sở hữu thấy/sửa; thẻ nháp không đến học viên; lịch cả bài có thẻ mới, lịch cố định giữ lựa chọn; bỏ qua thẻ xóa/bài mất quyền; không gửi nhắc trùng/dồn quá hạn. Không tăng điểm/tiến độ vì chỉ lướt thẻ. Áp dụng F-F01…F-F12, NF-C03.

### E2E-08 — AI quá tải và phục hồi phụ thuộc (W, P0)

**Tiền điều kiện:** nguồn AI có đáp án chuẩn; ghi số kết nối DB và latency API học/thi nền.

1. Học viên chat nhiều lượt/cross-course trong phạm vi; U2 đồng thời sinh đề và flashcard.
2. Tăng tải đến trần job; giả lập model timeout, RAG unavailable, file nguồn bị xóa lúc ingest.
3. Trong thời gian đó tiếp tục đăng nhập, mở bài, lưu tiến độ, nộp bài và đọc thông báo.
4. Khôi phục dịch vụ; kiểm tra trạng thái job, lịch sử chat, dữ liệu đã duyệt và retry.

**Mong đợi:** có trạng thái lỗi/bận rõ ràng, không giả báo thành công; không tự publish kết quả; không lộ nguồn ngoài phạm vi; không giữ cạn DB pool; API nền đáp ứng ngưỡng đã chốt. Không tạo trùng kết quả đã lưu khi retry. Áp dụng F-I01…F-I10, NF-P04/NF-P05/NF-R03.

### E2E-09 — Backup, sự cố và đối soát toàn hệ thống (W, P0)

**Tiền điều kiện:** môi trường phục hồi riêng; snapshot chứa DB và file liên quan; có dữ liệu đã biết trước.

1. Tạo enrollment, tiến độ, lượt thi, thẻ, lịch và báo cáo chuẩn; ghi thời điểm commit.
2. Backup, mô phỏng mất backend/DB/volume theo từng lượt; phục hồi DB và file tương ứng.
3. Đăng nhập, mở bài/tài liệu/video, review thi, mở lịch và lịch sử chat.
4. Đối chiếu số bản ghi, tổng điểm, số lượt thi, liên kết file và CSV trước/sau; kiểm tra nhắc học sau phục hồi.

**Mong đợi:** dữ liệu nằm trong RPO đã chốt; phục hồi trong RTO; không FK mồ côi/file mất âm thầm, không cộng thưởng hoặc nhắc trùng do replay. Áp dụng NF-R04/NF-D01/NF-D03. Chỉ chạy thao tác phá hủy trong môi trường phục hồi riêng.

## 4. Functional test cases

Tiền điều kiện mặc định: dịch vụ sẵn sàng; đúng role và phạm vi; dữ liệu theo mục 2. Mỗi hàng nêu điều kiện bổ sung, thao tác và kết quả quan sát được.

### 4.1. Xác thực, phiên và tài khoản

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-A01 | H/P0 | Người dùng active: đăng nhập đúng; reload; vào link bài trực tiếp | Có session, menu đúng quyền, duy trì đăng nhập và route hợp lệ |
| F-A02 | N/B/P0 | Thử sai mật khẩu, username không tồn tại, rỗng, khoảng trắng, chuỗi rất dài | Không tạo session; validation/thông báo rõ; không 500 hoặc trả hash/token |
| F-A03 | H/N/P0 | Tài khoản mustChangePassword: thử bỏ qua bằng URL và API; đổi mật khẩu hợp lệ; đăng nhập lại | Bắt đổi mật khẩu theo policy; mật khẩu mới hoạt động; API nhạy cảm không vượt điều kiện qua UI |
| F-A04 | H/N/P0 | OTP: yêu cầu với thông tin hợp lệ, nhận mail sandbox, reset; thử lại mã đã dùng và mật khẩu cũ | Reset thành công một lần; OTP bị hủy; mật khẩu cũ và token trước reset không dùng được |
| F-A05 | W/P0 | Đăng nhập nhiều tab; khóa tài khoản/đăng xuất; phát lại cookie/token cũ | Session bị thu hồi theo tokenVersion; API bảo vệ từ chối; không lộ dữ liệu cache của người trước |
| F-A06 | B/N/P0 | OTP tại trước/sau 5 phút; sai 5 lần rồi đúng; xin lại OTP rồi dùng mã cũ | Đúng giới hạn expiry/attempt; mã cũ không dùng được sau thay thế; không đổi mật khẩu khi thất bại |
| F-A07 | N/W/P1 | Email sai/không tồn tại; SMTP unavailable khi xin OTP | Không lộ tài khoản qua nội dung thông báo; lỗi gửi mail rõ; không báo reset thành công |
| F-A08 | N/P0 | Có CSRF hợp lệ, gọi register và reset-password-direct không đăng nhập và có đăng nhập | Hai endpoint bị chặn; không tạo tài khoản/đổi mật khẩu |
| F-A09 | B/N/P1 | Đổi mật khẩu <8 ký tự và giá trị biên theo DTO/policy; đổi hồ sơ trường hợp lệ và trường quyền tự chèn | Validation nhất quán; không tự đổi role/đơn vị/points qua cập nhật hồ sơ |

### 4.2. Phân quyền, đơn vị, người dùng

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-P01 | H/N/P0 | Tạo role chỉ read; cấp cho U3; mở menu, rồi gọi create/update/delete trực tiếp | Read đúng phạm vi; mọi thao tác ngoài quyền bị chặn tại API |
| F-P02 | H/N/P0 | U1 quản lý A/A1; tìm/mở/sửa người và khóa ở B qua UI và đổi ID API | Không thấy hoặc truy cập được đối tượng ngoài phạm vi; số liệu tổng cũng không lộ |
| F-P03 | W/P0 | U8: update A, read B; dùng quyền update gửi ID khóa B | Bị chặn; quyền và scope không ghép chéo giữa hai role |
| F-P04 | H/N/P1 | U2 chỉ được giao C1: xem/sửa C1 và khóa khác chưa được giao; role inactive | Chỉ thao tác khóa được giao khi đủ quyền; role inactive không cấp quyền |
| F-P05 | H/B/N/P1 | Tạo/sửa đơn vị cha/con; mã trùng; tự làm cha; chuyển cha vào hậu duệ; deactivate cha | Cây hợp lệ, không chu trình; mã trùng không lưu; phạm vi khi cha inactive đúng policy |
| F-P06 | H/N/P1 | Tạo/sửa/khóa/xóa user; trùng username/email theo constraint; đơn vị không hợp lệ | Dữ liệu hợp lệ được lưu; lỗi không tạo bản ghi nửa chừng; quan hệ lịch sử được xử lý theo policy |
| F-P07 | H/N/P1 | Tải template người dùng, import file đúng; chạy file thiếu cột, trùng dòng, sai đơn vị, lỗi giữa file | Template dùng được; lỗi chỉ rõ; đối chiếu commit/rollback thực tế, không báo thành công toàn bộ khi có lỗi |
| F-P08 | H/N/P0 | Sửa membership bằng replace và delta; thêm/xóa cùng user; sửa role bảo vệ với role thường | Thành viên cuối đúng; không nâng quyền trái phép; role bảo vệ giữ ràng buộc hệ thống |
| F-P09 | H/W/P1 | Thay quyền khi tab admin đang mở; chuyển trang trước/sau chu kỳ revalidate 30s | Backend kiểm quyền; menu/route cập nhật sau revalidate; không vòng lặp chuyển hướng |

### 4.3. Cấu trúc đào tạo, bài học và tài liệu

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-C01 | H/P1 | Tạo/sửa lĩnh vực → chương trình → khóa → chương → bài; reload từng cấp | Quan hệ và nội dung đã lưu đúng; tra cứu theo cha trả đúng con |
| F-C02 | N/B/P1 | Bỏ trường bắt buộc, mã trùng, ID cha sai; xóa cha đang có dữ liệu con | Validation trước khi commit; không dữ liệu mồ côi; ràng buộc xóa thể hiện rõ |
| F-C03 | H/N/P1 | Đổi thứ tự chương/bài; gửi danh sách ID trùng, thiếu, thuộc khóa khác | Thứ tự hợp lệ tồn tại sau reload; không chuyển nhầm tài nguyên qua scope |
| F-C04 | H/N/P0 | Chuyển nháp → xuất bản → lưu trữ; mở bằng học viên ở từng trạng thái | Chỉ nội dung đủ điều kiện được học; archive/draft không bị vượt bằng URL/API |
| F-C05 | B/N/P0 | Publish bài rỗng, HTML chỉ whitespace, chỉ file phụ trợ; rồi thêm text/video/slide | Bài không có nội dung thực bị chặn; tài liệu phụ trợ không thay nội dung bài |
| F-C06 | H/W/P1 | Sửa bài có người hoàn thành; xem count, sinh tóm tắt, chỉnh lại và gửi changelog | Count đúng; summary có thể kiểm tra; thông báo và latest-changelog trỏ đúng bài/version |
| F-C07 | H/B/N/P1 | Upload ảnh khóa, slide, video, tài liệu đúng; file rỗng/hỏng/sai MIME/M−1/M/M+1 | File đúng hiển thị; file sai/quá giới hạn bị chặn; không tham chiếu file không tồn tại |
| F-C08 | H/N/P1 | Gắn tài liệu nguồn theo bài/mục outline; chọn ID ngoài khóa; lưu rồi tải lại | Nguồn/mục chọn đúng; nguồn ngoài phạm vi không được gắn; không tin tên nguồn từ client |
| F-C09 | H/W/P1 | Xóa tài liệu đã ingest/đang được tham chiếu; đọc lại RAG và nội dung bài | Không còn truy xuất nguồn đã xóa; không lỗi toàn trang; thẻ đã duyệt không tự bị xóa theo tài liệu |
| F-C10 | H/N/P1 | Dùng nhập nhanh chương trình/nội dung nếu màn hình cung cấp; lỗi ở một mục giữa danh sách | Cấu trúc tạo đúng; nêu rõ mục lỗi; kiểm tra không tạo trùng khi retry |

### 4.4. Phân bổ, đăng ký và hạn học

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-E01 | H/P0 | Phân bổ theo lĩnh vực/chương trình/từng khóa cho A; đăng nhập người A và B | Danh mục và khả năng đăng ký khớp grantType và phạm vi đã cấu hình |
| F-E02 | H/N/P0 | U4 gửi đăng ký C1; mở bài/quiz/tài liệu trước và sau U1 duyệt | Chờ duyệt chưa học được; sau duyệt quyền học nhất quán |
| F-E03 | N/W/P1 | Gửi đăng ký trùng từ hai tab; phát lại request | Không nhiều yêu cầu/enrollment active cho cùng người/khóa |
| F-E04 | N/H/P1 | Từ chối yêu cầu, kiểm tra lý do/thông báo; thử đăng ký lại | Trạng thái và quyền không mâu thuẫn; đường đăng ký lại theo chính sách thực tế |
| F-E05 | H/B/N/P1 | Batch assign/sync/remove; chọn cả chương trình; thử payload trộn khóa A/B ngoài quyền | Chỉ mục hợp lệ trong phạm vi được xử lý theo contract; không ghi một phần âm thầm |
| F-E06 | W/P0 | Thu hồi phân bổ/enrollment, đổi đơn vị user, archive khóa khi đang học | API học phản ánh quyền hiện hành; dữ liệu học lịch sử không bị mất ngoài policy |
| F-E07 | H/B/P1 | Gán/sửa hạn học; trước hạn, đúng hạn, sau hạn và quanh 00:00 UTC+7 | Dashboard/deadline/báo cáo cùng quy tắc; không tự giả định quá hạn đồng nghĩa khóa quyền học |

### 4.5. Học nội dung, bookmark, tiến độ

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-L01 | H/N/P0 | Học L1 theo thứ tự; trước khi đủ điều kiện mở bài/chương sau bằng UI/API | Outline đúng; bài/chương khóa không mở được qua API trực tiếp |
| F-L02 | H/B/P0 | Văn bản bắt buộc: chưa đọc/đọc hết; video 89.9%/90%/100%; từng slide chưa/đã đến cuối | Chỉ đủ mọi thành phần bắt buộc mới đạt content-complete; video đạt từ 90% |
| F-L03 | H/P1 | Hai tài liệu: lưu trang/scroll riêng, đổi tab, refresh và resume từ dashboard | Khôi phục đúng bài và vị trí từng tài liệu; không nhầm giữa hai documentId |
| F-L04 | H/N/P1 | Video upload và YouTube: play/pause/seek/reload; video lỗi hoặc bị gỡ | Bookmark đúng; lỗi có thông báo, không giả hoàn thành khi video không tải được |
| F-L05 | N/B/P0 | Gửi progress âm, documentPage=0, contentType lạ, documentId của bài khác, NaN/Infinity nếu parser nhận | Từ chối dữ liệu không hợp lệ; không ghi progress bẩn hoặc hoàn thành nhầm |
| F-L06 | H/N/P0 | Đủ nội dung nhưng quiz bắt buộc chưa đạt; đạt quiz nhưng thiếu nội dung | Chưa hoàn thành bài cho đến khi đủ cả hai; quiz tùy chọn không chặn |
| F-L07 | H/B/P1 | Chỉ text/đơn video/đơn slide; tài liệu phụ trợ chưa mở; tất cả cờ required=false | Đúng từng tổ hợp; không buộc học tài liệu phụ trợ; mở bài không tự thưởng khi không có nội dung bắt buộc |
| F-L08 | W/P0 | Gọi hoàn thành bài lặp/đồng thời; reload nhiều lần | Một completion; cộng 100 điểm một lần; phần trăm không vượt 100 |
| F-L09 | H/N/P1 | Gọi reset completion rồi mở lại/học lại; thử ID bài không có quyền | Reset đúng người/bài; kiểm tra điểm và mở khóa theo policy hiện hành, không tăng điểm vô hạn **[ĐX]** |
| F-L10 | W/P0 | Mất mạng khi lưu; request watchedSeconds cũ đến sau mới; hai thiết bị lưu | Không giảm watchedSeconds tích lũy; không mất dữ liệu server đã xác nhận; thông báo trạng thái lưu rõ |
| F-L11 | W/P0 | Thêm/bớt nội dung bắt buộc, đổi purpose SLIDE sang phụ trợ sau tiến độ cũ | Đánh giá requirements hiện tại; không chặn vì supporting file; completion lịch sử/version theo quyết định mục E2E-05 |
| F-L12 | H/B/P1 | Hoàn thành bài cuối, khóa rỗng/chỉ nháp hoặc thêm bài sau khi hoàn thành | Tổng số bài/mẫu số và trạng thái khóa theo policy; không chia 0 hoặc hiển thị NaN |

### 4.6. Thi, ngân hàng câu hỏi và chống gian lận nghiệp vụ

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-Q01 | H/N/P0 | Mở quiz-context cấp bài/khóa; kiểm tra chưa mở khóa, thiếu quyền, quiz rỗng | Đúng scope và điều kiện; quiz rỗng không được tính đạt/thưởng |
| F-Q02 | H/B/P0 | Một lựa chọn và nhiều lựa chọn: đúng hết, thiếu một đáp án, thừa một đáp án, đảo thứ tự chọn | Chấm theo tập đáp án đúng; không coi đáp án thiếu/thừa là hoàn toàn đúng |
| F-Q03 | B/P0 | Bộ 20 câu: 0/20, 15/20, 16/20, 18/20, 19/20, 20/20; đổi passingScore | Điểm lần lượt 0/75/80/90/95/100; passed theo cấu hình; huy hiệu tách riêng ngưỡng đạt |
| F-Q04 | N/W/P0 | Sửa score/percent/points trong payload; gửi questionId lạ/khác quiz, index ngoài khoảng, đáp án trùng | Server tự chấm bộ câu hợp lệ; không nâng điểm hoặc thưởng theo giá trị client |
| F-Q05 | H/B/P0 | Giới hạn null, 1, 300 phút; cấu hình 0, −1, 301; passingScore 0/1/100/101 | Null không giới hạn; chỉ 1–300 và passingScore 1–100 được lưu |
| F-Q06 | W/P0 | Start quiz có giờ, reload và start lại khi phiên đang còn hạn; mở hai tab | Dùng lại phiên hiện hành, remainingSeconds giảm; không cộng thêm giờ |
| F-Q07 | B/W/P0 | Nộp trước deadline và +29/+31s; chỉnh giờ client | Server chấp nhận trong grace 30s; sau grace từ chối; client không kéo dài giờ server |
| F-Q08 | N/W/P0 | Nộp thiếu sessionId, ID của người khác/bài khác, đã nộp; hai request nộp cùng lúc | Phiên không hợp lệ bị chặn; phiên hợp lệ không tạo hai kết quả/thưởng |
| F-Q09 | H/N/P0 | Không trả lời/một phần; hết giờ; rời trang rồi quay lại | Chấm phần thực nộp, câu bỏ trống không được đúng; không giả lưu lựa chọn chưa được xác nhận |
| F-Q10 | H/N/P0 | Trước nộp xem JSON/network/source; sau nộp mở review của mình; sửa ID để xem người khác | Không lộ đáp án trước nộp; review đúng lượt/quyền; không lộ bài người khác |
| F-Q11 | W/P0 | Server nhận bài nhưng response timeout; reconnect và tra cứu review/lịch sử | Kết quả đã commit còn nguyên; không yêu cầu nộp lại rồi cộng thưởng trùng |
| F-Q12 | H/B/P1 | Đạt 80/90/95/100% trên quiz khóa; thi lại cùng mức, rồi mức cao hơn | Đồng ở 80/90, bạc ở 95, vàng ở 100; mỗi huy hiệu chỉ cấp/thưởng một lần; đối chiếu policy nâng hạng |
| F-Q13 | H/N/P1 | Tạo/sửa/xóa câu trong quiz; một/nhiều lựa chọn; không đáp án đúng, đáp án ngoài options | Câu hợp lệ lưu được; câu sai bị chặn; không đổi nhầm bài/khóa |
| F-Q14 | H/B/N/P1 | Tạo bank question, capture từ quiz, tìm/lọc; add 1/100/101 câu, ID trùng/null/ngoài quyền | 1–100 ID khác nhau hợp lệ; chặn payload sai; bản sao/câu gắn đúng scope |
| F-Q15 | W/P0 | Sửa/xóa câu hoặc quiz trong lúc học viên làm; mở lại review/lịch sử cũ | **[ĐX]** Không âm thầm chấm sai theo bộ câu đã thay; lịch sử có căn cứ kiểm chứng. Chốt yêu cầu snapshot nếu implementation chưa hỗ trợ |

### 4.7. RAG, sinh đề và sinh flashcard AI

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-I01 | H/P1 | Upload nguồn, chờ ingest, hỏi câu có đáp án chuẩn trong khóa | Nội dung trả lời đối chiếu được với nguồn; citation đúng tài liệu/mục, không chỉ link có tồn tại |
| F-I02 | N/P0 | Hỏi ngoài nguồn, nguồn rỗng/chưa ingest; câu cố tình yêu cầu bịa | Nêu thiếu căn cứ **[ĐX]**; không bịa nguồn hoặc tự khẳng định dữ liệu không có |
| F-I03 | H/N/P0 | Chat cross-course có hai khóa được phép; trộn ID C4 chưa được phép | Chỉ truy xuất nguồn trong tập khóa hợp lệ; không lộ khóa ngoài quyền |
| F-I04 | H/N/P0 | Chat nhiều lượt; đổi conversation; phân trang lịch sử; sửa conversationId của người khác | Ngữ cảnh/lịch sử đúng chủ sở hữu và scope; không lẫn hội thoại giữa người dùng |
| F-I05 | H/P1 | Sinh đề theo khóa/bài và nguồn đã chọn; theo dõi job; kiểm nội dung trước lưu | Đúng scope, số lượng/loại câu; nguồn kiểm chứng được; không tự phát hành nội dung chưa duyệt |
| F-I06 | B/N/P1 | Sinh 0/số âm/quá trần câu; lesson không thuộc course; nguồn không hợp lệ | Chặn sớm, không tốn tài nguyên AI cho yêu cầu sai; không tạo quiz nửa chừng |
| F-I07 | W/P1 | Job chỉ sinh được một phần, toàn bộ bị kiểm định loại, hết budget | Trạng thái và số câu đạt thực tế đúng; cảnh báo thiếu; không báo đủ câu khi thiếu |
| F-I08 | W/P0 | Quá trần active job, RAG tắt, model timeout, job ID hết hạn/khác quyền | Phân biệt bận/timeout/không tồn tại; UI thoát trạng thái loading; không truy cập job ngoài scope |
| F-I09 | H/N/P1 | Sinh thẻ thuật ngữ/điền khuyết; thiếu blank, đáp án lộ mặt trước, đáp án không có trong nguồn | Kiểm định loại thẻ sai; giảng viên xem/sửa/duyệt trước publish |
| F-I10 | W/P1 | Rời trang rồi mở lại khi polling; mất mạng; bấm tạo/lưu hai lần | Trạng thái job có thể tra lại khi còn retention; không lưu/publish trùng do retry **[ĐX]** |

### 4.8. Flashcard và lịch học

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-F01 | H/N/P1 | Admin tạo/sửa/xóa thẻ chung; batch publish/draft; học viên mở bài | Chỉ thẻ publish ở bài/chương/khóa đủ điều kiện hiện; draft không lộ |
| F-F02 | H/N/P0 | Người học tạo/sửa/xóa thẻ riêng; người khác/admin qua API thẻ chung thử sửa | Chỉ chủ sở hữu sửa; người khác sửa/xóa nhận 404; công cụ admin không lộ thẻ cá nhân |
| F-F03 | B/N/P1 | Tạo thẻ cá nhân thứ 199/200/201 trên một bài; thử PERSONAL qua admin | Tối đa 200; không giả mạo origin cá nhân qua admin |
| F-F04 | W/P1 | Hai tab cùng sửa một thẻ bằng version cũ | Phát hiện xung đột khóa lạc quan; không ghi đè im lặng |
| F-F05 | H/B/P2 | Lật, trước/sau, Space/←/→, shuffle khi ở giữa; đóng mở; bộ 0/1/n thẻ | Không vượt chỉ số; đếm thẻ đã lướt đúng; shuffle không làm mất thẻ; phiên mới không giả giữ kết quả nhớ |
| F-F06 | H/N/P1 | Thư viện chọn nhiều khóa/bài/thẻ; phân trang hơn 50 khóa; bài đang khóa | Chỉ tài nguyên được học; phân trang không trùng/mất; chọn thẻ không làm tăng completion |
| F-F07 | H/B/P1 | Tạo lịch 1 ngày/khoảng ngày, chọn thứ, giờ nhắc/không nhắc; sửa/pause/resume/xóa | Ngày chạy và active/paused đúng; không nhắc ở lịch tắt nhắc/paused/đã xóa |
| F-F08 | B/N/P1 | Lịch thứ 20/21; khoảng 366/367 ngày; cuối trước đầu; không có ngày phù hợp | Chấp nhận trong giới hạn; từ chối vượt biên hoặc lịch không hợp lệ, không 500 |
| F-F09 | H/W/P1 | Lịch chọn cả bài và lịch chọn từng thẻ; thêm/xóa thẻ sau khi lưu lịch | Cả bài bao gồm thẻ mới; từng thẻ giữ tập ID; thẻ xóa bỏ qua an toàn |
| F-F10 | W/P0 | Mất quyền bài sau khi tạo lịch; truy cập plan người khác; tự xóa thẻ của mình sau mất quyền bài | Không lộ plan/thẻ ngoài quyền; lịch bỏ bài không còn quyền; vẫn được xóa thẻ cá nhân của mình |
| F-F11 | B/W/P1 | Nhắc đúng giờ UTC+7, sát nửa đêm, lặp nhiều thứ; scheduler trễ <2h và >2h | Thông báo đúng lịch; trễ >2h bỏ lần cũ; không dồn nhắc bị lỡ |
| F-F12 | W/P1 | Hai scheduler chạy đồng thời, >200 lịch đến hạn; restart giữa batch | Không gửi trùng; lịch tiếp theo được cập nhật đúng; backlog được xử lý qua các batch |

### 4.9. Tương tác, thông báo, đánh giá

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-N01 | H/N/P1 | Bình luận, trả lời; phân trang nhiều reply; xóa comment của mình/người khác bằng các role | Đúng thread/bài; quyền xóa đúng; không thiếu/trùng reply khi tải tiếp |
| F-N02 | H/N/P1 | Tạo/sửa/xóa ghi chú bài và ghi chú video tại mốc; đổi ID ghi chú người khác | Ghi chú đúng bài/mốc/chủ; không truy cập/sửa dữ liệu riêng của người khác |
| F-N03 | H/B/N/P1 | Đánh giá khóa 1–5 sao, sửa đánh giá; 0/6 sao; người chưa đủ điều kiện | Đúng điều kiện đánh giá hiện hành; trung bình/số lượt cập nhật không nhân đôi theo số lần sửa |
| F-N04 | H/W/P1 | Nhận thông báo duyệt, changelog, nhắc thẻ; mark read/read-all; cursor khi có thông báo mới | Số chưa đọc đúng; link đúng đối tượng; không trùng/mất trang; không đọc hộ notification người khác |
| F-N05 | W/P1 | Socket ngắt/reconnect, đăng xuất/đổi user, người dùng không online lúc sự kiện | Không lẫn notification; dữ liệu persisted có thể đọc lại; reconnect không nhân đôi nội dung |
| F-N06 | H/N/P1 | Gửi báo lỗi nội dung kèm ảnh; quản trị nhận, cập nhật xử lý; mở trang chi tiết và ảnh | Đúng người/bài/trạng thái; ảnh được bảo vệ; file sai/quá giới hạn bị chặn |

### 4.10. Dashboard, hồ sơ, xếp hạng, báo cáo

| ID | Loại/Ưu tiên | Điều kiện và bước thực hiện | Kết quả mong đợi |
|---|---|---|---|
| F-R01 | H/B/P1 | Dashboard khi chưa học/đang học/hoàn thành; tìm/lọc khóa; resume | Summary khớp danh sách; resume đúng vị trí; empty state rõ, không NaN |
| F-R02 | H/P1 | Hồ sơ, radar, huy hiệu sau trượt/đạt/thi lại; cập nhật thông tin cho phép | Dựa dữ liệu thật; không lấy điểm tạm phía client hoặc dữ liệu mock |
| F-R03 | H/B/P1 | Leaderboard có điểm bằng nhau, đổi bộ lọc/đơn vị, phân trang | Thứ tự ổn định theo tie-break hiện hành; điểm khớp lịch sử; không lộ phạm vi ngoài quyền |
| F-R04 | H/N/P0 | U3 xem báo cáo A, lọc khóa/đơn vị/học viên/trạng thái/ngày; thử B bằng params | Tổng/chi tiết cùng scope; thay filter không mở rộng quyền |
| F-R05 | H/B/P0 | Lịch sử nhiều lượt thi, phân trang attempts, analysis từng lượt; câu hỏi đã sửa | Không thiếu/trùng; điểm đúng lượt; giới hạn lịch sử khi sửa câu được ghi nhận rõ theo F-Q15 |
| F-R06 | H/B/N/P0 | Export CSV với bộ lọc; số dòng 0/1/lớn; tên tiếng Việt, dấu phẩy/xuống dòng | CSV và màn hình cùng dữ liệu/phạm vi; Unicode và escape đúng; không cắt mất dữ liệu âm thầm |
| F-R07 | B/W/P1 | Dữ liệu 23:59/00:00 UTC+7, học viên đổi đơn vị/khóa archive, khoảng ngày rỗng | Ngày và mẫu số nhất quán; lịch sử được giải thích theo policy; không lệch ngày do UTC |

## 5. Non-functional test cases

Các phép đo tải phải ghi cấu hình máy, kích thước DB, model, cold/warm state, số user, mix request và thời gian duy trì. Dùng tài khoản riêng khi thao tác đồng thời; dùng cùng tài khoản chỉ cho ca kiểm tra tranh chấp. Không dùng một ngưỡng chung cho CRUD, tải file, stream video và AI.

### 5.1. Hiệu năng, dung lượng, khả năng mở rộng

| ID | Loại/Ưu tiên | Tiền điều kiện và cách thực hiện | Tiêu chí nghiệm thu / số đo |
|---|---|---|---|
| NF-P01 | H/P1 | Warm-up 5 phút; chạy 50/100/200 user đồng thời, mỗi mức 15 phút, think time 3–10s; mix 30% dashboard/danh mục, 30% bài, 20% progress, 10% quiz, 10% thông báo | **[ĐX]** Ở tải mục tiêu đã chốt: p95 API đọc/ghi thường ≤2s, p99 ≤5s, lỗi ngoài lỗi nghiệp vụ chủ động <1%; không tính AI/file vào ngưỡng này |
| NF-P02 | B/P1 | DB thử 1k/10k/100k user, 100/1k khóa, nhiều progress/attempt/comment; tìm, filter, phân trang | **[ĐX]** Trang đầu ≤3s; payload có giới hạn; ghi query count và EXPLAIN; không tải toàn bộ danh sách để lọc client |
| NF-P03 | W/P1 | Spike 5 lần tải mục tiêu trong 2 phút, rồi về tải thường 10 phút | **[ĐX]** Không mất dữ liệu; không OOM; trở lại SLO trong 5 phút; overload trả lỗi có kiểm soát |
| NF-P04 | B/W/P1 | Chat 1→2→4→8→12→16 request đồng thời; ghi cold/warm, giữ mỗi mức ≥2 phút; đo TTFT nếu có streaming và tổng latency | **[ĐX]** Bậc đủ điều kiện: success >95%, p95 tổng <50s theo benchmark loadtest hiện có; xác nhận lại với cấu hình timeout/model triển khai, không coi 50s là hard timeout hiện hành |
| NF-P05 | W/P0 | Sinh đề 1→2→3→5 job; thêm job thẻ và chat; chạy traffic học/thi nền | **[ĐX]** Job hoàn thành >90% tại bậc công bố; lưu tỷ lệ câu/thẻ đạt thực tế; vượt trần nhận 429 có kiểm soát; API nền vẫn đạt SLO; DB pool không cạn |
| NF-P06 | W/P1 | Soak 8 giờ với tải mục tiêu, chat nhiều lượt, polling job, socket reconnect, upload định kỳ | **[ĐX]** Không tăng RAM/thread/connection không giới hạn; lỗi <1% API thường; không giảm throughput kéo dài; kiểm DB growth và log rotation |
| NF-P07 | B/P1 | Video/file nhiều kích thước; mạng 10Mbps/1Mbps, RTT 100/300ms; seek và Range qua reverse proxy thật | **[ĐX]** Video bắt đầu ≤3s ở 10Mbps; Range đúng byte/206 hoặc lỗi Range đúng contract; không đọc toàn file vào RAM; báo buffering rõ |
| NF-P08 | B/W/P1 | Export tập lớn theo dung lượng production dự kiến; đồng thời phân trang báo cáo và nộp bài | **[ĐX]** CSV đủ dòng, stream không OOM; đặt ngân sách export riêng trước chạy; API nộp bài vẫn đạt SLO |

### 5.2. Bảo mật và riêng tư

| ID | Loại/Ưu tiên | Tiền điều kiện và cách thực hiện | Tiêu chí nghiệm thu / số đo |
|---|---|---|---|
| NF-S01 | N/P0 | Không cookie/token, token hết hạn/sai chữ ký, sửa role/tokenVersion; mở URL/API bảo vệ | Không truy cập dữ liệu/hành động bảo vệ; phản hồi 401/403 theo contract; không tin localStorage |
| NF-S02 | N/P0 | Có cookie auth; gửi request ghi thiếu/sai CSRF; thử origin không được phép; kiểm token rotate login/logout | Request không hợp lệ bị chặn; CORS không cấp credentials cho origin lạ; CSRF trước login/logout không tiếp tục hợp lệ sau rotate |
| NF-S03 | W/P0 | Lập ma trận role × action × scope cho user/course/lesson/document/quiz/report/flashcard/chat/job/notification; đổi từng ID | Không IDOR, không nâng quyền, không rò tổng số/metadata ngoài scope; kiểm cả read và write |
| NF-S04 | N/W/P0 | Truy cập /uploads/videos trực tiếp; token stream sửa/hết hạn; document path traversal/URL encode; asset sau thu hồi quyền | Upload video raw bị chặn; token/file chỉ mở đúng tài nguyên hợp lệ; không đọc file hệ thống. SLA thu hồi token đã cấp cần chốt **[ĐX]** |
| NF-S05 | N/P0 | Chèn HTML/script vào comment, tên, text, ghi chú, tên file và AI output; SQL payload vào search/filter | Không thực thi script, không SQL injection; HTML cần cho bài giảng được sanitize; nội dung dữ liệu không thành lệnh |
| NF-S06 | W/P0 | Tài liệu có prompt injection yêu cầu bỏ luật, lấy khóa B/token; câu hỏi tương tự từ chat | AI không mở rộng scope, không lộ bí mật/nguồn ngoài quyền; nguồn truy xuất được kiểm quyền độc lập với lời model |
| NF-S07 | N/W/P1 | Flood login/OTP và gửi lại mã; upload file giả extension/MIME, kích thước lớn/tên traversal | **[ĐX]** Có hạn chế tài nguyên/tần suất phù hợp; giới hạn OTP được cưỡng chế; không ghi đè/thoát thư mục; thiếu rate limit ghi nhận là gap |
| NF-S08 | H/N/P0 | Kiểm cookie/header ở HTTPS production-like, request/response và log lỗi | Auth cookie HttpOnly/Secure/SameSite theo triển khai; không trả JWT trong login body; không log mật khẩu/OTP/token/chat nhạy cảm không kiểm soát |
| NF-S09 | N/P1 | Tên/nội dung bắt đầu =,+,−,@ trong CSV; mở bằng công cụ bảng tính sandbox | **[ĐX]** Không thực thi CSV formula; escape dấu phẩy/xuống dòng không đủ để chống formula injection |

### 5.3. Tin cậy, chịu lỗi và phục hồi

| ID | Loại/Ưu tiên | Tiền điều kiện và cách thực hiện | Tiêu chí nghiệm thu / số đo |
|---|---|---|---|
| NF-R01 | W/P0 | Ngắt mạng trước gửi/sau server commit/trước response cho progress, enroll, submit, save AI | Không báo thành công sai; dữ liệu commit không mất; retry không thưởng/tạo trùng; trạng thái có thể đối soát |
| NF-R02 | W/P0 | Restart backend khi có request ghi; DB mất kết nối 30s rồi phục hồi | Transaction atomic, không nửa kết quả; lỗi rõ; kết nối phục hồi; kiểm session và job đang chạy theo contract |
| NF-R03 | W/P0 | RAG/model/email/storage từng dịch vụ unavailable; thêm timeout/hết dung lượng volume | Luồng phụ thuộc thất bại rõ; không làm sập API không liên quan; không lưu URL file không tồn tại; AI không giữ connection DB suốt thời gian chờ |
| NF-R04 | W/P0 | Backup/restore DB + file + cấu hình cần thiết vào môi trường riêng; chạy E2E-09 | **[ĐX]** RPO ≤24h, RTO ≤4h cho mức khởi đầu; phải được chủ hệ thống chốt. Chứng minh bằng thời gian và đối soát, không chỉ “backup job thành công” |
| NF-R05 | W/P1 | Rolling restart nhiều backend, reload frontend sau deploy, job AI còn chạy | **[ĐX]** Không mất request đã commit; không trắng trang do asset/cache; job mất sau restart phải có trạng thái/đường xử lý rõ, không spinner vô hạn |

### 5.4. Đồng thời và toàn vẹn dữ liệu

| ID | Loại/Ưu tiên | Tiền điều kiện và cách thực hiện | Tiêu chí nghiệm thu / số đo |
|---|---|---|---|
| NF-C01 | W/P0 | Dùng barrier đồng bộ 10 request cùng hoàn thành bài/nộp cùng quiz session; lặp với user khác nhau | Một completion/tiêu thụ phiên; điểm/huy hiệu không trùng; không deadlock kéo dài; lỗi xung đột rõ |
| NF-C02 | W/P0 | Hai admin duyệt/từ chối enrollment hoặc sửa cùng khóa/role; một bên gửi version cũ | Trạng thái cuối hợp lệ; không mất cập nhật trái policy; **[ĐX]** báo xung đột thay vì ghi đè âm thầm ở dữ liệu cần version |
| NF-C03 | W/P1 | Hai backend cùng chạy scheduler trên PostgreSQL, >200 và >2000 lịch đến hạn; kill một instance trước/sau commit | Không nhắc trùng; instance còn lại xử lý được; đo backlog/lag; không kẹt khóa sau restart |
| NF-D01 | H/W/P0 | Snapshot dữ liệu rồi chạy thi/học/reset/retry; đối soát user points, completion, badges, attempt, dashboard, CSV | Mọi sai lệch giải thích được bằng quy tắc; không thưởng lặp, mẫu số sai hoặc lượt thi bị mất |
| NF-D02 | B/W/P0 | Cùng dữ liệu ngày giờ, chạy server timezone UTC và UTC+7; client ở timezone khác; kiểm migration timestamp | Giá trị nghiệp vụ UTC+7 nhất quán; không dịch giờ hai lần; lịch nhắc/hạn học/báo cáo khớp |
| NF-D03 | W/P0 | Nâng cấp DB có dữ liệu cũ bằng Flyway; restart lại; kiểm constraints flashcard/notification/content và xóa thẻ từng được ôn | Migration chạy đúng một lần; không mất dữ liệu ngoài thay đổi đã công bố; không FK bảng ôn cũ cản xóa; có kế hoạch phục hồi khi migration thất bại |

### 5.5. Tương thích, khả dụng, khả năng vận hành

| ID | Loại/Ưu tiên | Tiền điều kiện và cách thực hiện | Tiêu chí nghiệm thu / số đo |
|---|---|---|---|
| NF-U01 | H/B/P1 | Chạy E2E-01/04/07 trên Chrome/Edge/Firefox/Safari theo ma trận hỗ trợ; desktop 1366×768, mobile 390×844, tablet | **[ĐX]** Không mất thao tác/nút, không tràn quiz/modal; tài liệu/video/phím tắt hoạt động trong trình duyệt được công bố |
| NF-U02 | H/N/P1 | Chỉ dùng bàn phím và screen reader qua login, modal, quiz, flashcard; zoom 200%, light/dark | **[ĐX]** Đạt WCAG 2.2 AA cho luồng chính: focus rõ, trap modal đúng, label/contrast/lỗi đọc được; không dùng màu làm tín hiệu duy nhất |
| NF-U03 | W/P1 | Double click, back/forward/reload, API chậm/hết hạn session trong modal hoặc quiz | Loading/error/empty rõ, không khóa UI vô hạn; không nộp hoặc tạo trùng; thông báo rời trang phù hợp |
| NF-O01 | W/P1 | Tạo lỗi có kiểm soát 401/403/429/5xx/AI timeout; tra UI → X-Correlation-Id → backend/RAG logs/metrics | **[ĐX]** Truy vết được lỗi và dependency; có cảnh báo error/latency/DB pool/storage/scheduler lag; không lộ bí mật trong log |
| NF-O02 | H/B/P1 | 30 câu hỏi AI có nguồn chuẩn, ≥20 câu ngoài nguồn; reviewer chấm chính xác/citation/từ chối; lặp khi đổi model | **[ĐX]** ≥90% câu trong nguồn đúng và có dẫn chứng đúng; 0 rò rỉ ngoài quyền; ≥95% câu ngoài nguồn không bịa. Ghi mẫu, rubric và kết quả từng câu, không chỉ success HTTP |

## 6. Ma trận bao phủ luồng và rủi ro

| Chức năng/rủi ro | Functional | Non-functional | Luồng xuyên hệ thống |
|---|---|---|---|
| Xác thực/OTP/khóa account | F-A01–A09 | NF-S01/S02/S07/S08, R03 | E2E-01, 06 |
| RBAC/phạm vi/đơn vị/user/import | F-P01–P09 | NF-S03, C02, P02 | E2E-01, 06 |
| Cấu trúc/nội dung/upload/version | F-C01–C10 | NF-S04/S05, R03, D02/D03 | E2E-01, 05 |
| Phân bổ/đăng ký/deadline | F-E01–E07 | NF-C02, D01/D02 | E2E-01, 02, 06 |
| Học/bookmark/completion | F-L01–L12 | NF-P01/P07, C01, R01, D01 | E2E-01, 03, 05 |
| Quiz/bank/huy hiệu | F-Q01–Q15 | NF-C01, S03, D01 | E2E-01, 04 |
| Chat/AI/nguồn/job | F-I01–I10 | NF-P04/P05, S06, R03, O02 | E2E-01, 08 |
| Thẻ/lịch/nhắc | F-F01–F12 | NF-C03, D02/D03, S03 | E2E-07 |
| Thảo luận/ghi chú/rating/report lỗi | F-N01–N06 | NF-S03/S05, U02 | E2E-01, 05 |
| Thông báo/WebSocket | F-N04/N05, F-F11/F12 | NF-C03, R05, O01 | E2E-02, 06, 07 |
| Dashboard/hồ sơ/BXH/báo cáo/CSV | F-R01–R07 | NF-P02/P08, S09, D01/D02 | E2E-01, 09 |
| Sự cố/khôi phục/triển khai | F-L10/Q11/I08/N05 | NF-R01–R05, D03, O01 | E2E-03, 04, 08, 09 |

## 7. Thứ tự thực thi và điều kiện kết thúc

1. **Smoke:** đăng nhập → mở khóa đã ghi danh → mở bài → lưu tiến độ → làm/nộp quiz → mở báo cáo; kiểm đủ các dịch vụ và file nguồn.
2. **P0 functional/security:** tài khoản, scope, điều kiện học, quiz/time/grading/idempotency, tính điểm, dữ liệu báo cáo.
3. **E2E-01/02/07:** luồng nghiệp vụ với dữ liệu mới, không bỏ qua bằng can thiệp DB.
4. **Boundary/negative:** chạy mọi biến thể trong bảng; không chỉ một giá trị đại diện.
5. **E2E-03…06/08:** đồng thời, thu hồi quyền, version, network/dependency faults.
6. **Non-functional:** load/spike/soak, AI quality, accessibility, compatibility; chạy restore E2E-09 ở môi trường riêng.
7. **Regression:** sau sửa lỗi, chạy lại ca lỗi, ca liền kề và E2E bị ảnh hưởng.

**Exit criteria [ĐX]:** tất cả ca P0 Pass; không còn lỗi critical/high chưa xử lý; P1 Pass hoặc có chấp nhận rủi ro cụ thể; 9 luồng E2E có bằng chứng; các SLO đã thống nhất đạt tại tải mục tiêu; backup/restore chứng minh được; mọi tiêu chí chưa thống nhất được ghi rõ owner và quyết định. Không kết luận “full coverage” chỉ vì danh sách dài: phải đối chiếu chức năng/cấu hình bật trong bản release với ma trận mục 6.

## 8. Mẫu ghi nhận thực thi

Mỗi ca/biến thể cần một dòng kết quả:

| Run ID | Case ID/biến thể | Build/môi trường | Người chạy/thời gian | Tài khoản/dữ liệu | Actual result | Status | Bằng chứng | Defect ID |
|---|---|---|---|---|---|---|---|---|
| RUN-001 | F-Q07/+31s | commit… / staging | … | U4/Q1/session… | … | Not Run / Pass / Fail / Blocked / N/A | log/ảnh/request… | BUG-… |

- N/A phải có lý do chức năng không bật/không thuộc release; Blocked phải nêu phụ thuộc. Không đổi hai trạng thái này thành Pass.
- Với tải: đính kèm script/version, traffic mix, VU, RPS, p50/p95/p99, error rate, CPU/RAM/DB pool và thời gian phục hồi.
- Với AI: lưu question, expected facts, source, actual answer, citation và đánh giá người duyệt; che dữ liệu nhạy cảm.
- Cleanup bằng tài khoản/bộ dữ liệu test, không xóa lịch sử thật; reset dữ liệu giữa các ca có ảnh hưởng điểm/thưởng/thời gian.

## 9. Nguồn đối chiếu trong repository

- Routes và API frontend: `CoreTraining/src/router/index.ts`, `CoreTraining/src/api/`, các màn hình `CoreTraining/src/views/`.
- Xác thực/quyền: `CoreTrainingAPI/src/main/java/com/bidv/coretraining/security/SecurityConfig.java`, `AccessPolicy.java`, `RolePolicy.java`, `auth/service/impl/AuthServiceImpl.java`.
- Học tập: `policy/LessonContentPolicy.java`, `policy/LearningPolicy.java`, `lesson/service/impl/LessonProgressServiceImpl.java`, `learner/`, `course/`, `training/` dưới package Java chính.
- Thi/AI: `quiz/controller/`, `quiz/service/impl/QuizServiceImpl.java`, `QuizSessionServiceImpl.java`, `QuizAdminServiceImpl.java`, `rag/`, `rag-service/app/`.
- Thẻ/lịch: `flashcard/`, `docs/flashcards-implementation.md`, migrations `V20261104_1`, `V20261104_2`, `V20261104_3`.
- Báo cáo/thông báo/file: `report/controller/`, `notification/`, `document/`, `video/`.
- Test có sẵn để hỗ trợ tự động hóa: `CoreTrainingAPI/src/test/`, `CoreTraining/tests/`, `rag-service/tests/`. Việc có test file không chứng minh bản triển khai đã Pass.
- Điểm bắt đầu kiểm thử tải AI: `scripts/loadtest/ai-concurrency.js`, `scripts/loadtest/README.md`; phải kiểm lại các timeout/trần job của môi trường hiện hành trước chạy.

Các đường dẫn Java rút gọn ở trên tương đối với `CoreTrainingAPI/src/main/java/com/bidv/coretraining/`. Tài liệu thiết kế/README có thể trễ hơn code; các kỳ vọng đánh dấu [ĐX] và chính sách chưa chốt phải được xác nhận trước khi dùng làm tiêu chí release.
