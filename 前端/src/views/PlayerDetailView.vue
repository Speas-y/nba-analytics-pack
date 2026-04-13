<script setup>
/**
 * 球员详情：从 store 排行榜取行，必要时请求 playerDetail API；含 Chart.js 图表，切换 tab 时销毁/重绘。
 */
import { ref, computed, watch, onBeforeUnmount, nextTick } from "vue";
import { useRouter } from "vue-router";
import { Chart } from "chart.js/auto";
import * as api from "../api/nbaApi";
import { useNbaStore } from "../stores/nba";
import { fmt, abbrColor, seasonLabelFromYear, avatarFallbackChar } from "../utils/format";
import { applyChartDefaults } from "../utils/chartDefaults";

const props = defineProps({ id: { type: String, required: true } });
const router = useRouter();
const store = useNbaStore();

const tab = ref("pstats");
const charts = {};
const detail = ref(null);

const pid = computed(() => Number(props.id));
const sl = computed(() => seasonLabelFromYear(store.seasonYear));
const row = computed(() => store.leaderboard.find((p) => p.id === pid.value));

const others = computed(() => store.leaderboard.filter((x) => x.id !== pid.value).slice(0, 6));

const nameZh = computed(() => {
  const id = pid.value;
  const n = row.value?.name || detail.value?.player?.name || `#${id}`;
  return store.pZh(n);
});

const team = computed(() => row.value?.team || "—");
const brSlug = computed(() => row.value?.brSlug || detail.value?.player?.brSlug || "");
const av = computed(() => detail.value?.averages || row.value);

async function loadDetail() {
  const id = pid.value;
  const cached = store.detailById[id];
  if (cached) {
    detail.value = cached;
    return;
  }
  try {
    const d = await api.playerDetail(id, store.seasonYear);
    store.setDetail(id, d);
    detail.value = d;
  } catch {
    detail.value = null;
  }
}

watch(
  () => [pid.value, store.seasonYear],
  () => {
    void loadDetail();
  },
  { immediate: true },
);

const notFound = computed(() => !row.value && !detail.value);

function destroyCharts() {
  Object.keys(charts).forEach((k) => {
    charts[k]?.destroy?.();
    delete charts[k];
  });
}

function bindPlayerChart() {
  applyChartDefaults();
  const r = row.value;
  if (!r) return;
  const el = document.getElementById("chart-p-bar");
  if (!el) return;
  charts.pb = new Chart(el, {
    type: "bar",
    data: {
      labels: ["得分", "篮板", "助攻", "抢断"],
      datasets: [
        {
          data: [r.pts, r.reb, r.ast, r.stl ?? 0],
          backgroundColor: ["#ff4a2c", "#5b8cff", "#34f5a4", "#f0b429"],
        },
      ],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        datalabels: { display: false },
      },
    },
  });
}

watch(
  tab,
  async (t) => {
    await nextTick();
    destroyCharts();
    if (t === "pcharts") bindPlayerChart();
  },
  { flush: "post" },
);

onBeforeUnmount(destroyCharts);

</script>

