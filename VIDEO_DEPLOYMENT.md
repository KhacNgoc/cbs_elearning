# Kiểm thử và triển khai video

## Chạy trên máy hiện tại

Không cần tên miền hoặc máy ảo. Từ thư mục gốc:

```sh
docker compose --env-file .env.production up -d --build
docker compose --env-file .env.production ps -a
```

Mở `http://localhost:5173`. `VITE_API_URL` để trống trong `.env.production`
để trình duyệt gọi `/api/` trên cùng địa chỉ frontend. Nginx chuyển API đến
backend và trả video sau khi backend xác minh quyền. Backend chỉ mở cổng 8000
trên loopback để debug; người dùng đi qua frontend.

Cookie Secure vẫn tắt ở cấu hình cơ sở để hỗ trợ HTTP trên máy phát triển.
Không áp dụng file override production khi thử bằng HTTP.

Lần đầu Ollama tải model có thể mất lâu. Database mới không có dữ liệu mẫu
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

## Kiểm thử tự động

Frontend từ thư mục gốc:

```sh
npm --prefix CoreTraining run test:video
npm --prefix CoreTraining run build
```

Backend cần JDK 17:

```sh
cd CoreTrainingAPI
mvn test
```

Kiểm thử proxy từ thư mục gốc, sau khi có image frontend và rag-service:

```sh
python3 scripts/test-video-proxy.py
```

Kiểm thử proxy dùng container/network/file tạm riêng và dọn sau khi chạy.
Nó kiểm tra Range/206, chặn truy cập trực tiếp, lỗi xác thực, forwarding và
upload qua Nginx. Backend trong bài kiểm thử proxy là server giả lập;
quyền học và JWT được kiểm chứng riêng trong bộ test backend.

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

Override bật cookie Secure, hỗ trợ forwarded headers cho backend và giới
hạn cổng dịch vụ vào loopback. Đặt reverse proxy HTTPS trên cùng máy trỏ
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
