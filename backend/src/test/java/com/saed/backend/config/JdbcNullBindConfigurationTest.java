package com.saed.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.core.SpringProperties;

/**
 * Oracle 18 + ojdbc11 rejects Spring's default null-bind path for positional parameters
 * (StatementCreatorUtils.setNull -> PreparedStatement.getParameterMetaData) with
 * "ORA-17004: invalid column type: 268435455". The classpath {@code spring.properties} switches that lookup
 * off so every JdbcTemplate null bind falls back to setNull(Types.NULL) / setObject(null).
 */
class JdbcNullBindConfigurationTest {

    @Test
    void classpathSpringPropertiesDisablesParameterTypeLookupForNullBinds() {
        assertThat(SpringProperties.getFlag("spring.jdbc.getParameterType.ignore")).isTrue();
    }
}
