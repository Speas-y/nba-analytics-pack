<script setup>
/** 球队详情：阵容、近战场次 API、Chart.js 分析图 */
import { ref, computed, watch, onBeforeUnmount, nextTick } from "vue";
import { useRouter } from "vue-router";
import { Chart } from "chart.js/auto";
import ChartDataLabels from "chartjs-plugin-datalabels";
import { useNbaStore } from "../stores/nba";
import { useTeamCardLogo } from "../composables/useTeamLogoError";
import { fmt, abbrColor, seasonLabelFromYear } from "../utils/format";
import { teamLogoUrlNba } from "../utils/nbaI18n";
import { applyChartDefaults } from "../utils/chartDefaults";
import * as api from "../api/nbaApi";

const props = defineProps({ abbr: { type: String, required: true } });
const router = useRouter();
const store = useNbaStore();
const { failed: logoFailed, onLogoImgError } = useTeamCardLogo();

const tab = ref("overview");
const charts = {};
let teamChartPluginsRegistered = false;
/** espn → nba.svg → 水印字母 */
const heroLogoTier = ref("espn");

const abbrU = computed(() => decodeURIComponent(props.abbr || "").toUpperCase());
const sl = computed(() => seasonLabelFromYear(store.seasonYear));
const teamSum = computed(() => store.teams.find((t) => t.abbr === abbrU.value));

const rosterBase = computed(() => store.leaderboard.filter((p) => p.team === abbrU.value));

const starterIdSet = computed(() => {
  const list = rosterBase.value;
  if (!list.length) return new Set();
  const sorted = [...list].sort((a, b) => {
    const g = (b.gs ?? 0) - (a.gs ?? 0);
    if (g !== 0) return g;
    return b.pts - a.pts;
  });
  return new Set(sorted.slice(0, 5).map((p) => p.id));
});

/** 首发（按赛季「首发场次 GS」前 5）在前，其余按得分排序 */
const roster = computed(() => {
  const list = rosterBase.value;
  const st = starterIdSet.value;
  const starters = [...list]
    .filter((p) => st.has(p.id))
    .sort((a, b) => (b.gs ?? 0) - (a.gs ?? 0) || b.pts - a.pts);
  const bench = [...list]
    .filter((p) => !st.has(p.id))
    .sort((a, b) => b.pts - a.pts);
  return [...starters, ...bench];
});

const notFound = computed(() => !teamSum.value && rosterBase.value.length === 0);

const rankLine = computed(() => {
  const t = teamSum.value;
  if (!t) return "";
  const parts = [];
  if (t.leagueRank != null) parts.push(`联盟第 ${t.leagueRank} 位`);
  const confZh = t.conference === "East" ? "东部" : t.conference === "West" ? "西部" : "";
  if (confZh && t.confRank != null && t.confRank < 90) {
    parts.push(`${confZh}第 ${t.confRank} 位`);
  }
  if (t.wins != null && t.losses != null) parts.push(`${t.wins} 胜 ${t.losses} 负`);
  return parts.join(" · ");
});

const recentGames = ref([]);
const recentGamesLoading = ref(false);
const recentGamesHint = ref("");

async function loadRecentGames() {
  const y = store.seasonYear;
  const ab = abbrU.value;
  if (!ab || y == null) return;
  recentGamesLoading.value = true;
  recentGamesHint.value = "";
  try {
    const rows = await api.teamRecentGames(ab, y);
    recentGames.value = Array.isArray(rows) ? rows : [];
    if (!recentGames.value.length) {
      recentGamesHint.value =
        "暂无比赛记录。仓库根目录执行 ./更新 会刷新 team_recent_games；或于 爬虫 目录运行 python fetch_team_recent_games.py。部署环境需能访问 stats.nba.com。";
    }
  } catch (e) {
    recentGames.value = [];
    recentGamesHint.value = (e && e.message) || "近期赛程加载失败";
  } finally {
    recentGamesLoading.value = false;
  }
}

watch([abbrU, () => store.seasonYear], loadRecentGames, { immediate: true });

const hasEspnLogo = computed(() => !!store.logoUrl(abbrU.value));

watch(
  abbrU,
  () => {
    heroLogoTier.value = "espn";
  },
  { immediate: true },
);

const heroLogoSrc = computed(() => {
  if (heroLogoTier.value === "nba") return teamLogoUrlNba(abbrU.value);
  return store.logoUrl(abbrU.value) || "";
});

function onHeroLogoErr() {
  const a = abbrU.value;
  if (heroLogoTier.value === "espn" && teamLogoUrlNba(a)) {
    heroLogoTier.value = "nba";
    return;
  }
  heroLogoTier.value = "done";
}

const showHeroWatermarkImg = computed(
  () => hasEspnLogo.value && heroLogoTier.value !== "done" && !!heroLogoSrc.value,
);

const showHeroMonogram = computed(
  () => !showHeroWatermarkImg.value && (heroLogoTier.value === "done" || !hasEspnLogo.value),
);

function destroyCharts() {
  Object.keys(charts).forEach((k) => {
    charts[k]?.destroy?.();
    delete charts[k];
  });
}

