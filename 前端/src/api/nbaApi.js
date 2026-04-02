const baseUrl = () =>
  String(import.meta.env.VITE_API_BASE || "http://localhost:3000/api").replace(/\/$/, "");

const scope = () => import.meta.env.VITE_STATS_SCOPE || "regular";

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

export async function seasons() {
  return nbaFetch("/public/nba/seasons");
}

export async function leaderboard(seasonStartYear, opt = {}) {
  const limit = opt.limit ?? 600;
  const minGames = opt.minGames ?? 1;
  const sc = encodeURIComponent(scope());
  return nbaFetch(
    `/public/nba/leaderboard?season=${seasonStartYear}&scope=${sc}&limit=${limit}&minGames=${minGames}`,
  );
}

export async function teams(seasonStartYear) {
  const sc = encodeURIComponent(scope());
  return nbaFetch(`/public/nba/teams?season=${seasonStartYear}&scope=${sc}`);
}

/** @returns {Promise<Array<{ gameDate: string, matchup: string, wl: string, pts: number, oppPts?: number, win: boolean }>>} */
export async function teamRecentGames(teamAbbr, seasonStartYear) {
  const sc = encodeURIComponent(scope());
  const abbr = encodeURIComponent(String(teamAbbr).toUpperCase());
  return nbaFetch(
    `/public/nba/teams/${abbr}/recent-games?season=${seasonStartYear}&scope=${sc}`,
  );
}

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
