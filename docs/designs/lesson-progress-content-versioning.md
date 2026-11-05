# Phiên bản nội dung và tiến độ bài học

- Ngày: 2026-10-26, Asia/Saigon.
- Trạng thái: Đề xuất triển khai đã rà soát độc lập; chờ duyệt thiết kế tổng thể.
- Phạm vi: Backend, PostgreSQL, frontend, chuyển đổi dữ liệu và kiểm thử.
- Quy tắc đã thống nhất: người đang học dở học lại khi nội dung thay đổi; người đã hoàn thành được bảo lưu thành tích; giữ tiến độ cũ làm lịch sử.

## 1. Vấn đề cần sửa

Hiện một bản ghi `lesson_progress` đại diện cho mọi lần thay đổi của cùng bài học. Không có thông tin đủ tin cậy để biết số giây đã xem thuộc video nào. Thời gian đã xem và thời lượng được gộp bằng `Math.max`, nên thời lượng video cũ dài hơn có thể tiếp tục làm mẫu số cho video mới.

Các nguyên nhân đã đọc trong mã:

1. `LessonProgressService.saveProgress` tìm theo người học và bài học, không tìm theo phiên bản nội dung.
2. `completionVersion` so với `lessons.version` nhưng chỉ kích hoạt tính lại; không tách hoặc khởi tạo lại tiến độ khi nội dung đổi.
3. `AdminServiceImpl.updateLesson` xóa và tạo lại danh sách tài liệu. `buildDocuments` không giữ ID, kể cả tài liệu không đổi.
4. Request tiến độ không mang phiên bản. Tab cũ và hàng đợi trong trình duyệt có thể ghi số liệu cũ vào nội dung mới.
5. `getAllProgress`, workspace và outline gom tiến độ theo `lessonId`. Sau khi cho phép nhiều phiên bản, các chỗ này có thể lấy nhầm hoặc lỗi khóa trùng nếu không sửa cùng lúc.
6. `QuizServiceImpl` có thể ghi hoàn thành ngay khi đạt bài kiểm tra; `completeContent` có nhánh bỏ kiểm tra nội dung khi quiz bắt buộc. Cần đưa các đường ghi hoàn thành về cùng điều kiện.
7. `LearningPolicy.courseCompleted` kiểm tra lại điểm quiz với điều kiện hiện tại. Điều này có thể mâu thuẫn với việc bảo lưu thành tích đã đạt.

Ví dụ đã quan sát: bài `SAMPLE-TD-DN-02-CH2-L1`, người học 3, từng có thời lượng lưu là 3483 giây; URL video đã thay đổi; tài liệu hiện tại là ID 10, trong JSON còn mốc ID 8 và 9. Những mốc cũ không chứng minh đã học nội dung hiện tại.

## 2. Mục tiêu và giới hạn

- Không trộn dữ liệu giữa hai phiên bản bài học.
- Không tự công nhận nội dung mới từ số liệu của nội dung cũ.
- Không hạ kết quả hoàn thành đã đạt, không thu hồi hoặc cộng lại điểm vì cập nhật nội dung.
- Không mất lịch sử tiến độ; không xóa hàng loạt tiến độ mỗi lần quản trị viên lưu bài.
- Request cũ được phát hiện ở backend, kể cả khi frontend chưa tải lại.
- Danh sách khóa học, sidebar, học tiếp và báo cáo cho cùng một kết quả.
- Thay đổi nội dung làm học lại TOÀN BỘ bài đối với người chưa hoàn thành, không chỉ phần vừa thay.

Không xây hệ thống phục hồi toàn bộ nội dung cũ, phiên bản riêng cho từng đoạn văn, nền tảng sự kiện hay bộ phân tích chống gian lận xem video. Tiến độ lịch sử lưu sự kiện và mô tả nội dung đã tham chiếu; không mặc nhiên cam kết còn phát lại được URL bên thứ ba hoặc file đã bị gỡ.

## 3. Quy tắc trạng thái

| Trước khi nội dung thay đổi | Khi truy cập phiên bản mới |
|---|---|
| Chưa học | Bắt đầu từ 0 ở phiên bản hiện tại |
| Đang học dở | Bản ghi cũ được giữ nguyên; phiên bản mới bắt đầu từ 0 |
| Đủ nội dung nhưng chưa đạt quiz bắt buộc | Vẫn là chưa hoàn thành; học lại toàn bộ phiên bản mới |
| Đã được backend công nhận hoàn thành | Giữ hoàn thành và thành tích khóa học; có thể xem thêm nội dung mới |

Ưu tiên hiển thị: đã hoàn thành hợp lệ ở bất kỳ phiên bản nào → `COMPLETED`; nếu chưa hoàn thành → đánh giá tiến độ của phiên bản hiện tại. Không dùng `contentCompleted` của bản cũ để công nhận lần đầu cho phiên bản mới.

Kết quả đã đạt vẫn giữ khi sửa quiz, tăng điểm đạt hoặc bổ sung tài liệu vào CÙNG bài học. Thêm hoặc xóa hẳn bài học, đổi cấu trúc khóa học, thu hồi thành tích bằng thao tác quản trị riêng là nghiệp vụ khác; không âm thầm xử lý qua thay đổi phiên bản nội dung.

## 4. Mô hình dữ liệu

### 4.1. `lessons`

Thêm `content_version BIGINT NOT NULL DEFAULT 1`, dương, do backend quản lý. Mỗi lần lưu có thay đổi nội dung thực sự tăng đúng 1 trong transaction.

Giữ `version` hiện có và `@Version` để kiểm soát hai quản trị viên sửa đồng thời. `version` kỹ thuật và `content_version` có mục đích khác nhau, không sao chép qua lại.

### 4.2. `lesson_progress`

- Thêm `content_version BIGINT NOT NULL`. Giá trị 0 dành riêng cho dữ liệu cũ không xác định được phiên bản; phiên bản đang học luôn >= 1.
- Đổi unique từ `(user_id, lesson_id)` thành `(user_id, lesson_id, content_version)`.
- Thêm index `(user_id, lesson_id, content_version)` qua unique; thêm index phù hợp truy vấn danh sách hiện tại sau khi xem query plan trên dữ liệu thực. Không thêm index trùng unique.
- Giữ các dữ liệu tiến độ: vị trí video, giây đã xem, thời lượng quan sát, vị trí tài liệu, văn bản hoàn thành, nội dung hoàn thành và thời điểm cập nhật.
- Thêm `content_snapshot` JSONB do SERVER tạo khi tạo bản ghi: định danh video chuẩn hóa, dấu vết văn bản, ID và định danh file tài liệu, quiz ID/revision/điểm đạt và quy tắc bắt buộc. Không nhận snapshot này từ client. Đây là mô tả phục vụ đối chiếu, không là bản sao đầy đủ tài nguyên.
- Thêm `completion_awarded BOOLEAN NOT NULL DEFAULT false`: bản ghi này là bằng chứng của lần được công nhận hoàn thành. Không đồng nhất với `content_completed`.
- Thêm `completion_awarded_at TIMESTAMP NULL`: thời điểm backend thực sự công nhận; dữ liệu chuyển đổi không biết thời điểm thì để NULL, không dựng ngày hoàn thành giả.

