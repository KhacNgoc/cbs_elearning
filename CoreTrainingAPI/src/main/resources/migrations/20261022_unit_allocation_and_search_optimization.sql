-- ============================================================================
-- CONSOLIDATED DATABASE MIGRATION SCRIPT (SINGLE UNIFIED SCRIPT)
-- File: 20261022_unit_allocation_and_search_optimization.sql
-- Mục đích: Tổng hợp toàn bộ thay đổi cơ sở dữ liệu phục vụ deploy:
--   1. Bật extension pg_trgm phục vụ tìm kiếm nhanh theo chuỗi con.
--   2. Cấu trúc Danh mục đào tạo 5 tầng (Lĩnh vực, Chương trình, Khóa học, Chương, Bài):
--      - Bổ sung business_module, level, seq_number, keywords, completion_deadline, owner_unit_id.
--      - Tối ưu chỉ mục phục vụ Lazy-Fetch & Server-Side Pagination theo từng nhánh cây.
--   3. Hệ thống Phân bổ Đào tạo theo Đơn vị (Unit Allocation):
--      - domain_unit_grants & training_domain_units (Phân bổ cấp Lĩnh vực: MANDATORY / OPTIONAL)
--      - program_unit_grants & training_program_units (Phân bổ cấp Chương trình: MANDATORY / OPTIONAL)
--      - course_unit_grants (Phân bổ cấp Khóa học: MANDATORY / OPTIONAL)
--      - user_registered_courses (Tự động ghi danh nhân sự phòng ban khi gán MANDATORY)
--   4. Tối ưu hóa tìm kiếm bằng 4 GIN Trigram Indexes (pg_trgm) cho courses & training_programs.
--   5. Đồng bộ Menu điều hướng (navigation_items) và Quyền hạn (admin_pages, access_role_permissions).
--   6. Đồng bộ tương thích khóa ngoại course_id thay cho topic_id & khởi tạo đơn vị tổ chức mẫu.
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. BẬT EXTENSION TÌM KIẾM MỜ / CHUỖI CON (POSTGRESQL TRIGRAM)
-- ----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- ----------------------------------------------------------------------------
-- 2. CẬP NHẬT CẤU TRÚC CÁC BẢNG DANH MỤC ĐÀO TẠO & CHỈ MỤC LAZY-FETCH
-- ----------------------------------------------------------------------------

-- 2.1. Bảng training_domains (Lĩnh vực đào tạo)
ALTER TABLE training_domains ADD COLUMN IF NOT EXISTS business_module VARCHAR(50);
ALTER TABLE training_domains ADD COLUMN IF NOT EXISTS seq_number INTEGER;
CREATE INDEX IF NOT EXISTS idx_domain_module_seq ON training_domains (business_module, seq_number DESC);
CREATE INDEX IF NOT EXISTS idx_domains_status ON training_domains (status);

-- 2.2. Bảng training_programs (Chương trình đào tạo)
ALTER TABLE training_programs ADD COLUMN IF NOT EXISTS business_module VARCHAR(255);
ALTER TABLE training_programs ADD COLUMN IF NOT EXISTS level VARCHAR(255);
ALTER TABLE training_programs ADD COLUMN IF NOT EXISTS seq_number INTEGER;
CREATE INDEX IF NOT EXISTS idx_prog_module_seq ON training_programs (business_module, seq_number DESC);
CREATE INDEX IF NOT EXISTS idx_programs_domain_status ON training_programs (domain_id, status);

-- 2.3. Bảng courses (Khóa học)
ALTER TABLE courses ADD COLUMN IF NOT EXISTS business_module VARCHAR(255);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS level VARCHAR(255);
ALTER TABLE courses ADD COLUMN IF NOT EXISTS keywords TEXT;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS completion_deadline DATE;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS seq_number INTEGER;
ALTER TABLE courses ADD COLUMN IF NOT EXISTS owner_unit_id VARCHAR(255) REFERENCES organization_units(id);
CREATE INDEX IF NOT EXISTS idx_course_module_seq ON courses (business_module, seq_number DESC);
CREATE INDEX IF NOT EXISTS idx_courses_program_status ON courses (program_id, status);
CREATE INDEX IF NOT EXISTS idx_courses_owner_unit ON courses (owner_unit_id);

