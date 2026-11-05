# Tối ưu đọc dữ liệu quản trị và học viên

Ngày xác minh: 30/10/2026. Trạng thái: đã sửa trong working tree, chưa triển khai môi trường chạy.

## Phạm vi đã thực hiện

- Bỏ tải danh mục khóa học và tiến độ toàn cục khi khởi động ứng dụng. Mỗi trang đọc dữ liệu theo nhu cầu; quiz có endpoint ngữ cảnh riêng, chatbot có danh sách khóa học nhẹ riêng.
- Dashboard học viên đọc thẻ khóa học phân trang và thống kê toàn phạm vi bằng truy vấn DB. Resume chỉ tải khi cần. Hồ sơ tách thông tin, thống kê, kết quả, lịch sử thi và huy hiệu; các tab tải khi mở.
- Bảng vinh danh đọc projection phân trang và hạng của người hiện tại. Huy hiệu phân trang riêng; tổng số bài lấy từ thống kê DB.
- Ngân hàng câu hỏi, người dùng/nhóm, vai trò/thành viên, phê duyệt ghi danh và phản ánh nội dung dùng truy vấn có điều kiện quyền, tìm kiếm và phân trang tại DB. Options quản trị không đọc các repository ngoài nhu cầu trang. Phê duyệt ghi danh có trang riêng để không khởi tạo AdminPortal.
- Báo cáo dùng quan hệ dữ liệu SQL chung cho thống kê, bảng chi tiết, nhóm, drilldown và CSV. Lịch sử điểm tải riêng. CSV giữ bộ lọc/nhóm đang chọn, chống công thức bảng tính và đọc JDBC theo fetch size 500 trong transaction chỉ đọc.
- Lịch sử chat phân trang theo cặp trao đổi hoàn chỉnh, kiểm tra chủ sở hữu. UI có tải lịch sử cũ.
- Bỏ nhánh lựa chọn mock trong API chạy thực tế, mock progress/localStorage và file `mockData.ts`. Lỗi API không được thay bằng dữ liệu giả. Fixture kiểm thử và tài nguyên thiết kế không được dùng trong luồng production vẫn được giữ.

Điều kiện quyền áp dụng trước LIMIT/count/aggregation. Giữ quy tắc nội dung đã xuất bản, phạm vi đơn vị, đăng ký/giao học, kiểm tra bắt buộc, phiên bản bài kiểm tra, điểm đầu/gần nhất và hạn riêng. Cập nhật thành viên vai trò theo delta để không mất thành viên ở trang khác.

## Ranh giới sở hữu

Không sửa các trang quản trị thuộc **Đào tạo & tài liệu**, `AdminPortal.vue`, `trainingApi.ts`, `TrainingStructureService` hay `CourseRepository`. Trong API dùng chung course/quiz chỉ bỏ nhánh mock; giữ hợp đồng endpoint thực. Các endpoint cũ còn phục vụ phần bị loại trừ được giữ tương thích.

## Hợp đồng đọc và phân trang

| Nhóm | Endpoint chính |
| --- | --- |
| Dashboard | `/api/users/me/learning/dashboard/summary`, `/courses`, `/courses/{id}/resume`, `/choices` |
| Hồ sơ | `/api/users/me/assessments`, `/assessments/history`, `/assessment-statistics`, `/awards` |
| Quản trị | `/api/admin/question-bank/page`, `/users/page`, các endpoint `/rbac/.../page`, `/enrollment-requests/page`, `/content-reports/page` |
| Báo cáo | `/api/admin/reports/training/read`, `/attempts`, `/export` |
| Chat | `/api/rag/chat/history/page` |

Danh sách tuần tự và lịch sử dùng cursor với thứ tự ổn định, tải thêm một dòng để xác định còn dữ liệu. Các bảng cần nhảy số trang giữ phân trang OFFSET có giới hạn tại DB; không khẳng định đã loại bỏ mọi OFFSET. Thay bộ lọc sẽ đặt lại vị trí và bỏ phản hồi cũ. Dữ liệu thay đổi giữa các request có thể làm thứ hạng/trang dịch chuyển; đây không phải snapshot cố định.

