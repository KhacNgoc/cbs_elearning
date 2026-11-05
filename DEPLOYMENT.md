# Kiểm thử và triển khai video

## Chạy trên máy hiện tại

Không cần tên miền hoặc máy ảo. Từ thư mục gốc:

```sh
docker compose --env-file .env.production up -d --build
docker compose --env-file .env.production ps -a
```

Mặc định chỉ chạy ứng dụng chính và database. Để chạy thêm chatbot và các
chức năng AI, bật profile `chatbot`:

```sh
docker compose --env-file .env.production --profile chatbot up -d --build
```

Mở `http://localhost:5173`. `VITE_API_URL` để trống trong `.env.production`
để trình duyệt gọi `/api/` trên cùng địa chỉ frontend. Nginx chuyển API đến
backend và trả video sau khi backend xác minh quyền. Backend chỉ mở cổng 8000
trên loopback để debug; người dùng đi qua frontend.

Cookie Secure vẫn tắt ở cấu hình cơ sở để hỗ trợ HTTP trên máy phát triển.
Không áp dụng file override production khi thử bằng HTTP.

**Máy có GPU NVIDIA:** thêm `-f docker-compose.yml -f docker-compose.gpu.yml` vào lệnh để Ollama trong
Docker dùng GPU. Không có file này thì Ollama chạy bằng CPU. Xem mục "Ollama GPU on NVIDIA" trong
`rag-service/README.md`.

**Máy đã có dữ liệu chatbot từ trước khi đổi sang `bge-m3`:** phải tạo collection mới trước khi
khởi động lại `rag-service`, nếu không chatbot sẽ không tìm thấy tài liệu nào. Xem mục "Changing the
embedding model" trong `rag-service/README.md`.

Khi bật profile chatbot, lần đầu Ollama tải model có thể mất lâu. Database mới không có dữ liệu mẫu
vì `DATA_INITIALIZER_ENABLED=false`; cần tài khoản và bài học hợp lệ để thử
luồng người học. Kiểm thử tự động dùng dữ liệu riêng, không sửa database thật.

## Luồng và giới hạn

- Admin tải video, backend xác minh nội dung bằng `ffprobe` rồi lưu tên UUID
  trong volume `training_uploads`. Image backend có cài công cụ này. Khi chạy
  Java trực tiếp trên máy, cần cài `ffprobe` trong PATH.
- Không tự chuyển đổi video khi upload. Video không đúng codec được từ chối
  với thông báo thay vì lưu file không phát được. Chuẩn khuyến nghị là MP4
  H.264/AAC; xem thông báo upload để biết các định dạng khác được hỗ trợ.
- Giới hạn file mặc định là 500 MiB; multipart request và Nginx có giới hạn
  510 MiB để chứa metadata. Nếu đổi giới hạn, cập nhật `MAX_UPLOAD_SIZE`,
  `MAX_UPLOAD_REQUEST_SIZE`, `VITE_VIDEO_MAX_UPLOAD_BYTES` và cấu hình Nginx.
- Thời gian chờ upload mặc định 30 phút, có tiến độ trên frontend. Thay đổi
  `VITE_VIDEO_UPLOAD_TIMEOUT_MS` cần build lại frontend.
- URL `/uploads/videos/...` và đường Nginx nội bộ không truy cập trực tiếp
  được. Endpoint stream kiểm tra token, bài học hiện tại và quyền người học.
- Player gia hạn token và giữ vị trí phát. Khi mất quyền hoặc file bị thay
  thế, hiển thị lỗi thay vì lặp vô hạn. Video đã upload trước thay đổi này
  cần kiểm tra codec lại; thay đổi không tự chuyển đổi file cũ.
- Volume được giữ qua việc tạo lại container. Trước khi dùng thật cần có
  sao lưu volume và database. Nếu chạy trên nhiều máy, thay volume cục bộ
  bằng kho dùng chung để backend và Nginx đều đọc được cùng file.