-- 2.3b. Đồng bộ dữ liệu sinh mã tự động (business_module & seq_number) cho dữ liệu hiện hữu
-- Trích xuất số thứ tự cuối mã (VD: RMLV1 -> 1, RMCT2 -> 2, RMKH3 -> 3) để hàm MAX(seq_number) sinh mã tiếp theo chính xác
UPDATE training_domains
SET business_module = COALESCE(business_module, NULLIF(REGEXP_REPLACE(UPPER(code), '(LV|_)?[0-9]+$', ''), ''))
WHERE business_module IS NULL AND code IS NOT NULL;

UPDATE training_domains
SET seq_number = COALESCE(CAST(SUBSTRING(code FROM '([0-9]+)$') AS INTEGER), 1)
WHERE seq_number IS NULL AND code IS NOT NULL;

UPDATE training_programs p
SET business_module = COALESCE(p.business_module, d.business_module, NULLIF(REGEXP_REPLACE(UPPER(p.code), '(CT|_)?[0-9]+$', ''), ''))
FROM training_domains d
WHERE p.domain_id = d.id AND p.business_module IS NULL;

UPDATE training_programs
SET seq_number = COALESCE(CAST(SUBSTRING(code FROM '([0-9]+)$') AS INTEGER), 1)
WHERE seq_number IS NULL AND code IS NOT NULL;

UPDATE courses c
SET business_module = COALESCE(c.business_module, p.business_module, NULLIF(REGEXP_REPLACE(UPPER(c.code), '(KH|_)?[0-9]+$', ''), ''))
FROM training_programs p
WHERE c.program_id = p.id AND c.business_module IS NULL;

UPDATE courses
SET seq_number = COALESCE(CAST(SUBSTRING(code FROM '([0-9]+)$') AS INTEGER), 1)
WHERE seq_number IS NULL AND code IS NOT NULL;

-- 2.4. Bảng chapters & lessons (Tối ưu Lazy-Fetch Chương & Bài học theo thứ tự)
CREATE INDEX IF NOT EXISTS idx_chapters_course_order ON chapters (course_id, chapter_order, id);
CREATE INDEX IF NOT EXISTS idx_lessons_chapter_order ON lessons (chapter_id, lesson_order, id);

-- 2.5. Bảng users (Chuẩn hóa độ dài status và default)
ALTER TABLE users ALTER COLUMN status TYPE VARCHAR(32);
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE';


-- ----------------------------------------------------------------------------
-- 3. TẠO CÁC BẢNG PHÂN BỔ ĐÀO TẠO THEO ĐƠN VỊ (UNIT ALLOCATION & AUTO-ENROLL)
-- ----------------------------------------------------------------------------

