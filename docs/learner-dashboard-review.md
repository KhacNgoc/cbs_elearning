# Đối chiếu dashboard học viên và đề xuất thiết kế

Ngày kiểm tra: 14/10/2026. Phạm vi: đọc mã frontend Vue và backend Java trong workspace; chưa kiểm thử hệ thống đang chạy hoặc xác minh dữ liệu production. Mockup là bản thiết kế dùng dữ liệu minh họa, không phải dashboard đã tích hợp API.

## 1. Kết luận đối chiếu

| Yêu cầu | Hiện trạng | Bằng chứng / khoảng thiếu |
|---|---|---|
| Lời chào người dùng | Có, cần chỉnh fallback | `Dashboard.vue` dùng fullName/username từ store; khi thiếu vẫn hiển thị tên cố định `quantrh`. |
| Khóa học được giao | Một phần | Có mandatoryCourses, registeredCourses, pendingCourses từ API; dashboard trộn toàn bộ catalog. Chưa có nhóm “Được giao” riêng, nguồn giao/đợt giao/hạn giao. Không đồng nhất “được giao” với “bắt buộc” nếu có giao khóa tùy chọn. |
| Chưa bắt đầu / đang học / hoàn thành | Một phần | Trang chủ chỉ phân biệt hoàn thành/chưa hoàn thành. Profile có ba trạng thái nhưng suy ra từ số bài đã hoàn thành: đã mở/xem video mà chưa hoàn thành bài vẫn bị coi là chưa bắt đầu. |
| Khóa học bắt buộc | Có | Nhãn từ mandatoryCourses. Backend lấy từ nhóm và chính sách phân quyền. Cần khử trùng ID khi thống kê. |
| Hạn hoàn thành | Chưa có | Chưa thấy dueAt/deadline trong Course, UserProgress hay mô hình giao học. Hạn nên gắn vào giao học/ghi danh của từng học viên, không chỉ khóa học. |
| Tỷ lệ hoàn thành | Có nhưng phạm vi chưa đúng | `userStore.totalLessonsCount` cộng toàn bộ catalog, kể cả khóa chưa tham gia; completedLessonsCount đếm toàn bộ ID hoàn thành. Có thể lệch tập dữ liệu và sai tổng quan. Profile có phạm vi joinedCourses gần phù hợp hơn. |
| Điểm kiểm tra | Một phần | API đã trả quizScores và lessonQuizScores; Profile hiển thị điểm cấp khóa. Dashboard chỉ dùng điểm gián tiếp trong radar, chưa có bảng điểm/lịch sử. |
| Điểm cao nhất | Một phần | Backend giữ điểm tốt nhất ở UserQuizScore và lưu QuizAttempt cho bài học; chưa có UI dashboard phân biệt lần gần nhất/điểm cao nhất. Logic cập nhật so số câu đúng tuyệt đối, cần đối chiếu theo tỷ lệ và phiên bản đề khi số câu thay đổi. |
| Biểu đồ năng lực | Có giao diện, chưa đạt dữ liệu | `RadarChart.vue` viết cố định 7 ID/tên nghiệp vụ; nếu chưa thi thì dùng tiến độ × 0,2 làm điểm năng lực. Điều này không chứng minh năng lực và làm khóa mới không được phản ánh. |
| Toàn bộ số liệu từ dữ liệu thực tế | Chưa đạt đầy đủ | Catalog/progress/profile có nhánh API thật và nhánh mock tùy VITE_USE_MOCK_API. Radar cố định danh mục và suy điểm; getCourseQuizScore fallback tổng câu = 5. Chưa xác minh biến môi trường triển khai. |
| Tiếp tục đến bài gần nhất | Một phần | Dashboard đọc currentLessonId trong Pinia, không persist lịch sử gần nhất theo người dùng; tải lại trang mất lựa chọn. Entity LessonProgress có updatedAt nhưng DTO không trả trường này. |
| Tiếp tục đến trang tài liệu gần nhất | Chưa có | LessonProgress DTO chỉ có vị trí video; không có documentId/page/scroll. LessonMediaStage mở PDF iframe/Word/PPT mà chưa nối lưu vị trí đọc. |
| Tiếp tục vị trí video gần nhất | Có nền tảng | LessonDetail truyền lastPositionSeconds/watchedSeconds vào VideoPlayer, progress lưu qua API. Cần kiểm thử tải lại/đăng nhập lại/đổi thiết bị; dashboard vẫn cần chọn đúng bài trước. |
| Nội dung tiếp theo cần hoàn thành | Có ở outline, chưa thống nhất | Backend outline.resumeLessonId lấy bài chưa hoàn thành đã mở khóa. Profile tự lấy bài chưa hoàn thành trong catalog; Dashboard fallback bài đầu. Chưa có bộ phân giải chung quyết định nội dung/quiz cần hoàn thành. |
| Tìm tên khóa học | Chưa có trên dashboard | Dashboard.vue không có input hay predicate tìm kiếm. |
| Lọc lĩnh vực / chương trình | Có metadata, chưa có UI | Course có domainName, programId, programName. Cần domainId nhất quán để lọc, tránh trùng tên. |
| Lọc phân hệ nghiệp vụ / mức độ | Chưa đủ mô hình/UI | Chưa thấy trường metadata riêng tương ứng trong Course DTO/type. Không nên suy mức độ từ tên khóa hoặc dùng chương trình thay cho phân hệ khi chưa xác định nghiệp vụ. |
| Lọc trạng thái học tập | Chưa có trên dashboard | Cần trạng thái từ sự kiện bắt đầu/lịch sử, hoàn thành theo chính sách; tách trạng thái duyệt đăng ký với trạng thái học. |
| Từ khóa | Chưa có trên dashboard | Có thể tìm name/code/description và tags; nếu “từ khóa” là taxonomy cần metadata riêng. |
| Chia tab profile | Chưa có | Profile.vue hiện hiển thị thông tin, kết quả và danh sách khóa trên cùng trang. |

