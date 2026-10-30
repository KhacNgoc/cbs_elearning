# Hệ thống Đào tạo Nội bộ & Đánh giá Năng lực (BIDV Core Training)

Chào mừng bạn đến với dự án **BIDV Core Training** - Hệ thống đào tạo trực tuyến dành cho onboard nhân sự mới và thi đánh giá năng lực các phân hệ nghiệp vụ ngân hàng trên hệ thống Core Banking (WebCSR).

Dự án được xây dựng bằng **Vue 3**, **TypeScript**, **Vite** và **Pinia Store** với giao diện tùy biến hiện đại (nhận diện thương hiệu BIDV) và cơ chế gamification hấp dẫn.

---

## ✨ Các Tính năng Chính

1.  **Trang Tổng quan Học tập (Dashboard):**
    *   Theo dõi tiến độ học tập tổng thể và chi tiết theo từng phân hệ.
    *   **Biểu đồ Radar Năng lực (Competency Chart):** Hiển thị trực quan điểm mạnh/yếu của học viên dựa trên điểm thi thực tế.
    *   **Hộp Huy hiệu (Badge Showcase):** Trưng bày các huy hiệu đạt được trực tiếp từ màn hình chính.
2.  **Chi tiết Bài học đa phương tiện (Lesson Detail):**
    *   **Tab Tài liệu:** Trình xem slide bài giảng bài bản.
    *   **Tab Video Hướng Dẫn:** Trình phát video bài học thực tế trực quan.
    *   **Tab Quy trình:** Hiển thị từng bước thao tác chi tiết theo vai trò Maker (Cán bộ QTTD) / Checker (Kiểm soát viên).
    *   **Thảo luận & Góp ý:** Cho phép học viên gửi đánh giá sao (1-5★) và bình luận đóng góp ý kiến thời gian thực.
3.  **Hệ thống Thi thử trắc nghiệm (Quiz Module):**
    *   Làm bài thi trắc nghiệm 5 câu ngẫu nhiên theo phân hệ chuyên môn, có bộ đếm thời gian.
    *   Ẩn đáp án đúng ở client chống gian lận (chỉ gửi về sau khi nộp bài thi thành công).
    *   Xem lại chi tiết bài làm (Review Answers) chỉ rõ câu đúng/sai và đáp án đúng.
    *   **Mở khóa Huy hiệu (Gamification):**
        *   Đạt **100%** câu trả lời đúng (5/5): Nhận **Huy hiệu Vàng** (+300 PTS).
        *   Đạt trên **90%** câu trả lời đúng: Nhận **Huy hiệu Bạc** (+200 PTS).
        *   Đạt từ **80% trở lên** (4/5): Nhận **Huy hiệu Đồng** (+100 PTS).
4.  **Bảng vinh danh Chi nhánh (Leaderboard):**
    *   Bảng xếp hạng thi đua động giữa các nhân sự dựa trên điểm tích lũy (PTS).
    *   Tủ trưng bày tất cả các Huy hiệu nghiệp vụ đã nhận và quy tắc mở khóa chi tiết.

---

## 🛠️ Công nghệ Sử dụng

*   **Core:** Vue 3 (Composition API với `<script setup lang="ts">`)
*   **State Management:** Pinia (quản lý trạng thái tập trung)
*   **Routing:** Vue Router (định tuyến SPA và Navigation Guards bảo vệ đăng nhập)
*   **HTTP Client:** Axios (giao tiếp REST API, tự động đính kèm Token JWT)
*   **Charts:** Chart.js & vue-chartjs (biểu đồ Radar năng lực)
*   **Icons:** Lucide Icons (`lucide-vue-next`)
*   **Styling:** Vanilla CSS (Emerald Green & Gold theme, glassmorphism, responsive)

---

## 📁 Cấu trúc Thư mục Nguồn (`/src`)

```
/src
  ├── api/             # Tầng Axios Client & gọi REST API từ Spring Boot
  ├── assets/          # Thư viện ảnh, CSS design system toàn cục
  ├── components/      # Các UI Component dùng chung (RadarChart, SlideViewer, VideoPlayer...)
  ├── data/            # Dữ liệu tham chiếu lịch sử; không dùng trong luồng API
  ├── router/          # Cấu hình định tuyến Vue Router & Security Guard
  ├── stores/          # Quản lý trạng thái Pinia (userStore, courseStore, quizStore)
  ├── types/           # Khai báo kiểu dữ liệu TypeScript (Lesson, Topic, Question, Badge...)
  ├── views/           # Các trang giao diện (Dashboard, LessonDetail, QuizView, Leaderboard, Login)
  ├── App.vue          # Khung layout chính và thanh Navbar điều hướng
  └── main.ts          # Điểm khởi chạy ứng dụng
```

---

## 🚀 Hướng dẫn Cài đặt & Chạy Dự án

### 1. Cài đặt các thư viện phụ thuộc (Dependencies)
Mở terminal tại thư mục gốc của dự án và chạy:
```bash
npm install
```

### 2. Cấu hình biến môi trường kết nối API
Mở file cấu hình môi trường [.env](file:///f:/CoreTraining/.env) hoặc [.env.development](file:///f:/CoreTraining/.env.development):

Frontend chỉ dùng API thật và dữ liệu từ database. Không còn chế độ mock/offline; cấu hình `VITE_USE_MOCK_API` cũ không có tác dụng.

```ini
VITE_API_URL=http://localhost:8000
VITE_RAG_CHAT_TIMEOUT_MS=330000
```

`VITE_RAG_CHAT_TIMEOUT_MS` chỉ áp dụng cho `POST /api/rag/chat`. Các API thông
thường vẫn dùng timeout 15 giây. Giá trị mặc định 330 giây dành một khoảng đệm
30 giây so với timeout 300 giây giữa Spring Boot và RAG service.

### 3. Chạy ứng dụng trên môi trường Phát triển (Local Dev)
Khởi động dev server:
```bash
npm run dev
```
Truy cập ứng dụng theo địa chỉ hiển thị trong terminal (thường là `http://localhost:5173`).

*   **Tài khoản đăng nhập mẫu:** `quantrh`
*   **Mật khẩu mẫu:** Mật khẩu tài khoản đã được tạo trên DB.

### 4. Đóng gói mã nguồn (Production Build)
Biên dịch dự án và tối ưu hóa code để sẵn sàng đưa lên hosting/môi trường thật:
```bash
npm run build
```
Các file tĩnh được tạo ra tại thư mục `/dist`.

---

## ⚠️ Lưu ý Quan trọng đối với Lập trình viên Backend

Để tính năng **Xem chi tiết bài làm (Review Answers)** của học viên hiển thị chính xác sau khi nộp bài thi trắc nghiệm, vui lòng đảm bảo API chấm điểm:
*   **Endpoint:** `POST /api/quizzes/{topicId}/submit`
*   **Payload phản hồi (Response):** Trả về bản đồ đáp án đúng `correctAnswers` chứa ánh xạ giữa ID câu hỏi và vị trí đáp án đúng, ví dụ:
    ```json
    {
      "score": 4,
      "total": 5,
      "percent": 80,
      "correctAnswers": {
        "1": 0,
        "2": 1,
        "3": 0,
        "4": 1,
        "5": 2
      }
    }
    ```
