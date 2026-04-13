package com.nbaanalytics.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 集中配置：JWT、CORS、爬虫目录（Zeabur / 本地需指向含 output 的爬虫根目录）。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

  private final Jwt jwt = new Jwt();
  private final Refresh refresh = new Refresh();
  private final Cors cors = new Cors();
  private final Crawler crawler = new Crawler();

  public Jwt getJwt() {
    return jwt;
  }

  public Refresh getRefresh() {
    return refresh;
  }

  public Cors getCors() {
    return cors;
  }

  public Crawler getCrawler() {
    return crawler;
  }

  public static class Jwt {
    private String secret = "dev-secret";
    /** 如 15m, 1h */
    private String accessExpires = "15m";

    public String getSecret() {
      return secret;
    }

    public void setSecret(String secret) {
      this.secret = secret;
    }

    public String getAccessExpires() {
      return accessExpires;
    }

    public void setAccessExpires(String accessExpires) {
      this.accessExpires = accessExpires;
    }
  }

  public static class Refresh {
    private int days = 14;

    public int getDays() {
      return days;
    }

    public void setDays(int days) {
      this.days = days;
    }
  }

  public static class Cors {
    private String frontendOrigin = "http://localhost:5173";
    private String dashboardOrigins = "http://127.0.0.1:8766,http://localhost:8766";

    public String getFrontendOrigin() {
      return frontendOrigin;
    }

    public void setFrontendOrigin(String frontendOrigin) {
      this.frontendOrigin = frontendOrigin;
    }

    public String getDashboardOrigins() {
      return dashboardOrigins;
    }

    public void setDashboardOrigins(String dashboardOrigins) {
      this.dashboardOrigins = dashboardOrigins;
    }

    public Set<String> allowedOrigins() {
      Set<String> s =
          new LinkedHashSet<>(
              Stream.concat(
                      Stream.of(frontendOrigin),
                      Arrays.stream(dashboardOrigins.split(",")).map(String::trim).filter(x -> !x.isEmpty()))
                  .toList());
      return s;
    }
  }

  /** 爬虫：home=output 与 nba_player_crawler.py 所在目录；outputDir 可单独覆盖 JSON 目录；python 可指定 venv */
  public static class Crawler {
    private String home = "";
    private String outputDir = "";
    private String python = "";

    public String getHome() {
      return home;
    }

    public void setHome(String home) {
      this.home = home;
    }

    public String getOutputDir() {
      return outputDir;
    }

    public void setOutputDir(String outputDir) {
      this.outputDir = outputDir;
    }

    public String getPython() {
      return python;
    }

    public void setPython(String python) {
      this.python = python;
    }
  }
}