## 2. Cấu trúc màn hình đề xuất

### Trang chủ học tập

1. Banner lời chào, tên khóa/bài gần nhất và nút tiếp tục có mô tả trang tài liệu hoặc mốc video. Không có lịch sử thì “Bắt đầu học”; không có khóa thì chuyển tab khám phá.
2. Một hàng tiến độ ngắn: chưa bắt đầu, đang học, đã hoàn thành; tiến độ tổng tính trên khóa đang tham gia. Không đưa bảng điểm/radar/huy hiệu vào đây.
3. Hai tab **Khóa học của tôi** và **Khám phá & đăng ký**; tab khám phá bao gồm trạng thái chờ duyệt. Khóa đã tham gia không lặp trong catalog đăng ký.
4. Tìm tên/mã/từ khóa và lọc trạng thái đặt ngay trên danh sách. Bộ lọc nâng cao thu gọn gồm lĩnh vực, phân hệ, chương trình, mức độ, nguồn tham gia và bắt buộc. AND giữa các trường, tìm không phân biệt hoa thường/dấu; tùy chọn lấy từ API và phạm vi quyền truy cập.
5. Thẻ khóa tái sử dụng icon, tên, nhãn, ProgressBar: trạng thái học, số bài/%, nguồn được giao hoặc đăng ký, hạn, nút bắt đầu/tiếp tục/xem lại. Sắp mặc định: quá hạn chưa hoàn thành → sắp đến hạn → học gần đây. Quá hạn độc lập với trạng thái học.
6. Phân trang (6–9 thẻ trên desktop tùy mật độ), không lặp một danh sách tiến độ dài ở cột bên phải. Bộ lọc không làm đổi tổng tiến độ cá nhân; chỉ đổi kết quả danh sách.

### Hồ sơ người dùng

Điểm truy cập hồ sơ giữ nguyên component avatar/tên người dùng trên header đang dẫn tới `/profile` trong `App.vue`. Không thêm mục điều hướng “Hồ sơ của tôi” riêng. Header giữ Tổng quan, Bảng vinh danh và cụm tiện ích hiện có; các tab sau nằm bên trong trang hồ sơ sau khi người dùng bấm avatar/tên.

