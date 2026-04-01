package com.nbaanalytics.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Render 手动创建 Web Service 时，数据库常见三种配法（择一即可）：
 *
 * <ul>
 *   <li>{@code DATABASE_URL}：Dashboard 里的 {@code postgresql://user:pass@host:port/db}
 *   <li>{@code SPRING_DATASOURCE_URL}：完整 {@code jdbc:postgresql://…}（可自带 query 里的 user/password）
 *   <li>拆开的 {@code RENDER_DB_HOST} / {@code PORT} / {@code NAME} / {@code USER} / {@code PASSWORD}
 * </ul>
 */
@Configuration
@Profile("render")
public class RenderDatabaseConfig {

  @Bean
  public DataSource dataSource(
      @Value("${DATABASE_URL:}") String databaseUrl,
      @Value("${SPRING_DATASOURCE_URL:}") String springJdbcUrl,
      @Value("${RENDER_DB_HOST:}") String renderHost,
      @Value("${RENDER_DB_PORT:5432}") String renderPort,
      @Value("${RENDER_DB_NAME:}") String renderDb,
      @Value("${RENDER_DB_USER:}") String renderUser,
      @Value("${RENDER_DB_PASSWORD:}") String renderPassword) {

    if (springJdbcUrl != null && !springJdbcUrl.isBlank()) {
      return hikari(springJdbcUrl.trim(), null, null);
    }
    if (databaseUrl != null && !databaseUrl.isBlank()) {
      return hikariFromPostgresUri(databaseUrl.trim());
    }
    if (renderHost != null && !renderHost.isBlank()) {
      String url =
          "jdbc:postgresql://"
              + renderHost
              + ":"
              + renderPort
              + "/"
              + renderDb
              + "?sslmode=require";
      return hikari(url, renderUser, renderPassword);
    }
    throw new IllegalStateException(
        "Render：请设置 DATABASE_URL，或 SPRING_DATASOURCE_URL，或 RENDER_DB_HOST 等拆开变量");
  }

  private static DataSource hikariFromPostgresUri(String databaseUrl) {
    String u = databaseUrl;
    if (u.startsWith("postgres://")) {
      u = "postgresql://" + u.substring("postgres://".length());
    }
    if (!u.startsWith("postgresql://")) {
      throw new IllegalArgumentException("DATABASE_URL 应以 postgres:// 或 postgresql:// 开头");
    }
    URI uri = URI.create(u);
    String userInfo = uri.getRawUserInfo();
    String user = "";
    String password = "";
    if (userInfo != null && !userInfo.isEmpty()) {
      int col = userInfo.indexOf(':');
      if (col >= 0) {
        user = decode(userInfo.substring(0, col));
        password = decode(userInfo.substring(col + 1));
      } else {
        user = decode(userInfo);
      }
    }
    String host = uri.getHost();
    if (host == null || host.isEmpty()) {
      throw new IllegalArgumentException("DATABASE_URL 缺少主机名");
    }
    int port = uri.getPort() > 0 ? uri.getPort() : 5432;
    String path = uri.getRawPath();
    String db = path != null && path.startsWith("/") ? path.substring(1) : path;
    if (db == null || db.isEmpty()) {
      throw new IllegalArgumentException("DATABASE_URL 缺少数据库名");
    }
    String jdbcUrl =
        "jdbc:postgresql://" + host + ":" + port + "/" + db + "?sslmode=require";
    return hikari(jdbcUrl, user, password);
  }

  private static String decode(String raw) {
    return URLDecoder.decode(raw, StandardCharsets.UTF_8);
  }

  private static DataSource hikari(String jdbcUrl, String user, String password) {
    HikariConfig c = new HikariConfig();
    c.setJdbcUrl(jdbcUrl);
    if (user != null && !user.isEmpty()) {
      c.setUsername(user);
    }
    if (password != null && !password.isEmpty()) {
      c.setPassword(password);
    }
    return new HikariDataSource(c);
  }
}
