-- PostgreSQL: run against the project database after starting the current API
-- at least once so Hibernate has created the tables. File encoding: UTF-8.
-- Creates 6 domains, 12 programs, 36 courses, 108 chapters, 324 lessons
-- with reading content, and 1 sample owning unit. Ready sample courses are published.
-- Re-running fills missing sample nodes without replacing existing content.
-- Publication changes statuses and increments versions only when needed.
-- No users, passwords, permissions or learning progress are changed.

BEGIN;
SET LOCAL client_encoding = 'UTF8';
SET LOCAL search_path = public;

CREATE TEMP TABLE seed_catalog_domains (
    code text PRIMARY KEY,
    name text NOT NULL,
    description text NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_catalog_domains (code, name, description) VALUES
('SAMPLE-TD', 'Tín dụng', 'Đào tạo quy trình cấp tín dụng, thẩm định và quản lý khoản vay.'),
('SAMPLE-HD', 'Huy động vốn', 'Đào tạo sản phẩm tiền gửi, tiền tiết kiệm và quản lý nguồn vốn.'),
('SAMPLE-TT', 'Thanh toán', 'Đào tạo thanh toán trong nước, thanh toán quốc tế và chuyển tiền.'),
('SAMPLE-NHS', 'Ngân hàng số', 'Đào tạo dịch vụ ngân hàng điện tử, nghiệp vụ thẻ và trải nghiệm số.'),
('SAMPLE-RR', 'Quản trị rủi ro và tuân thủ', 'Đào tạo nhận diện rủi ro, kiểm soát nội bộ và tuân thủ.'),
('SAMPLE-DVKH', 'Dịch vụ khách hàng', 'Đào tạo kỹ năng phục vụ, tư vấn và quản lý quan hệ khách hàng.');

CREATE TEMP TABLE seed_catalog_programs (
    code text PRIMARY KEY,
    domain_code text NOT NULL REFERENCES seed_catalog_domains(code),
    name text NOT NULL,
    description text NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_catalog_programs (code, domain_code, name, description) VALUES
('SAMPLE-TD-CN', 'SAMPLE-TD', 'Tín dụng khách hàng cá nhân', 'Nền tảng và thực hành nghiệp vụ cho vay khách hàng cá nhân.'),
('SAMPLE-TD-DN', 'SAMPLE-TD', 'Tín dụng khách hàng doanh nghiệp', 'Thẩm định, cấp tín dụng và giám sát khoản vay doanh nghiệp.'),
('SAMPLE-HD-CN', 'SAMPLE-HD', 'Tiền gửi khách hàng cá nhân', 'Sản phẩm, quy trình và chăm sóc khách hàng tiền gửi cá nhân.'),
('SAMPLE-HD-DN', 'SAMPLE-HD', 'Quản lý tiền gửi doanh nghiệp', 'Tài khoản thanh toán, quản lý dòng tiền và huy động vốn doanh nghiệp.'),
('SAMPLE-TT-TN', 'SAMPLE-TT', 'Thanh toán trong nước', 'Quy trình chuyển tiền, đối soát và xử lý giao dịch trong nước.'),
('SAMPLE-TT-QT', 'SAMPLE-TT', 'Thanh toán quốc tế', 'Nền tảng nghiệp vụ chuyển tiền quốc tế và tài trợ thương mại.'),
('SAMPLE-NHS-DT', 'SAMPLE-NHS', 'Dịch vụ ngân hàng điện tử', 'Đăng ký, hỗ trợ và vận hành dịch vụ ngân hàng số.'),
('SAMPLE-NHS-THE', 'SAMPLE-NHS', 'Nghiệp vụ thẻ', 'Phát hành, sử dụng và hỗ trợ khách hàng đối với sản phẩm thẻ.'),
('SAMPLE-RR-TD', 'SAMPLE-RR', 'Quản trị rủi ro tín dụng', 'Nhận diện, đánh giá và theo dõi rủi ro trong hoạt động tín dụng.'),
('SAMPLE-RR-TT', 'SAMPLE-RR', 'Tuân thủ và kiểm soát nội bộ', 'Kiểm soát nghiệp vụ, bảo vệ dữ liệu và phòng chống rửa tiền.'),
('SAMPLE-DVKH-GD', 'SAMPLE-DVKH', 'Dịch vụ tại quầy', 'Kỹ năng giao tiếp và quy trình phục vụ khách hàng tại quầy.'),
('SAMPLE-DVKH-QH', 'SAMPLE-DVKH', 'Quản lý quan hệ khách hàng', 'Tư vấn giải pháp, chăm sóc và phát triển quan hệ khách hàng.');

CREATE TEMP TABLE seed_catalog_courses (
    code text PRIMARY KEY,
    program_code text NOT NULL REFERENCES seed_catalog_programs(code),
    name text NOT NULL,
    description text NOT NULL,
    icon_name text NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_catalog_courses (code, program_code, name, description, icon_name) VALUES
('SAMPLE-TD-CN-01', 'SAMPLE-TD-CN', 'Tổng quan tín dụng cá nhân', 'Giới thiệu sản phẩm vay, điều kiện cấp tín dụng và các bước tiếp nhận hồ sơ cá nhân.', 'BookOpen'),
('SAMPLE-TD-CN-02', 'SAMPLE-TD-CN', 'Thẩm định hồ sơ vay cá nhân', 'Thực hành kiểm tra hồ sơ, đánh giá thu nhập và khả năng trả nợ của khách hàng.', 'BookOpen'),
('SAMPLE-TD-CN-03', 'SAMPLE-TD-CN', 'Giải ngân và quản lý khoản vay cá nhân', 'Thực hành kiểm tra điều kiện giải ngân, theo dõi khoản vay và nhắc nợ.', 'BookOpen'),
('SAMPLE-TD-DN-01', 'SAMPLE-TD-DN', 'Phân tích tài chính doanh nghiệp', 'Đọc báo cáo tài chính và phân tích các chỉ tiêu phục vụ đánh giá khách hàng doanh nghiệp.', 'BookOpen'),
('SAMPLE-TD-DN-02', 'SAMPLE-TD-DN', 'Thẩm định phương án kinh doanh', 'Đánh giá nhu cầu vốn, dòng tiền và tính khả thi của phương án kinh doanh.', 'BookOpen'),
('SAMPLE-TD-DN-03', 'SAMPLE-TD-DN', 'Quản lý tín dụng doanh nghiệp', 'Theo dõi hạn mức, điều kiện tín dụng và tình hình sử dụng vốn sau giải ngân.', 'BookOpen'),
('SAMPLE-HD-CN-01', 'SAMPLE-HD-CN', 'Sản phẩm tiền gửi và tiết kiệm', 'Phân biệt các sản phẩm tiền gửi, kỳ hạn và cách tư vấn theo nhu cầu khách hàng.', 'Coins'),
('SAMPLE-HD-CN-02', 'SAMPLE-HD-CN', 'Mở và tất toán tiền gửi', 'Thực hành tiếp nhận hồ sơ, mở tiền gửi và kiểm tra yêu cầu tất toán.', 'Coins'),
('SAMPLE-HD-CN-03', 'SAMPLE-HD-CN', 'Chăm sóc khách hàng tiền gửi', 'Xây dựng kế hoạch chăm sóc, theo dõi ngày đáo hạn và duy trì quan hệ khách hàng.', 'Coins'),
('SAMPLE-HD-DN-01', 'SAMPLE-HD-DN', 'Tài khoản thanh toán doanh nghiệp', 'Kiểm tra hồ sơ pháp lý, người đại diện và yêu cầu mở tài khoản doanh nghiệp.', 'Coins'),
('SAMPLE-HD-DN-02', 'SAMPLE-HD-DN', 'Quản lý dòng tiền doanh nghiệp', 'Giới thiệu giải pháp thu chi, quản lý số dư và đối soát dòng tiền.', 'Coins'),
('SAMPLE-HD-DN-03', 'SAMPLE-HD-DN', 'Phát triển nguồn vốn doanh nghiệp', 'Phân tích nhu cầu nguồn vốn và xây dựng phương án tư vấn sản phẩm phù hợp.', 'Coins'),
('SAMPLE-TT-TN-01', 'SAMPLE-TT-TN', 'Chuyển tiền trong nước', 'Thực hành tiếp nhận lệnh chuyển tiền, kiểm tra thông tin và xác nhận giao dịch.', 'BookOpen'),
('SAMPLE-TT-TN-02', 'SAMPLE-TT-TN', 'Đối soát giao dịch thanh toán', 'Đối chiếu trạng thái, số tiền và chứng từ để phát hiện sai lệch giao dịch.', 'BookOpen'),
('SAMPLE-TT-TN-03', 'SAMPLE-TT-TN', 'Tra soát và xử lý sai sót thanh toán', 'Tiếp nhận yêu cầu tra soát, xác minh thông tin và phối hợp xử lý sai sót.', 'BookOpen'),
('SAMPLE-TT-QT-01', 'SAMPLE-TT-QT', 'Chuyển tiền quốc tế cơ bản', 'Giới thiệu hồ sơ chuyển tiền quốc tế và các thông tin cần kiểm tra trước khi xử lý.', 'BookOpen'),
('SAMPLE-TT-QT-02', 'SAMPLE-TT-QT', 'Thư tín dụng và bộ chứng từ', 'Nhận diện các thành phần của thư tín dụng và thực hành kiểm tra bộ chứng từ mẫu.', 'BookOpen'),
('SAMPLE-TT-QT-03', 'SAMPLE-TT-QT', 'Nhờ thu và tài trợ thương mại', 'Giới thiệu nghiệp vụ nhờ thu, quy trình phối hợp và các điểm kiểm soát chính.', 'BookOpen'),
('SAMPLE-NHS-DT-01', 'SAMPLE-NHS-DT', 'Đăng ký dịch vụ ngân hàng số', 'Thực hành hướng dẫn đăng ký, kích hoạt và kiểm tra thông tin sử dụng dịch vụ.', 'BookOpen'),
('SAMPLE-NHS-DT-02', 'SAMPLE-NHS-DT', 'Hỗ trợ giao dịch ngân hàng số', 'Phân loại yêu cầu hỗ trợ và hướng dẫn khách hàng xử lý tình huống thường gặp.', 'BookOpen'),
('SAMPLE-NHS-DT-03', 'SAMPLE-NHS-DT', 'An toàn khi sử dụng ngân hàng số', 'Nhận diện tình huống lừa đảo và hướng dẫn khách hàng bảo vệ tài khoản, thiết bị.', 'BookOpen'),
('SAMPLE-NHS-THE-01', 'SAMPLE-NHS-THE', 'Sản phẩm và phát hành thẻ', 'Phân biệt sản phẩm thẻ và thực hành tiếp nhận hồ sơ phát hành thẻ.', 'BookOpen'),
('SAMPLE-NHS-THE-02', 'SAMPLE-NHS-THE', 'Quản lý giao dịch thẻ', 'Kiểm tra trạng thái giao dịch và phối hợp xử lý các yêu cầu liên quan đến thẻ.', 'BookOpen'),
('SAMPLE-NHS-THE-03', 'SAMPLE-NHS-THE', 'Tra soát và khiếu nại thẻ', 'Tiếp nhận khiếu nại, xác minh chứng từ và theo dõi tiến độ xử lý tra soát.', 'BookOpen'),
('SAMPLE-RR-TD-01', 'SAMPLE-RR-TD', 'Nhận diện rủi ro tín dụng', 'Phân tích tình huống mẫu để xác định nguồn rủi ro và biện pháp kiểm soát.', 'BookOpen'),
('SAMPLE-RR-TD-02', 'SAMPLE-RR-TD', 'Cảnh báo sớm khoản vay', 'Nhận biết dấu hiệu bất thường và lập danh sách theo dõi khoản vay có rủi ro.', 'BookOpen'),
('SAMPLE-RR-TD-03', 'SAMPLE-RR-TD', 'Giám sát sau giải ngân', 'Thực hành kiểm tra mục đích sử dụng vốn và cập nhật tình hình khách hàng.', 'BookOpen'),
('SAMPLE-RR-TT-01', 'SAMPLE-RR-TT', 'Nhận biết khách hàng và phòng chống rửa tiền', 'Thực hành kiểm tra thông tin khách hàng và nhận diện giao dịch cần xem xét.', 'BookOpen'),
('SAMPLE-RR-TT-02', 'SAMPLE-RR-TT', 'Kiểm soát nội bộ tại đơn vị', 'Xác định điểm kiểm soát, phân công trách nhiệm và ghi nhận kết quả kiểm tra.', 'BookOpen'),
('SAMPLE-RR-TT-03', 'SAMPLE-RR-TT', 'Bảo vệ dữ liệu khách hàng', 'Thực hành phân loại thông tin và xử lý dữ liệu khách hàng theo quyền được giao.', 'BookOpen'),
('SAMPLE-DVKH-GD-01', 'SAMPLE-DVKH-GD', 'Giao tiếp và phục vụ tại quầy', 'Thực hành đón tiếp, xác định nhu cầu và hướng dẫn khách hàng thực hiện giao dịch.', 'BookOpen'),
('SAMPLE-DVKH-GD-02', 'SAMPLE-DVKH-GD', 'Xử lý tình huống và khiếu nại', 'Luyện tập lắng nghe, xác minh sự việc và đề xuất hướng xử lý phù hợp.', 'BookOpen'),
('SAMPLE-DVKH-GD-03', 'SAMPLE-DVKH-GD', 'Chuẩn hóa chất lượng dịch vụ', 'Đánh giá tình huống phục vụ và xây dựng kế hoạch cải thiện chất lượng tại quầy.', 'BookOpen'),
('SAMPLE-DVKH-QH-01', 'SAMPLE-DVKH-QH', 'Phân tích nhu cầu khách hàng', 'Thu thập thông tin, phân nhóm khách hàng và xác định nhu cầu sử dụng dịch vụ.', 'BookOpen'),
('SAMPLE-DVKH-QH-02', 'SAMPLE-DVKH-QH', 'Tư vấn giải pháp tài chính', 'Thực hành đề xuất sản phẩm phù hợp và trình bày lợi ích, điều kiện sử dụng.', 'BookOpen'),
('SAMPLE-DVKH-QH-03', 'SAMPLE-DVKH-QH', 'Chăm sóc và phát triển quan hệ khách hàng', 'Lập kế hoạch liên hệ, theo dõi phản hồi và duy trì quan hệ khách hàng.', 'BookOpen');

-- Each program requires at least one assigned unit in the current API.
INSERT INTO organization_units (id, name, type, active, parent_id)
VALUES ('SAMPLE-TRAINING-UNIT', 'Đơn vị đào tạo mẫu', 'UNIT', true, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO training_domains (id, code, name, description, status, version)
SELECT code, code, name, description, 'ACTIVE', 0
FROM seed_catalog_domains
ON CONFLICT DO NOTHING;

-- Resolve relationships by code, including when a code already has another ID.
INSERT INTO training_programs (id, code, name, description, status, version, domain_id)
SELECT s.code, s.code, s.name, s.description, 'ACTIVE', 0, d.id
FROM seed_catalog_programs s
JOIN training_domains d ON d.code = s.domain_code
ON CONFLICT DO NOTHING;

-- Assign the sample unit only to programs originally created by this script.
INSERT INTO training_program_units (program_id, unit_id)
SELECT p.id, 'SAMPLE-TRAINING-UNIT'
FROM training_programs p
JOIN seed_catalog_programs s ON s.code = p.code AND p.id = s.code
WHERE NOT EXISTS (
    SELECT 1 FROM training_program_units u WHERE u.program_id = p.id
)
ON CONFLICT DO NOTHING;

-- Start as DRAFT; publish below only after chapters and lesson contents exist.
INSERT INTO courses (
    id, code, name, description, icon_name, quiz_passing_score,
    status, version, program_id, owner_unit_id
)
SELECT s.code, s.code, s.name, s.description, s.icon_name, 80,
       'DRAFT', 0, p.id, 'SAMPLE-TRAINING-UNIT'
FROM seed_catalog_courses s
JOIN training_programs p ON p.code = s.program_code
ON CONFLICT DO NOTHING;

-- Stable IDs allow this upgrade to work with the previously seeded 36 courses.
-- Only courses whose ID and code match this seed's codes receive sample content.
CREATE TEMP TABLE seed_catalog_chapter_templates (
    chapter_no integer PRIMARY KEY,
    code text NOT NULL,
    title text NOT NULL,
    description text NOT NULL
) ON COMMIT DROP;

INSERT INTO seed_catalog_chapter_templates VALUES
(1, 'CH01', 'Kiến thức nền tảng', 'Mục tiêu, thuật ngữ, đối tượng và các điểm kiểm soát cơ bản.'),
(2, 'CH02', 'Quy trình thực hành', 'Chuẩn bị thông tin, thực hiện nghiệp vụ và kiểm tra kết quả.'),
(3, 'CH03', 'Tình huống và tổng kết', 'Thực hành tình huống, xử lý sai lệch và tổng kết kiến thức.');

INSERT INTO chapters (id, course_id, code, title, description, chapter_order, status, version)
SELECT c.id || '-CH' || t.chapter_no, c.id, t.code, t.title,
       t.description, t.chapter_no - 1, 'DRAFT', 0
FROM courses c
JOIN seed_catalog_courses s ON s.code = c.code AND c.id = s.code
CROSS JOIN seed_catalog_chapter_templates t
WHERE c.status IN ('DRAFT', 'PUBLISHED')
ON CONFLICT DO NOTHING;

CREATE TEMP TABLE seed_catalog_lesson_templates (
    chapter_no integer NOT NULL REFERENCES seed_catalog_chapter_templates(chapter_no),
    lesson_no integer NOT NULL,
    title text NOT NULL,
    objective text NOT NULL,
    guidance text NOT NULL,
    exercise text NOT NULL,
    PRIMARY KEY (chapter_no, lesson_no)
) ON COMMIT DROP;

INSERT INTO seed_catalog_lesson_templates VALUES
(1, 1, 'Tổng quan và mục tiêu',
 'Mô tả được mục tiêu khóa học và vai trò của nghiệp vụ trong hoạt động phục vụ khách hàng.',
 E'1. Đọc phần giới thiệu khóa học và xác định đối tượng sử dụng.\n2. Liệt kê thông tin đầu vào và kết quả cần đạt.\n3. Xác định các bộ phận cần phối hợp.',
 'Viết một đoạn giới thiệu nghiệp vụ và nêu ba kết quả cần đạt sau khi hoàn thành khóa học.'),
(1, 2, 'Thuật ngữ và hồ sơ liên quan',
 'Nhận diện các thuật ngữ, loại thông tin và hồ sơ cần kiểm tra trước khi thực hiện nghiệp vụ.',
 E'1. Lập bảng thuật ngữ liên quan đến chủ đề khóa học.\n2. Nhóm hồ sơ theo mục đích sử dụng.\n3. Đánh dấu thông tin cần xác minh hoặc bổ sung.',
 'Tạo bảng gồm năm thuật ngữ, ý nghĩa và một ví dụ sử dụng; kèm danh sách hồ sơ đầu vào mẫu.'),
(1, 3, 'Vai trò và điểm kiểm soát',
 'Phân biệt trách nhiệm của người thực hiện, người kiểm soát và các bên phối hợp.',
 E'1. Xác định người chịu trách nhiệm tại từng bước.\n2. Xác định nội dung cần kiểm tra độc lập.\n3. Ghi nhận tình huống cần chuyển cấp có thẩm quyền.',
 'Lập sơ đồ vai trò và chọn ba điểm kiểm soát cần có trong một tình huống nghiệp vụ mẫu.'),
(2, 1, 'Chuẩn bị và kiểm tra đầu vào',
 'Chuẩn bị dữ liệu, đối chiếu thông tin và nhận diện hồ sơ chưa đầy đủ.',
 E'1. Tiếp nhận yêu cầu và xác định mục đích xử lý.\n2. Đối chiếu thông tin với nguồn được phép sử dụng.\n3. Ghi nhận thiếu sót và yêu cầu bổ sung trước khi thực hiện.',
 'Với một bộ hồ sơ giả lập, lập danh sách thông tin hợp lệ, thông tin còn thiếu và cách xử lý tiếp theo.'),
(2, 2, 'Thực hiện quy trình mẫu',
 'Trình bày được trình tự thực hiện và ghi nhận kết quả tại từng bước.',
 E'1. Chọn đúng chức năng và kiểm tra thông tin trước khi ghi nhận.\n2. Thực hiện từng bước theo hướng dẫn nghiệp vụ đang được phê duyệt.\n3. Lưu kết quả và chuyển kiểm soát khi cần.',
 'Mô phỏng một yêu cầu trên dữ liệu giả lập và viết nhật ký gồm thao tác, đầu vào và kết quả của từng bước.'),
(2, 3, 'Đối chiếu và hoàn tất',
 'Kiểm tra kết quả, đối chiếu hồ sơ và hoàn tất việc bàn giao hoặc lưu trữ.',
 E'1. So sánh kết quả xử lý với yêu cầu ban đầu.\n2. Đối chiếu trạng thái và thông tin trên hồ sơ.\n3. Ghi nhận kết quả kiểm soát và lưu trữ theo hướng dẫn của đơn vị.',
 'Lập checklist hoàn tất gồm ít nhất năm mục và xác định bằng chứng kiểm tra tương ứng.'),
(3, 1, 'Tình huống thực hành',
 'Vận dụng các bước đã học để phân tích và giải quyết một tình huống mẫu.',
 E'1. Đọc yêu cầu và tách các dữ kiện đã biết, chưa biết.\n2. Chọn trình tự xử lý phù hợp với dữ kiện.\n3. Trình bày phương án và các điểm cần xác minh.',
 'Giả lập một yêu cầu có thông tin đầu vào chưa đầy đủ. Nêu câu hỏi cần làm rõ và trình tự xử lý đề xuất.'),
(3, 2, 'Sai lệch và phối hợp xử lý',
 'Nhận diện sai lệch, lưu bằng chứng và phối hợp xử lý trong phạm vi được giao.',
 E'1. So sánh thông tin để xác định vị trí sai lệch.\n2. Lưu mô tả sự việc và bằng chứng cần thiết.\n3. Báo cáo hoặc chuyển yêu cầu tới bộ phận có trách nhiệm.',
 'Với tình huống kết quả không khớp yêu cầu, lập phiếu ghi nhận gồm hiện tượng, thông tin cần kiểm tra và đơn vị phối hợp.'),
(3, 3, 'Tổng kết và tự đánh giá',
 'Tổng hợp kiến thức, tự đánh giá mức độ hiểu bài và lập kế hoạch thực hành thêm.',
 E'1. Tóm tắt mục tiêu, đầu vào, trình tự và kết quả của nghiệp vụ.\n2. Đối chiếu bài thực hành với checklist kiểm soát.\n3. Ghi lại nội dung cần ôn tập và kế hoạch cải thiện.',
 'Viết bản tổng kết một trang và tự đánh giá khả năng giải thích quy trình, phát hiện sai lệch và phối hợp xử lý.');

CREATE TEMP TABLE seed_catalog_lesson_nodes ON COMMIT DROP AS
SELECT c.id AS course_id, c.name AS course_name, c.description AS course_description,
       d.name AS domain_name, p.name AS program_name, ch.id AS chapter_id,
       ch.id || '-L' || t.lesson_no AS lesson_id, t.lesson_no - 1 AS lesson_order,
       t.title || ': ' || c.name AS title, t.objective, t.guidance, t.exercise
FROM courses c
JOIN seed_catalog_courses s ON s.code = c.code AND c.id = s.code
JOIN training_programs p ON p.id = c.program_id
JOIN training_domains d ON d.id = p.domain_id
JOIN seed_catalog_chapter_templates ct ON true
JOIN chapters ch ON ch.id = c.id || '-CH' || ct.chapter_no
                AND ch.course_id = c.id AND ch.code = ct.code
JOIN seed_catalog_lesson_templates t ON t.chapter_no = ct.chapter_no
WHERE c.status IN ('DRAFT', 'PUBLISHED');

INSERT INTO lessons (id, course_id, chapter_id, title, hint, lesson_order, status, version, updated_at)
SELECT lesson_id, course_id, chapter_id, title,
       'Bài học mẫu thuộc chương trình ' || program_name,
       lesson_order, 'DRAFT', 0, CURRENT_TIMESTAMP
FROM seed_catalog_lesson_nodes
ON CONFLICT DO NOTHING;

INSERT INTO lesson_contents (id, lesson_id, text_body, video_source, video_url, version)
SELECT l.id || '-CONTENT', l.id,
       format(E'%s\n\nLĩnh vực: %s\nChương trình: %s\nKhóa học: %s\n\nGiới thiệu khóa học\n%s\n\nMục tiêu bài học\n%s\n\nHướng dẫn thực hành\n%s\n\nBài tập tình huống\n%s\n\nNội dung mẫu phục vụ thử nghiệm hệ thống đào tạo. Khi biên soạn khóa học thực tế, thay bằng tài liệu và quy trình đã được đơn vị phê duyệt.',
              n.title, n.domain_name, n.program_name, n.course_name,
              n.course_description, n.objective, n.guidance, n.exercise),
       'URL', NULL, 0
FROM seed_catalog_lesson_nodes n
JOIN lessons l ON l.id = n.lesson_id AND l.course_id = n.course_id AND l.chapter_id = n.chapter_id
ON CONFLICT DO NOTHING;

-- Match the API's publish prerequisite: every chapter has lessons and every
-- lesson has a content record with text, video, documents or procedure sections.
-- Existing incomplete custom chapters prevent publication of that course.
CREATE TEMP TABLE seed_catalog_publish_courses ON COMMIT DROP AS
SELECT c.id
FROM courses c
JOIN seed_catalog_courses s ON s.code = c.code AND c.id = s.code
WHERE c.status IN ('DRAFT', 'PUBLISHED')
  AND EXISTS (SELECT 1 FROM chapters ch WHERE ch.course_id = c.id)
  AND NOT EXISTS (
      SELECT 1 FROM chapters ch
      WHERE ch.course_id = c.id
        AND (
            NOT EXISTS (SELECT 1 FROM lessons l WHERE l.chapter_id = ch.id)
            OR EXISTS (
                SELECT 1 FROM lessons l
                LEFT JOIN lesson_contents lc ON lc.lesson_id = l.id
                WHERE l.chapter_id = ch.id
                  AND (lc.id IS NULL OR (
                      btrim(coalesce(lc.text_body, '')) = ''
                      AND btrim(coalesce(lc.video_url, '')) = ''
                      AND NOT EXISTS (SELECT 1 FROM lesson_documents ld WHERE ld.lesson_id = l.id)
                      AND NOT EXISTS (SELECT 1 FROM procedure_sections ps WHERE ps.lesson_id = l.id)
                  ))
            )
        )
  );

UPDATE lessons l SET status = 'PUBLISHED', version = coalesce(l.version, 0) + 1, updated_at = CURRENT_TIMESTAMP
FROM seed_catalog_publish_courses s
WHERE l.course_id = s.id AND l.status IS DISTINCT FROM 'PUBLISHED';

UPDATE chapters ch SET status = 'PUBLISHED', version = coalesce(ch.version, 0) + 1
FROM seed_catalog_publish_courses s
WHERE ch.course_id = s.id AND ch.status IS DISTINCT FROM 'PUBLISHED';

UPDATE courses c SET status = 'PUBLISHED', version = coalesce(c.version, 0) + 1
FROM seed_catalog_publish_courses s
WHERE c.id = s.id AND c.status IS DISTINCT FROM 'PUBLISHED';

-- Verify totals and show the complete domain -> program -> course -> chapter -> lesson tree.
SELECT
    (SELECT count(*) FROM training_domains d JOIN seed_catalog_domains s ON s.code = d.code) AS domains,
    (SELECT count(*) FROM training_programs p JOIN seed_catalog_programs s ON s.code = p.code) AS programs,
    (SELECT count(*) FROM courses c JOIN seed_catalog_courses s ON s.code = c.code) AS courses,
    (SELECT count(*) FROM seed_catalog_publish_courses) AS published_courses,
    (SELECT count(*) FROM chapters ch JOIN courses c ON c.id = ch.course_id
     JOIN seed_catalog_courses s ON s.code = c.code AND c.id = s.code) AS chapters,
    (SELECT count(*) FROM lessons l JOIN courses c ON c.id = l.course_id
     JOIN seed_catalog_courses s ON s.code = c.code AND c.id = s.code) AS lessons,
    (SELECT count(*) FROM lesson_contents lc JOIN lessons l ON l.id = lc.lesson_id
     JOIN courses c ON c.id = l.course_id
     JOIN seed_catalog_courses s ON s.code = c.code AND c.id = s.code) AS lesson_contents;

SELECT d.code AS domain_code, d.name AS domain_name,
       p.code AS program_code, p.name AS program_name,
       c.code AS course_code, c.name AS course_name, c.status AS course_status,
       ch.code AS chapter_code, ch.title AS chapter_title,
       l.id AS lesson_id, l.title AS lesson_title, l.status AS lesson_status
FROM courses c
JOIN seed_catalog_courses s ON s.code = c.code
JOIN training_programs p ON p.id = c.program_id
JOIN training_domains d ON d.id = p.domain_id
LEFT JOIN chapters ch ON ch.course_id = c.id
LEFT JOIN lessons l ON l.chapter_id = ch.id AND l.course_id = c.id
ORDER BY d.code, p.code, c.code, ch.chapter_order, l.lesson_order;

COMMIT;
