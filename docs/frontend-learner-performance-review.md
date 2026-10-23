# Rà soát hiệu năng giao diện học viên

Ngày: 22/10/2026.

Phạm vi: Đăng nhập/đổi mật khẩu, Tổng quan, Cấu trúc khóa học, Học bài, Kiểm tra, Bảng vinh danh, Hồ sơ và các thành phần dùng chung. Đây là rà soát mã nguồn và phân tích production bundle; chưa đo Lighthouse, Core Web Vitals, độ trễ API hay bộ nhớ trên thiết bị thật. Các tác động về thời gian phản hồi dưới đây là suy luận từ luồng xử lý, không phải số mili giây đã đo. Chưa sửa mã ứng dụng trong lần rà soát này.

## Cập nhật triển khai sau rà soát

### Lưu trang tài liệu (23/10/2026)

- Lưu snapshot tài liệu cũ trước khi đổi tài liệu/nội dung. Hàng đợi phân biệt từng tài liệu để các snapshot chờ của tài liệu A không bị tài liệu B thay thế; vẫn tuần tự hóa request để giữ thứ tự resume.
- Vị trí đọc theo trang: gom request trong cửa sổ 1 giây, không ghi lại vì cuộn thêm trong cùng trang; chọn lại nội dung có thể ghi touch để cập nhật resume. Vị trí khởi tạo viewer được tách khỏi progress đang cập nhật.
- Ghi journal localStorage đồng bộ khi đổi trang, theo tài khoản. Đóng trang/ẩn tab/rời bài cố gắng flush; mở lại bài thì khôi phục và gửi lại snapshot chưa được server xác nhận. Lỗi mạng giữ journal để thử lại; sự kiện online flush tài liệu hiện tại.
- Backend trả `documentPositions` theo id tài liệu; tương thích bookmark một tài liệu của dữ liệu cũ. Schema bổ sung cột JSON `lesson_progress.document_positions`; có migration SQL tùy chọn cho PostgreSQL không dùng Hibernate update.
- Crash không bảo đảm request mạng cuối hoàn tất. Phần chưa đồng bộ khôi phục trên cùng trình duyệt/tài khoản khi localStorage còn khả dụng; xóa dữ liệu trình duyệt hoặc storage bị chặn sẽ mất bản dự phòng. Độ chính xác lưu theo trang, không theo từng pixel cuộn.
- Kiểm chứng: 19 kiểm thử frontend learning performance và 8 kiểm thử `LearningQueryAuditTest` đạt; TypeScript/Vite production build đạt. Có kiểm thử đổi tài liệu, không ghi trùng khi cuộn cùng trang, hàng đợi giữ snapshot của cả hai tài liệu, khôi phục journal sau tạo lại phiên và đọc lại JSON backend gồm dữ liệu cũ. Chưa kiểm chứng crash trình duyệt thật hay migration trên PostgreSQL production.

Đã triển khai phần Học bài và Cấu trúc khóa học: tách tải ghi chú khỏi nội dung chính; dùng cache outline có giới hạn thời gian, theo tài khoản/quyền/tiến độ; lưu tiến độ nền có thứ tự và gộp snapshot chờ; lưu video theo chu kỳ và khi tạm dừng; bỏ prefetch outline bị lặp từ Tổng quan; chỉ mount danh sách bài của chương mở và tính khóa bài trong một lượt duyệt. Các phát hiện bên dưới mô tả hiện trạng **trước** đợt tối ưu này. Chưa triển khai tối ưu renderer PDF/Word/PowerPoint hoặc tách bundle trong đợt này.

## Kết quả đo bundle

### Bổ sung ngày 23/10/2026: giữ sidebar khi chuyển bài

Outline cũng được bổ sung metadata tài liệu theo lô để sidebar hiện mục Tài liệu/số lượng và danh sách file ngay khi mở chương, không cần chọn bài trước. Kiểm thử frontend xác nhận thao tác mở danh sách tài liệu không gọi tải detail của bài đó.

