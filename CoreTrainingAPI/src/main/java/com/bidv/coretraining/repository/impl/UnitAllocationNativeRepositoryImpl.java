package com.bidv.coretraining.repository.impl;

import com.bidv.coretraining.dto.TrainingStructureDto.UnitAllocationDirectoryDto;
import com.bidv.coretraining.repository.UnitAllocationNativeRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

        // 4. Soft delete course
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
}