-- 3.1. Phân bổ đơn vị cho Lĩnh vực đào tạo (MANDATORY / OPTIONAL)
CREATE TABLE IF NOT EXISTS domain_unit_grants (
    domain_id VARCHAR(255) NOT NULL REFERENCES training_domains(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    grant_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (domain_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_domain_unit_grants_unit ON domain_unit_grants (unit_id);

CREATE TABLE IF NOT EXISTS training_domain_units (
    domain_id VARCHAR(255) NOT NULL REFERENCES training_domains(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    PRIMARY KEY (domain_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_training_domain_units_unit ON training_domain_units (unit_id);

-- 3.2. Phân bổ đơn vị cho Chương trình đào tạo (MANDATORY / OPTIONAL)
CREATE TABLE IF NOT EXISTS program_unit_grants (
    program_id VARCHAR(255) NOT NULL REFERENCES training_programs(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    grant_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (program_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_prog_unit_grants_unit ON program_unit_grants (unit_id);

CREATE TABLE IF NOT EXISTS training_program_units (
    program_id VARCHAR(255) NOT NULL REFERENCES training_programs(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    PRIMARY KEY (program_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_training_program_units_unit ON training_program_units (unit_id);

-- 3.3. Phân bổ đơn vị cho Khóa học (MANDATORY / OPTIONAL)
CREATE TABLE IF NOT EXISTS course_unit_grants (
    course_id VARCHAR(255) NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    grant_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (course_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_course_unit_grants_unit ON course_unit_grants (unit_id);

-- 3.4. Bảng ghi danh khóa học của học viên (Tự động ghi danh khi phân bổ MANDATORY)
CREATE TABLE IF NOT EXISTS user_registered_courses (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    course_id VARCHAR(255) NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, course_id)
);
CREATE INDEX IF NOT EXISTS idx_user_registered_courses_course ON user_registered_courses (course_id);


-- ----------------------------------------------------------------------------
-- 4. TỐI ƯU HÓA TÌM KIẾM BẰNG GIN TRIGRAM INDEX (pg_trgm)
-- ----------------------------------------------------------------------------

-- Dọn dẹp các functional index cũ nếu có
DROP INDEX IF EXISTS idx_courses_name_lower_trgm;
DROP INDEX IF EXISTS idx_courses_code_lower_trgm;
DROP INDEX IF EXISTS idx_programs_name_lower_trgm;
DROP INDEX IF EXISTS idx_programs_code_lower_trgm;

-- 2 GIN Trigram indexes cho bảng courses
CREATE INDEX IF NOT EXISTS idx_courses_name_trgm ON courses USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_code_trgm ON courses USING gin (code gin_trgm_ops);

-- 2 GIN Trigram indexes cho bảng training_programs
CREATE INDEX IF NOT EXISTS idx_programs_name_trgm ON training_programs USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_programs_code_trgm ON training_programs USING gin (code gin_trgm_ops);


-- ----------------------------------------------------------------------------
-- 5. ĐỒNG BỘ MENU ĐIỀU HƯỚNG & PHÂN QUYỀN QUẢN TRỊ ĐÀO TẠO
-- ----------------------------------------------------------------------------

-- Bổ sung cột permission_page cho navigation_items (nếu chưa có)
ALTER TABLE navigation_items ADD COLUMN IF NOT EXISTS permission_page VARCHAR(255);

-- Cập nhật nhãn & trạng thái hiển thị menu Danh mục đào tạo & Phân bổ theo đơn vị
UPDATE navigation_items
SET label = 'Danh mục đào tạo', visible = true, permission_page = 'courses'
WHERE id = 'admin.courses';

UPDATE navigation_items
SET visible = false
WHERE id IN ('admin.curriculum', 'admin.catalog');

INSERT INTO navigation_items (id, label, route, icon, area, sort_order, visible, permission_page)
VALUES ('admin.unit-allocation', 'Phân bổ theo đơn vị', '/admin/unit-allocation', 'Building2', 'ADMIN', 107, true, 'courses')
ON CONFLICT (id) DO UPDATE
SET label = EXCLUDED.label,
    route = EXCLUDED.route,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = true,
    permission_page = 'courses';

INSERT INTO navigation_item_roles (navigation_id, role_name)
SELECT 'admin.unit-allocation', 'ROLE_ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM navigation_item_roles
    WHERE navigation_id = 'admin.unit-allocation' AND role_name = 'ROLE_ADMIN'
);

-- Đồng bộ quyền courses:* và catalog:* cho các Role quản trị hệ thống (protected_role = true)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'admin_page_actions') THEN
        INSERT INTO admin_page_actions (page_id, action)
        SELECT p.id, a.action
        FROM admin_pages p
        CROSS JOIN (VALUES ('create'), ('read'), ('update'), ('delete')) AS a(action)
        WHERE p.id IN ('courses', 'catalog')
        ON CONFLICT DO NOTHING;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = 'access_role_permissions') THEN
        INSERT INTO access_role_permissions (role_id, permission)
        SELECT r.id, perm.p
        FROM access_roles r
        CROSS JOIN (
            VALUES
                ('courses:create'), ('courses:read'), ('courses:update'), ('courses:delete'),
                ('catalog:create'), ('catalog:read'), ('catalog:update'), ('catalog:delete')
        ) AS perm(p)
        WHERE r.protected_role = true
        ON CONFLICT DO NOTHING;
    END IF;
END $$;


-- ----------------------------------------------------------------------------
-- 6. ĐỒNG BỘ DỮ LIỆU TƯƠNG THÍCH KHÓA NGOẠI course_id THAY CHO topic_id
-- ----------------------------------------------------------------------------

-- Bảng user_quiz_scores
ALTER TABLE user_quiz_scores ADD COLUMN IF NOT EXISTS course_id VARCHAR(255);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'user_quiz_scores' AND column_name = 'topic_id') THEN
        UPDATE user_quiz_scores SET course_id = topic_id WHERE course_id IS NULL AND topic_id IS NOT NULL;
    END IF;
END $$;

-- Bảng questions
ALTER TABLE questions ADD COLUMN IF NOT EXISTS course_id VARCHAR(255);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'questions' AND column_name = 'topic_id') THEN
        UPDATE questions SET course_id = topic_id WHERE course_id IS NULL AND topic_id IS NOT NULL;
    END IF;
END $$;

-- Bảng badges
ALTER TABLE badges ADD COLUMN IF NOT EXISTS course_id VARCHAR(255);
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'badges' AND column_name = 'topic_id') THEN
        UPDATE badges SET course_id = topic_id WHERE course_id IS NULL AND topic_id IS NOT NULL;
    END IF;
END $$;

-- Bảng lessons
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema() AND table_name = 'lessons' AND column_name = 'topic_id') THEN
        ALTER TABLE lessons ALTER COLUMN topic_id DROP NOT NULL;
        UPDATE lessons SET course_id = topic_id WHERE course_id IS NULL AND topic_id IS NOT NULL;
    END IF;
END $$;


-- ----------------------------------------------------------------------------
-- 7. KHỞI TẠO ĐƠN VỊ TỔ CHỨC CƠ BẢN (NẾU CHƯA CÓ)
-- ----------------------------------------------------------------------------
INSERT INTO organization_units (id, name, type, active, parent_id) VALUES
    ('HO', 'Hội sở chính BIDV', 'UNIT', true, NULL),
    ('HO_QLRR', 'Ban Quản trị rủi ro & Tuân thủ', 'DEPARTMENT', true, 'HO'),
    ('HO_KHDN', 'Ban Khách hàng doanh nghiệp', 'DEPARTMENT', true, 'HO'),
    ('HO_KHCN', 'Ban Khách hàng cá nhân', 'DEPARTMENT', true, 'HO'),
    ('HO_CNTT', 'Trung tâm Công nghệ thông tin', 'DEPARTMENT', true, 'HO'),
    ('CN_HANOI', 'BIDV Chi nhánh Hà Nội', 'UNIT', true, NULL),
    ('CN_HANOI_PGD1', 'Phòng Giao dịch Tràng Tiền', 'DEPARTMENT', true, 'CN_HANOI'),
    ('CN_HOANKIEM', 'BIDV Chi nhánh Hoàn Kiếm', 'UNIT', true, NULL),
    ('CN_HOANKIEM_PGD1', 'Phòng Giao dịch Lý Thường Kiệt', 'DEPARTMENT', true, 'CN_HOANKIEM'),
    ('CN_SAIGON', 'BIDV Chi nhánh TP. Hồ Chí Minh', 'UNIT', true, NULL),
    ('CN_SAIGON_PGD1', 'Phòng Giao dịch Bến Thành', 'DEPARTMENT', true, 'CN_SAIGON')
ON CONFLICT (id) DO NOTHING;

COMMIT;