`LessonDetail.vue` trước đây đặt toàn bộ trang dưới `v-if="lesson && chapter && !lessonLoading"`. Khi chuyển bài, điều kiện này gỡ rồi mount lại sidebar dù outline được cache, làm chạy lại animation và mất trạng thái cục bộ/vị trí cuộn. Đã giữ khung trang/sidebar theo outline; chỉ phần nội dung bài hiển thị trạng thái tải hoặc lỗi. Sidebar vẫn cập nhật bài đang chọn, chương hiện tại, tài liệu và tiến độ; có thể chọn bài khác sau lỗi tải. Metadata khóa lấy từ outline hiện có để không đổi nguồn trong lúc chờ detail.

Kiểm chứng: 15 kiểm thử learning performance đạt, gồm kiểm thử sidebar thật giữ cùng DOM và vị trí cuộn khi request đang chờ, thất bại và chọn bài khác; TypeScript/Vite production build đạt. Chưa đo Performance trace trên trình duyệt thực.

Chạy Vite production build với `write: false`, lần theo `imports` của entry và cộng kích thước UTF-8/gzip của các chunk được nhập tĩnh:

- 5 chunk JavaScript ban đầu: **893.999 byte**, tổng gzip **289.321 byte**.
- Không tính CSS, ảnh, video, worker PDF và các chunk chỉ tải động.
- Trong đồ thị tải ban đầu có Chart.js và các màn quản trị như AdminPortal, AdminQuizDetail, TrainingAdmin, LessonContentEditor. Người chỉ vào trang đăng nhập/Tổng quan vẫn phải tải và xử lý đồ thị này.
- Số liệu là kích thước bundle, không phải thời gian tải thực tế hoặc cam kết mức cải thiện.

## Các điểm nên xử lý, theo mức ưu tiên

### 1. Cao — Giảm mã tải ngay khi mở frontend

Bằng chứng: `CoreTraining/src/router/index.ts:4` nhập tĩnh các màn học viên và nhiều màn quản trị. `CoreTraining/src/views/Profile.vue:404` nhập tĩnh RadarChart; component này đăng ký Chart.js. Dù biểu đồ chỉ mount khi mở tab thống kê, thư viện đã nằm trong đồ thị JavaScript ban đầu.

Đề xuất: lazy-load theo route, ưu tiên tách toàn bộ màn quản trị khỏi luồng học viên; tải RadarChart khi mở tab thống kê. Đo lại tổng entry/static imports sau thay đổi, không chỉ nhìn kích thước một chunk. Thư viện render Word/PowerPoint hiện đã dùng import động; nên giữ cách này.

Xác minh: mở mới trang đăng nhập/Tổng quan không yêu cầu chunk quản trị/Chart.js; chuyển sang thống kê hoặc quản trị vẫn tải đúng component. Kiểm tra đường dẫn trực tiếp và hành vi khi tải chunk thất bại sau triển khai.

### 2. Cao — Mỗi lần đổi trang đều chờ hai API kiểm tra liên tiếp

Bằng chứng: `CoreTraining/src/router/index.ts:133` chờ `authApi.session()`, rồi `CoreTraining/src/router/index.ts:161` chờ `navigationStore.load(true)`. `force=true` bỏ qua cả trạng thái đã tải và nhánh dùng chung request của navigation store. Việc đổi tab hồ sơ bằng `router.replace()` cũng chạy qua guard này.

Tác động: hai lượt chờ mạng trước khi render trang, kể cả chuyển giữa các màn vừa xem hoặc đổi query tab.

Đề xuất: dùng chung request đang chạy; chính sách làm mới có giới hạn thời gian hoặc theo `permissionRevision`, làm mới khi phiên/quyền đổi và xử lý 401/403. Có thể cho điều hướng dùng dữ liệu quyền còn hiệu lực và cập nhật nền theo chính sách đã thống nhất. Không đơn thuần bỏ kiểm tra quyền hoặc cache vô thời hạn.