`completionVersion` cũ ngừng dùng để xác định phiên bản nội dung. Giữ cột trong đợt chuyển đổi/rollback, bỏ ở một đợt riêng sau khi không còn reader/writer cũ. Không dùng giá trị cũ để đoán nguồn video.

`content_snapshot` của hàng legacy có thể NULL vì không thể phục dựng nguồn nội dung quá khứ. Hàng mới có snapshot được backend tạo; frontend không dùng snapshot lịch sử để dựng nội dung hiện tại.

Bản ghi phiên bản cũ không nhận request ghi mới. Bản ghi hiện tại có thể tiếp tục cập nhật vị trí khi người đã hoàn thành xem lại, nhưng không được xóa `completion_awarded` hoặc sửa thời điểm đã cấp.

### 4.3. Thành tích bài và khóa học

Giữ `user_completed_lessons` làm nguồn xác định thành tích bảo lưu để tái sử dụng `User.completedLessons`, mở khóa bài, tính điểm và báo cáo. Không tạo thêm bảng thành tích cạnh tranh nguồn dữ liệu này.

`completion_awarded` và thời điểm trong progress là bằng chứng gắn với phiên bản của lần cấp; chúng không có quyền thu hồi thành tích. Một cặp user/bài chỉ được cấp thành tích và điểm lần đầu. Nếu thành tích cũ tồn tại mà không có tiến độ, migration tạo bằng chứng legacy ở phiên bản 0, không suy luận đã học phiên bản hiện tại.

Giữ `user_completed_courses` và ngày đã đạt. Việc chỉ sửa nội dung bài không được làm mất kết quả khóa học: khi tính theo cấu trúc khóa hiện tại, thành tích từng bài đã được bảo lưu không bị điều kiện quiz mới phủ định. Không xóa hoặc sửa ngày khóa học đã đạt.

### 4.4. Thời lượng video: cái gì được giữ và cái gì không được trộn

Trong đợt sửa này, `duration_seconds` vẫn là thời lượng do player quan sát, gắn với một bản tiến độ có phiên bản rõ ràng. Không coi nó là thông tin dùng chung cho mọi video của bài, cũng không dùng bản ghi lịch sử làm mẫu số hiện tại.

Lý do chưa chuyển toàn bộ thời lượng sang một cột chung trên bài: video URL bên thứ ba hiện lấy metadata lúc phát; hệ thống chưa có nguồn metadata máy chủ đã xác minh cho mọi nhà cung cấp. Tự ghi một thời lượng chung từ báo cáo đầu tiên của bất kỳ client nào sẽ tạo thêm nguồn dữ liệu không đáng tin.

Quy tắc xử lý: 0/NaN/vô cực không hợp lệ; thời lượng dương hợp lệ của cùng phiên bản có thể thay thế quan sát trước, không lấy cực đại qua các lần đổi nội dung. `watched_seconds` chỉ giữ cực đại trong CÙNG phiên bản theo cơ chế snapshot hiện tại. Không dùng vị trí tua làm số giây đã xem.

Chuyển thời lượng sang metadata tài nguyên được xác minh phía máy chủ là hướng riêng có thể bổ sung sau, không là điều kiện để sửa lỗi trộn phiên bản. Cơ chế này không nâng mức chống giả mạo thời gian xem so với hiện tại.

## 5. Khi nào tăng phiên bản

| Thay đổi | Tăng `content_version` |
|---|---|
| Thay video hoặc nguồn video thực sự | Có |
| Thêm/xóa video | Có |
| Thay nội dung văn bản, thêm/xóa văn bản | Có |
| Thêm/xóa/sửa hoặc đổi thứ tự các mục/bước quy trình người học nhìn thấy | Có |
| Thêm/xóa tài liệu hoặc thay file tài liệu | Có |
| Thay nội dung câu hỏi, đáp án, lựa chọn hoặc quy tắc/điểm đạt quiz | Có |
| Đổi tên bài, ghi chú quản trị, trạng thái hiển thị, thứ tự bài | Không |
| Đổi tên tài liệu, đổi thứ tự danh sách tài liệu, lưu lại dữ liệu y nguyên | Không |
| Chỉ đổi cách biểu diễn cùng một YouTube video, như watch URL sang youtu.be | Không |

So sánh biểu diễn chuẩn hóa trước/sau transaction: YouTube theo provider + video ID; upload theo asset path bất biến; tài liệu theo ID + file identity; văn bản theo chuẩn hóa xuống dòng, giữ nguyên nội dung hiển thị; quy trình theo thứ tự các tiêu đề mục và thứ tự/nội dung bước; quiz theo câu hỏi/đáp án/cấu hình. Snapshot ở progress cũng gồm dấu vết quy trình. Không strip HTML hoặc bỏ khoảng trắng đến mức che mất thay đổi có ý nghĩa.

File upload không được ghi đè byte mới vào cùng asset path mà bỏ qua lưu bài. Với URL bên thứ ba có thể thay nội dung mà không đổi URL, hệ thống không tự phát hiện chắc chắn: thêm thao tác quản trị `Đánh dấu nội dung đã thay đổi` để tăng phiên bản khi cần. Áp dụng quyền sửa bài và kiểm soát `version` kỹ thuật như lưu bài.

Contract thao tác này: `POST /api/admin/lessons/{lessonId}/content-version`, body `{version}` là `@Version` kỹ thuật người sửa đang giữ, response `LessonDto` có cả hai phiên bản. `version` bắt buộc; thiếu trả 400, không khớp trả 409 code `LESSON_EDIT_CONFLICT`. Kiểm tra quyền sửa khóa, khóa ghi lesson, tăng contentVersion một lần và cập nhật updatedAt trong cùng transaction. Request lặp với version kỹ thuật cũ không được tăng tiếp. Editor chỉ cho gọi khi form không có thay đổi chưa lưu; thành công thay DTO/cache bằng response, xung đột tải lại trước khi thử tiếp.

Một lần lưu bài tăng phiên bản một lần dù thay nhiều trường. Một API sửa quiz riêng là một transaction thay nội dung và tăng phiên bản một lần. Không tăng vì autosave không đổi hoặc chỉ vì JPA cập nhật timestamp.