## Build và kiểm tra video

Frontend từ thư mục gốc:

```sh
npm --prefix CoreTraining run build
```

Backend cần JDK 17:

```sh
cd CoreTrainingAPI
mvn package
```

Kiểm thử thủ công với video thật trên Chrome, Firefox và Safari nếu dùng:

1. Admin tải video MP4 H.264/AAC, xem tiến độ và lưu bài học.
2. Người có quyền mở bài, phát, tạm dừng, tua và tải lại trang.
3. Trong Network của trình duyệt, request stream khi tua phải trả `206`
   và `Content-Range`. Không tải toàn bộ file mỗi lần tua.
4. Người không có quyền không lấy được token; URL file trực tiếp bị từ chối.
5. Đặt `VIDEO_ACCESS_DURATION_SECONDS=30` rồi tạo lại backend để thử gia hạn
   nhanh. Xem/tạm dừng lâu hơn 30 giây rồi tiếp tục và tua; sau đó khôi phục
   giá trị thường dùng (3600 giây).
6. Upload file quá lớn hoặc đổi tên file văn bản thành `.mp4` phải thất bại.
7. Thử mạng upload chậm và video dài; kiểm tra giữ nguyên vị trí sau gia hạn.

Kiểm thử tự động không thay thế kiểm thử codec trên trình duyệt hoặc đo tải
đồng thời. Băng thông cần thiết phụ thuộc bitrate video và số người xem.

## Khi có máy chủ và HTTPS

Docker Compose cần hỗ trợ `!override` (2.24.4 trở lên). Dùng:

```sh
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.production.yml up -d --build
```

Override bật cookie Secure, hỗ trợ forwarded headers cho backend, bỏ hoàn
toàn port mapping của PostgreSQL và không chạy pgAdmin mặc định (profile
`db-tools`). Backend vẫn kết nối database qua `postgres:5432` trong mạng
Docker; giữ `SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/coretraining`.
Không bật profile `db-tools` hoặc `--profile '*'` trên production. pgAdmin
cũng không có port mapping trong cấu hình production.

Nếu pgAdmin đã chạy từ cấu hình cũ, dừng và xóa container trước khi áp dụng
override (các lệnh này giữ nguyên volume dữ liệu):

```sh
docker compose --env-file .env.production -f docker-compose.yml stop pgadmin
docker compose --env-file .env.production -f docker-compose.yml rm -f pgadmin
docker compose --env-file .env.production \
  -f docker-compose.yml -f docker-compose.production.yml up -d --build
```

Kiểm tra bằng `docker ps --format 'table {{.Names}}\t{{.Ports}}'`: PostgreSQL
không có mapping dạng `5432->5432`, và không có container
`coretraining-db-ide` đang chạy. Docker có thể vẫn hiển thị `5432/tcp`, đây
là cổng bên trong container, không phải cổng publish ra host.

Các cổng frontend và dịch vụ AI trong override chỉ bind loopback.
Đặt reverse proxy HTTPS trên cùng máy trỏ
đến `http://127.0.0.1:5173`; API và video cùng tên miền với frontend.
Proxy ngoài cần ghi đè Host và X-Forwarded-Proto, giữ nguyên Range, hỗ trợ
upload 510 MiB và thời gian chờ upload 30 phút. Ví dụ bên trong server HTTPS:

```nginx
client_max_body_size 510m;
client_body_timeout 1800s;
location / {
    proxy_pass http://127.0.0.1:5173;
    proxy_set_header Host $http_host;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_http_version 1.1;
    proxy_request_buffering off;
    proxy_read_timeout 1800s;
    proxy_send_timeout 1800s;
}
```

Thêm cấu hình chứng chỉ và websocket `/ws/` vào proxy ngoài nếu dùng tính
năng thời gian thực. Tránh log query string của URL stream vì có token;
Nginx frontend đã bỏ query string trong access log. Thay các secret mẫu
trước khi triển khai. Khi giữ API cùng origin, không cần mở CORS cho nhiều
domain; nếu tách domain cần cấu hình lại origin/cookie và kiểm thử riêng.

