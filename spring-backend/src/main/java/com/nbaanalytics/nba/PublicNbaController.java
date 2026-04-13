package com.nbaanalytics.nba;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * 无需登录的 NBA 数据 API（供 Vercel 静态前端通过 {@code VITE_API_BASE} 访问）。
 * 路径前缀为 {@code /api/public/nba}（若配置了 context-path=/api）。
 */
@Validated
@RestController
@RequestMapping("/public/nba")
public class PublicNbaController {

  private final NbaCrawlerDataService crawler;

  public PublicNbaController(NbaCrawlerDataService crawler) {
    this.crawler = crawler;
  }

  /** 可选赛季列表（来自 output 下 player_stats_* 文件名） */
  @GetMapping("/seasons")
  public List<Map<String, Object>> seasons() {
    return crawler.listAvailableSeasons();
  }

  /** 得分榜 */
  @GetMapping("/leaderboard")
  public List<Map<String, Object>> leaderboard(
      @RequestParam @Min(1946) @Max(2100) int season,
      @RequestParam String scope,
      @RequestParam(required = false) @Min(1) @Max(800) Integer limit,
      @RequestParam(required = false) @Min(0) @Max(82) Integer minGames) {
    validateScope(scope);
    return crawler.getLeaderboard(
        season, scope, limit != null ? limit : 500, minGames != null ? minGames : 1);
  }

  /** 球队汇总；响应头 {@code X-NBA-Standings-Merged} 表示是否已合并联盟战绩 JSON */
  @GetMapping("/teams")
  public ResponseEntity<List<Map<String, Object>>> teams(
      @RequestParam @Min(1946) @Max(2100) int season, @RequestParam String scope) {
    validateScope(scope);
    NbaCrawlerDataService.TeamsSummaryBundle b = crawler.getTeamsSummaryBundle(season, scope);
    return ResponseEntity.ok()
        .header("X-NBA-Standings-Merged", b.standingsMerged() ? "true" : "false")
        .body(b.teams());
  }

  /**
   * 常规赛近 10 场左右比分（优先本地 {@code team_recent_games_*.json}；缺失时再请求 stats.nba.com，短超时）。
   */
  @GetMapping("/teams/{abbr}/recent-games")
  public List<Map<String, Object>> teamRecentGames(
      @PathVariable String abbr,
      @RequestParam @Min(1946) @Max(2100) int season,
      @RequestParam String scope) {
    validateScope(scope);
    return crawler.getTeamRecentGames(abbr.toUpperCase(Locale.ROOT), season);
  }

  /** 球员详情（场均等） */
  @GetMapping("/players/{id}/detail")
  public Map<String, Object> detail(
      @PathVariable int id,
      @RequestParam @Min(1946) @Max(2100) int season,
      @RequestParam String scope) {
    validateScope(scope);
    return crawler.getPlayerDetail(id, season, scope);
  }

  /**
   * 球员英文名 → 中文名映射（文件位于仓库 {@code nba-pc-analytics/player-zh.json}）。
   * 若后端未挂载该文件则 404，前端回退到打包的 {@code public/player-zh.json}。
   */
  @GetMapping("/i18n/player-zh")
  public ResponseEntity<JsonNode> i18nPlayerZh() {
    return crawler
        .readI18nJsonFile("player-zh.json")
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /** 同 {@link #i18nPlayerZh()}：BR 球员 slug → NBA Stats PERSON_ID，用于头像 CDN */
  @GetMapping("/i18n/br-slug-to-nba-person-id")
  public ResponseEntity<JsonNode> i18nBrSlugToPersonId() {
    return crawler
        .readI18nJsonFile("br-slug-to-nba-person-id.json")
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  private static void validateScope(String scope) {
    if (!List.of("regular", "playoff", "all").contains(scope)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid scope");
    }
  }
}
