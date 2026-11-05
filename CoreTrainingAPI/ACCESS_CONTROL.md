# Phân quyền P0–P2

## Vai trò và phạm vi

- `ROLE_USER`: học viên. Thuộc tính `learnerType`: `NEW_STAFF`, `TRANSFERRED_STAFF`, `CURRENT_STAFF`, `EXTERNAL`.
- `ROLE_INSTRUCTOR`: giảng viên / cán bộ quản lý nội dung; chỉ được biên tập các khóa có tên trong danh sách phân công.
- `ROLE_UNIT_ADMIN`: quản trị viên đơn vị; quản lý người dùng, nhóm, khóa thuộc phạm vi được cấp và đơn vị con.
- `ROLE_REPORT_VIEWER`: lãnh đạo / người xem báo cáo; có quyền `REPORT_VIEW`, không có quyền quản trị hoặc ghi kết quả học. API và màn hình báo cáo được dành cho P3.
- `ROLE_ADMIN`: quản trị viên toàn hệ thống; giữ tương thích với tài khoản quản trị hiện có.

Một tài khoản có nhiều vai trò. `roles` là danh sách chính; trường `role` còn lại để tương thích. Với dữ liệu cũ chưa có `roles`, backend dùng `role` hiện có. Vai trò không có trong danh sách hợp lệ bị từ chối.

`unitId` là đơn vị công tác chính; `scopeUnitIds` là các gốc phạm vi quản lý/xem báo cáo; nếu trống, dùng đơn vị công tác. Mỗi gốc bao gồm đơn vị con. Không cấp vai trò toàn cục, đơn vị hoặc phạm vi vượt quyền của người thực hiện. Quản trị viên đơn vị không được sửa tài khoản quản trị toàn hệ thống hoặc tự mở rộng quyền/phạm vi.

## Loại role và phạm vi quản trị

Khi tạo/sửa role, bật **Role học viên** để tạo role chỉ tham gia học tập. Role này luôn có quyền `LEARN`, không có quyền quản trị chi tiết, không cấu hình đơn vị cho role. Danh mục học được xác định theo đơn vị công tác của tài khoản (và chương trình giao cho đơn vị cấp trên). Một role học viên có thể dùng cho tài khoản thuộc nhiều đơn vị.

Role học viên không có nút **Phân quyền** và không xuất hiện trong danh sách role của menu **Phân quyền**. Backend từ chối `PUT /api/admin/rbac/permissions/{id}` đối với loại này, đồng thời không sử dụng quyền quản trị/phạm vi đơn vị còn sót lại trong dữ liệu của role học viên.

Tắt **Role học viên** để cấu hình role quản trị; phần **Phạm vi quản trị dữ liệu** giới hạn dữ liệu cho các quyền quản trị đã cấp. Role quản trị vẫn có thể bật **Cho phép tham gia học tập** nếu cần. Chuyển sang role học viên sẽ xóa quyền quản trị và phạm vi đơn vị của chính role đó; form thông báo trước khi lưu, giữ nguyên danh sách tài khoản và các role khác của họ.

Trường API mới là `learnerRole`. Khi khởi động, `LearnerRoleMigration` phân loại một lần những role cũ chỉ có quyền học tập, không có quyền quản trị và không phải role hệ thống/phân công giảng viên; phạm vi đơn vị cũ không còn cần thiết của chúng được gỡ. Role quản trị và lựa chọn loại role đã lưu không bị tự phân loại lại. Với schema quản lý thủ công, chạy `src/main/resources/migrations/20261016_learner_roles.sql` trước khi triển khai; với `ddl-auto=update`, ứng dụng tạo cột và chuyển đổi khi khởi động.

`OPTIONAL` cho phép nhìn thấy và gửi yêu cầu đăng ký học; phải được duyệt trước khi học. `MANDATORY` giao học trực tiếp, áp dụng cả đơn vị con. Việc chia sẻ không cấp quyền biên tập. Muốn chia sẻ ra ngoài phạm vi quản trị đơn vị cần quản trị toàn hệ thống thực hiện. Những chia sẻ/phân công ngoài phạm vi đã được quản trị toàn hệ thống tạo phải được quản trị đơn vị giữ nguyên khi cập nhật.

## Giao diện

Vào các menu riêng **Tài khoản** (`/admin/users`), **Đơn vị / phòng** (`/admin/units`) và **Phân quyền** (`/admin/permissions`). Đường dẫn cũ `/admin/access` được chuyển tiếp để giữ tương thích:

1. Tạo cây đơn vị/phòng (quản trị toàn hệ thống). Cấm vòng lặp; có thể ngừng hoạt động, giữ lịch sử.
2. Tạo/sửa tài khoản: đơn vị, loại học viên, đa vai trò, phạm vi và trạng thái.
3. Chọn khóa, gán đơn vị sở hữu, giảng viên, các đơn vị nhận chia sẻ/giao học.

