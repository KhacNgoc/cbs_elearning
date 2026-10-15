# Mockup Dashboard và thống kê đào tạo — phiên bản 3

## Phân tích và cấu trúc

Dashboard cần trả lời nhanh: phạm vi nào đang xem, tình hình chung, tiến độ, chất lượng kết quả và nơi cần xử lý. Vì vậy `/admin` và `/admin?tab=overview` có đúng 5 khu vực:

1. Bộ lọc toàn cục: ngày ghi danh, chương trình, khóa học, phòng/nhóm, đơn vị, trạng thái.
2. KPI tổng quan: học viên duy nhất; tỷ lệ hoàn thành; điểm kiểm tra trung bình; tỷ lệ đạt; tỷ lệ học viên quá hạn.
3. Tiến độ đào tạo: phân bố trạng thái, lượt hoàn thành theo ngày hoàn thành thực tế, so sánh phòng/nhóm.
4. Kết quả & năng lực: đạt lần đầu/đã từng đạt, chưa đạt/chưa thi; mức bao phủ đánh giá và khu vực kiến thức/mục tiêu chưa đủ dữ liệu.
5. Danh sách cần chú ý: đơn vị có quá hạn; khóa học có nhiều quá hạn/chưa đạt; học viên quá hạn, sắp đến hạn chưa bắt đầu, đã thi chưa đạt, chưa thi.

Nhóm sidebar `Thống kê & báo cáo` xổ 8 màn hình bằng query `report`: `learners`, `courses`, `programs`, `groups`, `units`, `completion`, `scores`, `domains`. Quyền dùng nhóm menu dựa vào quyền trang `admin.overview` hiện có; không thay đổi phân quyền backend. Query `tab` của các màn hình quản trị cũ vẫn được giữ cho đường dẫn cũ. Bản duyệt độc lập development `/mockups/reports` hiển thị cùng sidebar và nội dung, không cần đăng nhập; không có đường dẫn công khai này trong production.

## Các màn hình chuyên sâu

- Học viên: một dòng mỗi người; số khóa bắt buộc/tham gia, hoàn thành, tỷ lệ, điểm và kết quả. Mở hồ sơ học tập, xem từng lượt và lịch sử thi.
- Khóa học: số lượt ghi danh, hoàn thành, tỷ lệ, điểm, kết quả, cột bỏ dở chưa có dữ liệu. Mở danh sách học viên, so sánh tiến độ các khóa trong cùng biểu đồ.
- Chương trình: số khóa bắt buộc được giao, hoàn thành bắt buộc, tiến độ; biểu đồ và bảng dùng cùng mẫu số. Mở từng khóa thành phần; chương trình chưa có khóa bắt buộc hiển thị —.
- Phòng/nhóm: so sánh tiến độ, sắp bảng theo tỷ lệ hoàn thành, tỷ lệ đạt và học viên quá hạn. Mở học viên của nhóm.
- Đơn vị: tổng hợp cùng quy tắc, mở xuống phòng/nhóm rồi học viên, giữ bộ lọc đơn vị.
- Hoàn thành: danh sách và bộ lọc toàn cục cho chưa bắt đầu/đang học/hoàn thành/quá hạn; xuất CSV theo danh sách.
- Kiểm tra: điểm trung bình, tỷ lệ đạt, phân bố điểm gần nhất, số lần thi, ngưỡng của từng bài và lịch sử thi. Khu vực phân tích câu hỏi là trạng thái chưa có dữ liệu trong fixture.
- Năng lực kiến thức: kết quả đại diện theo lĩnh vực hiện có; số học viên đã đánh giá; điểm năng lực, mục tiêu, khoảng cách hiển thị chưa đủ dữ liệu/chưa thiết lập. Không tự coi lĩnh vực là nhóm kiến thức hoặc bịa ngưỡng để kết luận năng lực.

## Một bộ quy tắc chung