Không bổ sung quy trình CMS nháp/xuất bản mới. Với bài đang công bố, lần lưu nội dung là thời điểm nội dung mới có hiệu lực; với bài nháp, người học chưa truy cập được. Ẩn rồi công bố lại mà không đổi nội dung không làm reset.

## 6. Giữ ID tài liệu

Sửa `updateLesson` thành đối chiếu theo ID thay vì clear/rebuild:

1. ID có sẵn phải thuộc đúng bài; cập nhật tên/thứ tự/file trên đối tượng đó.
2. Không có ID thì tạo tài liệu mới.
3. ID cũ không còn trong request thì xóa khỏi tập tài liệu hiện tại theo nghiệp vụ đang có.
4. Thay file cùng một ID vẫn tăng phiên bản nội dung; tiến độ hiện tại mới bắt đầu từ 0 theo chính sách học lại toàn bài.
5. So sánh tài liệu theo tập ID + file identity, không để đổi thứ tự danh sách gây tăng phiên bản.

JSON của tiến độ cũ có thể giữ ID tài liệu đã xóa. Không kiểm tra chúng để chặn hoàn thành hiện tại; chỉ kiểm tra các tài liệu hiện thuộc bài và bản tiến độ đúng phiên bản.

## 7. Luồng đọc và lưu tiến độ

### 7.1. Đọc

Lesson detail và outline trả `contentVersion`. API tiến độ hiện tại chỉ lấy bản ghi có `p.contentVersion = lesson.contentVersion`. Không có bản ghi thì trả dữ liệu tiến độ rỗng mang phiên bản hiện tại; không cần tạo hàng loạt row khi quản trị viên sửa bài.

Trả độc lập `lessonCompleted` từ thành tích bảo lưu, `contentCompleted` và `currentQuizPassed` từ phiên bản hiện tại, `completedContentVersion` nếu xác định được, và `needsRestart` khi có lịch sử cũ nhưng chưa hoàn thành và không có tiến độ hiện tại. Thành tích legacy có `completedContentVersion = null`. `currentQuizPassed` dựa trên attempts cùng phiên bản/revision, không lấy `UserQuizScore` xuyên phiên bản; truy vấn theo lô khi trả nhiều bài, không tạo N+1.

`getAllProgress` vẫn trả tối đa một entry mỗi lesson ID, nhưng đó là bản hiện tại. Lịch sử không trộn vào map này. Không thêm API lịch sử công khai trong đợt này; dữ liệu được lưu để có thể xây báo cáo có quyền truy cập sau.

Workspace, outline, học tiếp và báo cáo phải dùng query hiện tại tường minh. Không sửa bằng cách lấy bản ghi có timestamp mới nhất hoặc phiên bản lớn nhất của riêng người học: phải đối chiếu với phiên bản hiện tại của bài.

### 7.2. Ghi

Các request tiến độ mang `contentVersion` bắt buộc. Các bước trong cùng transaction:

1. Xác thực và kiểm tra quyền học như hiện tại.
2. Khóa user theo `lockForLearning`, sau đó khóa chia sẻ hàng lesson; đọc lại phiên bản/nội dung cần kiểm tra sau khóa.
3. Nếu request không có phiên bản → HTTP 428, code `CONTENT_VERSION_REQUIRED`. Nếu khác hiện tại → HTTP 409, code `LESSON_CONTENT_CHANGED`, kèm lesson ID và phiên bản hiện tại; không cập nhật dữ liệu hoặc điểm.
4. Lấy đúng `(user, lesson, contentVersion)`; chưa có thì tạo bản mới từ 0 và snapshot nội dung do backend sinh. Không sao chép mốc video/văn bản/tài liệu/quiz từ bản cũ.
5. Kiểm tra document ID thuộc bài hiện tại; cập nhật snapshot tiến độ trong bản đúng phiên bản.
6. Tính lại điều kiện nội dung theo phiên bản hiện tại sau mỗi cập nhật có ý nghĩa. Bỏ phụ thuộc vào `completionVersion == @Version` và điều kiện chỉ tính lại khi một cờ chuyển false→true.
7. Nếu đã có thành tích bảo lưu, giữ `lessonCompleted = true`; cập nhật việc xem lại nhưng không cấp lại điểm.
8. Nếu chưa có thành tích, gọi luồng xét hoàn thành thống nhất ở mục 8.
9. Trả DTO có phiên bản. Không gửi thành công giả cho request đã lỗi phiên bản.

Query hiện tại đã có unique mới và khóa user chống tạo hai bản ghi trong hai tab. Thử lại cùng snapshot không cấp điểm lại.

### 7.3. Cạnh tranh khi quản trị viên đổi nội dung

Mọi thao tác thay nội dung khóa ghi hàng lesson trước khi sửa. Request học khóa user → khóa đọc lesson. Thao tác quản trị không khóa các user, tránh vòng khóa ngược user/lesson.

Nếu request hoàn thành khóa lesson trước, nó được đánh giá theo phiên bản đang hiệu lực và có thể được công nhận; lần cập nhật sau đó bảo lưu thành tích này. Nếu cập nhật nội dung khóa trước, request cũ bị 409. Không có khoảng giữa kiểm tra phiên bản và commit mà nội dung được thay âm thầm.

Áp dụng thứ tự này cho cả quiz và API hoàn thành thủ công. Khi một transaction sửa nhiều bài, khóa lesson theo thứ tự ID. Trong giai đoạn triển khai phải kiểm tra toàn bộ writer thực sự tuân thủ, không chỉ `saveProgress`.

## 8. Một điều kiện hoàn thành cho tất cả các đường ghi

Tách logic xét hoàn thành dùng chung, không để từng controller quyết định khác nhau:

- Thành tích đã tồn tại → bảo lưu, không xét lại để thu hồi.
- Chưa có thành tích → cần tất cả nội dung được đánh dấu bắt buộc ở phiên bản hiện tại (xem `lesson-content-types.implementation.md`; đổi cờ bắt buộc không phải thay đổi nội dung nên không tăng `content_version`): văn bản nếu có; video nếu có đạt ít nhất 90% thời lượng hợp lệ; mọi tài liệu hiện tại có dấu hoàn thành trang cuối; quiz nếu thực sự có câu hỏi và bắt buộc thì có lần đạt thuộc phiên bản hiện tại.
- Bài không có bất kỳ nội dung/quiz hợp lệ không tự hoàn thành vì các phép kiểm tra rỗng đều true.
- Đạt quiz trước khi học đủ nội dung chỉ lưu lần thi; học nội dung sau đó đủ sẽ tự xét hoàn thành. Học đủ nội dung rồi đạt quiz cũng xét qua cùng hàm.
- `complete-lesson` và `content-complete` phải mang phiên bản, không được bypass kiểm tra nội dung khi có quiz. API hoàn thành đối với người đã hoàn thành vẫn không cấp điểm lần nữa.
- Cấp thành tích, đánh dấu `completion_awarded`, timestamp, cộng điểm đúng một lần và cập nhật thành tích khóa học cùng transaction khóa user.

