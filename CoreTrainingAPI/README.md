# BIDV Core Training (WebCSR) - API Backend

Dự án này là hệ thống API Backend hoàn chỉnh phục vụ cho ứng dụng đào tạo nghiệp vụ **BIDV Core Training (WebCSR)**. Hệ thống được xây dựng bằng **Spring Boot 3.3.1 (Java 17)** kết hợp với **Spring Security (xác thực JWT)**, **Spring Data JPA (Hibernate)**, **PostgreSQL** (hoặc **H2 Database** làm phương án chạy thử nhanh), và **MapStruct**.

---

## 🛠 Yêu cầu hệ thống
* **Java**: JDK 17
* **Maven**: Phiên bản 3.6 trở lên
* **Docker / Docker Desktop** (Không bắt buộc, chỉ dùng nếu bạn muốn chạy PostgreSQL qua Docker)

---

## 🚀 Hướng dẫn khởi chạy dự án nhanh (Sử dụng H2 Database - Mặc định)

Không cần cài đặt cơ sở dữ liệu bên ngoài, ứng dụng sẽ chạy với cơ sở dữ liệu in-memory H2 và tự động khởi tạo dữ liệu mẫu nghiệp vụ bằng tiếng Việt.

### Bước 1: Clone/Mở thư mục dự án
Mở terminal tại thư mục `F:\CoreTrainingAPI`.

### Bước 2: Biên dịch dự án
Chạy lệnh Maven để tải các thư viện và biên dịch mã nguồn:
```bash
mvn clean compile
```

### Bước 3: Khởi chạy ứng dụng
Chạy ứng dụng Spring Boot:
```bash
mvn spring-boot:run
```
Ứng dụng sẽ khởi động thành công trên cổng **`8080`**.

* **Địa chỉ console H2 để kiểm tra bảng biểu**: `http://localhost:8080/h2-console`
  - *JDBC URL*: `jdbc:h2:mem:coretraining`
  - *User Name*: `sa`
  - *Password*: (Để trống)

---

## 🐘 Hướng dẫn khởi chạy với PostgreSQL thực tế

Nếu bạn muốn chuyển sang lưu trữ dữ liệu thực tế trên PostgreSQL:

### Bước 1: Khởi động Docker Container PostgreSQL
Dự án đã cấu hình sẵn file `docker-compose.yml`. Khởi động database bằng lệnh:
```bash
docker-compose up -d
```
Lệnh này sẽ tạo một cơ sở dữ liệu PostgreSQL chạy ngầm tại cổng `5432`, tên DB: `coretraining`, user/pass: `postgres`/`postgres`.

### Bước 2: Chạy ứng dụng Spring Boot với Profile `postgres`
Chạy ứng dụng bằng cách chỉ định profile hoạt động là `postgres`:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

---

## 👤 Danh sách Tài khoản Khởi tạo Sẵn

Hệ thống tự động khởi tạo các tài khoản sau khi chạy lần đầu:

1. **Tài khoản Học viên (ROLE_USER)**:
   - **Tên đăng nhập**: `quantrh`
   - **Mật khẩu**: `123456`
   *(Tài khoản này dùng để học tập, thi trắc nghiệm và đồng bộ với giao diện Vue hiện tại)*

2. **Tài khoản Quản trị viên (ROLE_ADMIN)**:
   - **Tên đăng nhập**: `admin`
   - **Mật khẩu**: `admin123`
   *(Quyền Admin dùng để thực hiện thêm, sửa, xóa cấu hình bài học và câu hỏi thi trắc nghiệm qua các API `/api/admin/**`)*

---

## 📑 Tài liệu Đặc tả REST API
* Tài liệu đặc tả JSON Request/Response chi tiết nằm tại tệp [api_documentation.md](file:///C:/Users/bidvadmin/.gemini/antigravity/brain/904fb4b4-6b67-4233-b4b3-c8ed02976d3a/api_documentation.md).
* Các nhóm chức năng chính bao gồm:
  - **Xác thực**: Đăng ký, đăng nhập nhận JWT Token.
  - **Khóa học & Bài học**: Lấy danh sách chủ đề, bài học, viết bình luận bài viết.
  - **Thi thử**: Làm bài thi trắc nghiệm tính giờ theo phân hệ, chấm điểm và trao huy hiệu.
  - **Xếp hạng**: Bảng vinh danh học viên theo chi nhánh dựa trên điểm tích lũy (`PTS`).
  - **Quản lý (Admin CRUD)**: Đọc/Ghi/Cập nhật/Xóa bài học, chủ đề, và đề thi.