Nguồn công thức: `CoreTraining/src/utils/reportingMetrics.ts`. Component Dashboard, biểu đồ, KPI các báo cáo, bảng và dialog đều gọi `summarize`, `learningStatus`, `latestScore`, `hasPassed`, `overdue`, `formatRatio`. Quy tắc chỉ được duy trì trong mã nguồn và tài liệu; không hiển thị trong giao diện. Các khu vực chỉ có tiêu đề và chú thích nội dung, không đánh số.

- Hoàn thành: lượt học 100% bài học và có lần đạt bài kiểm tra mẫu. Tỷ lệ trên tổng lượt học trong phạm vi.
- Tỷ lệ đạt chính: lượt học đã từng đạt / lượt học có kiểm tra. Đạt lần đầu là chỉ số phụ. Nhiều lần thi không nhân mẫu số.
- Điểm TB: trung bình điểm gần nhất của mỗi lượt học có kết quả; chưa kiểm tra không tính thành điểm 0. Giá trị hiển thị một chữ số thập phân.
- Học viên quá hạn: người có ít nhất một lượt chưa hoàn thành và có hạn trước ngày chốt / người có ít nhất một lượt có hạn. Đếm người duy nhất.
- Lượt học quá hạn: số lượt quá hạn / lượt có hạn. Phân biệt tên và mẫu số với tỷ lệ học viên quá hạn.
- Tiến độ chương trình: lượt khóa bắt buộc được giao hoàn thành / lượt khóa bắt buộc được giao. Không lấy trung bình phần trăm các khóa.
- Gộp nhóm: tổng hợp số liệu gốc, không lấy trung bình tỷ lệ đã làm tròn; đếm lại học viên distinct. Mẫu số 0 hiển thị —.
- Quá hạn là cờ bổ sung, không phải trạng thái loại trừ đang học/chưa bắt đầu. Không suy ra bỏ dở từ chưa hoàn thành.

Các quy tắc trên hiện là hợp đồng cho mockup quản trị, chưa thay thế logic hồ sơ học viên hoặc backend. Khi tích hợp dữ liệu thực phải đưa quy tắc vào dịch vụ/API chung để toàn hệ thống cùng dùng, đặc biệt chốt cách xử lý nhiều bài kiểm tra bắt buộc, phiên bản bài thi và giao khóa theo chương trình. Không cam kết các màn hình sản phẩm khác đã dùng quy tắc này.

## Phạm vi dữ liệu

Dữ liệu tách riêng tại `src/mockups/reportFixture.ts`, chốt 15/10/2026, là dữ liệu minh họa có nhãn. Bộ lọc thời gian chọn nhóm ghi danh; trạng thái là tại ngày chốt, không phải snapshot lịch sử. Xu hướng hoàn thành chỉ tính ngày hoàn thành trong kỳ của nhóm ghi danh đã lọc. Giả định mẫu một lượt học có một bài kiểm tra nhiều lần thi; cờ bắt buộc là fixture phục vụ duyệt quy tắc.

Chưa có: kết nối DB báo cáo, dữ liệu bỏ dở, ánh xạ câu hỏi vào nhóm kiến thức, mục tiêu năng lực, dữ liệu câu trả lời trong fixture, gửi nhắc nhở, Excel `.xlsx` và PDF từ server. CSV UTF-8 có BOM xuất toàn bộ bảng đang lọc/tìm kiếm, kèm phạm vi và nhãn mẫu. PDF dùng in/lưu PDF trình duyệt, gồm trang đang xem và toàn bộ bảng.

## Kiểm tra

- `npm run build`: Vue/TypeScript và Vite.
- `node --experimental-strip-types --test tests/reportingMetrics.test.mjs`: 6 trường hợp cho điểm gần nhất/đã từng đạt, thiếu dữ liệu, đếm học viên quá hạn distinct, khóa bắt buộc, tổng hợp tỷ lệ, thứ tự lịch sử thi.
- Browser: đối chiếu 5 KPI giống nhau trên Dashboard và 8 báo cáo; giữ trạng thái toàn cục khi chuyển màn; drill-down chương trình → khóa và đơn vị → nhóm.
- Responsive: desktop có sidebar, mobile có menu báo cáo thu gọn và các vùng cuộn bảng riêng. Không tràn ngang toàn trang.