Không dùng điểm tốt nhất trong `UserQuizScore` xuyên phiên bản làm điều kiện đạt lần đầu của phiên bản mới. Giữ bảng này cho tương thích báo cáo hiện có, không xóa điểm cũ.

## 9. Bài kiểm tra và thành tích khóa học

Thêm `content_version` vào `quiz_attempts`; bổ sung vào request nộp bài. API lấy session phải khóa chia sẻ hàng lesson TRƯỚC khi đọc contentVersion, quiz cấu hình/revision và câu hỏi, giữ khóa đến khi dựng xong DTO. Mọi writer quiz giữ khóa ghi lesson tương ứng. Chỉ đặt `@Transactional(readOnly=true)` ở mức READ COMMITTED là chưa đủ để đảm bảo nhiều query cùng snapshot.

Giữ endpoint lấy câu hỏi dạng danh sách hiện có để tránh phá consumer không cần ghi; thêm endpoint `GET /api/quizzes/lessons/{lessonId}/session` trả `{lessonId, contentVersion, quizRevision, passingScore, questions}` cho frontend nộp bài mới. Không trả đáp án đúng.

Request nộp bài có `contentVersion` và `quizRevision`. Sai phiên bản/revision thì từ chối trước chấm/lưu. Việc đổi câu hỏi phải đi qua cơ chế tăng phiên bản ở mục 5; không được chỉ sửa trực tiếp hàng questions.

`quizRevision` là `Quiz.revision` hiện có, không là `Quiz.version` của JPA. Quiz mới có revision 1; với một quiz đã tồn tại, mỗi transaction thay đổi câu hỏi, đáp án hoặc cấu hình có ý nghĩa tăng revision đúng một lần cùng contentVersion. Lưu không đổi không tăng cả hai. Sửa chỉ video/văn bản/tài liệu/quy trình tăng contentVersion nhưng không tăng quizRevision; các attempts cũ vẫn không áp cho người đang học dở vì khác contentVersion.

Xóa bài kiểm tra làm rỗng tập câu hỏi và bỏ yêu cầu quiz hiện tại, nhưng giữ đối tượng Quiz làm chỗ tham chiếu cho `quiz_attempts`; không `setQuiz(null)` gây orphan removal hoặc xóa lịch sử thi. Quiz rỗng không chặn hoàn thành theo chính sách hiện có. Thêm lại câu hỏi dùng cùng Quiz với revision mới. Việc xóa câu hỏi không được xóa các snapshots đã lưu của attempts.

Điều kiện đạt của phiên bản hiện tại lấy từ `quiz_attempts` đúng user/lesson/contentVersion, quiz revision và quy tắc đã lưu khi thi. Điểm cũ vẫn hiển thị lịch sử, không mở khóa nội dung mới cho người đang học dở.

Sửa cả đường xem lại: `GET /api/quizzes/lessons/{lessonId}/review` nhận `attemptId` tùy chọn. Có ID thì chỉ lấy attempt của đúng user và bài; không có ID thì chọn attempt có snapshot mới nhất của phiên bản hiện tại, nếu không có mới lấy attempt lịch sử mới nhất và đánh dấu `historical=true` (sắp theo takenAt rồi ID). Response dùng questionsJson, answersJson, score, total, passingScore và passed đã lưu, kèm attemptId/contentVersion/quizRevision. Không ghép điểm v1 với câu hỏi hoặc điểm đạt v2. Nếu chỉ còn aggregate legacy hoặc thiếu snapshot cần thiết, trả 422 code `QUIZ_REVIEW_UNAVAILABLE`, không dựng lại theo câu hỏi hiện tại. QuizResultDto trả attemptId để mở đúng lần vừa thi.

Frontend và sidebar dùng `currentQuizPassed` để điều hướng làm bài hiện tại; điểm lịch sử chỉ để hiển thị và xem lại với nhãn tương ứng. Người chưa hoàn thành không bị tự đưa vào màn review v1 thay vì làm đề v2. Người có thành tích bảo lưu vẫn có thể xem lịch sử hoặc chủ động làm lại, không nhận điểm thành tích lần hai.

Sửa `LearningPolicy.courseCompleted` và các nơi dùng nó: tính theo thành tích các bài trong cấu trúc hiện tại, bỏ bước kiểm lại quiz mới đối với bài đã được công nhận. Vì cập nhật nội dung không đổi danh sách bài và không mất thành tích bài, khóa đã hoàn thành vẫn hoàn thành. Không cộng điểm khóa học/badge lần nữa khi xem lại. Không chỉ dựa vào việc có timestamp trong `user_completed_courses` để bỏ qua mọi thay đổi cấu trúc về sau; thêm/xóa hẳn bài vẫn theo chính sách cấu trúc hiện có, ngoài quyết định versioning này.

## 10. Frontend, cache và request cũ

- Thêm `contentVersion` vào lesson/detail/outline, progress request/response và quiz session.
- Khóa cache và hàng đợi ghi theo user + lesson + contentVersion; tài liệu thêm document ID. Không merge completion boolean giữa hai phiên bản.
- Đổi namespace localStorage journal sang v2. Không tự nâng bản ghi v1 thiếu phiên bản thành phiên bản hiện tại. Loại chúng khỏi hàng đợi gửi; không xóa dữ liệu khác của user.
- `getLatestLessonProgress` chỉ gộp saved/pending/journal khi cùng phiên bản với lesson đang mở.
- Khi API báo 409, dừng player, hủy hàng đợi của bản cũ, loại snapshot cũ khỏi journal hoạt động; tải lại lesson, tiến độ và outline; hiển thị thông báo nội dung đã đổi. Không đổi nhãn request cũ sang phiên bản mới rồi gửi lại.
- Không tiếp tục retry 409/428 như lỗi mạng. Lỗi mạng thực sự vẫn giữ snapshot đúng phiên bản và thử lại theo cơ chế hiện có.
- Response đến muộn phải kiểm tra user, lesson, phiên bản và lần tải trước khi cập nhật store. Các tab khác được thông báo bằng sự kiện storage phiên bản; backend vẫn là hàng rào quyết định khi tab chưa nhận thông báo.
- Khi quay lại tab hoặc chuyển bài, revalidate lesson trước khi phát/hồi phục journal. TTL cache không phải bằng chứng phiên bản còn hiệu lực.
- Remount VideoPlayer, reader văn bản và tài liệu bằng key chứa lesson + contentVersion. Reset trạng thái text-reported, vị trí trang và bộ đếm xem theo phiên bản.
- Giữ bản sửa YouTube `cueVideoById`; không quay lại cặp `seekTo` rồi `pauseVideo` khi khởi tạo.
- Badge hoàn thành luôn ưu tiên `completedLessons`/`lessonCompleted` từ backend. Khi người đã hoàn thành xem bản mới, thông báo ngắn “Bạn đã hoàn thành bài này. Nội dung đã được cập nhật.”; không ép học lại hoặc đổi badge thành chưa học.
- Người chưa hoàn thành nhận thông báo “Nội dung bài học đã thay đổi. Tiến độ của phiên bản mới bắt đầu lại.” Không hiển thị chi tiết DB/version kỹ thuật không cần thiết trong luồng học.
- Trình soạn bài có cảnh báo khi thay nội dung sẽ làm người đang học dở bắt đầu lại; người đã hoàn thành được giữ kết quả. Sửa tên/thứ tự không hiện cảnh báo reset.

