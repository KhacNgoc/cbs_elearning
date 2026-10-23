# Rà soát backend: Học bài và Cấu trúc khóa học

Ngày 22/10/2026. Phạm vi: outline, chi tiết bài, ghi chú, lưu tiến độ, cùng các đường tải dữ liệu dùng chung khi mở hai màn này. Đã triển khai các thay đổi bên dưới. Các mục rà soát phía sau ghi lại **trạng thái trước khi sửa**, không phải mô tả mã hiện tại.

## Thay đổi đã triển khai

- `LearningOutlineService` đọc metadata chương/bài bằng projection, lấy tập id hoàn thành trong phạm vi khóa và tính khóa/hoàn thành theo một lượt duyệt. Không map chi tiết rồi xóa. `course.lessons` trong outline là danh sách rỗng; frontend dùng `chapters[].lessons` làm nguồn bài duy nhất.
- `LearningAccessService` kiểm tra đăng ký/khóa bắt buộc và bài trước chưa hoàn thành bằng truy vấn scalar/exists. Detail, notes, progress và `TrainingStructureService.requireLesson` dùng chung đường này. Giữ lọc published, quyền editor, chương rỗng chặn chương sau và thứ tự có id để xử lý trường hợp trùng thứ tự.
- Detail gom kiểm tra quyền và tạo DTO trong một transaction read-only; chỉ hydrate content/quiz/documents/procedure của bài đích. `hasQuiz` dùng exists, không hydrate nội dung câu hỏi. Các API outline và notes chỉ đọc metadata cần thiết.
- Resume chỉ đọc lịch sử của khóa đang mở. Tài liệu được kiểm tra bằng exists và id projection. Giữ quy tắc chuyển sang QUIZ khi đã học nội dung nhưng chưa đạt kiểm tra bắt buộc.
- Danh sách khóa và chi tiết khóa dành cho học viên lấy metadata bài published; danh sách nhiều khóa gom metadata và cờ quiz cấp khóa theo lô. Nhánh editor của hai API này giữ DTO đầy đủ phục vụ chỉnh sửa.
- Bình luận trong chi tiết bài giới hạn 20 bình luận gốc mới nhất, thêm `commentCount` (số bình luận gốc). Endpoint `GET /api/lessons/{id}/comments?page=0&size=20` trả `{content,page,last,totalElements}`; size giới hạn 1–50. Replies của các root thuộc trang được lấy chung một truy vấn và map nông, không truy cập collection replies cho từng root. Frontend thêm nút **Xem thêm thảo luận**, loại id trùng khi nối trang, chặn response cũ khi đổi bài và tải lại trang đầu sau khi gỡ root.
- Các thao tác tạo/cập nhật/xóa tiến độ khóa hàng `users` theo học viên trước khi đọc progress. Nhờ vậy cả lần tạo đầu tiên lẫn phép cập nhật `max(watchedSeconds)` và cờ hoàn thành được tuần tự giữa các tab/thiết bị. Khóa nằm trong transaction; các học viên khác độc lập. Vị trí xem/bookmark dùng request được xử lý cuối. Không cần migration schema.

## Kết quả sau sửa

Cùng fixture H2 cold context, 2 chương, 4 hoặc 12 bài như phép đo ban đầu:

| Luồng service | Trước: 4 / 12 bài | Sau: 4 / 12 bài | Entity nội dung sau sửa |
|---|---:|---:|---|
| Outline | 34 / 74 SQL | **13 / 13 SQL** | 0 content, quiz, question, comment, document |
| Lưu tiến độ TEXT | 13 / 13 SQL | **12 / 12 SQL** | 0 content, quiz, question, comment, document |
| Chi tiết bài, gồm kiểm tra quyền | 16 / 16 SQL | **15 / 15 SQL** | Chỉ content/quiz/document/comment của bài đích; 0 question |
| Ghi chú rỗng | 13 / 13 SQL | **10 / 10 SQL** | 0 content, quiz, question, comment, document |

Outline không còn danh sách bài trùng trong `course.lessons`; khi có 12 bản tiến độ ở khóa khác, số entity progress ngoài khóa được tải là **0**.

