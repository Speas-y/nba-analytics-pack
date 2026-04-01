import { defineStore } from "pinia";
import * as api from "../api/nbaApi";
import {
  teamZh,
  teamLogoUrl,
  playerZh as pZh,
  nbaHeadshotUrl,
  setI18nMaps,
} from "../utils/nbaI18n";

const STORAGE_YEAR = "nba-pc-analytics-season-year";

/** Vite 的 BASE_URL 只是路径（如 /），必须与页面 origin 拼成绝对 URL，否则 new URL(".", "/") 会报错 */
function staticFilesBaseHref() {
  const path = import.meta.env.BASE_URL || "/";
  return new URL(path, `${window.location.origin}/`).href;
}

export const useNbaStore = defineStore("nba", {
  state: () => ({
    status: "loading",
    error: "",
    seasons: [],
    seasonYear: null,
    leaderboard: [],
    teams: [],
    detailById: {},
  }),
  getters: {
    seasonLabel: (s) => {
      const y = s.seasonYear;
      if (y == null) return "";
      const end = String((y + 1) % 100).padStart(2, "0");
      return `${y}–${end}`;
    },
    tZh: () => (abbr) => teamZh(abbr),
    logoUrl: () => (abbr) => teamLogoUrl(abbr),
    pZh: () => (name) => pZh(name),
    headshot: () => (brSlug) => nbaHeadshotUrl(brSlug),
  },
  actions: {
    getSeasonYear() {
      if (this.seasonYear != null) return this.seasonYear;
      const raw = localStorage.getItem(STORAGE_YEAR);
      const n = raw != null ? Number(raw) : NaN;
      if (!Number.isNaN(n)) return n;
      return new Date().getFullYear();
    },
    setSeasonYear(y) {
      const n = Number(y);
      if (!Number.isFinite(n)) return;
      localStorage.setItem(STORAGE_YEAR, String(n));
      this.seasonYear = n;
    },
    async loadBundle() {
      this.status = "loading";
      this.error = "";
      try {
        const base = staticFilesBaseHref();
        const [zhMap, idMap, seasons] = await Promise.all([
          fetch(new URL("player-zh.json", base))
            .then((r) => (r.ok ? r.json() : {}))
            .catch(() => ({})),
          fetch(new URL("br-slug-to-nba-person-id.json", base))
            .then((r) => (r.ok ? r.json() : {}))
            .catch(() => ({})),
          api.seasons(),
        ]);
        setI18nMaps(
          zhMap && typeof zhMap === "object" ? zhMap : {},
          idMap && typeof idMap === "object" ? idMap : {},
        );
        if (!seasons?.length) {
          throw new Error(
            "后端未找到任何赛季 JSON，请在爬虫目录运行爬虫生成 output。",
          );
        }
        this.seasons = seasons;
        let y = this.getSeasonYear();
        if (!seasons.some((s) => s.startYear === y)) y = seasons[0].startYear;
        this.setSeasonYear(y);
        const [lb, tm] = await Promise.all([
          api.leaderboard(y, { limit: 650, minGames: 1 }),
          api.teams(y),
        ]);
        this.leaderboard = lb;
        this.teams = tm;
        this.detailById = {};
        this.status = "ready";
      } catch (e) {
        this.status = "error";
        this.error = (e && e.message) || String(e);
      }
    },
    setDetail(id, detail) {
      this.detailById = { ...this.detailById, [id]: detail };
    },
    async changeSeason(y) {
      this.setSeasonYear(y);
      this.status = "loading";
      this.error = "";
      try {
        const [lb, tm] = await Promise.all([
          api.leaderboard(y, { limit: 650, minGames: 1 }),
          api.teams(y),
        ]);
        this.leaderboard = lb;
        this.teams = tm;
        this.detailById = {};
        this.status = "ready";
      } catch (e) {
        this.status = "error";
        this.error = (e && e.message) || String(e);
      }
    },
  },
});