## Migration database (Flyway)

Profile `postgres` dùng Flyway: khi backend khởi động, các script còn thiếu trong `CoreTrainingAPI/src/main/resources/db/migration/` tự chạy theo thứ tự phiên bản, lịch sử nằm trong bảng `flyway_schema_history`. Sau đó Hibernate chỉ kiểm tra schema khớp với entity (`ddl-auto=validate`), không tự sửa. Thiếu bảng hoặc cột thì backend dừng và báo lỗi.

Thêm thay đổi schema:

1. Tạo file mới `V<yyyyMMdd>_<số thứ tự>__<mô tả>.sql`, phiên bản cao hơn mọi script đã có, không ghi `BEGIN`/`COMMIT`.
2. Thêm giá trị vào enum lưu trong database cũng cần script, vì check constraint không còn do Hibernate cập nhật.
3. Không sửa script đã chạy ở bất kỳ môi trường nào: Flyway so checksum và sẽ không cho backend khởi động. Muốn đổi thì thêm script mới.

Thư mục `migrations/` chỉ còn là lịch sử, Flyway không đọc. Riêng `20261105_timestamps_to_vietnam_time.sql` có điều kiện nên vẫn chạy tay theo mục Múi giờ backend.

### Lần đầu chuyển sang Flyway ở môi trường đang có dữ liệu

Deploy này **tự động** chạy các script `V20261102_1` đến `V20261105_1`. Chúng chạy lại an toàn nếu môi trường đã chạy trước đó, nhưng `V20261104_3` xóa lịch sử ôn flashcard cũ, còn `V20261105_1` xóa vĩnh viễn nhóm học viên: học viên mất quyền học các khóa chỉ được giao qua nhóm (lịch sử học vẫn giữ; quyền học trở lại nếu khóa được giao theo đơn vị hoặc học viên đăng ký).

1. Sao lưu database (`pg_dump -Fc`).
2. Nếu cần lưu danh sách học viên bị ảnh hưởng, truy vấn `user_group_memberships` và `group_mandatory_courses` trước khi deploy.
3. Nếu môi trường từng chạy UTC, dừng backend và chạy script múi giờ trước (xem mục Múi giờ backend).
4. Deploy như bình thường. Log cần có `Successfully baselined schema with version: 1` và `Successfully applied ... migrations`.

Flyway đánh dấu database cũ là đã ở `V1` mà không kiểm tra schema thật. Nếu backend dừng với lỗi `Schema-validation: missing ...`, schema môi trường đó đang lệch so với `V1`:

- Cách đúng: thêm script `V...` bổ sung phần thiếu rồi deploy lại.
- Cách tạm: đặt `JPA_DDL_AUTO=update` trong file env để Hibernate tự thêm phần thiếu, khởi động một lần, rồi bỏ biến này.

Script lỗi giữa chừng thì transaction của script đó được hoàn tác và backend không khởi động. Bản miễn phí của Flyway không có lệnh undo: muốn quay lại phiên bản cũ thì khôi phục từ bản sao lưu.

## Múi giờ backend

Backend cố định múi giờ `Asia/Ho_Chi_Minh` khi khởi động. Các cột thời gian không có múi giờ lưu giờ Việt Nam, riêng `notifications.created_at` lưu UTC.

Khi lần đầu triển khai thay đổi này trên môi trường có backend từng chạy UTC:

1. Sao lưu cơ sở dữ liệu và dừng backend.
2. Chạy `CoreTrainingAPI/src/main/resources/migrations/20261105_timestamps_to_vietnam_time.sql` (chạy lại không có tác dụng).
3. Khởi động phiên bản backend mới.

Không chạy script này trên môi trường có backend vốn đã chạy giờ Việt Nam.