## 11. API và lỗi có cấu trúc

| API/DTO | Thay đổi |
|---|---|
| Lesson detail/outline | Thêm `contentVersion` |
| POST progress | `contentVersion` bắt buộc |
| Progress DTO | `contentVersion`, `lessonCompleted`, `contentCompleted`, `currentQuizPassed`, `completedContentVersion`, `needsRestart` |
| POST complete-lesson/content-complete | Body có `contentVersion`; dùng chung logic xét hoàn thành |
| GET quiz session mới | Snapshot câu hỏi và phiên bản nhất quán |
| GET quiz review | Attempt snapshot, ID tùy chọn, phiên bản/revision, cờ historical; 422 khi thiếu snapshot |
| POST quiz submit | Thêm `contentVersion`, `quizRevision` |
| Lỗi phiên bản | 409/428 + code + lessonId + currentContentVersion; frontend xử lý riêng |

Không tin client truyền user ID, snapshot nội dung, cờ “đã được cấp thành tích” hoặc thời điểm hoàn thành. Trường `contentVersion` là điều kiện kiểm tra, không là quyền sửa lịch sử.

## 12. Dữ liệu hiện tại và chuyển đổi

Không thể từ số giây hiện có suy ra chắc chắn đã xem video cũ hay mới. `completionVersion` bằng `lessons.version` cũng không đủ, vì mã cũ cập nhật nó mỗi lần ghi.

Chọn cách chuyển đổi bảo toàn bằng chứng và không tự công nhận sai:

1. Dừng writer trong một cửa sổ triển khai; backup các bảng lesson, nội dung, progress, quiz_attempts và các bảng thành tích/điểm liên quan; giữ số lượng và hash bản ghi để đối chiếu.
2. Tất cả bài hiện tại bắt đầu `content_version = 1`. Không suy diễn phiên bản quá khứ từ `@Version`.
3. Chuyển toàn bộ progress cũ sang `content_version = 0`, giữ nguyên số giây, JSON, cờ và thời điểm. Đây là dữ liệu legacy không đủ bằng chứng để gắn với nội dung hiện tại.
4. Người có `user_completed_lessons` giữ thành tích và điểm. Đánh dấu bằng chứng ở progress legacy; nếu thiếu row thì tạo row legacy có `completion_awarded=true`, timestamp NULL. Không khẳng định họ đã hoàn thành nội dung hiện tại.
5. `quiz_attempts` cũ nhận version 0; giữ nguyên revision, điểm và ảnh chụp câu hỏi. Không dùng để vượt điều kiện lần đầu của version 1 cho người chưa hoàn thành.
6. Người chưa hoàn thành sẽ bắt đầu lại version 1 khi mở bài. Đây là ảnh hưởng rõ ràng của lần chuyển đổi đầu tiên, kể cả bài chưa vừa được quản trị viên sửa. Không tự động quy đổi tỷ lệ cũ sang video mới.
7. Không tạo progress version 1 hàng loạt; tạo khi có ghi tiến độ. Read trả dữ liệu rỗng đúng phiên bản và lịch sử cũ vẫn còn.
8. Sau khi đổi unique constraint, kiểm tra không còn reader dùng Optional theo user/bài hoặc gom nhiều version vào cùng khóa.
9. Đối chiếu số thành tích bài/khóa, tổng điểm, badge và số row legacy trước/sau. Sai khác thì dừng mở writer.

Các trường hợp có bằng chứng nguồn nội dung trùng khớp hoàn toàn có thể được chuyển tiếp qua đợt xử lý riêng được duyệt; thiết kế này không mặc định suy luận để giữ tiến độ đang học.

## 13. Triển khai và rollback

- Dùng migration SQL có phiên bản, kiểm tra điều kiện trước/sau và ghi nhận lần chạy. Dự án hiện chưa có Flyway/Liquibase; không giả định SQL tự chạy chỉ vì nằm trong resources/migrations.
- PostgreSQL đang dùng `ddl-auto=update`; không dựa vào Hibernate tự bỏ unique cũ hoặc tự backfill. Chạy migration thủ công có kiểm soát trước mở backend mới; kiểm tra schema sau khi backend khởi động không tạo lại unique cũ.
- Chuẩn bị thay đổi additive trước; cutover đổi constraint/backfill trong cửa sổ dừng writer; đưa backend và frontend có kiểm tra phiên bản lên cùng đợt. Chặn client thiếu phiên bản thay vì nhận dữ liệu nhập nhằng.
- Chạy thử trên bản sao DB thật, đo số hàng/lock time trước quyết định thời lượng cửa sổ. Không áp migration trực tiếp lên DB người dùng trong lúc đang duyệt giải pháp.
- Giữ một ảnh backup trước cutover. Sau khi có nhiều row/user/bài, KHÔNG rollback bằng cách khởi động binary cũ trên schema mới; query Optional cũ không còn hợp lệ.
- Nếu lỗi sau khi mở writer: ưu tiên sửa tiếp; nếu bắt buộc rollback thì dừng writer, xuất riêng tiến độ/attempt/thành tích mới để không mất dữ liệu, khôi phục backup và chạy thủ tục đối soát dữ liệu phát sinh trước khi mở lại. Không xóa cột mới hoặc gộp lịch sử tự động.
- Chỉ dọn cột deprecated và namespace v1 ở đợt sau. Không thêm job xóa lịch sử tự động trong phạm vi này.

## 14. Tính đúng, tải và quan sát

