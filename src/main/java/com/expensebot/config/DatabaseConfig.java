package com.expensebot.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Railway предоставляет DATABASE_URL в формате:
 *   postgres://user:password@host:port/database
 *
 * JDBC требует:
 *   jdbc:postgresql://host:port/database
 *
 * Этот бин конвертирует URL автоматически.
 * Активируется только если DATABASE_URL содержит "postgres://" (Railway-формат).
 */
@Configuration
public class DatabaseConfig {

    @Value("${DATABASE_URL:}")
    private String rawDatabaseUrl;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        if (rawDatabaseUrl != null && rawDatabaseUrl.startsWith("postgres://")) {
            // Конвертируем Railway postgres:// → jdbc:postgresql://
            try {
                URI uri = new URI(rawDatabaseUrl);
                String host     = uri.getHost();
                int    port     = uri.getPort();
                String database = uri.getPath().replaceFirst("/", "");
                String userInfo = uri.getUserInfo();
                String user     = userInfo.split(":")[0];
                String password = userInfo.split(":")[1];

                String jdbcUrl = String.format(
                    "jdbc:postgresql://%s:%d/%s?sslmode=require", host, port, database);

                config.setJdbcUrl(jdbcUrl);
                config.setUsername(user);
                config.setPassword(password);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot parse DATABASE_URL: " + rawDatabaseUrl, e);
            }
        } else if (rawDatabaseUrl != null && rawDatabaseUrl.startsWith("jdbc:")) {
            // Уже в JDBC-формате (локальная разработка)
            config.setJdbcUrl(rawDatabaseUrl);
        } else {
            throw new IllegalStateException(
                "DATABASE_URL env variable is not set or has unsupported format: " + rawDatabaseUrl);
        }

        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);          // Railway hobby tier ограничен
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);

        return new HikariDataSource(config);
    }
}
