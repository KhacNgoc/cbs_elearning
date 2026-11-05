# Thay đổi của nhánh `hieuvm_performance` so với `main`

Ngày lập: 01/11/2026. Trạng thái: đã push `origin/hieuvm_performance` (commit `b31c53a`), chưa tạo PR vào `main`. Nhánh đã merge `origin/main` mới nhất (`6b9b13a`), nên PR vào `main` sẽ không còn conflict.

## Tổng quan

| Commit | Nội dung | Ghi chú |
| --- | --- | --- |
| `c13ed55` | Tối ưu đọc dữ liệu quản trị và học viên | Chi tiết trong [data-performance-implementation.md](data-performance-implementation.md) |
| `7a5c1bd` | Sửa lỗi UI trang tạo bài kiểm tra | `AdminQuizDetail.vue`, `LessonKnowledgeSources.vue` |
| `1e24aed` | Sửa lỗi UI, sửa câu hỏi trong bài kiểm tra | Chuẩn hóa đáp án khi sửa câu hỏi |
| `20d5326` | Chuẩn hóa kiến trúc backend | Nội dung chính của tài liệu này |
| `b31c53a` | Merge `origin/main` vào nhánh | Xử lý conflict và đưa code mới của `main` về đúng kiến trúc |

Quy mô so với `main`: 317 file thay đổi.

| Khu vực | File | Dòng thêm / xóa |
| --- | --- | --- |
| Backend (`CoreTrainingAPI/src/main`) | 218 | +9 884 / −6 457 |
| Test backend (`CoreTrainingAPI/src/test`) | 34 | +1 035 / −42 |
| Frontend (`CoreTraining`) | 58 | +1 242 / −2 665 |
| Tài liệu (`docs`) | 7 | +488 |

## Tương thích khi phát hành