function barGradient(ctx, topColor, bottomColor) {
  const { chart } = ctx;
  const { ctx: c, chartArea } = chart;
  if (!chartArea) return topColor;
  const g = c.createLinearGradient(0, chartArea.bottom, 0, chartArea.top);
  g.addColorStop(0, bottomColor);
  g.addColorStop(1, topColor);
  return g;
}

function renderAnalyticsCharts() {
  applyChartDefaults();
  if (!teamChartPluginsRegistered) {
    Chart.register(ChartDataLabels);
    teamChartPluginsRegistered = true;
  }
  const pieN = roster.value.slice(0, 6);
  const elPie = document.getElementById("chart-team-pie");
  const elBar = document.getElementById("chart-team-bar");

  const anim = {
    duration: 1200,
    easing: "easeOutQuart",
  };

  const datalabelsCommon = {
    color: "#f1f5f9",
    font: { weight: "700", size: 11 },
    textStrokeColor: "rgba(0,0,0,0.45)",
    textStrokeWidth: 3,
  };

  if (elPie && pieN.length) {
    const pts = pieN.map((x) => x.pts);
    const sum = pts.reduce((a, b) => a + b, 0) || 1;
    charts.tp = new Chart(elPie, {
      type: "pie",
      data: {
        labels: pieN.map((x) => store.pZh(x.name).slice(0, 10)),
        datasets: [
          {
            data: pts,
            backgroundColor: [
              "rgba(255, 74, 44, 0.9)",
              "rgba(240, 180, 41, 0.88)",
              "rgba(52, 245, 164, 0.75)",
              "rgba(255, 122, 24, 0.85)",
              "rgba(139, 155, 184, 0.8)",
              "rgba(255, 51, 95, 0.82)",
            ],
            borderColor: "rgba(7, 8, 12, 0.94)",
            borderWidth: 2,
            hoverOffset: 14,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: anim,
        plugins: {
          legend: {
            position: "bottom",
            labels: { usePointStyle: true, padding: 14 },
          },
          datalabels: {
            ...datalabelsCommon,
            formatter: (v, ctx) => {
              const n = Number(v);
              const pct = sum ? Math.round((n / sum) * 100) : 0;
              return `${fmt(n)}\n${pct}%`;
            },
          },
        },
      },
    });
  }

  const top = roster.value.slice(0, 12);
  if (elBar && top.length) {
    charts.tb = new Chart(elBar, {
      type: "bar",
      data: {
        labels: top.map((x) => {
          const zh = store.pZh(x.name);
          return zh.length > 5 ? zh.slice(0, 4) + "…" : zh;
        }),
        datasets: [
          {
            data: top.map((x) => x.pts),
            borderRadius: 10,
            borderSkipped: false,
            backgroundColor: (c) =>
              barGradient(c, "rgba(249, 115, 22, 0.92)", "rgba(120, 24, 24, 0.45)"),
            borderColor: "rgba(255, 90, 60, 0.35)",
            borderWidth: 1,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        animation: {
          ...anim,
          delay: (ctx) =>
            ctx.type === "data" && ctx.mode === "default" ? ctx.dataIndex * 48 : 0,
        },
        plugins: {
          legend: { display: false },
          datalabels: {
            ...datalabelsCommon,
            anchor: "end",
            align: "end",
            offset: -2,
            formatter: (v) => fmt(Number(v)),
          },
        },
        scales: {
          x: {
            grid: { display: false },
            ticks: { maxRotation: 42, minRotation: 0 },
          },
          y: {
            beginAtZero: true,
            grid: { color: "rgba(255,255,255,0.06)" },
          },
        },
      },
    });
  }
}

function openTab(id) {
  tab.value = id;
}

watch(
  [tab, roster],
  async () => {
    await nextTick();
    destroyCharts();
    if (tab.value === "analytics") renderAnalyticsCharts();
  },
  { flush: "post", immediate: true },
);

onBeforeUnmount(destroyCharts);

const heroBgGradient = computed(() => {
  const c1 = abbrColor(abbrU.value);
  return `linear-gradient(135deg, ${c1}55 0%, ${c1}28 42%, #0f172a 100%)`;
});

function scoreLine(g) {
  const a = g.pts;
  const b = g.oppPts;
  if (b == null || Number.isNaN(Number(b))) return String(a);
  return `${a} – ${b}`;
}
</script>

<template>
  <div v-if="notFound" class="page-detail">
    <p>未找到球队 <strong>{{ abbrU }}</strong>。<router-link to="/teams">返回</router-link></p>
  </div>
  <div v-else class="page-team-detail">
    <nav class="breadcrumb">
      <router-link to="/">首页</router-link> › <router-link to="/teams">球队</router-link> ›
      <span>{{ store.tZh(abbrU) }}</span>
    </nav>
    <div class="detail-hero-team">
      <div class="bg" :style="{ backgroundImage: heroBgGradient }"></div>
      <img
        v-if="showHeroWatermarkImg"
        class="detail-hero-watermark-logo"
        :src="heroLogoSrc"
        alt=""
        decoding="async"
        @error="onHeroLogoErr"
      />
      <div
        v-else-if="showHeroMonogram"
        class="detail-hero-monogram"
        :style="{ color: abbrColor(abbrU) }"
        aria-hidden="true"
      >
        {{ abbrU }}
      </div>
      <div class="overlay"></div>
      <div class="detail-hero-inner">
        <div class="detail-hero-titlestack">
          <div class="team-hero-badge-wrap">
            <img
              v-if="store.logoUrl(abbrU) && !logoFailed[abbrU]"
              class="team-hero-badge-logo"
              :src="store.logoUrl(abbrU)"
              alt=""
              width="56"
              height="56"
              decoding="async"
              crossorigin="anonymous"
              referrerpolicy="no-referrer-when-downgrade"
              @error="onLogoImgError(abbrU, $event)"
            />
            <div
              v-else
              class="team-badge-lg team-badge-lg--fallback"
              :style="{ background: abbrColor(abbrU) }"
            >
              {{ abbrU }}
            </div>
          </div>
          <div>
            <h1>{{ store.tZh(abbrU) }}</h1>
            <p v-if="rankLine" class="detail-rank-line">{{ rankLine }}</p>
            <p class="detail-meta-muted">{{ sl }} · {{ roster.length }} 名球员列入统计表</p>
            <div class="badge-row">
              <span class="stat-badge">球员场均均值 {{ fmt(teamSum?.avgPts) }}</span>
              <span class="stat-badge">
                头牌 {{ store.pZh(teamSum?.topScorer?.name) }} {{ fmt(teamSum?.topScorer?.pts) }}
              </span>
            </div>
          </div>
        </div>
        <div class="record-block">
          <div class="big">{{ roster.length }}</div>
          <div class="small">登记球员</div>
          <div v-if="teamSum?.wins != null" class="record-sub">
            战绩 {{ teamSum.wins }}-{{ teamSum.losses }}
          </div>
        </div>
      </div>
    </div>
    <div class="tabs team-tabs" role="tablist">
      <button type="button" class="tab" :class="{ active: tab === 'overview' }" @click="openTab('overview')">
        概览
      </button>
      <button type="button" class="tab" :class="{ active: tab === 'roster' }" @click="openTab('roster')">
        阵容
      </button>
      <button type="button" class="tab" :class="{ active: tab === 'analytics' }" @click="openTab('analytics')">
        分析
      </button>
    </div>
    <div class="tab-panel" :class="{ hidden: tab !== 'overview' }" data-panel="overview">
      <div class="chart-card recent-games-card">
        <h3>近 {{ recentGames.length || 10 }} 场常规赛</h3>
        <p v-if="recentGamesLoading" class="recent-games-hint">加载中…</p>
        <p v-else-if="recentGamesHint" class="sub recent-games-hint">{{ recentGamesHint }}</p>
        <div v-else class="recent-games-grid">
          <div
            v-for="(g, idx) in recentGames"
            :key="idx + (g.gameDate || '') + (g.matchup || '')"
            class="recent-game-tile"
            :class="{ win: g.win, loss: g.wl === 'L' }"
          >
            <div class="recent-game-top">
              <span class="recent-game-wl">{{ g.win ? "胜" : "负" }}</span>
              <span class="recent-game-date">{{ g.gameDate }}</span>
            </div>
            <div class="recent-game-score">{{ scoreLine(g) }}</div>
            <div class="recent-game-match muted">{{ g.matchup }}</div>
          </div>
        </div>
      </div>
    </div>
    <div class="tab-panel" :class="{ hidden: tab !== 'roster' }" data-panel="roster">
      <p class="roster-starter-note sub">
        标记为「首发」的球员为本队赛季首发场次（GS）最多的前五名，用于区分主力与板凳。
      </p>
      <div class="table-shell">
        <table class="table-pro">
          <thead>
            <tr>
              <th>球员</th>
              <th class="num">首发场</th>
              <th class="num">场均得分</th>
              <th class="num">篮板</th>
              <th class="num">助攻</th>
              <th class="num">场次</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="p in roster"
              :key="p.id"
              class="row-click"
              :class="{ 'row-starter': starterIdSet.has(p.id) }"
              style="cursor: pointer"
              @click="router.push('/players/' + p.id)"
            >
              <td>
                <strong>{{ store.pZh(p.name) }}</strong>
                <span v-if="starterIdSet.has(p.id)" class="starter-pill">首发</span>
              </td>
              <td class="num">{{ p.gs ?? 0 }}</td>
              <td class="num">{{ fmt(p.pts) }}</td>
              <td class="num">{{ fmt(p.reb) }}</td>
              <td class="num">{{ fmt(p.ast) }}</td>
              <td class="num">{{ p.games }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div class="tab-panel" :class="{ hidden: tab !== 'analytics' }" data-panel="analytics">
      <div class="charts-grid-2">
        <div class="chart-card">
          <h3>得分占比（前 6 人）</h3>
          <div class="chart-box"><canvas id="chart-team-pie"></canvas></div>
        </div>
        <div class="chart-card">
          <h3>场均得分</h3>
          <div class="chart-box"><canvas id="chart-team-bar"></canvas></div>
        </div>
      </div>
    </div>
  </div>
</template>
