package com.nbaanalytics.nba;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nbaanalytics.config.AppProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NbaCrawlerDataService {

  public record TeamsSummaryBundle(
      List<Map<String, Object>> teams, boolean standingsMerged, String standingsPathOrHint) {}

  private static final Logger log = LoggerFactory.getLogger(NbaCrawlerDataService.class);

  private static final HttpClient NBA_STATS_HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
  private static final Pattern DIR_FILE = Pattern.compile("^player_directory_(\\d{4}_\\d{2})\\.json$");
  private static final Pattern STATS_REG =
      Pattern.compile("^player_stats_(\\d{4})_\\d{2}_regular_season_pergame\\.json$");
  private static final Pattern AGG_TM = Pattern.compile("^\\d+TM$");

  private final AppProperties appProperties;
  private final ObjectMapper objectMapper;
  private final Map<String, FileCache> jsonCache = new ConcurrentHashMap<>();
  /** 本地无该队近 10 场时，在线结果的短期缓存（仅缓存非空结果）。 */
  private final Map<String, RecentGamesLiveCache> recentGamesLiveCache = new ConcurrentHashMap<>();

  private static final long RECENT_GAMES_LIVE_CACHE_TTL_MS = 5 * 60_000L;
  /** 仅在缺少 team_recent_games JSON 时请求 NBA Stats；超时过大会拖慢接口，不宜与爬虫脚本同级。 */
  private static final Duration NBA_STATS_RECENT_GAMES_TIMEOUT = Duration.ofSeconds(8);

  private record FileCache(long mtimeMs, JsonNode root) {}

  private record RecentGamesLiveCache(long expiresAtMs, List<Map<String, Object>> rows) {}

  public NbaCrawlerDataService(AppProperties appProperties, ObjectMapper objectMapper) {
    this.appProperties = appProperties;
    this.objectMapper = objectMapper;
  }

  public void clearJsonCache() {
    jsonCache.clear();
    recentGamesLiveCache.clear();
  }

  private Path resolveDefaultCrawlerHome() {
    Path cwd = Paths.get("").toAbsolutePath();
    List<Path> candidates =
        List.of(
            cwd.resolve("../nba-crawler"),
            cwd.resolve("../爬虫"),
            cwd.resolve("../../nba-crawler"),
            cwd.resolve("../../爬虫"),
            cwd.resolve("nba-crawler"),
            cwd.resolve("爬虫"),
            cwd.resolve("../spring-backend/../爬虫"));
    for (Path c : candidates) {
      Path norm = c.normalize();
      if (Files.isRegularFile(norm.resolve("nba_player_crawler.py"))) {
        return norm;
      }
    }
    return candidates.get(3).normalize();
  }

  private Path getCrawlerHome() {
    String fromEnv = appProperties.getCrawler().getHome().trim();
    if (!fromEnv.isEmpty()) {
      return Paths.get(fromEnv).toAbsolutePath().normalize();
    }
    return resolveDefaultCrawlerHome();
  }

  private Path getOutputDir() {
    String fromEnv = appProperties.getCrawler().getOutputDir().trim();
    if (!fromEnv.isEmpty()) {
      return Paths.get(fromEnv).toAbsolutePath().normalize();
    }
    return getCrawlerHome().resolve("output");
  }

  /** 与爬虫 scripts 一致：仓库根下的 nba-pc-analytics/（容器内为 /app/nba-pc-analytics） */
  public Path resolveI18nDir() {
    return getCrawlerHome().getParent().normalize().resolve("nba-pc-analytics");
  }

  public Optional<JsonNode> readI18nJsonFile(String filename) {
    Path p = resolveI18nDir().resolve(filename);
    if (!Files.isRegularFile(p)) {
      return Optional.empty();
    }
    try {
      return Optional.of(objectMapper.readTree(Files.newInputStream(p)));
    } catch (IOException e) {
      log.warn("readI18nJsonFile {}: {}", p, e.getMessage());
      return Optional.empty();
    }
  }

  private String getPythonExecutable(Path crawlerHome) {
    String fromEnv = appProperties.getCrawler().getPython().trim();
    if (!fromEnv.isEmpty()) return fromEnv;
    Path venvUnix = crawlerHome.resolve(".venv/bin/python");
    if (Files.isRegularFile(venvUnix)) return venvUnix.toString();
    Path venvWin = crawlerHome.resolve(".venv/Scripts/python.exe");
    if (Files.isRegularFile(venvWin)) return venvWin.toString();
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    return os.contains("win") ? "python" : "python3";
  }

  private String seasonFileTag(int seasonStartYear) {
    int y2 = (seasonStartYear + 1) % 100;
    return seasonStartYear + "_" + String.format("%02d", y2);
  }

  private JsonNode readJsonCached(Path absPath) {
    if (!Files.isRegularFile(absPath)) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "缺少数据文件：" + absPath);
    }
    long mtime;
    try {
      mtime = Files.getLastModifiedTime(absPath).toMillis();
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "无法读取：" + absPath);
    }
    FileCache hit = jsonCache.get(absPath.toString());
    if (hit != null && hit.mtimeMs == mtime) {
      return hit.root();
    }
    try {
      JsonNode root = objectMapper.readTree(Files.newInputStream(absPath));
      jsonCache.put(absPath.toString(), new FileCache(mtime, root));
      return root;
    } catch (IOException e) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "JSON 解析失败");
    }
  }

  private List<Path> listDirectoryJsonFiles(Path outDir) {
    if (!Files.isDirectory(outDir)) return List.of();
    List<Path> out = new ArrayList<>();
    try (var stream = Files.list(outDir)) {
      stream
          .filter(p -> DIR_FILE.matcher(p.getFileName().toString()).matches())
          .forEach(out::add);
    } catch (IOException e) {
      return List.of();
    }
    return out;
  }

  private String parseTagFromDirectoryPath(Path absPath) {
    var m = DIR_FILE.matcher(absPath.getFileName().toString());
    return m.matches() ? m.group(1) : null;
  }

  private Path resolveLatestDirectoryPath() {
    Path outDir = getOutputDir();
    List<Path> files = listDirectoryJsonFiles(outDir);
    if (files.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "未在 "
              + outDir
              + " 找到 player_directory_*.json，请先运行爬虫生成数据。");
    }
    files.sort(
        Comparator.comparing(
            (Path p) -> parseTagFromDirectoryPath(p) == null ? "" : parseTagFromDirectoryPath(p),
            Comparator.reverseOrder()));
    return files.get(0);
  }

  private Path statsFilePath(int seasonStartYear, String scope) {
    String tag = seasonFileTag(seasonStartYear);
    String stem = "regular".equals(scope) ? "regular_season" : "playoffs";
    String fname = "player_stats_" + tag + "_" + stem + "_pergame.json";
    return getOutputDir().resolve(fname);
  }

  public List<Map<String, Object>> searchPlayers(String query) {
    String q = query.trim().toLowerCase(Locale.ROOT);
    if (q.isEmpty()) return List.of();
    Path dirPath = resolveLatestDirectoryPath();
    JsonNode arr = readJsonCached(dirPath);
    if (!arr.isArray()) return List.of();
    List<Map<String, Object>> out = new ArrayList<>();
    Set<Integer> seen = ConcurrentHashMap.newKeySet();
    for (JsonNode r : arr) {
      int id = r.path("球员ID").asInt(0);
      String name = r.path("姓名").asText("").trim();
      String alt = r.path("姓名_姓在前").asText("").trim();
      if (name.isEmpty() || seen.contains(id)) continue;
      String blob = (name + " " + alt).toLowerCase(Locale.ROOT);
      if (blob.contains(q)) {
        seen.add(id);
        out.add(Map.of("id", id, "name", name));
        if (out.size() >= 100) break;
      }
    }
    return out;
  }

  private JsonNode findStatRow(List<JsonNode> rows, int playerId) {
    for (JsonNode r : rows) {
      if (r.path("球员ID").asInt(0) == playerId) return r;
    }
    return null;
  }

  private List<JsonNode> loadStatsRowsOrNull(int seasonStartYear, String scope) {
    Path p = statsFilePath(seasonStartYear, scope);
    if (!Files.isRegularFile(p)) return null;
    try {
      JsonNode root = readJsonCached(p);
      if (!root.isArray()) return null;
      List<JsonNode> list = new ArrayList<>();
      root.forEach(list::add);
      return list;
    } catch (Exception e) {
      log.warn("Failed reading stats file {}: {}", p, e.getMessage());
      return null;
    }
  }

  private record Avg(int games, double pts, double reb, double ast) {}

  private Avg mergeAvg(Avg a, Avg b) {
    if (a == null && b == null) return null;
    if (a == null) return b;
    if (b == null) return a;
    int g = a.games + b.games;
    if (g <= 0) return a;
    return new Avg(
        g,
        (a.games * a.pts + b.games * b.pts) / g,
        (a.games * a.reb + b.games * b.reb) / g,
        (a.games * a.ast + b.games * b.ast) / g);
  }

  private Avg rowToAvg(JsonNode r) {
    return new Avg(
        r.path("出场次数").asInt(0),
        r.path("得分").asDouble(0),
        r.path("篮板").asDouble(0),
        r.path("助攻").asDouble(0));
  }

  private double pctToDisplay(JsonNode r, String key) {
    if (!r.has(key) || r.get(key).isNull()) return Double.NaN;
    double n = r.get(key).asDouble(Double.NaN);
    if (Double.isNaN(n)) return Double.NaN;
    if (n >= 0 && n <= 1) return Math.round(n * 1000) / 10.0;
    return Math.round(n * 10) / 10.0;
  }

  private JsonNode mergeTwoStatRows(JsonNode a, JsonNode b) {
    int g1 = a.path("出场次数").asInt(0);
    int g2 = b.path("出场次数").asInt(0);
    int g = g1 + g2;
    if (g <= 0) return a;
    java.util.function.BiFunction<Double, Double, Double> wavg =
        (x, y) -> (g1 * x + g2 * y) / g;
    var om = objectMapper;
    var o = om.createObjectNode();
    o.set("球员ID", a.get("球员ID"));
    o.put(
        "球员姓名",
        a.path("球员姓名").asText("").isBlank() ? b.path("球员姓名").asText("") : a.path("球员姓名").asText(""));
    o.put(
        "球员网址别名",
        a.path("球员网址别名").asText("").isBlank()
            ? b.path("球员网址别名").asText("")
            : a.path("球员网址别名").asText(""));
    o.put(
        "球队缩写",
        a.path("球队缩写").asText("").isBlank() ? b.path("球队缩写").asText("") : a.path("球队缩写").asText(""));
    o.put("出场次数", g);
    o.put("得分", wavg.apply(a.path("得分").asDouble(0), b.path("得分").asDouble(0)));
    o.put("篮板", wavg.apply(a.path("篮板").asDouble(0), b.path("篮板").asDouble(0)));
    o.put("助攻", wavg.apply(a.path("助攻").asDouble(0), b.path("助攻").asDouble(0)));
    if (a.has("抢断"))
      o.put("抢断", wavg.apply(a.path("抢断").asDouble(0), b.path("抢断").asDouble(0)));
    if (a.has("盖帽"))
      o.put("盖帽", wavg.apply(a.path("盖帽").asDouble(0), b.path("盖帽").asDouble(0)));
    if (a.has("失误"))
      o.put("失误", wavg.apply(a.path("失误").asDouble(0), b.path("失误").asDouble(0)));
    if (a.has("上场时间"))
      o.put("上场时间", wavg.apply(a.path("上场时间").asDouble(0), b.path("上场时间").asDouble(0)));
    if (a.has("投篮命中率")) o.set("投篮命中率", a.get("投篮命中率"));
    if (a.has("三分命中率")) o.set("三分命中率", a.get("三分命中率"));
    return o;
  }

  private List<JsonNode> rowsForScope(int seasonStartYear, String scope) {
    List<JsonNode> reg = loadStatsRowsOrNull(seasonStartYear, "regular");
    List<JsonNode> post = loadStatsRowsOrNull(seasonStartYear, "playoff");
    if ("regular".equals(scope)) return reg != null ? new ArrayList<>(reg) : new ArrayList<>();
    if ("playoff".equals(scope)) return post != null ? new ArrayList<>(post) : new ArrayList<>();
    Map<Integer, JsonNode> m = new HashMap<>();
    if (reg != null) {
      for (JsonNode r : reg) m.put(r.path("球员ID").asInt(), r.deepCopy());
    }
    if (post != null) {
      for (JsonNode r : post) {
        int id = r.path("球员ID").asInt();
        JsonNode ex = m.get(id);
        if (ex == null) m.put(id, r.deepCopy());
        else m.put(id, mergeTwoStatRows(ex, r));
      }
    }
    return new ArrayList<>(m.values());
  }

  public List<Map<String, Object>> listAvailableSeasons() {
    Path outDir = getOutputDir();
    if (!Files.isDirectory(outDir)) return List.of();
    Set<Integer> years = ConcurrentHashMap.newKeySet();
    try (var stream = Files.list(outDir)) {
      stream
          .map(p -> p.getFileName().toString())
          .forEach(
              f -> {
                var m = STATS_REG.matcher(f);
                if (m.matches()) years.add(Integer.parseInt(m.group(1)));
              });
    } catch (IOException e) {
      return List.of();
    }
    return years.stream()
        .sorted(Comparator.reverseOrder())
        .map(
            y -> {
              Map<String, Object> row = new LinkedHashMap<>();
              row.put("startYear", y);
              row.put("label", y + "-" + String.format("%02d", (y + 1) % 100));
              return row;
            })
        .toList();
  }

  public List<Map<String, Object>> getLeaderboard(
      int seasonStartYear, String scope, int limit, int minGames) {
    List<JsonNode> rows = rowsForScope(seasonStartYear, scope);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND,
          "未找到该赛季统计数据（scope=" + scope + "），请先运行 nba-crawler 生成 output");
    }
    List<JsonNode> filtered =
        rows.stream().filter(r -> r.path("出场次数").asInt(0) >= minGames).toList();
    List<JsonNode> sorted =
        new ArrayList<>(filtered);
    sorted.sort(
        Comparator.comparingDouble((JsonNode r) -> r.path("得分").asDouble(0)).reversed());
    int cap = Math.min(Math.max(1, limit), 800);
    List<Map<String, Object>> out = new ArrayList<>();
    for (int idx = 0; idx < Math.min(cap, sorted.size()); idx++) {
      JsonNode r = sorted.get(idx);
      String name =
          r.path("球员姓名").asText("").trim();
      if (name.isEmpty()) name = resolvePlayerName(seasonStartYear, r.path("球员ID").asInt(), r);
      String brSlug = r.path("球员网址别名").asText("").trim();
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("rank", idx + 1);
      row.put("id", r.path("球员ID").asInt());
      row.put("name", name);
      row.put("brSlug", brSlug.isEmpty() ? null : brSlug);
      String team = r.path("球队缩写").asText("—").trim();
      row.put("team", team.isEmpty() ? "—" : team);
      row.put("pts", round1(r.path("得分").asDouble(0)));
      row.put("reb", round1(r.path("篮板").asDouble(0)));
      row.put("ast", round1(r.path("助攻").asDouble(0)));
      row.put("games", r.path("出场次数").asInt(0));
      row.put("gs", r.has("GS") ? (int) Math.round(r.path("GS").asDouble(0)) : 0);
      double fg = pctToDisplay(r, "投篮命中率");
      double tp = pctToDisplay(r, "三分命中率");
      row.put("fgPct", Double.isNaN(fg) ? null : fg);
      row.put("tpPct", Double.isNaN(tp) ? null : tp);
      row.put("stl", r.has("抢断") ? round1(r.path("抢断").asDouble(0)) : null);
      row.put("blk", r.has("盖帽") ? round1(r.path("盖帽").asDouble(0)) : null);
      row.put("tov", r.has("失误") ? round1(r.path("失误").asDouble(0)) : null);
      row.put("mpg", r.has("上场时间") ? round1(r.path("上场时间").asDouble(0)) : null);
      out.add(row);
    }
    return out;
  }

  private static double round1(double v) {
    return Math.round(v * 10) / 10.0;
  }

  private static boolean isAggregateTeamAbbr(String abbr) {
    String a = abbr == null ? "" : abbr.trim().toUpperCase(Locale.ROOT);
    if (a.isEmpty() || "—".equals(a)) return false;
    if ("TOT".equals(a)) return true;
    return AGG_TM.matcher(a).matches();
  }

  public List<Map<String, Object>> getTeamsSummary(int seasonStartYear, String scope) {
    return getTeamsSummaryBundle(seasonStartYear, scope).teams();
  }

  public TeamsSummaryBundle getTeamsSummaryBundle(int seasonStartYear, String scope) {
    List<JsonNode> rows = rowsForScope(seasonStartYear, scope);
    if (rows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该赛季统计数据");
    }
    Optional<Path> standingsPath = resolveLeagueStandingsPath(seasonStartYear);
    JsonNode standingsArr =
        standingsPath.map(this::loadStandingsFileOrNull).orElse(null);
    Map<String, JsonNode> stByAbbr = indexStandingsByAbbr(standingsArr);
    boolean useStandingsSort = stByAbbr.size() >= 25;
    String standingsHint;
    if (useStandingsSort) {
      standingsHint = standingsPath.map(Path::toString).orElse("merged");
    } else if (standingsPath.isEmpty()) {
      standingsHint =
          "missing league_standings_"
              + seasonFileTag(seasonStartYear)
              + ".json (tried output dir, stats sibling dir, 爬虫/output)";
    } else if (standingsArr == null) {
      standingsHint = "invalid or empty JSON: " + standingsPath.get();
    } else {
      standingsHint =
          "standings teams indexed " + stByAbbr.size() + " (<25): " + standingsPath.get();
    }

    Map<String, List<JsonNode>> by = new HashMap<>();
    for (JsonNode r : rows) {
      String ab = r.path("球队缩写").asText("").trim();
      if (ab.isEmpty()) ab = "—";
      if (isAggregateTeamAbbr(ab)) continue;
      by.computeIfAbsent(ab, k -> new ArrayList<>()).add(r);
    }
    List<Map<String, Object>> out = new ArrayList<>();
    for (var e : by.entrySet()) {
      String abbr = e.getKey();
      List<JsonNode> list = e.getValue();
      list.sort(
          Comparator.comparingDouble((JsonNode x) -> x.path("得分").asDouble(0)).reversed());
      JsonNode top = list.get(0);
      double avgPts =
          list.stream().mapToDouble(x -> x.path("得分").asDouble(0)).average().orElse(0);
      Map<String, Object> topScorer = new LinkedHashMap<>();
      String tname = top.path("球员姓名").asText("").trim();
      topScorer.put("name", tname.isEmpty() ? "#" + top.path("球员ID").asInt() : tname);
      topScorer.put("pts", round1(top.path("得分").asDouble(0)));
      String slug = top.path("球员网址别名").asText("").trim();
      topScorer.put("brSlug", slug.isEmpty() ? null : slug);
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("abbr", abbr);
      row.put("playerCount", list.size());
      row.put("avgPts", round1(avgPts));
      row.put("topScorer", topScorer);
      JsonNode st = stByAbbr.get(abbr.toUpperCase(Locale.ROOT));
      if (st != null) {
        row.put("conference", st.path("conference").asText(""));
        row.put("confRank", st.path("confRank").asInt(99));
        row.put("wins", st.path("wins").asInt(0));
        row.put("losses", st.path("losses").asInt(0));
        row.put("winPct", st.path("winPct").asDouble(0));
      }
      out.add(row);
    }
    if (useStandingsSort) {
      out.sort(
          Comparator.<Map<String, Object>>comparingInt(
                  NbaCrawlerDataService::conferenceSortKey)
              .thenComparingInt(
                  m -> ((Number) m.getOrDefault("confRank", 99)).intValue())
              .thenComparing(
                  m -> ((Number) m.getOrDefault("winPct", 0.0)).doubleValue(),
                  Comparator.reverseOrder())
              .thenComparing(
                  m -> ((Number) m.getOrDefault("wins", 0)).intValue(),
                  Comparator.reverseOrder()));
    } else {
      log.warn("赛季 {} 球队列表未合并战绩：{}", seasonStartYear, standingsHint);
      out.sort(
          Comparator.<Map<String, Object>, Double>comparing(m -> (Double) m.get("avgPts"))
              .reversed());
    }
    if (useStandingsSort) {
      List<Map<String, Object>> withPct = new ArrayList<>();
      for (Map<String, Object> leagueRow : out) {
        if (leagueRow.containsKey("winPct")) {
          withPct.add(leagueRow);
        }
      }
      withPct.sort(
          Comparator.comparing(
                  (Map<String, Object> m) -> ((Number) m.get("winPct")).doubleValue(),
                  Comparator.reverseOrder())
              .thenComparing(
                  m -> ((Number) m.get("wins")).intValue(), Comparator.reverseOrder())
              .thenComparing(m -> String.valueOf(m.get("abbr"))));
      for (int i = 0; i < withPct.size(); i++) {
        withPct.get(i).put("leagueRank", i + 1);
      }
    }
    return new TeamsSummaryBundle(out, useStandingsSort, standingsHint);
  }

  /** 在多目录中查找与「球员 pergame 表」同目录或默认 output 下的战绩文件（解决 IDE cwd / 环境目录不一致）。 */
  private List<Path> candidateLeagueStandingsPaths(int seasonStartYear) {
    String name = "league_standings_" + seasonFileTag(seasonStartYear) + ".json";
    LinkedHashSet<Path> candidates = new LinkedHashSet<>();
    candidates.add(getOutputDir().resolve(name).toAbsolutePath().normalize());
    Path statsReg = statsFilePath(seasonStartYear, "regular");
    if (statsReg.getParent() != null) {
      candidates.add(statsReg.getParent().resolve(name).toAbsolutePath().normalize());
    }
    try {
      if (Files.isRegularFile(statsReg)) {
        candidates.add(statsReg.toRealPath().getParent().resolve(name).normalize());
      }
    } catch (IOException ignored) {
    }
    candidates.add(getCrawlerHome().resolve("output").resolve(name).toAbsolutePath().normalize());
    Path cwd = Paths.get("").toAbsolutePath();
    for (String rel :
        List.of(
            "爬虫/output",
            "../爬虫/output",
            "../spring-backend/../爬虫/output",
            "spring-backend/../爬虫/output")) {
      candidates.add(cwd.resolve(rel).resolve(name).normalize().toAbsolutePath().normalize());
    }
    return new ArrayList<>(candidates);
  }

  private Optional<Path> resolveLeagueStandingsPath(int seasonStartYear) {
    for (Path p : candidateLeagueStandingsPaths(seasonStartYear)) {
      if (Files.isRegularFile(p)) {
        log.info("使用 NBA 战绩文件：{}", p);
        return Optional.of(p);
      }
    }
    return Optional.empty();
  }

  private JsonNode loadStandingsFileOrNull(Path p) {
    try {
      JsonNode root = readJsonCached(p);
      return root != null && root.isArray() ? root : null;
    } catch (Exception e) {
      log.warn("读取战绩文件失败 {}：{}", p, e.getMessage());
      return null;
    }
  }

  private static Map<String, JsonNode> indexStandingsByAbbr(JsonNode arr) {
    if (arr == null || !arr.isArray()) {
      return Map.of();
    }
    Map<String, JsonNode> m = new HashMap<>();
    for (JsonNode n : arr) {
      String ab = n.path("abbr").asText("").trim().toUpperCase(Locale.ROOT);
      if (!ab.isEmpty()) {
        m.put(ab, n);
      }
    }
    return m;
  }

  /** 列表排序：东部 → 西部；无联盟字段的队排在最后 */
  private static int conferenceSortKey(Map<String, Object> row) {
    Object c = row.get("conference");
    if (c == null) {
      return 9;
    }
    String s = String.valueOf(c).trim();
    if (s.isEmpty()) {
      return 9;
    }
    if ("East".equalsIgnoreCase(s)) {
      return 0;
    }
    if ("West".equalsIgnoreCase(s)) {
      return 1;
    }
    return 5;
  }

  private String resolvePlayerName(int seasonStartYear, int playerId, JsonNode statRow) {
    if (statRow != null && !statRow.path("球员姓名").asText("").trim().isEmpty()) {
      return statRow.path("球员姓名").asText("").trim();
    }
    String tag = seasonFileTag(seasonStartYear);
    Path dirPath = getOutputDir().resolve("player_directory_" + tag + ".json");
    if (!Files.isRegularFile(dirPath)) return "#" + playerId;
    JsonNode dir = readJsonCached(dirPath);
    if (!dir.isArray()) return "#" + playerId;
    for (JsonNode d : dir) {
      if (d.path("球员ID").asInt(0) == playerId) {
        String n = d.path("姓名").asText("").trim();
        return n.isEmpty() ? "#" + playerId : n;
      }
    }
    return "#" + playerId;
  }

  private String resolveBrSlugFromDirectory(int seasonStartYear, int playerId) {
    String tag = seasonFileTag(seasonStartYear);
    Path dirPath = getOutputDir().resolve("player_directory_" + tag + ".json");
    if (!Files.isRegularFile(dirPath)) return "";
    JsonNode dir = readJsonCached(dirPath);
    if (!dir.isArray()) return "";
    for (JsonNode d : dir) {
      if (d.path("球员ID").asInt(0) == playerId) {
        return d.path("球员网址别名").asText("").trim();
      }
    }
    return "";
  }

  public Map<String, Object> getPlayerDetail(int playerId, int seasonStartYear, String scope) {
    List<JsonNode> reg = loadStatsRowsOrNull(seasonStartYear, "regular");
    List<JsonNode> post = loadStatsRowsOrNull(seasonStartYear, "playoff");
    Avg merged = null;
    JsonNode statRowForName = null;

    if ("regular".equals(scope)) {
      if (reg == null) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "未找到 "
                + seasonStartYear
                + "-"
                + String.format("%02d", (seasonStartYear + 1) % 100)
                + " 常规赛 per_game 数据文件，请先运行爬虫生成数据。");
      }
      JsonNode row = findStatRow(reg, playerId);
      if (row == null) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "球员在该赛季常规赛统计中无记录");
      }
      merged = rowToAvg(row);
      statRowForName = row;
    } else if ("playoff".equals(scope)) {
      if (post == null) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "未找到该赛季季后赛统计文件（或尚未产生季后赛数据）。");
      }
      JsonNode row = findStatRow(post, playerId);
      if (row == null) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND, "球员在该赛季季后赛统计中无记录");
      }
      merged = rowToAvg(row);
      statRowForName = row;
    } else {
      JsonNode ra = reg != null ? findStatRow(reg, playerId) : null;
      JsonNode pa = post != null ? findStatRow(post, playerId) : null;
      if (ra == null && pa == null) {
        if (reg == null && post == null) {
          throw new ResponseStatusException(
              HttpStatus.NOT_FOUND, "未找到该赛季的爬虫统计文件，请先运行爬虫生成数据。");
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "球员在该赛季统计中无记录");
      }
      merged = mergeAvg(ra != null ? rowToAvg(ra) : null, pa != null ? rowToAvg(pa) : null);
      statRowForName = ra != null ? ra : pa;
    }

    if (merged == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "球员无可用统计");
    }

    String name = resolvePlayerName(seasonStartYear, playerId, statRowForName);
    String brSlug =
        statRowForName != null
            ? statRowForName.path("球员网址别名").asText("").trim()
            : "";
    if (brSlug.isEmpty()) brSlug = resolveBrSlugFromDirectory(seasonStartYear, playerId);

    Map<String, Object> player = new LinkedHashMap<>();
    player.put("id", playerId);
    player.put("name", name);
    player.put("brSlug", brSlug.isEmpty() ? null : brSlug);

    Map<String, Object> averages = new LinkedHashMap<>();
    averages.put("pts", merged.pts);
    averages.put("reb", merged.reb);
    averages.put("ast", merged.ast);
    averages.put("games", merged.games);

    Map<String, Object> career = new LinkedHashMap<>();
    career.put("pts", 0);
    career.put("date", "—");
    career.put(
        "matchup", "Basketball-Reference 联盟表为赛季汇总，无单场得分纪录");

    Map<String, Object> root = new LinkedHashMap<>();
    root.put("player", player);
    root.put("season", seasonStartYear);
    root.put("scope", scope);
    root.put("averages", averages);
    root.put("last10Points", List.of());
    root.put("careerHighGame", career);
    root.put("last5Games", List.of());
    root.put("dataSource", "nba_crawler");
    root.put(
        "dataNote",
        "数据由 nba-crawler 从 Basketball-Reference 抓取联盟统计表（球员 ID 为 BR 球员 slug 的稳定哈希，非 NBA 官网 PERSON_ID）。近场次与单场最高需单场日志类数据源。");
    return root;
  }

  public Map<String, Object> compare(int a, int b, int season, String scope) {
    if (a == b) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Pick two different players");
    }
    Map<String, Object> da = getPlayerDetail(a, season, scope);
    Map<String, Object> db = getPlayerDetail(b, season, scope);
    @SuppressWarnings("unchecked")
    Map<String, Object> pa = (Map<String, Object>) da.get("player");
    @SuppressWarnings("unchecked")
    Map<String, Object> pb = (Map<String, Object>) db.get("player");
    @SuppressWarnings("unchecked")
    Map<String, Object> aa = (Map<String, Object>) da.get("averages");
    @SuppressWarnings("unchecked")
    Map<String, Object> abv = (Map<String, Object>) db.get("averages");
    Map<String, Object> one = new LinkedHashMap<>();
    one.put("id", pa.get("id"));
    one.put("name", pa.get("name"));
    one.put("pts", aa.get("pts"));
    one.put("reb", aa.get("reb"));
    one.put("ast", aa.get("ast"));
    one.put("games", aa.get("games"));
    Map<String, Object> two = new LinkedHashMap<>();
    two.put("id", pb.get("id"));
    two.put("name", pb.get("name"));
    two.put("pts", abv.get("pts"));
    two.put("reb", abv.get("reb"));
    two.put("ast", abv.get("ast"));
    two.put("games", abv.get("games"));
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("season", season);
    root.put("scope", scope);
    root.put("players", List.of(one, two));
    return root;
  }

  /**
   * 球队近常规赛场次（默认最多 10 场，新→旧）。
   *
   * <p>优先读爬虫写入的 {@code team_recent_games_*.json}（由 {@code ./更新} 生成），避免每次请求都访问
   * stats.nba.com 导致长时间阻塞。仅当本地无该队数据时再请求 NBA Stats（短超时 + 内存缓存）。
   */
  public List<Map<String, Object>> getTeamRecentGames(String abbrUpper, int seasonStartYear) {
    String abbr = abbrUpper.trim().toUpperCase(Locale.ROOT);
    if (abbr.isEmpty()) {
      return List.of();
    }
    List<Map<String, Object>> fromFile = loadTeamRecentGamesFromFiles(abbr, seasonStartYear);
    if (!fromFile.isEmpty()) {
      return fromFile;
    }
    String liveKey = abbr + ":" + seasonStartYear;
    long now = System.currentTimeMillis();
    RecentGamesLiveCache cached = recentGamesLiveCache.get(liveKey);
    if (cached != null && cached.expiresAtMs > now && !cached.rows().isEmpty()) {
      return cached.rows();
    }
    List<Map<String, Object>> live = fetchTeamRecentGamesLive(abbr, seasonStartYear);
    if (!live.isEmpty()) {
      recentGamesLiveCache.put(
          liveKey, new RecentGamesLiveCache(now + RECENT_GAMES_LIVE_CACHE_TTL_MS, live));
      return live;
    }
    return List.of();
  }

  private List<Map<String, Object>> loadTeamRecentGamesFromFiles(String abbr, int seasonStartYear) {
    for (Path p : candidateTeamRecentGamesPaths(seasonStartYear)) {
      if (!Files.isRegularFile(p)) {
        continue;
      }
      try {
        JsonNode root = objectMapper.readTree(Files.newInputStream(p));
        JsonNode arr = root.path(abbr);
        if (arr.isArray() && !arr.isEmpty()) {
          List<Map<String, Object>> fromFile = mapRecentGamesJsonArray(arr);
          if (!fromFile.isEmpty()) {
            return fromFile;
          }
        }
      } catch (IOException e) {
        log.warn("读取 {} 失败：{}", p, e.getMessage());
      }
    }
    return List.of();
  }

  private List<Path> candidateTeamRecentGamesPaths(int seasonStartYear) {
    String name = "team_recent_games_" + seasonFileTag(seasonStartYear) + ".json";
    LinkedHashSet<Path> candidates = new LinkedHashSet<>();
    candidates.add(getOutputDir().resolve(name).toAbsolutePath().normalize());
    Path statsReg = statsFilePath(seasonStartYear, "regular");
    if (statsReg.getParent() != null) {
      candidates.add(statsReg.getParent().resolve(name).toAbsolutePath().normalize());
    }
    try {
      if (Files.isRegularFile(statsReg)) {
        candidates.add(statsReg.toRealPath().getParent().resolve(name).normalize());
      }
    } catch (IOException ignored) {
    }
    candidates.add(getCrawlerHome().resolve("output").resolve(name).toAbsolutePath().normalize());
    Path cwd = Paths.get("").toAbsolutePath();
    for (String rel :
        List.of(
            "爬虫/output",
            "../爬虫/output",
            "../spring-backend/../爬虫/output",
            "spring-backend/../爬虫/output")) {
      candidates.add(cwd.resolve(rel).resolve(name).normalize().toAbsolutePath().normalize());
    }
    return new ArrayList<>(candidates);
  }

  private List<Map<String, Object>> mapRecentGamesJsonArray(JsonNode arr) {
    List<Map<String, Object>> rows = new ArrayList<>();
    for (JsonNode n : arr) {
      if (!n.isObject()) {
        continue;
      }
      Map<String, Object> one = new LinkedHashMap<>();
      String date =
          textOrEmpty(n, "gameDate", "GAME_DATE", "date");
      String matchup = textOrEmpty(n, "matchup", "MATCHUP");
      String wl = textOrEmpty(n, "wl", "WL");
      int pts = n.path("pts").asInt(n.path("PTS").asInt(0));
      JsonNode opp = n.get("oppPts");
      if (opp == null || opp.isNull()) {
        opp = n.get("opp_pts");
      }
      Integer oppPts = null;
      if (opp != null && !opp.isNull()) {
        if (opp.isNumber()) {
          oppPts = opp.asInt();
        } else {
          try {
            oppPts = Integer.parseInt(opp.asText().trim());
          } catch (NumberFormatException ignored) {
          }
        }
      }
      if (!wl.isEmpty() && (wl.length() == 1 || wl.equalsIgnoreCase("win") || wl.equalsIgnoreCase("loss"))) {
        if (wl.length() > 1) {
          wl = wl.toLowerCase(Locale.ROOT).startsWith("w") ? "W" : "L";
        } else {
          wl = wl.toUpperCase(Locale.ROOT);
        }
      }
      one.put("gameDate", date);
      one.put("matchup", matchup);
      one.put("wl", wl);
      one.put("pts", pts);
      if (oppPts != null) {
        one.put("oppPts", oppPts);
      }
      one.put("win", "W".equalsIgnoreCase(wl));
      rows.add(one);
    }
    rows.sort(Comparator.comparingLong(m -> -parseNbaGameDateMillis((String) m.get("gameDate"))));
    return trimRecentGames(rows, 10);
  }

  private static String textOrEmpty(JsonNode n, String... keys) {
    for (String k : keys) {
      if (n.has(k) && !n.path(k).isNull()) {
        String t = n.path(k).asText("").trim();
        if (!t.isEmpty()) {
          return t;
        }
      }
    }
    return "";
  }

  private List<Map<String, Object>> trimRecentGames(List<Map<String, Object>> rows, int max) {
    if (rows.size() <= max) {
      return rows;
    }
    return new ArrayList<>(rows.subList(0, max));
  }

  private List<Map<String, Object>> fetchTeamRecentGamesLive(String abbr, int seasonPreview) {
    Optional<Integer> tid = resolveTeamIdFromStandings(seasonPreview, abbr);
    if (tid.isEmpty()) {
      return List.of();
    }
    String seasonLabel = seasonApiLabel(seasonPreview);
    String url =
        "https://stats.nba.com/stats/leaguegamefinder?PlayerOrTeam=T&TeamID="
            + tid.get()
            + "&Season="
            + seasonLabel
            + "&SeasonType=Regular%20Season&LeagueID=00";
    try {
      HttpRequest req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(NBA_STATS_RECENT_GAMES_TIMEOUT)
              .header(
                  "User-Agent",
                  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
              .header("Accept", "application/json, text/plain, */*")
              .header("Referer", "https://www.nba.com/")
              .GET()
              .build();
      HttpResponse<String> resp =
          NBA_STATS_HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (resp.statusCode() != 200) {
        log.debug("team recent games: stats.nba.com HTTP {}", resp.statusCode());
        return List.of();
      }
      JsonNode root = objectMapper.readTree(resp.body());
      return parseLeagueGameFinderRows(root, 10);
    } catch (Exception e) {
      log.debug("team recent games live fetch failed: {}", e.getMessage());
      return List.of();
    }
  }

  private Optional<Integer> resolveTeamIdFromStandings(int seasonStartYear, String abbr) {
    Optional<Path> path = resolveLeagueStandingsPath(seasonStartYear);
    if (path.isEmpty()) {
      return Optional.empty();
    }
    try {
      JsonNode arr = objectMapper.readTree(Files.newInputStream(path.get()));
      if (!arr.isArray()) {
        return Optional.empty();
      }
      for (JsonNode n : arr) {
        String a = n.path("abbr").asText("").trim();
        if (a.isEmpty()) {
          a = n.path("nbaAbbr").asText("").trim();
        }
        if (abbr.equalsIgnoreCase(a)) {
          int id = n.path("teamId").asInt(0);
          if (id > 0) {
            return Optional.of(id);
          }
        }
      }
    } catch (IOException e) {
      log.warn("resolveTeamIdFromStandings: {}", e.getMessage());
    }
    return Optional.empty();
  }

  private static String seasonApiLabel(int seasonStartYear) {
    int y2 = (seasonStartYear + 1) % 100;
    return seasonStartYear + "-" + String.format("%02d", y2);
  }

  private List<Map<String, Object>> parseLeagueGameFinderRows(JsonNode root, int limit) {
    JsonNode rs = root.path("resultSets");
    if (!rs.isArray() || rs.isEmpty()) {
      return List.of();
    }
    JsonNode set = rs.get(0);
    JsonNode headers = set.path("headers");
    JsonNode rowSet = set.path("rowSet");
    if (!headers.isArray() || !rowSet.isArray()) {
      return List.of();
    }
    Map<String, Integer> ix = new HashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      ix.put(headers.get(i).asText(), i);
    }
    List<Map<String, Object>> parsed = new ArrayList<>();
    for (JsonNode row : rowSet) {
      if (!row.isArray()) {
        continue;
      }
      String wl = cellStr(row, ix, "WL");
      if (wl.isEmpty()) {
        continue;
      }
      String date = cellStr(row, ix, "GAME_DATE");
      String matchup = cellStr(row, ix, "MATCHUP");
      int pts = cellInt(row, ix, "PTS", 0);
      Integer pm = cellIntNullable(row, ix, "PLUS_MINUS");
      Integer oppPts = null;
      if (pm != null) {
        oppPts = pts - pm;
      }
      Map<String, Object> one = new LinkedHashMap<>();
      one.put("gameDate", date);
      one.put("matchup", matchup);
      one.put("wl", wl.toUpperCase(Locale.ROOT));
      one.put("pts", pts);
      if (oppPts != null) {
        one.put("oppPts", oppPts);
      }
      one.put("win", "W".equalsIgnoreCase(wl));
      parsed.add(one);
    }
    parsed.sort(Comparator.comparingLong(m -> -parseNbaGameDateMillis((String) m.get("gameDate"))));
    return trimRecentGames(parsed, limit);
  }

  private static String cellStr(JsonNode row, Map<String, Integer> ix, String key) {
    Integer i = ix.get(key);
    if (i == null || i < 0 || i >= row.size()) {
      return "";
    }
    JsonNode c = row.get(i);
    if (c == null || c.isNull()) {
      return "";
    }
    return c.asText("").trim();
  }

  private static int cellInt(JsonNode row, Map<String, Integer> ix, String key, int dflt) {
    Integer i = ix.get(key);
    if (i == null || i < 0 || i >= row.size()) {
      return dflt;
    }
    JsonNode c = row.get(i);
    if (c == null || c.isNull()) {
      return dflt;
    }
    return c.asInt(dflt);
  }

  private static Integer cellIntNullable(JsonNode row, Map<String, Integer> ix, String key) {
    Integer i = ix.get(key);
    if (i == null || i < 0 || i >= row.size()) {
      return null;
    }
    JsonNode c = row.get(i);
    if (c == null || c.isNull()) {
      return null;
    }
    if (c.isIntegralNumber()) {
      return c.asInt();
    }
    try {
      return (int) Math.round(Double.parseDouble(c.asText().trim()));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static long parseNbaGameDateMillis(String raw) {
    if (raw == null || raw.isBlank()) {
      return 0L;
    }
    String s = raw.trim();
    try {
      LocalDate d = LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
      return d.atStartOfDay(ZoneId.of("America/New_York")).toInstant().toEpochMilli();
    } catch (DateTimeParseException ignored) {
    }
    for (String pat : List.of("MMM d, yyyy", "MMM dd, yyyy", "MMMM d, yyyy")) {
      try {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(pat, Locale.US);
        LocalDate d = LocalDate.parse(s, fmt);
        return d.atStartOfDay(ZoneId.of("America/New_York")).toInstant().toEpochMilli();
      } catch (DateTimeParseException ignored) {
      }
    }
    return 0L;
  }
}