Kiểm thử hồi quy `LearningQueryAuditTest` đã chuyển từ chẩn đoán sang assertion: số query không tăng khi tăng số bài, không hydrate entity nặng ở metadata, không đọc lịch sử khóa khác, giữ khóa bài/published/quiz rỗng, phân trang 26 root có replies, không tải body qua catalog học viên, và hai transaction cùng lưu lần đầu chỉ sinh một dòng với watchedSeconds=100 và contentCompleted=true. Kiểm thử HTTP với OSIV tắt xác minh outline/detail/comments/notes, thu hồi đăng ký và khóa bắt buộc qua nhóm.

Kiểm chứng sau triển khai: **114 kiểm thử backend đạt** (toàn bộ suite 113 kiểm thử, sau đó chạy lại lớp HTTP với kiểm thử bổ sung); **25 kiểm thử frontend liên quan đạt**, TypeScript/Vite production build đạt. Bộ kiểm thử backend cần HTTP stub localhost đã được chạy ngoài sandbox; cấu hình Mockito tạm trong `target` được dọn sau khi chạy.

Giới hạn: đây là phép đo service trên H2, không phải benchmark HTTP PostgreSQL. Danh sách khóa vẫn giữ entity/quyền tổ chức hiện có; chưa tối ưu lại toàn bộ EAGER grants/instructors, workspace/dashboard, phản hồi tổng hợp UserProgress hoặc các đường quản trị. Một trang thảo luận giới hạn số root nhưng vẫn trả toàn bộ replies của các root đó. Khóa theo học viên có thể tăng thời gian chờ khi cùng tài khoản gửi nhiều request progress; transaction được giữ ngắn và frontend đã gộp lần ghi. Cần đo PostgreSQL thực tế và EXPLAIN trước khi thêm chỉ mục hoặc chọn upsert đặc thù DB.

---

Phần dưới là kết quả rà soát ban đầu, dùng để đối chiếu trước/sau.

## Kết quả đo trực tiếp

Dùng `LearningQueryAuditTest`, Spring Data JPA/Hibernate, H2 và JDK 17. Trước từng phép đo: flush dữ liệu chuẩn bị, clear persistence context và reset Hibernate Statistics. Mỗi khóa có **2 chương**, tăng từ **4 lên 12 bài**; mỗi bài có text, một quiz/câu hỏi, một bình luận gốc và một tài liệu. Đo luồng học viên ROLE_USER đã đăng ký khóa. Bài được kiểm tra/lưu là bài đầu chương đầu.

| Luồng service | 4 bài | 12 bài | Entity tải thừa quan sát được |
|---|---:|---:|---|
| Outline | 34 SQL | 74 SQL | Content, quiz, câu hỏi, bình luận, tài liệu của tất cả 4/12 bài |
| Lưu tiến độ TEXT, gồm flush | 13 SQL | 13 SQL | Content và quiz của 2/6 bài trong chương đầu |
| Kiểm tra quyền rồi lấy chi tiết bài | 16 SQL | 16 SQL | Content và quiz của các bài cùng chương, ngoài nội dung bài đích cần trả |
| Lấy ghi chú, danh sách rỗng | 13 SQL | 13 SQL | Content và quiz của 2/6 bài cùng chương dù không có ghi chú |

Outline tăng **40 SQL khi thêm 8 bài**, thể hiện mẫu truy vấn tăng tuyến tính theo số bài. Không có cơ sở để nói các endpoint còn lại cũng tăng số SQL tuyến tính trong fixture này: số câu giữ nguyên nhưng số entity được hydrate tăng.

Kiểm thử bổ sung: khi mở outline khóa 4 bài, nếu học viên có 12 bản tiến độ ở khóa khác, `resumeCourse()` vẫn tải cả **12 entity LessonProgress không thuộc khóa đang mở**.

Đây là số SQL của service trong context kiểm thử, **không phải số truy vấn HTTP production**. Chưa tính JWT/security filter, các API phiên/điều hướng và CSRF, dữ liệu lớn, bộ đệm triển khai hoặc execution plan PostgreSQL. Các lời gọi service trong phép đo chi tiết bài cùng một persistence context; triển khai có ranh giới transaction/OSIV khác có thể cho số khác. Không suy ra độ trễ theo mili giây hoặc CPU production từ bảng này.