Xác minh: chuyển liên tiếp các màn không lặp request vô ích; thu hồi quyền, hết phiên, bắt buộc đổi mật khẩu và đăng xuất vẫn có hiệu lực đúng yêu cầu.

### 3. Cao — Trang học bài phải chờ ghi tiến độ và tải lại cấu trúc toàn khóa

Bằng chứng: `CoreTraining/src/views/LessonDetail.vue:408` chờ ghi tiến độ bài cũ trước khi tải bài mới; `loadLessonData` tại dòng 327 chờ cả chi tiết bài và ghi chú rồi mới gọi API outline toàn khóa. `switchLesson` cũng ghi tiến độ trước `router.push`, nên đổi bài qua sidebar có thể tiếp tục ghi cùng bài cũ trong watcher. Ghi chú không cần thiết để hiển thị nội dung chính vẫn nằm trong chuỗi chờ.

Tác động: khi chuyển bài, mạng chậm ở thao tác ghi hoặc ghi chú cũng làm chậm việc mở nội dung mới; khóa nhiều chương phải tải lặp outline lớn.

Đề xuất: tách tải nội dung chính khỏi ghi chú; dùng cache outline theo khóa và cập nhật trạng thái hoàn thành/khóa khi cần. Gom việc lưu tiến độ tại một đầu mối, bỏ bản ghi không đổi. Nếu lưu nền, phải chụp cố định `lessonId` và snapshot để không ghi dữ liệu bài cũ sang bài mới. Hủy hoặc bỏ qua kết quả tải đã lỗi thời.

Xác minh: đổi bài nhanh, chuyển chương, mở tài liệu khác bài, rời trang, bài có kiểm tra bắt buộc và resume vẫn đúng; mỗi chuyển bài chỉ phát sinh số lần ghi cần thiết.

### 4. Vừa — API trùng tại Tổng quan → Khóa học và Hồ sơ

- `CoreTraining/src/views/Dashboard.vue:553` tải outline nhưng bỏ kết quả, sau đó chuyển trang. `CoreTraining/src/views/CourseOutline.vue:198` tải outline lần nữa. Nên chuyển trang và để màn đích tải/hiển thị lỗi, hoặc dùng chung kết quả prefetch.
- `CoreTraining/src/views/Profile.vue:458` gọi `getMyProfile()` đồng thời với `fetchProgress()`. `CoreTraining/src/stores/userStore.ts:237` lại gọi cùng API hồ sơ. Nên dùng chung request/kết quả hoặc tách chức năng tải progress khỏi tải profile. Việc dedupe progress đang có không dedupe request profile riêng.
- `CoreTraining/src/App.vue:194` tải danh sách toàn bộ khóa khi app mount. Khi chỉ mở hồ sơ/bảng vinh danh, cần đánh giá việc trì hoãn tải catalog đến lúc trang học hoặc trợ lý cần dữ liệu này.

Xác minh: Network không còn hai request outline cho một lần mở khóa; hồ sơ chỉ có một request profile trong lượt tải thông thường. Dữ liệu sau sửa hồ sơ/đăng ký vẫn được cập nhật.

### 5. Vừa — Mỗi thao tác ghi đều tải lại CSRF token

Bằng chứng: `CoreTraining/src/api/axiosClient.ts:65` gọi `/api/auth/csrf` trước mọi request ngoài GET/HEAD/OPTIONS. Một lần lưu tiến độ hoặc gửi bình luận luôn thêm một lượt mạng; nhiều thao tác đồng thời còn tạo nhiều request token.

Đề xuất: dùng chung request token đang chạy; xem xét giữ token trong bộ nhớ theo vòng đời phiên, xóa/lấy mới theo quy tắc backend khi đăng nhập/đăng xuất/đổi phiên. Chỉ retry an toàn khi xác định lỗi token, không tự gửi lại tùy ý các thao tác có tác dụng phụ. Vẫn giữ nguyên bảo vệ CSRF.

