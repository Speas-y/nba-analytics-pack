package com.nbaanalytics.nba;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/players")
public class PlayersController {

  private final NbaCrawlerDataService crawler;

  public PlayersController(NbaCrawlerDataService crawler) {
    this.crawler = crawler;
  }

  @GetMapping("/search")
  public List<Map<String, Object>> search(@RequestParam("q") String q) {
    return crawler.searchPlayers(q != null ? q : "");
  }

  @GetMapping("/compare")
  public Map<String, Object> compare(
      @RequestParam int a,
      @RequestParam int b,
      @RequestParam @Min(1946) @Max(2100) int season,
      @RequestParam String scope) {
    if (!List.of("regular", "playoff", "all").contains(scope)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid scope");
    }
    return crawler.compare(a, b, season, scope);
  }

  @GetMapping("/{id}/detail")
  public Map<String, Object> detail(
      @PathVariable int id,
      @RequestParam @Min(1946) @Max(2100) int season,
      @RequestParam String scope) {
    if (!List.of("regular", "playoff", "all").contains(scope)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid scope");
    }
    return crawler.getPlayerDetail(id, season, scope);
  }
}