- Tạo version mới là thay đổi một lesson, không fan-out UPDATE toàn bộ user progress.
- Mỗi write tra đúng unique row; các endpoint danh sách dùng join/filter theo phiên bản, tránh N+1 truy vấn từng bài.
- Với các báo cáo “đã từng bắt đầu”, có thể dùng lịch sử; với “đang học bản hiện tại” và resume thì chỉ dùng current. Không đổi nghĩa báo cáo mà không đổi nhãn/kiểm thử.
- Log có trace ID, lesson ID, request/current version và code xung đột; không log nội dung văn bản hoặc đáp án người học. Theo dõi số 409, số version mới, số row mới và số lần cấp thành tích trùng bị chặn.
- Snapshot tiến độ do client gửi vẫn theo mức tin cậy hiện tại. Sửa việc bỏ khoảng cập nhật trên 2 giây hoặc theo dõi khoảng video chưa xem là bài toán bộ đếm khác; không dùng việc versioning để tuyên bố đã sửa các lỗi đó.
- Lưu lịch sử tăng dung lượng tuyến tính theo số người thực sự học mỗi phiên bản, không theo tích mọi người × mọi bài × mọi version. Chưa đặt hạn xóa khi chưa có yêu cầu lưu trữ dữ liệu.

## 15. Các phương án không chọn

| Phương án | Lý do không chọn |
|---|---|
| Xóa progress mỗi lần lưu bài | Mất lịch sử; dễ mất thành tích; tốn cập nhật hàng loạt; request cũ ghi ngược trở lại |
| Chỉ so với lesson hiện tại, không lưu version | Không phân biệt 900 giây của video A với video B |
| Dùng `@Version` làm phiên bản học | Đổi metadata kỹ thuật có thể bắt học lại; không thể hiện đúng nội dung |
| Sửa thời lượng progress bằng thời lượng video mới | Giữ số giây video cũ cho video mới, có thể công nhận hoàn thành sai |
| Mỗi loại nội dung một hệ thống version riêng ngay | Giữ tiến độ phần không đổi tốt hơn, nhưng trái lựa chọn học lại TOÀN bài cho người chưa hoàn thành và làm tăng phạm vi |
| Dựng kho phiên bản đầy đủ mọi file/nội dung | Vượt nhu cầu sửa tiến độ; chưa cần chức năng phát lại giáo trình cũ |

## 16. Các hành trình phải đạt

1. A học 60% v1; thay video và lưu thành v2; A mở lại thấy tiến độ v2 bằng 0; v1 vẫn tra được trong DB.
2. B đã hoàn thành v1; thay video/quiz/tài liệu thành v2; B vẫn hoàn thành, khóa sau vẫn mở, điểm không đổi.
3. A đang mở v1; quản trị viên lưu v2; lần ghi tiếp theo trả 409, frontend không tự gắn số giây cũ vào v2.
4. Video cũ 3483 giây, video mới 900 giây; sau học đủ v2, đánh giá với thời lượng v2, không còn chia cho 3483.
5. Chỉ sửa tên bài hoặc tài liệu; version không đổi, ID tài liệu giữ nguyên, tiến độ tiếp tục.
6. Có dirty journal/offline request v1; mở v2 không khôi phục hoặc upload dữ liệu đó vào v2.
7. Đạt quiz v1 nhưng chưa hoàn thành bài; sang v2 không dùng điểm v1 để công nhận; người đã hoàn thành v1 vẫn giữ thành tích.
8. Hai tab cùng hoàn thành một phiên bản; chỉ có một row progress và một lần cộng điểm.
9. Đủ nội dung trước quiz hoặc đạt quiz trước nội dung đều dẫn tới cùng kết quả khi đủ mọi điều kiện.
10. Nội dung đổi ngay lúc hoàn thành: kết quả theo thứ tự khóa/commit ở mục 7.3, không có grant từ trạng thái đọc cũ sau khi cập nhật đã commit.

## Detailed Implementation Plan

Các đường dẫn dưới đây tính từ `D:/psnPrj/cbs_elearning`. Đây là danh sách phạm vi thay đổi dự kiến; khi triển khai nếu phát hiện writer khác phải cập nhật danh sách và kiểm thử tương ứng, không bỏ qua vì chưa có trong bảng.

### 17. Backend và dữ liệu

