package com.bidv.coretraining.config;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.assertThat;

class GroupMembershipMigrationTest {
    private JdbcTemplate database() {
        return new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", ""));
    }

    @Test void preservesLegacyAssignmentsAndDoesNotRestoreRemovedMembersOnRestart() {
        JdbcTemplate jdbc = database();
        jdbc.execute("create table users (id bigint primary key, group_id varchar(255))");
        jdbc.execute("create table user_group_memberships (user_id bigint, group_id varchar(255), primary key(user_id, group_id))");
        jdbc.update("insert into users values (1, 'OLD'), (2, null)");
        jdbc.update("insert into user_group_memberships values (1, 'OTHER')");
        var migration = new GroupMembershipMigration(jdbc);
        migration.run();
        assertThat(jdbc.queryForList("select group_id from user_group_memberships where user_id=1", String.class))
                .containsExactlyInAnyOrder("OLD", "OTHER");
        jdbc.update("delete from user_group_memberships where group_id='OLD'");
        migration.run();
        assertThat(jdbc.queryForList("select group_id from user_group_memberships where user_id=1", String.class))
                .containsExactly("OTHER");
    }

    @Test void freshSchemaDoesNotNeedLegacyColumn() {
        JdbcTemplate jdbc = database();
        jdbc.execute("create table users (id bigint primary key)");
        new GroupMembershipMigration(jdbc).run();
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }
}