## 1. Ưu tiên cao: outline map đầy đủ rồi mới xóa dữ liệu

Vị trí:

- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/service/TrainingStructureService.java:253`: map toàn bộ LessonDto của từng chương.
- Dòng 265–270: `outlineOnly()` chỉ xóa text/video/documents/procedureSteps/comments **sau khi đã map**.
- `CoreTrainingAPI/src/main/java/com/bidv/coretraining/dto/CourseMapper.java:39`: mapper chi tiết truy cập các quan hệ của bài.

Hệ quả: lazy collections đã được load, DTO con đã được tạo, rồi mới bị loại khỏi response. JSON gọn không đồng nghĩa với ít SQL hoặc ít bộ nhớ. Phép đo xác nhận response đã null text và bỏ comments, nhưng Hibernate vẫn hydrate toàn bộ text/comment/document/question của 4/12 bài.

Đề xuất: DTO/projection outline riêng, chỉ lấy id, tiêu đề, thứ tự, trạng thái, thông tin quiz cần hiển thị và số liệu tiến độ. Không gọi mapper chi tiết từ outline. Với dữ liệu chương/bài, dùng một số query theo lô hoặc projection có điều kiện khóa/published, thay vì đi qua từng collection lazy. Chỉ fetch tài liệu của bài cần dùng nếu UI yêu cầu chúng.

## 2. Ưu tiên cao: chỉ kiểm tra có quiz nhưng tải câu hỏi

`CoreTrainingAPI/src/main/java/com/bidv/coretraining/entity/Lesson.java:64` dùng `quiz.getQuestions().isEmpty()`; `CourseMapper` gọi `lesson.hasQuiz()` cho từng bài. Quan hệ questions không có cấu hình extra-lazy/batch trong đường này. Phép đo outline hydrate 4/12 entity Question dù giao diện chỉ cần boolean `hasQuiz`.

Đề xuất: `exists`/count theo nhóm lesson/quiz, hoặc lấy boolean trong projection. Không lấy nội dung câu hỏi/đáp án chỉ để hiển thị nhãn kiểm tra. Cần giữ đúng quy tắc hiện tại: quiz rỗng không được chặn tiến độ.

`CourseMapper:20` cũng duyệt `course.getQuestions()` để tìm câu hỏi kiểm tra cấp khóa. Đây là đường tải thêm câu hỏi cần thay bằng phép kiểm tra tồn tại phù hợp khi xử lý DTO tóm tắt.

## 3. Ưu tiên cao: endpoint lưu tiến độ kéo theo graph nội dung của chương

`LessonProgressService.java:34` tải User và Lesson rồi gọi `LearningPolicy.assertLessonAccess`. `LearningPolicy:39–46` duyệt chương/bài trước để kiểm tra điều kiện mở khóa. Việc lấy `chapter.getLessons()` hydrate các bài của chương; `Lesson.content` và `Lesson.quiz` ở dòng 43–46 là `@OneToOne` **không ghi fetch**, tức mặc định EAGER.

Kết quả: lưu bookmark TEXT vẫn tải content và quiz của các bài cùng chương. Điều tương tự xuất hiện khi chỉ lấy danh sách ghi chú rỗng. Với frontend lưu video theo chu kỳ, đây là đường nóng cần giảm tải.

Đề xuất:

- Kiểm tra điều kiện bằng metadata/projection, trạng thái đăng ký và phép kiểm tra tồn tại bài trước chưa hoàn thành, không tải text/content của toàn chương.
- Kiểm tra tài liệu thuộc bài bằng query `existsByIdAndLessonId` khi lưu DOCUMENT, thay vì khởi tạo toàn bộ `lesson.documents` chỉ để tìm một id.
- Có fetch plan riêng cho **chi tiết bài đích**; tối ưu đồng thời chỗ gọi getter. Chỉ đổi toàn bộ sang LAZY không giải quyết việc mapper/policy vẫn truy cập tất cả, và inverse one-to-one lazy cần kiểm chứng theo Hibernate/enhancement đang dùng.
- Giữ nguyên xác thực quyền ở backend, không thay bằng tin dữ liệu khóa/mở khóa từ client.

## 4. Ưu tiên vừa: resume lấy tiến độ của mọi khóa

`LearnerWorkspaceService.java:186–192` gọi `progress.findByUserId(user.id)` rồi chỉ dùng các bài của một khóa.

Đây là **overfetch**, chưa phải bằng chứng N+1: lấy `progress.lesson.id` trên proxy thường không cần load entity Lesson. Nhưng bộ nhớ, số hàng đọc và thời gian dựng map tăng theo toàn bộ lịch sử học viên. Kiểm thử đã xác nhận 12 bản tiến độ khóa khác được hydrate khi xem khóa hiện tại.

Đề xuất: repository query theo `userId + courseId`, ưu tiên projection các trường resume thực sự cần. Lấy chung dữ liệu tiến độ của khóa để tính resume/hoàn thành thay vì tải lặp các graph User.

## 5. Ưu tiên vừa: response outline chứa danh sách bài trùng hai nơi

`TrainingStructureService.java:401` dựng `chapters[].lessons`; dòng 405 lại `mapper.toCourseDto(course)` có `course.lessons`. Hai mẫu thử trả đồng thời 4+4 hoặc 12+12 bản DTO bài học.

Tác động: map lại dữ liệu và gửi metadata trùng. Không nên kết luận SQL bị nhân đôi chỉ vì gọi mapper hai lần: các collection đã khởi tạo có thể được tái sử dụng trong persistence context, nhưng chi phí tạo DTO/serialize vẫn có.

Đề xuất: trong response outline, course là metadata khóa, chapters là nguồn duy nhất chứa bài. Cần đổi hợp đồng endpoint hoặc DTO riêng, kiểm tra mọi nơi dùng trước khi bỏ trường; không xóa `CourseDto.lessons` trên toàn hệ thống vì các API khác vẫn dùng.

Ngoài ra, nhánh `chapters` lọc published cho học viên nhưng mapper course đi qua `course.lessons` không có bộ lọc tương ứng. Cần loại metadata bài nháp khỏi nhánh dư này; không chỉ xóa text/video sau map.

## 6. Ưu tiên vừa: tính khóa/hoàn thành lặp lại nhiều lần trong bộ nhớ

`LearningPolicy.completed()` quét toàn bộ `user.completedLessons`; `chapterUnlocked()` duyệt lại các chương trước; `publishedLessons()` lọc/sort lại collection mỗi lần. Outline gọi các hàm này khi dựng từng chương, tìm resume và lại qua `workspace.resumeCourse()`.

Không phải mỗi lần gọi đều sinh SQL: first-level cache giữ các collection đã load. Nhưng chi phí stream/sort/lặp tăng theo số chương, bài và lịch sử hoàn thành; còn collection `completedLessons` đang hydrate entity bài thay vì chỉ lấy id.

Đề xuất: lấy tập id bài đã hoàn thành theo phạm vi cần dùng; tính trạng thái các chương/bài bằng một lượt duyệt theo thứ tự. Chuẩn bị một learning context trong request để dùng chung cho xác thực, khóa bài, tiến độ và resume, thay vì tính độc lập nhiều lượt.

## 7. Ưu tiên vừa: bình luận chi tiết không phân trang, có lazy collection theo bình luận

`CourseMapper.java:70` map `Comment.replies`; `toRootCommentDtoList` tại dòng 77 lấy toàn bộ comments của bài rồi map các root. Mỗi root có thể khởi tạo collection replies riêng, kể cả collection rỗng. Bài có nhiều bình luận dễ phát sinh N+1 và response lớn.

Trong chi tiết bài, comments/procedureSteps hiện được frontend sử dụng nên không thể gọi chúng là dữ liệu hoàn toàn vô dụng. Tuy nhiên không cần tải mọi bình luận trước khi học viên mở nội dung chính; outline thì không cần tải những dữ liệu đó.

Đề xuất: endpoint bình luận có phân trang; lấy replies theo lô cho các root đang hiển thị hoặc nhóm từ dữ liệu phẳng đã tải. Quy trình của bài đích có thể giữ trong chi tiết, tránh fetch quy trình của các bài không được xem.

## 8. Ưu tiên vừa: lời gọi kiểm tra/tải lặp và transaction đọc

- `CourseController.java:44–45` gọi service kiểm tra quyền rồi service lấy bài. Với một persistence context, entity có thể được tái sử dụng; khi không dùng OSIV, hai transaction có thể tải lại. Nên gom kiểm tra quyền và tạo DTO của bài vào cùng một service read-only.
- `CourseServiceImpl.java:194–196`: `requireInteraction()` đã `findById` và xác thực Lesson, sau đó `getVideoNotes()` còn gọi `existsById`. Đây là phép kiểm tra tồn tại dư.
- `TrainingStructureService` đặt `@Transactional` ghi ở cấp lớp; `outline()` chưa override `readOnly=true`. Nên để transaction đọc đúng mục đích nhằm giảm công việc flush/dirty checking khi đang hydrate nhiều entity. Đây là cải thiện phụ, không thay thế việc sửa fetch plan.

## 9. Cần xử lý tính đúng khi có truy cập đồng thời

`LessonProgress` có unique `(user_id,lesson_id)` nhưng không có `@Version`; `saveProgress()` dùng mô hình đọc → `Math.max` trong Java → ghi lại entity.

Nguy cơ từ mã, **chưa tái hiện bằng kiểm thử đồng thời**:

- Hai tab/thiết bị cùng tạo progress lần đầu có thể cùng thấy chưa có rồi tranh chấp unique; handler hiện trả conflict nhưng service không có cơ chế upsert/retry dành riêng.
- Hai request đọc cùng giá trị cũ có thể ghi đè `watchedSeconds` hoặc trạng thái hoàn thành của nhau. `Math.max` trong Java không làm thao tác read-modify-write trở thành atomic.

Đề xuất: atomic update/upsert có phép `greatest` và bảo toàn cờ hoàn thành, hoặc optimistic locking + retry có giới hạn. Cần xác định chính sách vị trí xem/thời điểm truy cập khi nhiều thiết bị cùng học. Hàng đợi frontend chỉ bảo vệ trong một phiên UI, không giải quyết cạnh tranh ở DB.

## Cách triển khai nên chọn

1. Tách DTO/projection outline, bỏ map full-then-strip, lấy hasQuiz bằng exists/count; loại danh sách bài trùng.
2. Tách learning access context nhẹ cho detail/notes/progress; lọc progress theo khóa và tính trạng thái trong một lượt.
3. Chỉ fetch chi tiết cho bài đích; phân trang bình luận, gom replies theo lô.
4. Bảo vệ cập nhật tiến độ đồng thời, xác minh PostgreSQL bằng execution plan và query-count test trước/sau.

Không nên giải quyết bằng cách chuyển mọi quan hệ sang EAGER hoặc join-fetch tất cả các collection List. Cách đó có thể tạo tích Descartes, lượng hàng lớn hoặc MultipleBagFetchException. Fetch to-one phù hợp, projection và các query to-many theo lô cần được thiết kế theo từng endpoint.

## Kiểm chứng và giới hạn

- `mvn -o -q -DskipTests compile` với JDK 17: thành công.
- `LearningQueryAuditTest`: 2 kiểm thử thành công; các assert kiểm tra dữ liệu đúng, không khóa cứng số query xấu để cản tối ưu sau này.
- Mockito inline không tự attach được JVM trong sandbox. Lượt đo dùng mock-maker-subclass qua cấu hình tạm trong `target/test-classes`; cấu hình tạm được dọn sau khi chạy. Không thay đổi cấu hình production.
- Repository progress đã có unique key `(user_id,lesson_id)`; VideoNote đã có chỉ mục `(lesson_id,user_id,timestamp_seconds)`. Chưa có cơ sở nói mọi truy vấn thiếu chỉ mục; cần kiểm tra DB triển khai và EXPLAIN trước khi thêm.
- Chưa đo tải đồng thời, lượng heap, thời gian HTTP thực hoặc execution plan PostgreSQL. Số liệu H2 dùng để xác nhận đường fetch và xu hướng số query, không thay số liệu production.
