package com.sandy.expense.integration;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Boots the whole application against a real Postgres.
 *
 * <p>This test asserts almost nothing on purpose. Its value is that it fails when the application
 * cannot start at all — which the mocked unit tests cannot detect.
 */
class ApplicationContextTest extends PostgresIntegrationTest {

    @Autowired DataSource dataSource;

    @Test
    void contextLoadsAndFlywayCreatedTheSchema() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        // Flyway ran (and `ddl-auto: validate` agreed the entities match what it produced,
        // otherwise the context above would not have come up).
        Integer migrations =
                jdbc.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(migrations).isGreaterThanOrEqualTo(2);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM departments", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_requests", Integer.class)).isNotNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM approvals", Integer.class)).isNotNull();
    }

    @Test
    void expenseRequestsCarryAVersionColumnForOptimisticLocking() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Integer present =
                jdbc.queryForObject(
                        """
                        SELECT count(*) FROM information_schema.columns
                        WHERE table_name = 'expense_requests' AND column_name = 'version'
                        """,
                        Integer.class);
        assertThat(present).isEqualTo(1);
    }
}