- **Không xóa endpoint nào.** Toàn bộ 154 endpoint của `main` vẫn giữ nguyên URL và HTTP method. Nhánh chỉ thêm 26 endpoint mới (xem [Endpoint mới](#endpoint-mới)).
- **Response JSON giữ nguyên tên key.** Các response trước đây trả `Map` nay trả record DTO có tên field trùng với key cũ, nên frontend không cần sửa. Các khác biệt nhỏ được liệt kê ở [Thay đổi hành vi](#thay-đổi-hành-vi-cần-lưu-ý).
- **Không có migration DB mới**, không đổi `pom.xml`, `package.json` hay `docker-compose`.
- **Phát hành FE và BE cùng đợt**, vì `c13ed55` đổi frontend sang dùng các API phân trang mới.

## Kiến trúc backend sau thay đổi

Code được chia theo domain trước, rồi đến tầng: `<domain>/controller`, `<domain>/service`, `<domain>/repository`, ... (ví dụ `course/service/impl`). Các phần dùng chung như `enums`, `mapper`, `policy`, `config`, `security` vẫn nằm ở gốc package.

Luồng chuẩn trong mỗi domain: `controller` → `service` (interface) → `service/impl` → `repository` (interface) → `repository/impl` → trả về `dto`.

| Tầng | Quy tắc |
| --- | --- |
| `controller` | Chỉ nhận request và gọi service. Không import `repository`/`entity`, không chứa logic nghiệp vụ hay kiểm tra quyền. Request/response dùng DTO, không dùng `Map`. |
| `service` | Chỉ chứa interface. |
| `service/impl` | Chứa class `*Impl` triển khai interface. Không gọi JDBC/`EntityManager` trực tiếp. |
| `repository` | Interface Spring Data hoặc interface truy vấn tự viết. |
| `repository/impl` | Truy vấn SQL/JDBC tự viết, trả về DTO hoặc record. |
| `dto` | Chỉ chứa dữ liệu cần trả ra, không trả nguyên entity. |

`LayerArchitectureTest` kiểm tra tự động các quy tắc trên mỗi lần chạy test:
- controller không truy cập tầng persistence;
- service không chạy thao tác DB;
- repository không phụ thuộc service hoặc controller;
- class service cụ thể phải nằm trong `<domain>/service/impl`.

Nhờ test này mà 3 vi phạm trong code mới của `main` đã bị phát hiện ngay khi merge.

### Package mới

| Package | Nội dung | Chuyển từ |
| --- | --- | --- |
| `enums` | `DocumentPurpose`, `DocumentStatus`, `DocumentType`, `EnrollmentStatus`, `NotificationType`, `QuestionType`, `VideoSource` | `entity` |
| `mapper` | `CourseMapper`, `QuizMapper`, `UserMapper`, `LessonOutlineMapper` | `dto` |
| `policy` | `LearningPolicy`, `LessonContentPolicy`, `QuestionValidation` | `service` |
| `learner/service/model` | `AuthorizedLesson` | `service` |
| `learner/repository/support` | `LearnerQueryScope`, `LearnerQueryScopeFactory`, `LearnerDashboardSqlRelations` | `repository`, `service/impl`, `repository/impl` |
| `lesson/repository/projection` | `LessonAccessView`, `LessonOutlineView` | `repository` |

Số class Impl tăng từ 7 (trên `main`) lên 47. Mỗi thư mục `<domain>/service/` chỉ chứa interface trực tiếp, class Impl nằm trong `<domain>/service/impl/`.

## Thay đổi backend chi tiết

### 1. Tách `AdminController` / `AdminService`

`AdminServiceImpl` (khoảng 780 dòng) gom nhiều nghiệp vụ khác nhau, nay tách theo nghiệp vụ. URL giữ nguyên `/api/admin/...`.

| Controller mới | Service mới | Phạm vi |
| --- | --- | --- |
| `AdminCourseContentController` | `CourseContentAdminService` | Khóa học, bài học, upload tài liệu/video, sắp xếp bài |
| `AdminQuizController` | `QuizAdminService` | Câu hỏi, cài đặt và xóa bài kiểm tra, sinh câu hỏi bằng AI |
| `AdminUserController` | `UserAdminService` | Người dùng, import/template Excel, nhóm |
| `AdminEnrollmentController` | `EnrollmentAdminService` | Duyệt yêu cầu ghi danh |

Phần điều phối từng nằm ở controller nay chuyển xuống service:
- upload rồi ghi nhận asset;
- kiểm tra quyền sửa khóa trước khi sinh câu hỏi bằng AI.

### 2. Không trả entity ra API

| Endpoint | Trước | Sau |
| --- | --- | --- |
| `GET /api/admin/groups` | Entity `UserGroup` (lộ cả `OrganizationUnit`) | `UserGroupDto(id, name, unitId, unitName)` |
| `GET /api/admin/rbac/pages` | Entity `AdminPage` | `RoleManagementDto.PageDto` (cùng field) |

`GET /api/auth/session` không còn tự dựng response từ entity trong controller. Phần này chuyển vào `AuthService.session()`, dùng chung hàm với login.

### 3. Response `Map` đổi sang DTO

| Service | DTO |
| --- | --- |
| `TrainingStructureService` | `CatalogDto`, `NextCodeDto`, `PagedResult<T>`, `UnitAllocationResultDto`, `UnitAllocationRemovalDto`, `DomainListItem`, `ProgramListItem`, `BatchAllocationResultDto`, `BatchSyncResultDto`, `UnitAllocationDirectoryDto`, `StaffCountDto` |
| `AdminOptionsService` | `AdminOptionsDto` |
| `ProfileAssessmentService` | `Stats`, `Award`, `LegacyScore`, `Competency` |
| `LearnerDashboardService` | `Choice`, `CourseChoice` |
| `LessonChangeService` | `LessonChangeDto.*` |
| Upload / CSRF | `UploadResultDto.Video`, `UploadResultDto.Image`, `CsrfTokenDto` |

`PagedResult<T>` là record phân trang dùng chung, gồm `items`, `page`, `size`, `totalElements`, `totalPages`, `hasNext`.

Endpoint `GET /admin/programs/{id}/courses` trước đây trả `Object` (khi trả trang, khi trả danh sách). Nay tách thành 2 method theo việc có hay không tham số `page`; URL giữ nguyên.

### 4. Request dùng DTO

| Endpoint | DTO |
| --- | --- |
| `PATCH /api/users/me/profile` | `UpdateProfileRequest` |
| `POST /api/users/me/change-password` | `ChangePasswordRequest` |
| `PUT /api/admin/content-reports/{id}` | `LessonReportReviewRequest` |
| `POST /api/admin/question-bank/add-to-quiz` | `QuestionBankAddRequest` |
| `POST /api/admin/courses/{id}/status` | `TrainingStructureDto.CourseStatusRequest` |
| Phân bổ đơn vị (assign, toggle, batch) | Record trong `TrainingStructureDto` |
| Change-log bài học | `LessonChangeDto.AiSummaryRequest`, `ChangeNotifyRequest` |

Phần kiểm tra mật khẩu mới và cấp lại JWT sau khi đổi mật khẩu chuyển từ `UserController` xuống `UserService` và `AuthService.reissueToken()`.

### 5. Truy vấn thay cho `findAll()` lọc trong bộ nhớ

| Vị trí | Thay bằng |
| --- | --- |
| Chương trình theo lĩnh vực (4 chỗ) | `findByDomainId` |
| Khóa học theo chương trình | `findByProgramIdAndStatusNot` |
| Kiểm tra lịch sử học trước khi xóa nội dung | `anyCompletedLessonIn` / `anyQuizScoreForLessonIn`. Trước đây đoạn này tải **toàn bộ user** kèm collection của từng người. |
| Hạn hoàn thành của học viên | `findByStatus("PUBLISHED")` |

Các `findAll()` có điều kiện lọc theo `AccessPolicy` vẫn chạy trong bộ nhớ (xem [Việc còn lại](#việc-còn-lại)).

### 6. Constructor injection

Toàn bộ field `@Autowired` trong controller, service và security được chuyển sang constructor với field `final`. Từ Spring Boot 3, phụ thuộc vòng mặc định bị cấm, và ứng dụng vẫn khởi động bình thường sau thay đổi, chứng tỏ không có vòng phụ thuộc nào.

## Merge với `main` (`b31c53a`)

| File conflict | Cách xử lý |
| --- | --- |
| `TrainingStructureService` | Ở điểm tách nhánh đây là class; nhánh đã tách thành interface + Impl, còn `main` vẫn thêm khoảng 770 dòng vào class cũ. Logic mới của `main` được áp vào `TrainingStructureServiceImpl` bằng merge 3 chiều. Interface có thêm 5 method mới. |
| `TrainingStructureController` | Giữ 3 endpoint mới của `main`; record request chuyển vào DTO. |
| `CourseRepository` | Giữ cả hai method mới của hai bên. |
| `UnitAllocationAdmin.vue` | Lấy bản của `main`, áp lại thay đổi font của nhánh. |

Ba chỗ code mới của `main` vi phạm kiến trúc và đã được sửa:
- Native SQL trong service chuyển sang `UnitAllocationNativeRepository` và `NotificationNativeRepository`. Câu SQL giữ nguyên văn.
- `LessonChangeService` tách thành interface + `LessonChangeServiceImpl`. `LessonChangeController` không còn gọi repository.
- Các response `Map` mới đổi sang DTO, giữ key JSON.

## Endpoint mới

| Nhóm | Endpoint |
| --- | --- |
| Dashboard học viên | `GET /api/users/me/learning/dashboard/summary`, `/courses`, `/courses/{id}/resume`, `/choices`; `GET /api/users/me/learning/quiz-context` |
| Hồ sơ | `GET /api/users/me/assessments`, `/assessments/history`, `/assessment-statistics`, `/awards` |
| Quản trị (phân trang) | `GET /api/admin/users/page`, `/access/groups/page`, `/access/groups/course-options`, `/question-bank/page`, `/enrollment-requests/page`, `/content-reports/page`, `/content-reports/{id}` |
| Vai trò | `GET /api/admin/rbac/roles/page`, `/roles/member-options/page`, `/role-options/page`, `/role-options/item/{id}`; `PUT /api/admin/rbac/roles/{id}/member-changes` |
| Báo cáo | `GET /api/admin/reports/training/read`, `/attempts`, `/export` |
| Khác | `GET /api/leaderboard/page`, `GET /api/rag/chat/history/page` |

## Thay đổi hành vi cần lưu ý

| Chỗ | Trước | Sau |
| --- | --- | --- |
| `PATCH /api/users/me/profile` | Báo lỗi nếu body có key lạ | Key lạ bị bỏ qua. DTO chỉ có 3 field (`fullName`, `email`, `avatarColor`), nên vẫn không sửa được field khác. |
| `GET /admin/training/next-code` khi thiếu module | `{code, seq}` | Thêm `"prefix": null` |
| `POST /admin/unit-allocations/batch-sync` khi không có thay đổi | `{status, message}` | Thêm các field đếm mang giá trị `null` |
| `DELETE /api/lessons/{id}/comments/{id}` | Controller tự tính `isAdmin` nhưng service không dùng | Bỏ tham số thừa; quyền vẫn kiểm tra bằng `AccessPolicy.canEdit` |
| Học viên xem khóa ngoài đơn vị | — | Đăng ký khóa được phép vượt phạm vi đơn vị. Quy tắc này đến từ commit `a456339` của `main`; test `AccessSecurityIntegrationTest` đã được cập nhật theo đó. |
| Nhãn nút gạt vai trò | "Là học viên" | "Role học viên" (thay đổi từ `main`; test FE đã cập nhật) |

## Kiểm thử

| Bộ test | Kết quả |
| --- | --- |
| Backend `mvn test` | 269 chạy, 0 failure, 0 error, 71 skip. Các test bị skip chỉ chạy khi có `-Dcbs.postgres.tests=true` và cần PostgreSQL; lần này chưa chạy. |
| `LayerArchitectureTest` | Đạt |
| Frontend `vue-tsc -b` | Không lỗi |
| Frontend `node --experimental-strip-types --test tests/*.test.mjs` | 159/159 đạt |

Sửa cấu hình test: 3 test (`AdminListQueryTest`, `AdminQuestionUpdateTest`, `RagHistoryRepositoryTest`) trước đây không khởi động được vì có nhiều class `@SpringBootConfiguration` trong package. Nay đã chỉ định rõ `@ContextConfiguration`.

Cách build backend:
- Dùng **JDK 17**. JDK 25 lỗi với Lombok.
- Sau khi kéo nhánh về, xóa `target/generated-sources` một lần, vì các mapper MapStruct đã đổi package.

```bash
cd CoreTrainingAPI && rm -rf target && JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn test
```

## Gợi ý khi review / kiểm tra thủ công

- Màn **Phân bổ đơn vị** có thay đổi lớn từ cả hai phía và chưa được kiểm tra thủ công trên trình duyệt.
- Các màn admin dùng API vừa đổi sang DTO: danh mục đào tạo, phân trang lĩnh vực/chương trình, options quản trị, hồ sơ học viên, dashboard.
- Luồng đổi mật khẩu (cấp lại cookie JWT), sửa hồ sơ, upload tài liệu/video, sinh câu hỏi AI.

## Việc còn lại

- `TrainingReportReadService` vẫn trả `Map`. Đây là báo cáo SQL động, cột thay đổi theo view, nên được giữ có chủ đích.
- Các `findAll()` lọc theo `AccessPolicy` (`editableCourses`, `getAllCourses`, thông báo cho admin…) vẫn lọc trong bộ nhớ. Muốn đẩy xuống SQL phải viết lại logic phân quyền thành câu truy vấn.
- `LessonKnowledgeSourceService.generationSources` vẫn nhận entity và trả `List<Map>`. Đây là API nội bộ gửi sang dịch vụ RAG.
- `NavigationController` vẫn truyền entity `User` (lấy từ principal) vào service.
- Còn tên class đầy đủ (FQN) ở các file không nằm trong phạm vi thay đổi.