- **Thông tin người dùng:** họ tên, email, mã nhân viên, đơn vị, nhóm, vai trò; sửa trường được cho phép. Tái sử dụng profile hero và form.
- **Thống kê học tập:** số khóa tham gia/theo trạng thái, hoàn thành bắt buộc, tỷ lệ bài và tỷ lệ khóa (ghi rõ mẫu số), radar năng lực theo lĩnh vực/phân hệ. Không trộn điểm tích lũy với điểm kiểm tra.
- **Kết quả kiểm tra:** điểm lần gần nhất, điểm cao nhất, ngưỡng đạt, thời gian, số lần thi, chi tiết lịch sử theo khóa/bài/phiên bản đề.
- **Thành tích:** huy hiệu và điểm tích lũy; tái sử dụng phần huy hiệu hiện tại. Chỉ hiển thị thành tích có bản ghi, không tạo mặc định.

Tách trang như vậy giúp trang chủ tập trung vào việc cần làm; tab nội dung và phân trang kiểm soát độ dài tốt hơn đưa toàn bộ thông tin vào một màn hình. Mockup chỉ thể hiện một phương án này để đánh giá bố cục và luồng.

## 3. Quy tắc dữ liệu cần thống nhất trước triển khai

- **Tập khóa tham gia:** giao học hợp lệ hoặc đăng ký đã duyệt; khử trùng theo courseId, giao học có thể bắt buộc hoặc tùy chọn. Đếm giao học và bắt buộc là hai chiều độc lập. Không tính pending hay catalog vào tổng tiến độ.
- **Chưa bắt đầu:** chưa có sự kiện startedAt hoặc hoạt động nội dung/thi. **Đang học:** đã có hoạt động nhưng chưa thỏa completion policy. **Hoàn thành:** dùng kết quả policy máy chủ, gồm bài xuất bản và kiểm tra bắt buộc. Khóa không có bài không tự thành 100%.
- **Tiến độ bài:** số bài hoàn thành hợp lệ / số bài thuộc phiên bản/cấu trúc được phép học. **Tỷ lệ khóa hoàn thành:** số khóa hoàn thành / số khóa tham gia. Không gọi hai chỉ số bằng cùng một nhãn. Tổng quan bài chỉ dùng ID thuộc tập đang tính.
- **Hạn:** enrollment/assignment.dueAt; theo múi giờ quy định Asia/Saigon. Không có hạn hiển thị “Không quy định”, không tự tạo hạn. Badge quá hạn/sắp hạn chỉ áp dụng khóa chưa hoàn thành.
- **Điểm:** score/total từ attempt chấm phía máy chủ, total phải > 0. Không có điểm hiển thị “Chưa thi”; dữ liệu lỗi hiển thị “Chưa xác định”, không dùng tổng câu giả 5.
- **Điểm cao nhất:** max tỷ lệ attempt hợp lệ trong cùng phạm vi đánh giá, lưu score/total/revision. Hiển thị điểm lần gần nhất riêng. Nếu tổng câu/đề đổi phải quy định so sánh theo phiên bản, tránh max(score) đơn thuần.
- **Radar:** axes lấy từ taxonomy thật; giá trị từ kết quả kiểm tra được gắn với từng năng lực. Đề xuất bình quân điểm cao nhất trên mỗi bài đánh giá trong một nhóm, kèm số bài đã đánh giá; cần nghiệp vụ xác nhận công thức. Nhóm chưa thi là null/“Chưa đánh giá”, không là 0 hoặc progress × 0,2. Ít hơn 3 nhóm có điểm thì dùng danh sách/thanh thay radar.
- **Lưu vị trí:** userId, courseId, lessonId, contentType, contentId/documentId, pageNumber/scrollOffset hoặc positionSeconds, contentVersion, lastAccessedAt, completedAt. Các định dạng tài liệu cần viewer phát sự kiện trang/scroll; PDF iframe hiện tại cần thay/bao bằng viewer kiểm soát được.
- **Resume chung:** nội dung gần nhất chưa hoàn thành còn quyền và đã mở khóa → đúng vị trí đã lưu. Nếu xong nội dung nhưng còn quiz bắt buộc thì quiz → nếu xong bài thì nội dung kế tiếp hợp lệ theo thứ tự chương/bài → không có lịch sử thì bài đầu đã mở khóa. Toàn khóa hoàn thành hiển thị “Xem lại”. Khi nội dung bị gỡ/sửa, chuẩn hóa bookmark hoặc báo rõ và chọn điểm hợp lệ tiếp theo.
- **Lỗi/không dữ liệu:** loading, retry, không khóa, không lịch sử, chưa thi và không có kết quả lọc là trạng thái riêng. Không hiện 0 như dữ liệu thực khi API lỗi. Máy chủ kiểm tra quyền cho cả danh sách, đăng ký, bookmark và endpoint thống kê.

