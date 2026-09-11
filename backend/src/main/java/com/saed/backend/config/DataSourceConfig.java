package com.saed.backend.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.hikari")
    public HikariDataSource hikariDataSource(DataSourceProperties properties) {
        HikariDataSource ds = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class).build();
        ds.setMaximumPoolSize(35);
        ds.setMinimumIdle(5);
        ds.setConnectionTimeout(30000);
        // leakDetectionThreshold is set per-profile in application.yml
        // (dev: 30000, prod: 60000). Do NOT hardcode here.

        return ds;
    }

    @Bean
    @Primary
    public DataSource dataSource(HikariDataSource hikariDataSource) {
        // Wrap Hikari with our SAED context proxy
        return new SaedDataSourceProxy(hikariDataSource);
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