| File | Thay đổi |
|---|---|
| `CoreTrainingAPI/src/main/resources/migrations/20261026_progress_content_versions.sql` (mới) | Migration additive, backfill legacy, đổi unique và các kiểm tra đối chiếu |
| `CoreTrainingAPI/src/main/resources/migrations/20261026_progress_content_versions_runbook.md` (mới) | Các bước dừng writer, backup, chạy/kiểm tra, cutover, đối soát, rollback |
| `CoreTrainingAPI/src/main/java/com/bidv/coretraining/lesson/entity/Lesson.java` | Thêm contentVersion; giữ @Version kỹ thuật |
| `.../lesson/entity/LessonProgress.java` | Unique mới, version nội dung, snapshot, bằng chứng hoàn thành |
| `.../quiz/entity/QuizAttempt.java` | Content version cho lần thi |
| `.../lesson/repository/LessonRepository.java` | Query/projection version nội dung và phương thức khóa hàng cho học/sửa |
| `.../repository/LessonAccessView.java`, `.../repository/LessonOutlineView.java` | Thêm contentVersion |
| `.../lesson/repository/LessonProgressRepository.java` | Query đúng phiên bản; current-list và history tách tên; bỏ Optional user/bài không version |
| `.../quiz/repository/QuizAttemptRepository.java` | Tìm lần đạt đúng user/bài/contentVersion/quizRevision |
| `.../lesson/dto/LessonDto.java`, `.../dto/CourseMapper.java` | Trả phiên bản ở detail và mapper |
| `.../lesson/dto/LessonProgressRequest.java`, `.../lesson/dto/LessonProgressDto.java` | Contract phiên bản và trạng thái current/bảo lưu |
| `.../dto/LessonVersionRequest.java` (mới) | Body có contentVersion cho các endpoint hoàn thành |
| `.../quiz/dto/QuizSessionDto.java` (mới), `.../quiz/dto/QuizSubmitRequest.java` | Snapshot session và request version/revision |
| `.../quiz/dto/QuizResultDto.java`, `.../quiz/dto/QuizReviewDto.java` | Kết quả/review có attemptId, contentVersion, quizRevision, historical; result có lessonCompleted để đồng bộ ngay |
| `.../dto/LessonContentVersionRequest.java` (mới) | Body version kỹ thuật bắt buộc cho thao tác đánh dấu nội dung thay đổi |
| `.../exception/LessonContentChangedException.java` (mới), `.../exception/GlobalExceptionHandler.java` | 409/428 có cấu trúc; không biến lỗi phiên bản thành lỗi mạng |
| `.../exception/ErrorResponse.java` | Thêm code, lessonId và currentContentVersion tùy chọn; giữ trường lỗi hiện có cho các API khác |
| `.../service/LessonContentVersionService.java` (mới) | Chuẩn hóa/so sánh nội dung, khóa khi sửa, tăng một lần, tạo snapshot; thao tác đánh dấu nội dung thay đổi |
| `.../service/LessonCompletionService.java` (mới) | Một hàm đánh giá đủ nội dung + quiz và cấp thành tích idempotent; ghi bằng chứng theo phiên bản |
| `.../lesson/service/LessonProgressService.java` | Luồng current read/write; bỏ trộn version và gate completionVersion cũ; không cung cấp đường xóa toàn lịch sử qua resetProgress |
| `.../service/impl/AdminServiceImpl.java` | Giữ ID tài liệu; hook contentVersion trong lưu bài, CRUD câu hỏi và cấu hình quiz |
| `.../service/AdminService.java` | Chữ ký bumpLessonContentVersion phục vụ endpoint mới; trả LessonDto |
| `.../training/service/TrainingStructureService.java` | Bài mới/copy bắt đầu version 1; các thao tác nội dung/quiz thực sự ghi phải dùng cùng cơ chế; metadata/reorder không bump |
| `.../user/service/impl/UserServiceImpl.java`, `.../user/service/UserService.java` | Hoàn thành có version, không bypass nội dung; bảo lưu thành tích; cập nhật chữ ký API |
| `.../quiz/service/impl/QuizServiceImpl.java`, `.../quiz/service/QuizService.java` | Session nhất quán, submit có version, attempt theo version, gọi completion dùng chung |
| `.../service/LearningPolicy.java` | Ưu tiên thành tích bảo lưu; không phủ định bằng quiz mới; dùng chung chính sách khóa học |
| `.../learner/service/LearningOutlineService.java` | Projection version, current-only resume và quiz gating theo version |
| `.../learner/service/LearnerWorkspaceService.java` | Current-only history map/resume; completed ưu tiên thành tích |
| `.../report/service/TrainingReportService.java` | Không lẫn lịch sử vào tiến độ hiện tại; giữ định nghĩa báo cáo đã từng học/thành tích |
| `.../user/controller/UserController.java`, `.../quiz/controller/QuizController.java` | Body bắt buộc có phiên bản; thêm quiz session |
| `.../controller/AdminController.java` | Endpoint đánh dấu nội dung thay đổi, có quyền sửa và version kỹ thuật |
| `CoreTrainingAPI/README.md` | Ghi rõ cách chạy migration/rollout mới, không hứa Hibernate tự chuyển dữ liệu |
| `CoreTrainingAPI/pom.xml` | Profile kiểm thử `progress-postgres-it`, tách tag PostgreSQL khỏi `mvn test` mặc định |
| `CoreTrainingAPI/src/test/resources/application-progress-postgres-test.yml` (mới) | Chỉ dùng database test chỉ định bằng biến môi trường; không fallback datasource đang dùng |
| `CoreTrainingAPI/src/test/resources/progress-legacy-fixture.sql` (mới) | Fixture schema/dữ liệu trước migration để đối chiếu tiến độ, thành tích và điểm sau migration |
| `scripts/test-progress-postgres.ps1` (mới) | Khởi tạo PostgreSQL 15 dùng riêng cho test, truyền kết nối qua biến môi trường, chạy profile test và dọn container do chính script tạo |

Trong bảng này, tiền tố `.../` là `CoreTrainingAPI/src/main/java/com/bidv/coretraining/`.

Giữ `User.completedLessons` và bảng liên kết hiện có; không đổi hàng loạt consumer sang một bảng thành tích mới. `LearningAccessService` sử dụng projection mới qua `LessonRepository`; nếu cần sửa chữ ký để trả snapshot có khóa, bổ sung trực tiếp tại file này và kiểm thử truy cập tương ứng.

### 18. Frontend

| File | Thay đổi |
|---|---|
| `CoreTraining/src/types/index.ts` | Kiểu lesson/progress phiên bản và trạng thái bảo lưu |
| `CoreTraining/src/api/courseApi.ts` | Request tiến độ/hoàn thành có phiên bản; xử lý contract mới |
| `CoreTraining/src/api/quizApi.ts` | Quiz session và request có phiên bản/revision |
| `CoreTraining/src/api/adminApi.ts` | Gọi endpoint đánh dấu thay đổi nguồn ngoài, giữ version kỹ thuật và DTO trả về |
| `CoreTraining/src/stores/userStore.ts` | Pending/saved chỉ gộp cùng version; completed ưu tiên backend; lỗi stale không retry mạng |
| `CoreTraining/src/stores/courseStore.ts` | Invalidate detail/outline và revalidate khi quay lại bài |
| `CoreTraining/src/stores/quizStore.ts` | Lưu questions, contentVersion và quizRevision từ cùng session; nộp đúng bộ giá trị; bỏ session stale |
| `CoreTraining/src/utils/lessonProgressWriter.ts` | Khóa có version; không coalesce/đánh dấu thành công chéo version; hủy stale job |
| `CoreTraining/src/utils/videoProgressJournal.ts` | Namespace v2, khóa version, discard pending cũ đúng phạm vi |
| `CoreTraining/src/utils/documentProgressJournal.ts` | Namespace v2, khóa version + document; không kế thừa completion cũ |
| `CoreTraining/src/utils/learningStructure.ts` | Đồng bộ cache context và hoàn thành bảo lưu |
| `CoreTraining/src/views/LessonDetail.vue` | Context version từ lesson, chống response muộn, refresh 409, thông báo restart/bảo lưu, reset reader state |
| `CoreTraining/src/components/lesson/LessonMediaStage.vue` | Key chứa lesson + version cho video/văn bản/tài liệu |
| `CoreTraining/src/components/lesson/LessonCourseSidebar.vue` | Dùng currentQuizPassed cho quiz hiện tại, không suy luận từ điểm aggregate lịch sử |
| `CoreTraining/src/components/VideoPlayer.vue` | Giữ cue-based restore; remount/reset đúng version; không tự lấy progress cũ từ parent |
| `CoreTraining/src/views/QuizView.vue` | Session đồng nhất, chặn nộp đề cũ và cập nhật completed từ backend |
| `CoreTraining/src/views/LessonContentEditor.vue` | Cảnh báo tác động sửa nội dung; giữ ID tài liệu khi lưu; thao tác đánh dấu thay đổi nguồn ngoài |

