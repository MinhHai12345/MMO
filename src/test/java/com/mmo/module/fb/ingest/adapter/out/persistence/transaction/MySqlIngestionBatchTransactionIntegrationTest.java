package com.mmo.module.fb.ingest.adapter.out.persistence.transaction;

import com.mmo.module.fb.ingest.application.port.out.IngestionBatchTransactionPort;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Exercises the batch transaction boundary against the same MySQL 8 service used by CI.
 * The data row and cursor row model the two writes that must commit or roll back together.
 */
@EnabledIfEnvironmentVariable(named = "MMO_MYSQL_TEST_URL", matches = ".+")
class MySqlIngestionBatchTransactionIntegrationTest {

    private static final String TABLE = "fb_ingestion_batch_atomicity_it";
    private static JdbcTemplate jdbc;
    private static IngestionBatchTransactionPort transactions;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl(System.getenv("MMO_MYSQL_TEST_URL"));
        dataSource.setUsername(System.getenv("MMO_MYSQL_TEST_USERNAME"));
        dataSource.setPassword(System.getenv("MMO_MYSQL_TEST_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        transactions = new IngestionBatchTransactionAdapter(new DataSourceTransactionManager(dataSource));
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + TABLE + " ("
                + "id BIGINT NOT NULL PRIMARY KEY, "
                + "data_value VARCHAR(100) NOT NULL, "
                + "cursor_value VARCHAR(100) NOT NULL"
                + ") ENGINE=InnoDB");
        jdbc.update("DELETE FROM " + TABLE);
    }

    @AfterAll
    static void tearDown() {
        if (jdbc != null) {
            jdbc.update("DELETE FROM " + TABLE);
        }
    }

    @Test
    void rollsBackPersistedDataAndCursorTogetherWhenBatchFails() {
        assertThrows(IllegalStateException.class, () -> transactions.execute(() -> {
            jdbc.update("INSERT INTO " + TABLE + " (id, data_value, cursor_value) VALUES (?, ?, ?)",
                    1L, "persisted-row", "cursor-100");
            throw new IllegalStateException("simulated failure before commit");
        }));

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class));
    }

    @Test
    void commitsDataAndCursorTogetherWhenBatchSucceeds() {
        transactions.execute(() -> {
            jdbc.update("INSERT INTO " + TABLE + " (id, data_value, cursor_value) VALUES (?, ?, ?)",
                    2L, "persisted-row", "cursor-200");
            return null;
        });

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class));
        assertEquals("cursor-200",
                jdbc.queryForObject("SELECT cursor_value FROM " + TABLE + " WHERE id = 2", String.class));
    }
}