## Đánh giá khóa học — đã tích hợp dữ liệu thực

Phần này bổ sung phạm vi dữ liệu thực cho mockup trên. `/admin` và báo cáo khóa học dùng `LiveCourseRatings.vue` gọi API; `/mockups/reports` vẫn dùng fixture riêng để duyệt thiết kế. Các số liệu tiến độ/kiểm tra khác vẫn là mockup. Không ghép đánh giá thật vào các dòng khóa học minh họa.

- Học viên có quyền học, được ghi danh/giao khóa, hoàn thành mọi chương đã xuất bản và đạt các bài kiểm tra bắt buộc mới được đánh giá khóa đã xuất bản. Form nằm sau phần hoàn thành ở trang đề cương khóa học.
- Mỗi học viên–khóa có một bản ghi `course_ratings`, 1–5 sao và nhận xét tùy chọn tối đa 2.000 ký tự. Cập nhật thay thế giá trị hiện hành; không cộng thêm lượt. Kiểm tra phiên bản chống ghi đè từ tab cũ, khóa giao dịch và ràng buộc unique bảo vệ ghi đồng thời.
- Bình luận bài học tiếp tục hoạt động; không nhận thêm sao bài học. Sao cũ được giữ và ghi rõ là đánh giá bài học trước đây, không chuyển thành sao khóa học.
- Điểm khóa = tổng sao hiện hành / số đánh giá khóa. Điểm chung tổng hợp theo số đánh giá, không lấy trung bình các điểm khóa. Không có đánh giá hiển thị `—` hoặc `Chưa đánh giá`.
- Tỷ lệ phản hồi = số đánh giá / số lượt học có ghi nhận hoàn thành trong `User.completedCourses`. Đây là dữ liệu hoàn thành đã lưu; không tự suy diễn hay backfill lịch sử. Lưu đánh giá cũng ghi nhận hoàn thành qua `LearningPolicy` hiện hành.
- Báo cáo thực có bộ lọc khóa riêng, phân bố sao, điểm và độ bao phủ theo khóa, phản hồi mới nhất, lọc phản hồi 1–2 sao, phân trang 20 và xuất CSV tổng hợp theo khóa. Bộ lọc phản hồi thấp chỉ lọc danh sách nhận xét; KPI giữ toàn bộ đánh giá trong phạm vi khóa. Phản hồi thấp là dấu hiệu cần xem xét, không tự kết luận khóa kém từ mẫu nhỏ.
- Bộ lọc thời gian/tổ chức của mockup chưa áp dụng cho khối đánh giá thực; giao diện có chú thích phạm vi riêng. Fixture chỉ tính người đã hoàn thành và không thay thế dữ liệu khi API lỗi.

API: `GET /api/courses/{id}/rating`, `PUT /api/courses/{id}/rating` với `{stars, content, version}` (lần đầu `version: null`); `GET /api/admin/courses/ratings?courseId=&lowOnly=false&page=0`. Báo cáo yêu cầu `courses:read` và giới hạn khóa theo phạm vi quản lý hiện có. Backend xác thực điều kiện đánh giá, không dựa vào việc frontend ẩn/hiện form.

Triển khai: khởi động lại backend để nhận entity/API mới. Với cấu hình Hibernate `ddl-auto=update`, bảng mới được tạo khi khởi động; môi trường quản lý schema thủ công dùng `CoreTrainingAPI/src/main/resources/migrations/20261015_course_ratings.sql` trước khi chạy bản mới. Không tự chạy migration trên cơ sở dữ liệu đang dùng trong lần triển khai mã nguồn này.

Kiểm tra bổ sung: 6 test dịch vụ đánh giá trên H2 (điều kiện hoàn thành/bài kiểm tra, sao không hợp lệ, cập nhật không trùng, xung đột phiên bản, chưa có dữ liệu, phạm vi quyền); 45 kiểm thử phân quyền/hồ sơ hiện có; 9 test tiện ích báo cáo; build Vue/TypeScript. Chưa kiểm thử đăng nhập và gửi đánh giá đầu cuối trên cơ sở dữ liệu triển khai.

