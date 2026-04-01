/** 球队中文名、队标、球员名/头像（由爬虫 JSON + store 注入） */

const TEAM_ZH = {
  ATL: "亚特兰大老鹰",
  BOS: "波士顿凯尔特人",
  BRK: "布鲁克林篮网",
  CHO: "夏洛特黄蜂",
  CHI: "芝加哥公牛",
  CLE: "克利夫兰骑士",
  DAL: "达拉斯独行侠",
  DEN: "丹佛掘金",
  DET: "底特律活塞",
  GSW: "金州勇士",
  HOU: "休斯敦火箭",
  IND: "印第安纳步行者",
  LAC: "洛杉矶快船",
  LAL: "洛杉矶湖人",
  MEM: "孟菲斯灰熊",
  MIA: "迈阿密热火",
  MIL: "密尔沃基雄鹿",
  MIN: "明尼苏达森林狼",
  NOP: "新奥尔良鹈鹕",
  NYK: "纽约尼克斯",
  OKC: "俄克拉荷马城雷霆",
  ORL: "奥兰多魔术",
  PHI: "费城76人",
  PHO: "菲尼克斯太阳",
  POR: "波特兰开拓者",
  SAC: "萨克拉门托国王",
  SAS: "圣安东尼奥马刺",
  TOR: "多伦多猛龙",
  UTA: "犹他爵士",
  WAS: "华盛顿奇才",
  "2TM": "多支球队",
  "3TM": "多支球队",
  TOT: "多支球队",
};

/** ESPN CDN 文件名与 BR 缩写不一致时在此映射（uta.png 为 404，需用 utah） */
const LOGO_SLUG = {
  BRK: "bkn",
  CHO: "cha",
  PHO: "phx",
  UTA: "utah",
  NOP: "no",
};

/** 官方 NBA Stats 球队 ID，用于 cdn.nba.com Logo 兜底（PNG/SVG） */
const NBA_TEAM_ID = {
  ATL: 1610612737,
  BOS: 1610612738,
  BRK: 1610612751,
  CHO: 1610612766,
  CHI: 1610612741,
  CLE: 1610612739,
  DAL: 1610612742,
  DEN: 1610612743,
  DET: 1610612765,
  GSW: 1610612744,
  HOU: 1610612745,
  IND: 1610612754,
  LAC: 1610612746,
  LAL: 1610612747,
  MEM: 1610612763,
  MIA: 1610612748,
  MIL: 1610612749,
  MIN: 1610612750,
  NOP: 1610612740,
  NYK: 1610612752,
  OKC: 1610612760,
  ORL: 1610612753,
  PHI: 1610612755,
  PHO: 1610612756,
  POR: 1610612757,
  SAC: 1610612758,
  SAS: 1610612759,
  TOR: 1610612761,
  UTA: 1610612762,
  WAS: 1610612764,
};

/** Basketball-Reference 球队缩写 → 东西部（用于分区排行展示） */
const TEAM_CONFERENCE_EAST = new Set([
  "ATL",
  "BOS",
  "BRK",
  "CHO",
  "CHI",
  "CLE",
  "DET",
  "IND",
  "MIA",
  "MIL",
  "NYK",
  "ORL",
  "PHI",
  "TOR",
  "WAS",
]);
const TEAM_CONFERENCE_WEST = new Set([
  "DAL",
  "DEN",
  "GSW",
  "HOU",
  "LAC",
  "LAL",
  "MEM",
  "MIN",
  "NOP",
  "OKC",
  "PHO",
  "POR",
  "SAC",
  "SAS",
  "UTA",
]);

let zhMap = {};
let brToId = {};

export function setI18nMaps(zh, br) {
  zhMap = zh || {};
  brToId = br || {};
}

export function teamZh(abbr) {
  const u = String(abbr || "").toUpperCase();
  if (!u || u === "—") return "—";
  return TEAM_ZH[u] || u;
}

/** @returns {"east"|"west"|null} */
export function teamConference(abbr) {
  const u = String(abbr || "").toUpperCase();
  if (TEAM_CONFERENCE_EAST.has(u)) return "east";
  if (TEAM_CONFERENCE_WEST.has(u)) return "west";
  return null;
}

/** 优先使用 API 返回的联盟（NBA Stats），否则按 BR 缩写静态表 */
export function teamRowConference(team) {
  const raw = String(team?.conference ?? "").trim().toLowerCase();
  if (raw === "east") return "east";
  if (raw === "west") return "west";
  return teamConference(team?.abbr);
}

/**
 * 东西部分开；有 confRank 时按常规赛分区名次，否则按球员场均均值（avgPts）排序。
 */
export function partitionTeamsByConference(teams) {
  const east = [];
  const west = [];
  for (const t of teams || []) {
    const c = teamRowConference(t);
    if (c === "east") east.push(t);
    else if (c === "west") west.push(t);
  }
  const byStandings = (a, b) => {
    const ra = Number(a?.confRank);
    const rb = Number(b?.confRank);
    const hasA = Number.isFinite(ra) && a.confRank != null;
    const hasB = Number.isFinite(rb) && b.confRank != null;
    if (hasA && hasB && ra !== rb) return ra - rb;
    if (hasA && !hasB) return -1;
    if (!hasA && hasB) return 1;
    return (Number(b?.avgPts) || 0) - (Number(a?.avgPts) || 0);
  };
  east.sort(byStandings);
  west.sort(byStandings);
  return { east, west };
}

export function teamLogoUrl(abbr) {
  const u = String(abbr || "").toUpperCase();
  if (!u || u === "—" || /^\d+TM$/.test(u)) return "";
  const slug = (LOGO_SLUG[u] || u).toLowerCase();
  return `https://a.espncdn.com/i/teamlogos/nba/500/${slug}.png`;
}

/** ESPN 加载失败时的备用：NBA 官方 SVG（更易被拦截 CORS，但多数浏览器可直接作 img src） */
export function teamLogoUrlNba(abbr) {
  const u = String(abbr || "").toUpperCase();
  const id = NBA_TEAM_ID[u];
  return id ? `https://cdn.nba.com/logos/nba/${id}/global/L/logo.svg` : "";
}

function hasCjk(s) {
  return /[\u3400-\u9fff]/.test(s);
}

export function playerZh(enName) {
  const raw = String(enName ?? "").trim();
  if (!raw || !zhMap) return raw;
  const direct = zhMap[raw];
  if (direct && (direct !== raw || hasCjk(direct))) return direct;
  const nfc = raw.normalize("NFC");
  if (nfc !== raw) {
    const hit = zhMap[nfc];
    if (hit && (hit !== nfc || hasCjk(hit))) return hit;
  }
  return raw;
}

export function nbaHeadshotUrl(brSlug) {
  const s = String(brSlug || "").trim();
  if (!s) return "";
  const pid = brToId[s];
  if (!pid) return "";
  return `https://cdn.nba.com/headshots/nba/latest/260x190/${pid}.png`;
}
