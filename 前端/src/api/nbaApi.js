/**
 * 与 Zeabur 等部署的后端通信：基址来自环境变量 VITE_API_BASE（生产一般为 https://你的服务/api）。
 * scope：常规赛 / 季后赛 / 合并，由 VITE_STATS_SCOPE 控制，默认 regular。
 */
const baseUrl = () =>
  String(import.meta.env.VITE_API_BASE || "http://localhost:3000/api").replace(/\/$/, "");

const scope = () => import.meta.env.VITE_STATS_SCOPE || "regular";

/** 统一 fetch：自动拼接 API 基址、解析 JSON、把 Spring 错误信息转成 Error */
async function nbaFetch(path, init = {}) {
  const r = await fetch(`${baseUrl()}${path}`, init);
  const txt = await r.text();
  if (!r.ok) {
    let msg = txt || r.statusText;
    try {
      const j = JSON.parse(txt);
      if (Array.isArray(j.message)) msg = j.message.join(" ");
      else if (typeof j.message === "string") msg = j.message;
    } catch (_) {}
    throw new Error(msg || `HTTP ${r.status}`);
  }
  return txt ? JSON.parse(txt) : null;
}

export function nbaApiScope() {
  return scope();
}

/** 后端从爬虫 output 扫描得到的赛季列表（用于赛季下拉框） */
export async function seasons() {
  return nbaFetch("/public/nba/seasons");
}

/** 得分榜：按场均分降序，受 limit / minGames 约束 */
export async function leaderboard(seasonStartYear, opt = {}) {
  const limit = opt.limit ?? 600;
  const minGames = opt.minGames ?? 1;
  const sc = encodeURIComponent(scope());
  return nbaFetch(
    `/public/nba/leaderboard?season=${seasonStartYear}&scope=${sc}&limit=${limit}&minGames=${minGames}`,
  );
}

/** 球队汇总：场均、队内得分王、若存在战绩 JSON 则带分区排名与胜负 */
export async function teams(seasonStartYear) {
  const sc = encodeURIComponent(scope());
  return nbaFetch(`/public/nba/teams?season=${seasonStartYear}&scope=${sc}`);
}

/**
 * 球队近若干场常规赛比分（后端优先读爬虫 JSON，无则短时请求 NBA Stats）。
 * @returns {Promise<Array<{ gameDate: string, matchup: string, wl: string, pts: number, oppPts?: number, win: boolean }>>}
 */
export async function teamRecentGames(teamAbbr, seasonStartYear) {
  const sc = encodeURIComponent(scope());
  const abbr = encodeURIComponent(String(teamAbbr).toUpperCase());
  return nbaFetch(
    `/public/nba/teams/${abbr}/recent-games?season=${seasonStartYear}&scope=${sc}`,
  );
}

/** 球员详情页：场均与元数据（数据源自爬虫 per-game JSON） */
export async function playerDetail(playerId, seasonStartYear) {
  const sc = encodeURIComponent(scope());
  return nbaFetch(
    `/public/nba/players/${playerId}/detail?season=${seasonStartYear}&scope=${sc}`,
  );
}

/**
 * 优先从后端拉取爬虫生成的映射（云托管）；失败则回退到静态 `public/*.json`（仅 dev / 旧部署）。
 * @param {string} apiPath 如 `/public/nba/i18n/player-zh`
 * @param {string} staticName 如 `player-zh.json`
 * @param {string} staticBaseHref `staticFilesBaseHref()`
 */
export async function fetchI18nJsonPreferApi(apiPath, staticName, staticBaseHref) {
  try {
    const r = await fetch(`${baseUrl()}${apiPath}`);
    if (r.ok) {
      const j = await r.json();
      if (j && typeof j === "object" && !Array.isArray(j)) return j;
    }
  } catch (_) {
    /* 离线或 CORS 等 */
  }
  try {
    const r = await fetch(new URL(staticName, staticBaseHref));
    if (r.ok) return await r.json();
  } catch (_) {
    /* ignore */
  }
  return {};
}
