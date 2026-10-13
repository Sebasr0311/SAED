package com.saed.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.sql.Types;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;

/** Database probe: positional null binds of each common column type work against the real Oracle driver. */
@SpringBootTest
@Tag("database")
class JdbcNullBindOracleProbeTest {

    @Autowired
    private JdbcTemplate jdbc;

    private int one(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? -1 : n;
    }

    @Test
    void nullVarchar2() {
        assertThat(one("SELECT COUNT(*) FROM DUAL WHERE NVL(?, 'x') = 'x'", (Object) null)).isEqualTo(1);
    }

    @Test
    void nullNumber() {
        assertThat(one("SELECT COUNT(*) FROM DUAL WHERE NVL(?, 0) = 0", (Object) null)).isEqualTo(1);
    }

    @Test
    void nullTimestampAndDate() {
        assertThat(one("SELECT COUNT(*) FROM DUAL WHERE NVL(?, SYSTIMESTAMP) IS NOT NULL", (Timestamp) null)).isEqualTo(1);
        assertThat(one("SELECT COUNT(*) FROM DUAL WHERE NVL(?, SYSDATE) IS NOT NULL", (java.sql.Date) null)).isEqualTo(1);
    }

    @Test
    void nullClob() {
        assertThat(one("SELECT COUNT(*) FROM DUAL WHERE DBMS_LOB.GETLENGTH(TO_CLOB(?)) IS NULL", (Object) null)).isEqualTo(1);
    }

    @Test
    void typedNullStillWorks() {
        assertThat(one("SELECT COUNT(*) FROM DUAL WHERE NVL(?, 7) = 7", new SqlParameterValue(Types.NUMERIC, null))).isEqualTo(1);
    }
}