Xác minh: tiến độ/bình luận/nộp bài giữ hành vi xác thực đúng, không ghi trùng khi thử lại, và số request token giảm trong cùng phiên hợp lệ.

### 6. Vừa — PDF vẽ lại quá thường xuyên khi sidebar đang chuyển động

Bằng chứng: `CoreTraining/src/components/lesson/PdfDocumentPreview.vue:144` gọi `draw()` qua requestAnimationFrame cho mỗi lần đổi kích thước. Sidebar đang animate `padding-right`, nên kích thước viewer có thể đổi mỗi frame trong suốt hiệu ứng. Mỗi draw hủy render trước và thay kích thước canvas. Dòng 80–85 còn nhân cả zoom và devicePixelRatio mà chưa có giới hạn tổng pixel canvas.

Tác động: máy yếu có thể tốn CPU, nháy trang hoặc tăng bộ nhớ khi zoom lớn trên màn Retina. Đây là điểm liên quan trực tiếp đến các hiệu ứng sidebar vừa bổ sung; requestAnimationFrame chỉ gom trong một frame, không đợi kích thước ổn định.

Đề xuất: trì hoãn render độ phân giải cao đến khi resize ổn định, tạm scale bản canvas hiện có trong lúc chuyển động; bỏ draw nếu kích thước không đổi và đặt ngân sách tổng pixel. PDF hiện tải toàn bộ ArrayBuffer trước khi parse (`:109`); nếu API file hỗ trợ Range/CORS/xác thực tương ứng, có thể chuyển sang tải theo range/stream.

Xác minh: ghi Performance trace khi đóng/mở sidebar và fullscreen, kiểm tra số lần render, trang hiện tại, độ sắc nét sau hiệu ứng và bộ nhớ khi zoom 300%.

### 7. Vừa — Word dựng toàn bộ trang và đo layout trên mỗi sự kiện cuộn

Bằng chứng: `CoreTraining/src/components/lesson/WordDocumentPreview.vue:54` truy vấn tất cả `section.docx-page` và gọi `getBoundingClientRect()` để xác định trang ngay trong handler scroll. `renderAsync` tại dòng 88 dựng toàn bộ tài liệu. Callback resize cũng đo lại kích thước trang.

Tác động: chi phí DOM/layout tăng theo số trang và có thể làm cuộn tài liệu dài bị khựng. Request tải file chưa có AbortController khi rời tài liệu; khác với PDF/PPT, component Word cũng chưa có request id để bỏ kết quả render cũ.

Đề xuất: cache danh sách trang, gom xử lý scroll theo frame/throttle, theo dõi trang bằng IntersectionObserver hoặc vị trí đã tính trước. Hủy request lỗi thời và kiểm tra vòng đời trước khi render. Với tài liệu rất dài, cân nhắc preview PDF được tạo sẵn hoặc cơ chế hiển thị trang phù hợp; đây là thay đổi lớn hơn, cần kiểm tra độ trung thực định dạng.

Xác minh: tài liệu 50–100 trang, cuộn liên tục, đổi file khi đang tải, xoay màn hình và bật fullscreen; không có dữ liệu cũ ghi đè viewer mới.

### 8. Vừa — Trang cấu trúc khóa học vẫn dựng bài ở mọi chương thu gọn

Bằng chứng: `CoreTraining/src/views/CourseOutline.vue:97` dùng `v-show`, nên các nút bài học của mọi chương vẫn được tạo DOM. `isLocked` tại dòng 171 quét các bài trước bằng `slice().some()`, gọi lặp ở từng bài; `completed()` lại dùng `includes` trên danh sách hoàn thành.

Đề xuất: chỉ mount bài của chương mở, tương tự sidebar mới; chuẩn bị Set bài đã hoàn thành và tính trạng thái khóa bằng một lượt duyệt mỗi chương. Chỉ cân nhắc virtual list khi số lượng thực tế đủ lớn để cần, không thêm mặc định.

