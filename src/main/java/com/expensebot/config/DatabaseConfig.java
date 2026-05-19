package com.expensebot.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Строит DataSource из переменных окружения Railway.
 *
 * Railway выставляет либо:
 *   DATABASE_URL=postgres://user:pass@host:port/db   (postgres:// формат)
 *   DATABASE_URL=jdbc:postgresql://...               (уже JDBC — для локалки)
 * либо набор отдельных переменных: PGHOST, PGPORT, PGDATABASE, PGUSER, PGPASSWORD.
 *
 * ВАЖНО: читаем через System.getenv(), а не @Value — это исключает проблему
 * с CGLIB-прокси, когда Spring вызывает dataSource() из другого @Bean-метода
 * до того, как @Value-поля инжектированы в экземпляр.
 */
@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig cfg = new HikariConfig();

        // 1. Пробуем DATABASE_URL
        String rawUrl = System.getenv("DATABASE_URL");

        if (rawUrl != null && !rawUrl.isBlank()) {
            if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
                // Railway-формат → конвертируем в JDBC
                try {
                    URI uri      = new URI(rawUrl);
                    String host  = uri.getHost();
                    int    port  = uri.getPort() > 0 ? uri.getPort() : 5432;
                    String db    = uri.getPath().replaceFirst("^/", "");
                    String info  = uri.getUserInfo();           // "user:password"
                    String user  = info.split(":", 2)[0];
                    String pass  = info.split(":", 2)[1];

                    cfg.setJdbcUrl(String.format(
                        "jdbc:postgresql://%s:%d/%s?sslmode=require", host, port, db));
                    cfg.setUsername(user);
                    cfg.setPassword(pass);
                } catch (Exception e) {
                    throw new IllegalStateException(
                        "Failed to parse DATABASE_URL: " + rawUrl, e);
                }
            } else if (rawUrl.startsWith("jdbc:")) {
                // Уже JDBC (локальная разработка)
                cfg.setJdbcUrl(rawUrl);
                // username/password берём из отдельных env или дефолты
                String user = System.getenv("PGUSER");
                String pass = System.getenv("PGPASSWORD");
                if (user != null) cfg.setUsername(user);
                if (pass != null) cfg.setPassword(pass);
            } else {
                throw new IllegalStateException(
                    "DATABASE_URL has unsupported format (expected postgres:// or jdbc:): " + rawUrl);
            }

        // 2. Fallback: отдельные PG* переменные (Railway тоже их выставляет)
        } else {
            String host = System.getenv("PGHOST");
            String port = System.getenv("PGPORT");
            String db   = System.getenv("PGDATABASE");
            String user = System.getenv("PGUSER");
            String pass = System.getenv("PGPASSWORD");

            if (host == null || db == null || user == null) {
                throw new IllegalStateException(
                    "No database configuration found. Set DATABASE_URL or PGHOST/PGDATABASE/PGUSER/PGPASSWORD.");
            }

            int pgPort = (port != null && !port.isBlank()) ? Integer.parseInt(port) : 5432;
            cfg.setJdbcUrl(String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=require", host, pgPort, db));
            cfg.setUsername(user);
            cfg.setPassword(pass != null ? pass : "");
        }

        cfg.setDriverClassName("org.postgresql.Driver");
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(1);
        cfg.setConnectionTimeout(30_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);

        return new HikariDataSource(cfg);
    }
}
