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

export async function refreshCrawler() {
  const headers = {};
  const t = String(import.meta.env.VITE_REFRESH_TOKEN || "").trim();
  if (t) headers["X-NBA-Refresh-Token"] = t;
  return nbaFetch("/public/nba/data/refresh", {
    method: "POST",
    headers,
  });
}