Quản trị viên đơn vị chỉ thấy dữ liệu trong phạm vi. Giảng viên dùng màn hình nội dung hiện có, chỉ thấy khóa phụ trách trong khu vực quản trị. Người dùng sửa tên/email trong hồ sơ cá nhân; API không cho sửa vai trò/đơn vị qua hồ sơ.

## Nâng cấp dữ liệu

Sao lưu database trước khi triển khai. Schema do Flyway quản lý (xem `DEPLOYMENT.md`). Nếu quản lý schema thủ công, chạy `src/main/resources/migrations/20261012_access_control.sql` trước khi khởi động bản mới. Đây là script vận hành, không được chạy tự động bằng Flyway. Không chạy lại `schema.sql` (đó là dump cấu trúc cũ).

JWT cũ không có trường phiên `ver` sẽ mất hiệu lực sau nâng cấp; người dùng cần đăng nhập lại. Dữ liệu vai trò cũ vẫn được dùng. Khóa chưa gán đơn vị chỉ hiển thị cho học viên chưa gán đơn vị và quản trị toàn hệ thống, giữ tương thích dữ liệu cũ nhưng không đưa khóa vào đơn vị mới một cách ngầm định.

Chuyển dữ liệu từng đợt bằng quản trị toàn hệ thống: tạo đơn vị → gán đơn vị sở hữu và chia sẻ khóa → gán đơn vị cho nhóm/tài khoản → giao vai trò quản trị đơn vị/giảng viên. Hoàn thành cả việc gán tài khoản và khóa trong cùng đợt để tránh khoảng thời gian học viên thiếu quyền. Lịch sử học được giữ nguyên; quyền học hiện tại vẫn được kiểm tra theo đơn vị hiện tại.

Tài liệu cũ đã gắn vào bài vẫn truy cập qua cùng URL, nhưng cần đăng nhập và quyền bài học. Tài liệu chưa gắn bài chỉ cho người tải lên (với quyền nội dung) hoặc quản trị toàn hệ thống xem. Không phục vụ `/uploads/documents/**` hoặc `/uploads/videos/**` bằng nginx/static volume công khai; đường dẫn tài liệu phải chuyển tiếp vào API.

## Phiên, mật khẩu và CSRF

- Tài khoản `ACTIVE`, `LOCKED`, `DISABLED`. Trạng thái và phiên được kiểm tra trên mỗi yêu cầu; đơn vị ngừng hoạt động cũng chặn phiên người dùng thuộc đơn vị đó.
- Đăng xuất thu hồi **toàn bộ phiên** của tài khoản. Đổi mật khẩu thu hồi phiên cũ và cấp cookie mới cho phiên hiện tại. Sửa tài khoản/quyền/trạng thái cũng thu hồi phiên cũ.
- Phiên websocket và quyền stream video kiểm tra lại trạng thái và phiên. Websocket cũ đóng khi kiểm tra định kỳ hoặc trước khi giao thông báo.
- Tài khoản bắt buộc đổi mật khẩu bị chặn API nghiệp vụ cho đến khi hoàn tất. Mật khẩu mới tối thiểu 8 ký tự.
- Yêu cầu ghi cần cookie `XSRF-TOKEN` và header `X-XSRF-TOKEN`. Lấy token qua `GET /api/auth/csrf`. Frontend tự làm việc này. Cookie đăng nhập vẫn HttpOnly; production bật `JWT_COOKIE_SECURE=true` và HTTPS.
- Tắt tự đăng ký công khai và đặt lại mật khẩu bằng thông tin nhân viên. Tài khoản được quản trị viên tạo hoặc nhập theo quy trình nội bộ.
- OTP dùng số ngẫu nhiên mật mã, lưu BCrypt, hết hạn sau 5 phút, tối đa 5 lần thử, chỉ dùng một lần; gửi lại cách nhau ít nhất 1 phút. Không có mã OTP cố định và không ghi OTP vào log.

Cấu hình email:

```env
SMTP_HOST=smtp.example.vn
SMTP_PORT=587
SMTP_USERNAME=...
SMTP_PASSWORD=...
SMTP_FROM=no-reply@example.vn
SMTP_AUTH=true
SMTP_STARTTLS=true
```

Khi chưa cấu hình SMTP hoặc gửi email thất bại, trả lỗi rõ ràng; không có luồng bỏ qua xác minh. Quản trị viên có thể đặt mật khẩu mới cho tài khoản trong phạm vi; người dùng phải đổi mật khẩu ở lần đăng nhập tiếp theo.

Import Excel cũ chưa có đơn vị trong file nên chỉ quản trị toàn hệ thống được dùng; cần gán đơn vị sau khi nhập. Mật khẩu nhập là ngẫu nhiên; người dùng dùng OTP qua email hoặc quản trị viên đặt mật khẩu ban đầu, không suy ra mật khẩu từ mã nhân viên. Các vai trò đang dùng phạm vi chung của tài khoản, chưa hỗ trợ gán cùng một vai trò với chính sách khác nhau theo từng đơn vị. Mỗi người có một đơn vị công tác và một nhóm đào tạo; phạm vi quản lý có thể gồm nhiều đơn vị.

