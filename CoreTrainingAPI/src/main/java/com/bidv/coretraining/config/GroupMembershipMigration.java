package com.bidv.coretraining.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Preserve former single-group assignments once, after Hibernate creates the join table. */
@Component
@Order(0)
public class GroupMembershipMigration implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    public GroupMembershipMigration(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        boolean legacyColumn = Boolean.TRUE.equals(jdbc.query("select * from users where 1 = 0", rs -> {
            var metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++)
                if ("group_id".equalsIgnoreCase(metadata.getColumnName(i))) return true;
            return false;
        }));
        if (!legacyColumn) return;
        jdbc.update("""
                insert into user_group_memberships (user_id, group_id)
                select u.id, u.group_id from users u
                where u.group_id is not null and not exists
                (select 1 from user_group_memberships m where m.user_id = u.id and m.group_id = u.group_id)
                """);
        // Clearing the former source prevents removed memberships reappearing on restart.
        jdbc.update("update users set group_id = null where group_id is not null");
    }
}