Không cần migration schema. Chưa bổ sung index suy đoán khi chưa có EXPLAIN trên dữ liệu đại diện. Cần phát hành FE và BE cùng đợt, khởi động lại BE; không cần bật chế độ mock.

## Xác minh

- `npm run build`: đạt; còn cảnh báo chunk lớn của bundler.
- `node --experimental-strip-types --test tests/*.test.mjs`: 158 đạt, không lỗi.
- Bộ truy vấn/HTTP/phân quyền tập trung trên PostgreSQL 15: 35 đạt, không lỗi; gồm 11 tình huống báo cáo, 6 dashboard, phân trang huy hiệu, lịch sử và kiểm tra phạm vi truy cập.
- `git diff --check`: đạt. Đối chiếu các file loại trừ nêu trên: không có thay đổi.
- Bộ backend mặc định: 250 test, 178 đạt, 71 bỏ qua theo điều kiện PostgreSQL, 1 thất bại đã tồn tại trên HEAD gốc, không có lỗi thực thi. Kiểm thử PostgreSQL là opt-in và được chạy riêng; H2 không thay thế bằng chứng thực thi SQL PostgreSQL.

Chạy FE trong `CoreTraining`. Chạy BE trong `CoreTrainingAPI` với Java 17:

```sh
mvn -o -q -DargLine=-javaagent:$HOME/.m2/repository/net/bytebuddy/byte-buddy-agent/1.14.17/byte-buddy-agent-1.14.17.jar test
```

Để chạy SQL PostgreSQL, dùng database kiểm thử **riêng, có thể xóa** (test tạo/xóa schema), thêm các thuộc tính sau và chọn các lớp query/read test trong kế hoạch:

```text
-Dspring.config.location=classpath:access-test.yml
-Dspring.test.database.replace=NONE
-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55439/cbs_query_tests
-Dspring.datasource.username=<test-user>
-Dspring.datasource.password=<test-password>
-Dspring.datasource.driver-class-name=org.postgresql.Driver
-Dcbs.postgres.tests=true
-Dtest=TrainingReportQueryTest,LearnerDashboardQueryTest,ProfileAssessmentQueryTest,AwardsReadQueryTest,LeaderboardReadServiceTest,AdminListQueryTest,AdminOptionsLoadingTest,LearnerQuizEntryTest,RagHistoryRepositoryTest,ReportReadOperationTest,DataReadEndpointsIntegrationTest#reportEndpointsUseOverviewPermissionAndRefuseLearners+learnerAndAdminPagesUseRegisteredScopedEndpoints
```

Khi dùng shell, đặt toàn bộ tham số `-Dtest=...` trong dấu nháy đơn. Một số test cần mở HTTP stub trên loopback; môi trường sandbox phải cho phép socket cục bộ.

## Vấn đề còn tồn tại và giới hạn

`AccessSecurityIntegrationTest.disabledUnitRevokesAccessAndForeignEnrollmentCannotOverrideScope`, dòng 265, mong HTTP 403 nhưng nhận 200 khi học viên được đăng ký tường minh vào khóa học khác phạm vi. Đã tái hiện bằng đúng test trên snapshot sạch của HEAD `17526f2`, không chứa các thay đổi tối ưu. Chưa sửa chính sách truy cập trong đợt này; đây là lỗi kiểm thử/phân quyền nền cần xử lý riêng.

Chưa có network trace trình duyệt thực, EXPLAIN/ANALYZE, kiểm thử tải hay benchmark production. Kết quả hiện tại chứng minh truy vấn/payload có giới hạn và các tình huống được kiểm thử, không chứng minh tỷ lệ tăng tốc cụ thể. Không chạy migration hoặc sửa dữ liệu ứng dụng.

Container PostgreSQL kiểm thử `cbs-performance-query-tests` đã được xóa sau xác minh. Database ứng dụng không bị tác động.
