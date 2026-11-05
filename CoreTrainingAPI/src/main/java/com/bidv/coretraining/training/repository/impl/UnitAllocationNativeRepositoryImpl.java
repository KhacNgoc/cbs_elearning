package com.bidv.coretraining.training.repository.impl;

import com.bidv.coretraining.course.dto.CourseDto;
import com.bidv.coretraining.training.dto.PagedResult;
import com.bidv.coretraining.training.dto.TrainingStructureDto.DomainListItem;
import com.bidv.coretraining.training.dto.TrainingStructureDto.ProgramListItem;
import com.bidv.coretraining.training.dto.TrainingStructureDto.UnitAllocationDirectoryDto;
import com.bidv.coretraining.training.repository.UnitAllocationNativeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Set-based native SQL for unit allocation, staff enrollment and catalog soft deletes (PostgreSQL). */
@Repository
public class UnitAllocationNativeRepositoryImpl implements UnitAllocationNativeRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public UnitAllocationNativeRepositoryImpl(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int enrollStaffForCourses(Collection<String> courseIds, Collection<String> unitIds) {
        if (courseIds == null || courseIds.isEmpty() || unitIds == null || unitIds.isEmpty()) {
            return 0;
        }
        String sql = """
            WITH RECURSIVE target_units AS (
                SELECT id FROM organization_units WHERE id IN (:unitIds)
                UNION ALL
                SELECT ou.id FROM organization_units ou INNER JOIN target_units tu ON ou.parent_id = tu.id
            )
            INSERT INTO user_registered_courses (user_id, course_id)
            SELECT u.id, c.id
            FROM users u
            CROSS JOIN courses c
            WHERE u.status = 'ACTIVE'
              AND u.unit_id IN (SELECT id FROM target_units)
              AND c.id IN (:courseIds)
            ON CONFLICT (user_id, course_id) DO NOTHING
            """;
        MapSqlParameterSource params =
                new MapSqlParameterSource();
        params.addValue("unitIds", unitIds);
        params.addValue("courseIds", courseIds);
        return jdbc.update(sql, params);
    }

    @Override
    public int unenrollUnstartedStaff(Collection<String> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return 0;
        }
        String sql = """
            DELETE FROM user_registered_courses urc
            WHERE urc.course_id IN (:courseIds)
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_completed_lessons ucl
                  JOIN lessons l ON ucl.lesson_id = l.id
                  WHERE ucl.user_id = urc.user_id AND l.course_id = urc.course_id
              )
            """;
        return jdbc.update(sql, Map.of("courseIds", courseIds));
    }

    @Override
    public int unenrollUnstartedStaff(Collection<String> courseIds, Collection<String> unitIds) {
        if (courseIds == null || courseIds.isEmpty() || unitIds == null || unitIds.isEmpty()) {
            return 0;
        }
        String sql = """
            WITH RECURSIVE target_units AS (
                SELECT id FROM organization_units WHERE id IN (:unitIds)
                UNION ALL
                SELECT ou.id FROM organization_units ou INNER JOIN target_units tu ON ou.parent_id = tu.id
            )
            DELETE FROM user_registered_courses urc
            USING users u
            WHERE urc.user_id = u.id
              AND urc.course_id IN (:courseIds)
              AND u.unit_id IN (SELECT id FROM target_units)
              AND NOT EXISTS (
                  SELECT 1
                  FROM user_completed_lessons ucl
                  JOIN lessons l ON ucl.lesson_id = l.id
                  WHERE ucl.user_id = u.id AND l.course_id = urc.course_id
              )
            """;
        MapSqlParameterSource params =
                new MapSqlParameterSource();
        params.addValue("unitIds", unitIds);
        params.addValue("courseIds", courseIds);
        return jdbc.update(sql, params);
    }

    @Override
    public int countActiveStaffInUnits(Collection<String> unitIds) {
        if (unitIds == null || unitIds.isEmpty()) return 0;
        String sql = """
            WITH RECURSIVE target_units AS (
                SELECT id FROM organization_units WHERE id IN (:unitIds)
                UNION ALL
                SELECT ou.id FROM organization_units ou INNER JOIN target_units tu ON ou.parent_id = tu.id
            )
            SELECT COUNT(*) FROM users u
            WHERE u.status = 'ACTIVE'
              AND u.unit_id IN (SELECT id FROM target_units)
            """;
        MapSqlParameterSource params =
                new MapSqlParameterSource("unitIds", unitIds);
        Integer count = jdbc.queryForObject(sql, params, Integer.class);
        return count != null ? count : 0;
    }

    @Override
    public List<String> courseIdsInDomain(String domainId) {
        return jdbc.queryForList(
                "SELECT c.id FROM courses c JOIN training_programs p ON c.program_id = p.id WHERE p.domain_id = :domainId",
                Map.of("domainId", domainId), String.class);
    }

    @Override
    public List<String> courseIdsInProgram(String programId) {
        return jdbc.queryForList("SELECT id FROM courses WHERE program_id = :programId",
                Map.of("programId", programId), String.class);
    }

    @Override
    public void softDeleteDomainTree(String domainId, String suffix, int negBase) {
        Map<String, Object> params = Map.of("domainId", domainId, "suffix", suffix, "negBase", negBase);
        // 2. Bulk soft delete lessons
        jdbc.update(
                "WITH numbered AS (" +
                "  SELECT l.id, ROW_NUMBER() OVER () as rn " +
                "  FROM lessons l JOIN courses c ON l.course_id = c.id JOIN training_programs p ON c.program_id = p.id " +
                "  WHERE p.domain_id = :domainId AND l.status <> 'DELETED'" +
                ") " +
                "UPDATE lessons l SET status = 'DELETED', lesson_order = :negBase - numbered.rn " +
                "FROM numbered WHERE l.id = numbered.id", params);

        // 3. Bulk soft delete chapters
        jdbc.update(
                "WITH numbered AS (" +
                "  SELECT ch.id, ROW_NUMBER() OVER () as rn " +
                "  FROM chapters ch JOIN courses c ON ch.course_id = c.id JOIN training_programs p ON c.program_id = p.id " +
                "  WHERE p.domain_id = :domainId AND ch.status <> 'DELETED'" +
                ") " +
                "UPDATE chapters ch SET status = 'DELETED', " +
                "code = CASE WHEN ch.code NOT LIKE '%_DEL_%' THEN ch.code || :suffix ELSE ch.code END, " +
                "chapter_order = :negBase - numbered.rn " +
                "FROM numbered WHERE ch.id = numbered.id", params);

        // 4. Bulk soft delete courses
        jdbc.update(
                "UPDATE courses SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE program_id IN (SELECT id FROM training_programs WHERE domain_id = :domainId) AND status <> 'DELETED'", params);

        // 5. Bulk soft delete programs
        jdbc.update(
                "UPDATE training_programs SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE domain_id = :domainId AND status <> 'DELETED'", params);

        // 6. Soft delete domain
        jdbc.update(
                "UPDATE training_domains SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE id = :domainId", params);
    }

    @Override
    public void softDeleteProgramTree(String programId, String suffix, int negBase) {
        Map<String, Object> params = Map.of("programId", programId, "suffix", suffix, "negBase", negBase);
        // 2. Bulk soft delete lessons
        jdbc.update(
                "WITH numbered AS (" +
                "  SELECT l.id, ROW_NUMBER() OVER () as rn " +
                "  FROM lessons l JOIN courses c ON l.course_id = c.id " +
                "  WHERE c.program_id = :programId AND l.status <> 'DELETED'" +
                ") " +
                "UPDATE lessons l SET status = 'DELETED', lesson_order = :negBase - numbered.rn " +
                "FROM numbered WHERE l.id = numbered.id", params);

        // 3. Bulk soft delete chapters
        jdbc.update(
                "WITH numbered AS (" +
                "  SELECT ch.id, ROW_NUMBER() OVER () as rn " +
                "  FROM chapters ch JOIN courses c ON ch.course_id = c.id " +
                "  WHERE c.program_id = :programId AND ch.status <> 'DELETED'" +
                ") " +
                "UPDATE chapters ch SET status = 'DELETED', " +
                "code = CASE WHEN ch.code NOT LIKE '%_DEL_%' THEN ch.code || :suffix ELSE ch.code END, " +
                "chapter_order = :negBase - numbered.rn " +
                "FROM numbered WHERE ch.id = numbered.id", params);

        // 4. Bulk soft delete courses
        jdbc.update(
                "UPDATE courses SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE program_id = :programId AND status <> 'DELETED'", params);

        // 5. Soft delete program
        jdbc.update(
                "UPDATE training_programs SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE id = :programId", params);
    }

    @Override
    public void softDeleteCourseTree(String courseId, String suffix, int negBase) {
        Map<String, Object> params = Map.of("courseId", courseId, "suffix", suffix, "negBase", negBase);
        // 2. Bulk soft delete lessons
        jdbc.update(
                "WITH numbered AS (" +
                "  SELECT id, ROW_NUMBER() OVER () as rn " +
                "  FROM lessons " +
                "  WHERE course_id = :courseId AND status <> 'DELETED'" +
                ") " +
                "UPDATE lessons l SET status = 'DELETED', lesson_order = :negBase - numbered.rn " +
                "FROM numbered WHERE l.id = numbered.id", params);

        // 3. Bulk soft delete chapters
        jdbc.update(
                "WITH numbered AS (" +
                "  SELECT id, ROW_NUMBER() OVER () as rn " +
                "  FROM chapters " +
                "  WHERE course_id = :courseId AND status <> 'DELETED'" +
                ") " +
                "UPDATE chapters ch SET status = 'DELETED', " +
                "code = CASE WHEN ch.code NOT LIKE '%_DEL_%' THEN ch.code || :suffix ELSE ch.code END, " +
                "chapter_order = :negBase - numbered.rn " +
                "FROM numbered WHERE ch.id = numbered.id", params);

        // 4. Bulk soft delete course
        jdbc.update(
                "UPDATE courses SET status = 'DELETED', " +
                "code = CASE WHEN code NOT LIKE '%_DEL_%' THEN code || :suffix ELSE code END " +
                "WHERE id = :courseId", params);
    }

    @Override
    public UnitAllocationDirectoryDto unitAllocationDirectory(String search, String type, String status, int page, int size) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String typeFilter = (type != null && !type.isBlank()) ? type.trim().toUpperCase() : "ALL";
        String statusFilter = (status != null && !status.isBlank()) ? status.trim().toUpperCase() : "ALL";

        int pageSize = size > 0 ? size : 10;
        int offset = Math.max(0, page) * pageSize;

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("search", query);
        params.addValue("limit", pageSize);
        params.addValue("offset", offset);

        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");
        if (query != null) {
            whereClause.append(" AND u.name ILIKE CONCAT('%', :search, '%') ");
        }
        if ("DEPARTMENT".equals(typeFilter)) {
            whereClause.append(" AND u.type = 'DEPARTMENT' ");
        } else if ("BRANCH".equals(typeFilter)) {
            whereClause.append(" AND u.type <> 'DEPARTMENT' ");
        }
        if ("ALLOCATED".equals(statusFilter)) {
            whereClause.append(" AND (EXISTS (SELECT 1 FROM domain_unit_grants dug WHERE dug.unit_id = u.id) OR " +
                               "      EXISTS (SELECT 1 FROM program_unit_grants pug WHERE pug.unit_id = u.id) OR " +
                               "      EXISTS (SELECT 1 FROM course_unit_grants cug WHERE cug.unit_id = u.id)) ");
        } else if ("UNALLOCATED".equals(statusFilter)) {
            whereClause.append(" AND NOT (EXISTS (SELECT 1 FROM domain_unit_grants dug WHERE dug.unit_id = u.id) OR " +
                               "          EXISTS (SELECT 1 FROM program_unit_grants pug WHERE pug.unit_id = u.id) OR " +
                               "          EXISTS (SELECT 1 FROM course_unit_grants cug WHERE cug.unit_id = u.id)) ");
        }

        String countSql = "SELECT COUNT(*) FROM organization_units u " + whereClause;
        Integer totalCount = jdbc.queryForObject(countSql, params, Integer.class);
        int totalElements = totalCount != null ? totalCount : 0;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        String dataSql = "SELECT u.id, u.name, u.type, u.active, u.parent_id, " +
                "(SELECT COUNT(DISTINCT dug.domain_id) FROM domain_unit_grants dug WHERE dug.unit_id = u.id) AS domain_count, " +
                "(SELECT COUNT(DISTINCT pug.program_id) FROM program_unit_grants pug WHERE pug.unit_id = u.id) AS program_count, " +
                "(SELECT COUNT(DISTINCT cug.course_id) FROM course_unit_grants cug WHERE cug.unit_id = u.id) AS course_count " +
                "FROM organization_units u " + whereClause + " ORDER BY u.name ASC LIMIT :limit OFFSET :offset";

        List<UnitAllocationDirectoryDto.Item> items = jdbc.query(dataSql, params, (rs, rowNum) -> {
            int dCount = rs.getInt("domain_count");
            int pCount = rs.getInt("program_count");
            int cCount = rs.getInt("course_count");
            return new UnitAllocationDirectoryDto.Item(rs.getString("id"), rs.getString("name"), rs.getString("type"),
                    rs.getBoolean("active"), rs.getString("parent_id"), dCount, pCount, cCount,
                    dCount > 0 || pCount > 0 || cCount > 0);
        });

        // Overview stats for the pills
        String statsSql = "SELECT " +
                "COUNT(*) AS total_count, " +
                "COUNT(CASE WHEN u.type = 'DEPARTMENT' THEN 1 END) AS department_count, " +
                "COUNT(CASE WHEN u.type <> 'DEPARTMENT' THEN 1 END) AS branch_count, " +
                "COUNT(CASE WHEN EXISTS (SELECT 1 FROM domain_unit_grants dug WHERE dug.unit_id = u.id) " +
                "             OR EXISTS (SELECT 1 FROM program_unit_grants pug WHERE pug.unit_id = u.id) " +
                "             OR EXISTS (SELECT 1 FROM course_unit_grants cug WHERE cug.unit_id = u.id) THEN 1 END) AS allocated_count " +
                "FROM organization_units u";

        Map<String, Object> stats = jdbc.queryForMap(statsSql, Collections.emptyMap());
        int totalUnits = ((Number) stats.getOrDefault("total_count", 0)).intValue();
        int allocatedUnits = ((Number) stats.getOrDefault("allocated_count", 0)).intValue();
        int branchUnits = ((Number) stats.getOrDefault("branch_count", 0)).intValue();
        int departmentUnits = ((Number) stats.getOrDefault("department_count", 0)).intValue();

        return new UnitAllocationDirectoryDto(items, page, pageSize, totalElements, Math.max(1, totalPages),
                new UnitAllocationDirectoryDto.Stats(totalUnits, allocatedUnits, Math.max(0, totalUnits - allocatedUnits),
                        branchUnits, departmentUnits));
    }

    @Override
    public List<Map<String, Object>> getUnitAllocatedItems(String unitId) {
        String sql = """
            SELECT 'domain' AS category_type, dug.domain_id AS target_id, dug.grant_type, d.code, d.name, NULL AS parent_id
            FROM domain_unit_grants dug
            JOIN training_domains d ON d.id = dug.domain_id
            WHERE dug.unit_id = :unitId AND d.status <> 'DELETED'
            UNION ALL
            SELECT 'program' AS category_type, pug.program_id AS target_id, pug.grant_type, p.code, p.name, p.domain_id AS parent_id
            FROM program_unit_grants pug
            JOIN training_programs p ON p.id = pug.program_id
            WHERE pug.unit_id = :unitId AND p.status <> 'DELETED'
            UNION ALL
            SELECT 'course' AS category_type, cug.course_id AS target_id, cug.grant_type, c.code, c.name, c.program_id AS parent_id
            FROM course_unit_grants cug
            JOIN courses c ON c.id = cug.course_id
            WHERE cug.unit_id = :unitId AND c.status <> 'DELETED'
        """;
        return jdbc.query(sql, Map.of("unitId", unitId), (rs, rowNum) -> {
            Map<String, Object> map = new HashMap<>();
            map.put("categoryType", rs.getString("category_type"));
            map.put("targetId", rs.getString("target_id"));
            map.put("grantType", rs.getString("grant_type"));
            map.put("code", rs.getString("code"));
            map.put("name", rs.getString("name"));
            map.put("parentId", rs.getString("parent_id"));
            return map;
        });
    }

    @Override
    public PagedResult<DomainListItem> adminDomainsPagedUnallocated(String search, int page, int size, String unitId) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        int pageSize = size > 0 ? size : 10;
        int offset = Math.max(0, page) * pageSize;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("search", query)
                .addValue("unitId", unitId)
                .addValue("limit", pageSize)
                .addValue("offset", offset);

        String whereClause = " WHERE d.status <> 'DELETED' " +
                (query != null ? " AND (d.name ILIKE CONCAT('%', :search, '%') OR d.code ILIKE CONCAT('%', :search, '%') " +
                                 "      OR EXISTS (SELECT 1 FROM training_programs p WHERE p.domain_id = d.id AND p.status <> 'DELETED' " +
                                 "                 AND (p.name ILIKE CONCAT('%', :search, '%') OR p.code ILIKE CONCAT('%', :search, '%') " +
                                 "                      OR EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                                 "                                 AND (c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')))))) " : "") +
                " AND ( " +
                "   EXISTS ( " +
                "     SELECT 1 FROM training_programs p WHERE p.domain_id = d.id AND p.status <> 'DELETED' " +
                "     AND ( " +
                "       EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                "               AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)) " +
                "       OR (NOT EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED') " +
                "           AND NOT EXISTS (SELECT 1 FROM program_unit_grants g WHERE g.program_id = p.id AND g.unit_id = :unitId)) " +
                "     ) " +
                "   ) " +
                "   OR ( " +
                "     NOT EXISTS (SELECT 1 FROM training_programs p WHERE p.domain_id = d.id AND p.status <> 'DELETED') " +
                "     AND NOT EXISTS (SELECT 1 FROM domain_unit_grants g WHERE g.domain_id = d.id AND g.unit_id = :unitId) " +
                "   ) " +
                " ) ";

        String countSql = "SELECT COUNT(*) FROM training_domains d " + whereClause;
        Integer totalCount = jdbc.queryForObject(countSql, params, Integer.class);
        int totalElements = totalCount != null ? totalCount : 0;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        String dataSql = "SELECT d.id, d.code, d.name, d.description, d.status, d.business_module, d.seq_number, " +
                "  (SELECT COUNT(*) FROM training_programs p WHERE p.domain_id = d.id AND p.status <> 'DELETED' " +
                "   AND ( " +
                "     EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                "             AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)) " +
                "     OR (NOT EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED') " +
                "         AND NOT EXISTS (SELECT 1 FROM program_unit_grants g WHERE g.program_id = p.id AND g.unit_id = :unitId)) " +
                "   )) AS programs_count " +
                "FROM training_domains d " + whereClause +
                "ORDER BY d.code ASC, d.name ASC LIMIT :limit OFFSET :offset";

        List<DomainListItem> items = jdbc.query(dataSql, params, (rs, rowNum) ->
                new DomainListItem(
                        rs.getString("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("status"),
                        Set.of("VIEW", "EDIT"),
                        rs.getString("business_module"),
                        rs.getObject("seq_number", Integer.class),
                        Collections.emptySet(),
                        Collections.emptySet(),
                        Collections.emptySet(),
                        rs.getLong("programs_count")
                ));

        return new PagedResult<>(items, page, pageSize, totalElements, Math.max(1, totalPages), (page + 1) < totalPages);
    }

    @Override
    public PagedResult<ProgramListItem> adminProgramsPagedUnallocated(String domainId, String search, int page, int size, String unitId) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        String dId = (domainId != null && !domainId.isBlank()) ? domainId.trim() : null;
        int pageSize = size > 0 ? size : 10;
        int offset = Math.max(0, page) * pageSize;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("domainId", dId)
                .addValue("search", query)
                .addValue("unitId", unitId)
                .addValue("limit", pageSize)
                .addValue("offset", offset);

        String whereClause = " WHERE p.status <> 'DELETED' " +
                (dId != null ? " AND p.domain_id = :domainId " : "") +
                (query != null ? " AND (p.name ILIKE CONCAT('%', :search, '%') OR p.code ILIKE CONCAT('%', :search, '%') " +
                                 "      OR EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                                 "                 AND (c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')))) " : "") +
                " AND ( " +
                "   EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                "           AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)) " +
                "   OR (NOT EXISTS (SELECT 1 FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED') " +
                "       AND NOT EXISTS (SELECT 1 FROM program_unit_grants g WHERE g.program_id = p.id AND g.unit_id = :unitId)) " +
                " ) ";

        String countSql = "SELECT COUNT(*) FROM training_programs p " + whereClause;
        Integer totalCount = jdbc.queryForObject(countSql, params, Integer.class);
        int totalElements = totalCount != null ? totalCount : 0;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        String dataSql = "SELECT p.id, p.domain_id, p.code, p.name, p.description, p.status, p.business_module, p.level, p.seq_number, " +
                "  (SELECT COUNT(*) FROM courses c WHERE c.program_id = p.id AND c.status <> 'DELETED' " +
                "   AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId)) AS courses_count " +
                "FROM training_programs p " + whereClause +
                "ORDER BY p.code ASC, p.name ASC LIMIT :limit OFFSET :offset";

        List<ProgramListItem> items = jdbc.query(dataSql, params, (rs, rowNum) ->
                new ProgramListItem(
                        rs.getString("id"),
                        rs.getString("domain_id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("status"),
                        Collections.emptySet(),
                        Set.of("VIEW", "EDIT"),
                        rs.getString("business_module"),
                        rs.getString("level"),
                        Collections.emptySet(),
                        Collections.emptySet(),
                        false,
                        rs.getObject("seq_number", Integer.class),
                        rs.getLong("courses_count")
                ));

        return new PagedResult<>(items, page, pageSize, totalElements, Math.max(1, totalPages), (page + 1) < totalPages);
    }

    @Override
    public PagedResult<CourseDto> adminCoursesByProgramPagedUnallocated(String programId, String search, int page, int size, String unitId) {
        String query = (search != null && !search.isBlank()) ? search.trim() : null;
        int pageSize = size > 0 ? size : 10;
        int offset = Math.max(0, page) * pageSize;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("programId", programId)
                .addValue("search", query)
                .addValue("unitId", unitId)
                .addValue("limit", pageSize)
                .addValue("offset", offset);

        String whereClause = " WHERE c.program_id = :programId AND c.status <> 'DELETED' " +
                (query != null ? " AND (c.name ILIKE CONCAT('%', :search, '%') OR c.code ILIKE CONCAT('%', :search, '%')) " : "") +
                " AND NOT EXISTS (SELECT 1 FROM course_unit_grants g WHERE g.course_id = c.id AND g.unit_id = :unitId) ";

        String countSql = "SELECT COUNT(*) FROM courses c " + whereClause;
        Integer totalCount = jdbc.queryForObject(countSql, params, Integer.class);
        int totalElements = totalCount != null ? totalCount : 0;
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);

        String dataSql = "SELECT c.id, c.code, c.name, c.description, c.status, c.business_module, c.level, c.seq_number, c.program_id, " +
                "  p.name AS program_name, p.domain_id, d.name AS domain_name " +
                "FROM courses c " +
                "LEFT JOIN training_programs p ON p.id = c.program_id " +
                "LEFT JOIN training_domains d ON d.id = p.domain_id " +
                whereClause +
                "ORDER BY c.code ASC, c.name ASC LIMIT :limit OFFSET :offset";

        List<CourseDto> items = jdbc.query(dataSql, params, (rs, rowNum) -> {
            CourseDto.CourseDtoBuilder b = CourseDto.builder();
            b.id(rs.getString("id"));
            b.code(rs.getString("code"));
            b.name(rs.getString("name"));
            b.description(rs.getString("description"));
            b.status(rs.getString("status"));
            b.businessModule(rs.getString("business_module"));
            b.level(rs.getString("level"));
            b.seqNumber(rs.getObject("seq_number", Integer.class));
            b.programId(rs.getString("program_id"));
            b.programName(rs.getString("program_name"));
            b.domainId(rs.getString("domain_id"));
            b.domainName(rs.getString("domain_name"));
            b.unitIds(Collections.emptySet());
            b.mandatoryUnitIds(Collections.emptySet());
            b.optionalUnitIds(Collections.emptySet());
            b.allowedActions(Set.of("VIEW", "EDIT"));
            return b.build();
        });

        return new PagedResult<>(items, page, pageSize, totalElements, Math.max(1, totalPages), (page + 1) < totalPages);
    }
}