<template>
  <div v-if="notFound" class="page-detail">
    <p>未找到球员。<router-link to="/players">返回</router-link></p>
  </div>
  <div v-else class="page-player-detail">
    <nav class="breadcrumb">
      <router-link to="/">首页</router-link> › <router-link to="/players">球员</router-link> ›
      <span>{{ nameZh }}</span>
    </nav>
    <div class="player-hero">
      <img
        v-if="store.headshot(brSlug)"
        class="player-hero-photo"
        :src="store.headshot(brSlug)"
        alt=""
        width="96"
        height="96"
        loading="lazy"
        decoding="async"
        :data-fallback-bg="abbrColor(team)"
        :data-fallback-text="'#' + (pid % 100)"
      />
      <div v-else class="jersey-lg" :style="{ background: abbrColor(team) }">#{{ pid % 100 }}</div>
      <div>
        <h1>{{ nameZh }}</h1>
        <p class="detail-meta-muted" style="color: var(--text-muted)">
          <router-link :to="'/teams/' + team">{{ store.tZh(team) }}</router-link> · 球员 ID {{ pid }} · {{ sl }}
        </p>
        <div class="stat-pills">
          <span class="stat-pill">篮板 {{ fmt(row?.reb ?? av?.reb) }}</span>
          <span class="stat-pill">助攻 {{ fmt(row?.ast ?? av?.ast) }}</span>
          <span class="stat-pill">抢断 {{ row?.stl != null ? fmt(row.stl) : "—" }}</span>
          <span class="stat-pill">盖帽 {{ row?.blk != null ? fmt(row.blk) : "—" }}</span>
          <span class="stat-pill">投篮 {{ row?.fgPct != null ? fmt(row.fgPct) + "%" : "—" }}</span>
          <span class="stat-pill">三分 {{ row?.tpPct != null ? fmt(row.tpPct) + "%" : "—" }}</span>
          <span class="stat-pill">时间 {{ row?.mpg != null ? fmt(row.mpg) : "—" }}</span>
        </div>
      </div>
      <div class="ppg-big">
        <div class="n">{{ fmt(row?.pts ?? av?.pts) }}</div>
        <div class="lbl">场均得分</div>
      </div>
    </div>
    <div class="tabs player-tabs">
      <button type="button" class="tab" :class="{ active: tab === 'pstats' }" @click="tab = 'pstats'">
        📋 赛季数据
      </button>
      <button type="button" class="tab" :class="{ active: tab === 'pcharts' }" @click="tab = 'pcharts'">
        📈 图表
      </button>
    </div>
    <div class="tab-panel" :class="{ hidden: tab !== 'pstats' }" data-panel="pstats">
      <div class="table-shell table-scroll">
        <table class="table-pro">
          <thead>
            <tr>
              <th>项目</th>
              <th>数值</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>场次</td>
              <td>{{ fmt(row?.games ?? av?.games) }}</td>
            </tr>
            <tr>
              <td>得分</td>
              <td>{{ fmt(row?.pts ?? av?.pts) }}</td>
            </tr>
            <tr>
              <td>篮板</td>
              <td>{{ fmt(row?.reb ?? av?.reb) }}</td>
            </tr>
            <tr>
              <td>助攻</td>
              <td>{{ fmt(row?.ast ?? av?.ast) }}</td>
            </tr>
            <tr>
              <td>投篮命中%</td>
              <td>{{ row?.fgPct != null ? fmt(row.fgPct) + "%" : "—" }}</td>
            </tr>
            <tr>
              <td>三分命中%</td>
              <td>{{ row?.tpPct != null ? fmt(row.tpPct) + "%" : "—" }}</td>
            </tr>
            <tr>
              <td>抢断</td>
              <td>{{ row?.stl != null ? fmt(row.stl) : "—" }}</td>
            </tr>
            <tr>
              <td>盖帽</td>
              <td>{{ row?.blk != null ? fmt(row.blk) : "—" }}</td>
            </tr>
            <tr>
              <td>失误</td>
              <td>{{ row?.tov != null ? fmt(row.tov) : "—" }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
    <div class="tab-panel" :class="{ hidden: tab !== 'pcharts' }" data-panel="pcharts">
      <div class="chart-card">
        <h3>场均四项</h3>
        <div class="chart-box"><canvas id="chart-p-bar"></canvas></div>
      </div>
    </div>
    <h3 style="margin: 1.5rem 0 0.5rem; font-size: 1rem">同榜球员</h3>
    <div class="other-players">
      <button
        v-for="o in others"
        :key="o.id"
        type="button"
        class="mini-player"
        @click="router.push('/players/' + o.id)"
      >
        <img
          v-if="store.headshot(o.brSlug)"
          class="mini-avatar"
          :src="store.headshot(o.brSlug)"
          alt=""
          loading="lazy"
        />
        <span v-else class="mini-avatar-fallback" :style="{ background: abbrColor(o.team) }">{{
          avatarFallbackChar(store.pZh(o.name))
        }}</span>
        <div class="mini-player-text">
          <div class="num">#{{ o.rank }}</div>
          <div class="nm">{{ store.pZh(o.name) }}</div>
          <div class="ppg">{{ fmt(o.pts) }} 场均得分</div>
        </div>
      </button>
    </div>
  </div>
</template>
