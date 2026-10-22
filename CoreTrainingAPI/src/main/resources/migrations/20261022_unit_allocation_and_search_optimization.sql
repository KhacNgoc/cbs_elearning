-- ============================================================================
-- CONSOLIDATED DATABASE MIGRATION SCRIPT
-- File: 20261022_unit_allocation_and_search_optimization.sql
-- Mục đích: Tổng hợp toàn bộ thay đổi cơ sở dữ liệu trên nhánh donv phục vụ deploy:
--   1. Cấu trúc danh mục đào tạo 5 tầng (Lĩnh vực, Chương trình, Khóa học, Chương, Bài)
--   2. Phân hệ (business_module), cấp độ (level), số thứ tự (seq_number)
--   3. Các bảng phân bổ đơn vị áp dụng (domain_unit_grants, program_unit_grants, course_unit_grants)
--   4. Tương thích chuyển đổi topic_id -> course_id trên các bảng nghiệp vụ
--   5. Tối ưu tìm kiếm: Bật pg_trgm và tạo 4 GIN Trigram Indexes cho courses & training_programs
--   6. Khởi tạo đơn vị tổ chức mẫu (nếu chưa có)
-- ============================================================================

BEGIN;

-- ----------------------------------------------------------------------------
-- 1. BẬT EXTENSION TÌM KIẾM MỜ / CHUỖI CON (POSTGRESQL TRIGRAM)
-- ----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pg_trgm;


-- ----------------------------------------------------------------------------
-- 2. CẬP NHẬT CẤU TRÚC CÁC BẢNG DANH MỤC ĐÀO TẠO
-- ----------------------------------------------------------------------------

-- Bảng training_domains (Lĩnh vực đào tạo)
ALTER TABLE training_domains ADD COLUMN IF NOT EXISTS business_module VARCHAR(50);
ALTER TABLE training_domains ADD COLUMN IF NOT EXISTS seq_number INTEGER;
CREATE INDEX IF NOT EXISTS idx_domain_module_seq ON training_domains (business_module, seq_number DESC);

-- Bảng training_programs (Chương trình đào tạo)
ALTER TABLE training_programs ADD COLUMN IF NOT EXISTS business_module VARCHAR(255);
ALTER TABLE training_programs ADD COLUMN IF NOT EXISTS level VARCHAR(255);
ALTER TABLE training_programs ADD COLUMN IF NOT EXISTS seq_number INTEGER;
CREATE INDEX IF NOT EXISTS idx_prog_module_seq ON training_programs (business_module, seq_number DESC);

-- Bảng courses (Khóa học)
ALTER TABLE courses ADD COLUMN IF NOT EXISTS seq_number INTEGER;
CREATE INDEX IF NOT EXISTS idx_course_module_seq ON courses (business_module, seq_number DESC);

-- Bảng users (Chuẩn hóa độ dài status và default)
ALTER TABLE users ALTER COLUMN status TYPE VARCHAR(32);
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE';


-- ----------------------------------------------------------------------------
-- 3. TẠO CÁC BẢNG PHÂN BỔ THEO ĐƠN VỊ (UNIT GRANTS)
-- ----------------------------------------------------------------------------

-- Phân bổ đơn vị cho Lĩnh vực đào tạo
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

-- Phân bổ đơn vị cho Chương trình đào tạo
CREATE TABLE IF NOT EXISTS program_unit_grants (
    program_id VARCHAR(255) NOT NULL REFERENCES training_programs(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    grant_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (program_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_prog_unit_grants_unit ON program_unit_grants (unit_id);

-- Phân bổ đơn vị cho Khóa học (nếu chưa có)
CREATE TABLE IF NOT EXISTS course_unit_grants (
    course_id VARCHAR(255) NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL,
    grant_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (course_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_course_unit_grants_unit ON course_unit_grants (unit_id);


-- ----------------------------------------------------------------------------
-- 4. TỐI ƯU HÓA TÌM KIẾM BẰNG GIN TRIGRAM INDEX (pg_trgm)
-- ----------------------------------------------------------------------------

-- Dọn dẹp các functional index cũ nếu có
DROP INDEX IF EXISTS idx_courses_name_lower_trgm;
DROP INDEX IF EXISTS idx_courses_code_lower_trgm;
DROP INDEX IF EXISTS idx_programs_name_lower_trgm;
DROP INDEX IF EXISTS idx_programs_code_lower_trgm;

-- Tạo 2 index GIN Trigram cho bảng courses
CREATE INDEX IF NOT EXISTS idx_courses_name_trgm ON courses USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_courses_code_trgm ON courses USING gin (code gin_trgm_ops);

-- Tạo 2 index GIN Trigram cho bảng training_programs
CREATE INDEX IF NOT EXISTS idx_programs_name_trgm ON training_programs USING gin (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_programs_code_trgm ON training_programs USING gin (code gin_trgm_ops);


-- ----------------------------------------------------------------------------
-- 5. ĐỒNG BỘ DỮ LIỆU TƯƠNG THÍCH KHÓA NGOẠI course_id THAY CHO topic_id
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
-- 6. KHỞI TẠO ĐƠN VỊ TỔ CHỨC CƠ BẢN (NẾU CHƯA CÓ)
-- ----------------------------------------------------------------------------
INSERT INTO organization_units (id, name, type, active)
VALUES
    ('HO', 'Hội sở chính', 'HEADQUARTERS', true),
    ('CN_HANOI', 'Chi nhánh Hà Nội', 'BRANCH', true),
    ('CN_HCM', 'Chi nhánh TP. Hồ Chí Minh', 'BRANCH', true),
    ('CN_DANANG', 'Chi nhánh Đà Nẵng', 'BRANCH', true),
    ('KHOI_KHDN', 'Khối Khách hàng Doanh nghiệp', 'DIVISION', true),
    ('KHOI_KHBL', 'Khối Bán lẻ', 'DIVISION', true),
    ('KHOI_CNTT', 'Khối Công nghệ Thông tin', 'DIVISION', true)
ON CONFLICT (id) DO NOTHING;

COMMIT;
