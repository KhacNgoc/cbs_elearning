-- Migration: 20261021_curriculum_structure_unit_grants.sql
-- Mục đích: Đồng bộ cấu trúc DB phục vụ deploy server:
--   1. Bổ sung trường phân hệ (business_module), cấp độ (level), và số thứ tự sinh mã (seq_number) cho Lĩnh vực, Chương trình, Khóa học.
--   2. Phân bổ đơn vị áp dụng cho Chương trình đào tạo (program_unit_grants) và Lĩnh vực đào tạo (domain_unit_grants).
--   3. Đồng bộ tương thích khóa ngoại course_id thay cho topic_id trên các bảng liên quan.
--   4. Mở rộng độ dài trường status trong bảng users.
--   5. Khởi tạo danh mục đơn vị tổ chức cơ bản (nếu chưa có).

BEGIN;

-- ============================================================================
-- 1. CẬP NHẬT CẤU TRÚC BẢNG LĨNH VỰC & CHƯƠNG TRÌNH & KHÓA HỌC
-- ============================================================================

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


-- ============================================================================
-- 2. TẠO CÁC BẢNG PHÂN BỔ ĐƠN VỊ ÁP DỤNG (UNIT GRANTS)
-- ============================================================================

-- Phân bổ đơn vị áp dụng cho Chương trình đào tạo (MANDATORY / OPTIONAL)
CREATE TABLE IF NOT EXISTS program_unit_grants (
    program_id VARCHAR(255) NOT NULL REFERENCES training_programs(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    grant_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (program_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_prog_unit_grants_unit ON program_unit_grants (unit_id);

-- Phân bổ đơn vị áp dụng cho Lĩnh vực đào tạo (MANDATORY / OPTIONAL)
CREATE TABLE IF NOT EXISTS domain_unit_grants (
    domain_id VARCHAR(255) NOT NULL REFERENCES training_domains(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    grant_type VARCHAR(255) NOT NULL,
    PRIMARY KEY (domain_id, unit_id)
);
CREATE INDEX IF NOT EXISTS idx_domain_unit_grants_unit ON domain_unit_grants (unit_id);

-- Bảng liên kết Lĩnh vực - Đơn vị phụ trợ
CREATE TABLE IF NOT EXISTS training_domain_units (
    domain_id VARCHAR(255) NOT NULL REFERENCES training_domains(id) ON DELETE CASCADE,
    unit_id VARCHAR(255) NOT NULL REFERENCES organization_units(id) ON DELETE CASCADE,
    PRIMARY KEY (domain_id, unit_id)
);


-- ============================================================================
-- 3. TƯƠNG THÍCH KHÓA NGOẠI course_id THAY THẾ CHO topic_id
-- ============================================================================

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
        UPDATE lessons SET course_id = topic_id WHERE course_id IS NULL AND topic_id IS NOT NULL;
    END IF;
END $$;


-- ============================================================================
-- 4. ĐIỀU CHỈNH CẤU TRÚC BẢNG USERS
-- ============================================================================

ALTER TABLE users ALTER COLUMN status TYPE VARCHAR(32);
ALTER TABLE users ALTER COLUMN status SET DEFAULT 'ACTIVE';


-- ============================================================================
-- 5. DANH MỤC ĐƠN VỊ TỔ CHỨC CƠ BẢN (NẾU CHƯA CÓ)
-- ============================================================================

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