## Kiểm thử

```sh
JAVA_HOME=/path/to/jdk17 mvn -f CoreTrainingAPI/pom.xml clean test
cd CoreTraining && npm run build
```

`AccessSecurityIntegrationTest` kiểm tra bằng JWT/cookie và API thật qua MockMvc trên H2: truy cập ngoài phạm vi, giả mạo vai trò/phạm vi, phân công giảng viên, tài liệu, CSRF, thu hồi phiên, OTP, nhóm/đơn vị con, đa vai trò. Chạy PostgreSQL staging trước khi triển khai production để xác nhận migration và proxy tải tài liệu. Không thay đổi dữ liệu production trong quá trình phát triển này.


## Phân quyền động (2026-10-12)

- `access_roles`, `access_role_permissions`, `access_role_units`, `user_access_roles` lưu role, các quyền và phạm vi riêng của từng role. Không có whitelist role cho tài khoản đã chuyển đổi.
- `admin_pages` + `admin_page_actions` lưu danh mục trang và các hành động API thực sự hỗ trợ. `navigation_items.permission_page` nối menu với quyền `page:read`; API dùng cùng mã trang/hành động. Quyền là dữ liệu động để gán/gỡ, nhưng một hành động mới phải được đăng ký và có triển khai kiểm tra API tương ứng.
- `/admin/roles` tạo/sửa role và gán nhiều tài khoản. `/admin/permissions` cấu hình ma trận quyền và cây đơn vị. Role hệ thống được bảo vệ; chỉ quản trị viên hệ thống gán thành viên cho role này, luôn giữ ít nhất một thành viên đang hoạt động.
- Tạo/sửa/xóa tự kèm quyền xem. Quyền chức năng và phạm vi dữ liệu phải được thoả mãn bởi **cùng một role**. Không ghép quyền sửa ở role A với đơn vị của role B. API trả `allowedActions`/`adminActions` cho từng bản ghi để UI không hiển thị thao tác ngoài phạm vi.
- Chọn đơn vị cha bao gồm hậu duệ đang hoạt động. Chương trình dùng bảng `training_program_units` liên kết nhiều đơn vị. Role có scope khớp một đơn vị của chương trình truy cập toàn bộ khóa thuộc chương trình trong giới hạn CRUD được cấp.
- Đơn vị công tác của tài khoản phục vụ cơ cấu, học tập và giới hạn gán thành viên; phạm vi quản trị mới lấy từ role. Role không có đơn vị không có phạm vi dữ liệu đào tạo. Tài khoản mới cần được gán role ở menu Quản lý role.
- JWT xác thực dùng `sub=userId`, `ver=tokenVersion`, `iat`, `exp`; không chứa role hay họ tên. JWT cũ chứa username sẽ hết khả năng xác thực sau khi cập nhật backend, người dùng cần đăng nhập lại.
- `PermissionCache` chỉ lưu policy bất biến, theo từng user, tối đa khoảng 10.000 mục, TTL 60 giây. Tài khoản/trạng thái/version vẫn đọc DB mỗi request. Role, membership, grants, đơn vị và tài khoản thay đổi sẽ xóa cache **sau commit**; generation ngăn tái sử dụng giá trị cache từ thế hệ cũ. Không cache entity JPA hoặc dữ liệu chương trình.
- Cache in-memory dành cho **một backend instance**. Nếu chạy nhiều instance phải bổ sung cơ chế invalidation chung; TTL không phải thay thế cho thu hồi quyền đồng bộ.
- Khởi động: JPA cập nhật schema theo cấu hình hiện có. `DynamicAccessInitializer` chạy sau các seeders, chuyển role cũ một lần trên từng tài khoản; tách quản trị viên theo chữ ký scope, giữ giảng viên theo khóa được phân công và giữ owner scope cho quản trị viên cũ. Lưu cấu hình grants mới chủ động chuyển role đó sang scope chương trình/đơn vị. Chương trình cũ lấy đơn vị từ owner của khóa; chương trình chưa có owner cần quản trị viên gán đơn vị.
- Tránh tự cấp quyền: người quản trị có phạm vi chỉ sửa/gán role nằm trong trần quyền và phạm vi của họ; không thể gán role cho chính mình, gán người ngoài phạm vi, thay đổi role được bảo vệ hoặc cấp permission/đơn vị vượt quyền. Trang Tài khoản không thể thay role hoặc scope qua payload cũ.

Kiểm tra: `mvn -f CoreTrainingAPI/pom.xml test` (Java 17), `npm --prefix CoreTraining run build`, `npm --prefix CoreTraining run test:organization-tree`.