Xác minh: khóa có hàng trăm bài, DOM chương đóng không chứa danh sách bài; trạng thái khóa, tiến độ, mở/thu chương và bàn phím vẫn đúng.

### 9. Vừa, phụ thuộc quy mô — Bảng vinh danh chưa phân trang

Bằng chứng: `CoreTraining/src/views/Leaderboard.vue:40` render toàn bộ response. API hiện trả danh sách không phân trang (`CoreTraining/src/api/leaderboardApi.ts`); kiểm tra đối chiếu `LeaderboardServiceImpl` cũng cho thấy endpoint không áp dụng giới hạn. Ngoài ra, dòng 167–169 tải leaderboard sau khi chờ progress dù hai request độc lập.

Đề xuất: tải hai phần độc lập; khi danh sách lớn, dùng phân trang phía server hoặc top N kèm hạng cá nhân. Phân trang chỉ trên frontend giảm DOM nhưng không giảm dung lượng response hoặc xử lý backend.

Xác minh: dữ liệu lớn, chuyển trang vẫn giữ rank và vị trí người đang đăng nhập; thành tích cá nhân không ngăn danh sách xếp hạng hiển thị nếu một API chậm.

### 10. Thấp — Bộ đếm bài kiểm tra tiếp tục chạy sau khi rời màn

Bằng chứng: `CoreTraining/src/stores/quizStore.ts:91` tạo interval, dừng khi bắt đầu/nộp bài hoặc xem lại; `QuizView.vue` chưa có cleanup khi unmount. Store sống lâu hơn component nên interval có thể tiếp tục thay đổi state mỗi giây ở màn khác.

Đề xuất: xác định rõ chính sách thời gian thi khi rời trang. Dọn interval của UI khi unmount; nếu thời gian vẫn phải chạy, tính từ mốc thời gian bắt đầu thay vì phụ thuộc interval đếm từng giây. Không vô tình thay đổi quy tắc tính giờ làm bài.

Xác minh: rời bài chưa nộp, quay lại, đổi bài kiểm tra và chuyển sang xem lại; không còn timer thừa và số liệu thời gian vẫn đúng chính sách.

## Những phần đã có nền tảng tốt

- Tổng quan và danh sách kết quả kiểm tra ở Hồ sơ đã phân trang frontend.
- CourseThumbnail đã có `loading="lazy"` và khung `aspect-ratio` để giữ chỗ.
- PDF chỉ vẽ trang hiện tại, có worker và hủy render cũ.
- PowerPoint đã bật `lazySlides`, `lazyMedia`, `renderMode: "slide"`.
- Sidebar mới chỉ dựng bài của chương đang mở; hiệu ứng có hỗ trợ giảm chuyển động.
- Biểu đồ hồ sơ chỉ mount ở tab thống kê; phần cần tách tiếp là việc tải thư viện.
- Đăng nhập/đổi mật khẩu chưa thấy điểm render riêng nào đáng ưu tiên hơn các vấn đề dùng chung phía trên.

## Thứ tự triển khai đề nghị

1. Tách bundle theo route và bỏ request trùng: dễ đo và ảnh hưởng nhiều màn.
2. Tối ưu chuỗi chuyển bài, quản lý cache/request phiên–điều hướng–CSRF theo vòng đời đúng.
3. Giảm vẽ lại PDF và công việc khi cuộn Word, đo trực tiếp trên mobile.
4. Mount theo nhu cầu ở cấu trúc khóa học, phân trang leaderboard khi quy mô yêu cầu, xử lý vòng đời timer.

Trước/sau mỗi nhóm, dùng cùng dữ liệu, thiết bị và cấu hình mạng để so sánh: tổng JavaScript ban đầu, số request khi chuyển trang, thời điểm nội dung chính xuất hiện, long task/frame bị rớt và đỉnh bộ nhớ của tài liệu. Chưa có đủ số liệu để khẳng định mức tăng tốc theo phần trăm.