Sidebar tiếp tục nhận danh sách completed thống nhất cho thành tích bài; riêng nhãn/điều hướng quiz chuyển sang `currentQuizPassed`. Không tự suy luận thành tích bài từ điểm lịch sử. Các consumer điểm cũ trong LessonDetail, QuizView và quizStore đều được sửa và có test v1/v2.

### 19. Kiểm thử cần bổ sung

Backend, dưới `CoreTrainingAPI/src/test/java/com/bidv/coretraining/<domain>/service/` (`lesson` hoặc `quiz` tùy nội dung test):

- `LessonContentVersionServiceTest.java` (mới): thay đổi thật/no-op, URL chuẩn hóa, metadata-only, ID tài liệu giữ nguyên, sửa/thêm/xóa/đổi thứ tự quy trình, quiz thay đổi; assert lifecycle quizRevision và bump thủ công lặp/stale/không có quyền.
- `LessonProgressVersioningTest.java` (mới): query current, row mới từ 0, snapshot server, legacy không áp vào current, request sai/missing version, bảo lưu completed, video ngắn hơn.
- `LessonCompletionVersioningTest.java` (mới): điều kiện thống nhất mọi thứ tự video/text/docs/quiz; hai lần cấp chỉ cộng điểm một lần; bảo lưu khóa học và quiz cũ không tính cho người dở.
- `QuizContentVersionTest.java` (mới): session nhất quán, revision cũ từ chối, attempt gắn version, chấm quiz chưa đủ nội dung không cấp hoàn thành; review snapshot cũ vẫn dùng câu hỏi/đáp án/điểm đạt lúc thi sau khi sửa/xóa quiz; legacy thiếu snapshot trả 422; không đọc attempt user khác.
- `ProgressVersioningPostgresTest.java` (mới): chạy trên PostgreSQL test database riêng; migration thật, unique mới, query JSON/current/history, race sửa bài vs progress/quiz và hoàn thành từ hai tab; session GET chạy đồng thời sửa câu hỏi/điểm đạt không ghép version cũ với đề mới. Không chạy migration test lên DB đang dùng.
- Cập nhật `EmptyQuizPolicyTest.java` nếu chữ ký/chính sách hoàn thành dùng chung thay đổi; giữ trường hợp quiz rỗng không chặn.

Frontend, dưới `CoreTraining/tests/`:

- `lessonProgressVersioning.test.mjs` (mới): hàng đợi/journal/store khác version, 409 terminal, response muộn, người hoàn thành không tụt badge.
- `quizContentVersion.test.mjs` (mới): UI/store dùng version từ session, xử lý stale submit/review legacy, điểm v1 không chặn làm đề v2, sidebar không coi score lịch sử là currentQuizPassed.
- Cập nhật `learningPerformance.test.mjs`, `learningPagesPerformance.test.mjs`, `lessonNavigation.test.mjs`, `lessonContent.test.mjs`: current-only restore, cache invalidate, trình tự chuyển bài và các kiểu nội dung.
- Cập nhật `videoPlayer.test.mjs`: đổi version cùng URL tạo instance mới qua media stage; giữ hồi phục cue và đếm thời gian trong cùng version.
- Thêm migration smoke assertions để chứng minh số thành tích/điểm/badge trước sau không đổi; không chỉ kiểm tra có thêm cột.

### 20. Trình tự thực hiện và kiểm chứng

1. Chốt thiết kế tổng thể và tác động migration lần đầu. Ghi test tái hiện trước khi sửa mã.
2. Làm schema, entity và query current/history; thử migration trên bản sao dữ liệu.
3. Làm contentVersion service, giữ ID tài liệu, hook mọi writer nội dung/quiz; chứng minh no-op không reset.
4. Làm progress và completion service cùng khóa/transaction; đổi API/quiz session; kiểm thử race trên PostgreSQL.
5. Làm frontend version context, cache/journal, xử lý stale, badge và thông báo; giữ bản sửa video đen.
6. Chạy `mvn test` tại CoreTrainingAPI. Test PostgreSQL mang tag `progress-postgres`, chạy qua `scripts/test-progress-postgres.ps1` và lệnh `mvn -Pprogress-postgres-it test`; script tạo database/container test riêng, không nhận DB ứng dụng làm mặc định, test từ chối database không có hậu tố `_progress_test`. Chạy tất cả file `CoreTraining/tests/*.test.mjs` bằng Node với experimental strip types, và `npm run build` tại CoreTraining.
7. Kiểm chứng trình duyệt: học dở→đổi video→bắt đầu lại; đã hoàn thành→đổi nội dung→vẫn hoàn thành; hai tab; mất mạng/replay; mở lại YouTube; quiz trước/sau nội dung.
8. Duyệt báo cáo đối soát migration và bản build; cutover theo mục 13. Không chỉnh tay `content_completed=true` hoặc cộng số giây để làm test qua.

Mốc kiểm chứng trước triển khai của thiết kế này: các lượt sửa YouTube trước đó có 11/11 test video/API đạt, build frontend đạt; toàn bộ frontend 97/98, còn lỗi `roleTypes.test.mjs:87` ngoài phần video. Đây là kết quả đã có từ lượt sửa trước, KHÔNG phải kết quả chạy của chức năng versioning chưa triển khai.

### 21. Điều kiện nghiệm thu

- Tất cả hành trình mục 16 có bằng chứng kiểm thử.
- Không có query progress current bỏ contentVersion; không có writer chấp nhận snapshot cũ thành bản mới.
- Sửa video không giữ thời lượng hoặc giây đã xem của video trước trong bản hiện tại.
- Bản ghi cũ được giữ; thành tích bài/khóa, điểm và badge không mất hoặc cấp trùng.
- Tên bài/tên tài liệu/thứ tự không reset tiến độ.
- Hoàn thành thủ công, progress và quiz dùng cùng điều kiện, không có đường bypass.
- Migration và rollback được diễn tập; không tuyên bố có thể rollback binary cũ trực tiếp sau khi unique thay đổi.
- Thiết kế phân biệt rõ hoàn thành nội dung hiện tại và thành tích đã đạt ở phiên bản cũ trên mọi màn liên quan.

*Soạn theo EGM từ quy tắc nghiệp vụ đã trao đổi và mã nguồn hiện tại. Các chi tiết kỹ thuật ở đây là đề xuất cần duyệt tổng thể; chưa là kết quả triển khai.*

Rà soát độc lập: xem [báo cáo rà soát](lesson-progress-content-versioning.review.md). Các điểm Major được nêu trong hai lượt đánh giá ban đầu đã được bổ sung và kiểm tra lại; không còn Blocker/Major trong phạm vi tái kiểm tra đó. Điều này không thay thế kiểm thử triển khai.