## 4. Tái sử dụng component và phần cần bổ sung

| Có thể tận dụng | Thay đổi cần làm |
|---|---|
| App navbar, màu BIDV, icon Lucide, welcome-banner | Banner gọn + resume target từ máy chủ; bỏ fallback tên người dùng cố định |
| Course card, các badge, modal đăng ký | Thêm dueAt, trạng thái, nguồn giao, tiến độ và CTA riêng; phân trang, search/filter |
| ProgressBar.vue | Đổi phạm vi tổng; không lặp toàn bộ catalog ở cột phải |
| Profile hero, form và thông tin cá nhân | Chia tab, đưa thống kê/kết quả/thành tích vào tab tương ứng |
| RadarChart.vue | Giữ component/chart library; thay axes/dataset và trạng thái chưa đánh giá |
| Huy hiệu từ dashboard | Chuyển sang tab Thành tích |
| CourseOutline.resumeLessonId, VideoPlayer, lessonProgress API | Hợp nhất resume resolver, bổ sung lastAccessedAt và bookmark tài liệu |
| TablePagination.vue, quiz attempt entity | Tận dụng phân trang; bổ sung endpoint lịch sử/aggregation cho profile |

## 5. Kiểm chứng khi triển khai thật

1. Người chưa tham gia khóa nào: tổng tiến độ không có mẫu số, không sinh radar/điểm; CTA khám phá.
2. Mở video/đọc tài liệu chưa hoàn thành bài: trạng thái đã chuyển đang học dù tiến độ hoàn thành bài còn 0%.
3. Học video/tài liệu rồi tải lại, đăng nhập lại, đổi thiết bị: đúng bài, đúng nội dung, đúng giây/trang đọc.
4. Hoàn thành nội dung nhưng còn quiz bắt buộc, bài bị khóa, chương kế tiếp và nội dung bị gỡ: resume đến điểm hợp lệ theo policy.
5. Một khóa nhiều nguồn giao/đăng ký: không đếm trùng; khóa pending/chưa tham gia không nằm trong mẫu số.
6. Đề kiểm tra đổi số câu/phiên bản: latest và best tách biệt, score/total chính xác; năng lực chưa đánh giá không được giả thành 0.
7. Bộ lọc kết hợp, tìm không dấu, xóa bộ lọc, không kết quả, phân trang, mobile và API lỗi.

## 6. Kiểm tra bản thiết kế

Đã kiểm tra cú pháp JavaScript, phân tích HTML và ID trùng; chạy kiểm tra logic với DOM mô phỏng cho tổng tiến độ, chuyển tab hồ sơ, radar, điểm gần nhất/cao nhất, lịch sử thi, resume tài liệu/video, nội dung tiếp theo, tìm kiếm không dấu và trạng thái đăng ký chờ duyệt. Bố cục có breakpoint cho màn hình nhỏ. Chưa kiểm chứng hình ảnh bằng trình duyệt: công cụ xem trước không truy cập được máy chủ localhost trong môi trường này. Chưa kiểm thử tích hợp API hoặc dữ liệu production.

Không thay đổi code ứng dụng trong đợt này; đầu ra là đối chiếu, quy tắc triển khai và mockup để duyệt thiết kế.
