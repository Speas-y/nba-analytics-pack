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

@Validated
@RestController
@RequestMapping("/public/nba")
public class PublicNbaController {

  private final NbaCrawlerDataService crawler;

  public PublicNbaController(NbaCrawlerDataService crawler) {
    this.crawler = crawler;
  }

  @GetMapping("/seasons")
  public List<Map<String, Object>> seasons() {
    return crawler.listAvailableSeasons();
  }

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

  @GetMapping("/teams")
  public ResponseEntity<List<Map<String, Object>>> teams(
      @RequestParam @Min(1946) @Max(2100) int season, @RequestParam String scope) {
    validateScope(scope);
    NbaCrawlerDataService.TeamsSummaryBundle b = crawler.getTeamsSummaryBundle(season, scope);
    return ResponseEntity.ok()
        .header("X-NBA-Standings-Merged", b.standingsMerged() ? "true" : "false")
        .body(b.teams());
  }

  /** 常规赛近 10 场左右比分（优先本地 team_recent_games_*.json，否则尝试 stats.nba.com）。 */
  @GetMapping("/teams/{abbr}/recent-games")
  public List<Map<String, Object>> teamRecentGames(
      @PathVariable String abbr,
      @RequestParam @Min(1946) @Max(2100) int season,
      @RequestParam String scope) {
    validateScope(scope);
    return crawler.getTeamRecentGames(abbr.toUpperCase(Locale.ROOT), season);
  }

  @GetMapping("/players/{id}/detail")
  public Map<String, Object> detail(
      @PathVariable int id,
      @RequestParam @Min(1946) @Max(2100) int season,
      @RequestParam String scope) {
    validateScope(scope);
    return crawler.getPlayerDetail(id, season, scope);
  }

  /**
   * 供托管前端拉取镜像内 nba-pc-analytics 的 player-zh；若不存在则 404，由前端回退到本地 public 静态文件。
   */
  @GetMapping("/i18n/player-zh")
  public ResponseEntity<JsonNode> i18nPlayerZh() {
    return crawler
        .readI18nJsonFile("player-zh.json")
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  /** 同 {@link #i18nPlayerZh()}，对应 br-slug → NBA PERSON_ID。 */
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
