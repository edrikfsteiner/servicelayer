package com.migration.servicelayer.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
public class OriginDbConfig {

    @Bean(name = "originDataSource")
    @ConfigurationProperties(prefix = "app.datasource.origin")
    public DataSource dataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean(name = "originJdbcTemplate")
    public JdbcTemplate jdbcTemplate(@Qualifier("originDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean(name = "originTransactionManager")
    public PlatformTransactionManager transactionManager(@Qualifier("originDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}