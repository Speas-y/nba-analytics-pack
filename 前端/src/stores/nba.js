/**
 * 全局 NBA 数据状态：赛季、排行榜、球队列表、球员详情缓存；首次加载时并行拉取 i18n 映射与赛季列表。
 * 赛季年份会持久化到 localStorage，刷新页面仍保持用户选择。
 */
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
    /** 展示用赛季文案，如 2024–25 */
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
    /** 读本地缓存的赛季起始年；无则回退为当前日历年 */
    getSeasonYear() {
      if (this.seasonYear != null) return this.seasonYear;
      const raw = localStorage.getItem(STORAGE_YEAR);
      const n = raw != null ? Number(raw) : NaN;
      if (!Number.isNaN(n)) return n;
      return new Date().getFullYear();
    },
    /** 写入赛季并同步 localStorage */
    setSeasonYear(y) {
      const n = Number(y);
      if (!Number.isFinite(n)) return;
      localStorage.setItem(STORAGE_YEAR, String(n));
      this.seasonYear = n;
    },
    /**
     * 首屏加载：拉取 player-zh / br-slug 映射、赛季列表，再拉取当前赛季的 leaderboard + 球队表。
     * 失败时 status=error，App 展示排查提示（后端/爬虫/VITE_API_BASE）。
     */
    async loadBundle() {
      this.status = "loading";
      this.error = "";
      try {
        const base = staticFilesBaseHref();
        const [zhMap, idMap, seasons] = await Promise.all([
          api.fetchI18nJsonPreferApi(
            "/public/nba/i18n/player-zh",
            "player-zh.json",
            base,
          ),
          api.fetchI18nJsonPreferApi(
            "/public/nba/i18n/br-slug-to-nba-person-id",
            "br-slug-to-nba-person-id.json",
            base,
          ),
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
    /** 球员详情页写入缓存，避免重复请求 */
    setDetail(id, detail) {
      this.detailById = { ...this.detailById, [id]: detail };
    },
    /** 切换全局赛季：仅刷新排行榜与球队，清空 detail 缓存 */
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
