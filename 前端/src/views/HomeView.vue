<script setup>
/** 首页：得分榜 Top10、东西部展示、联盟场均等（数据来自 Pinia store） */
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useNbaStore } from "../stores/nba";
import { useTeamCardLogo } from "../composables/useTeamLogoError";
import { fmt, abbrColor, seasonLabelFromYear, avatarFallbackChar } from "../utils/format";
import { partitionTeamsByConference } from "../utils/nbaI18n";

const store = useNbaStore();
const router = useRouter();
const { failed: logoFailed, onLogoImgError } = useTeamCardLogo();

const sl = computed(() => seasonLabelFromYear(store.seasonYear));
const top = computed(() => store.leaderboard.slice(0, 10));

/** 东、西部常规赛分区排名前 2（有战绩 JSON 时按分区名次；否则回退为场均排序） */
const homeConferenceShowcase = computed(() => {
  const { east, west } = partitionTeamsByConference(store.teams);
  const pill = (t, zoneHan, idx) => {
    const r = Number(t?.confRank);
    if (Number.isFinite(r)) return `${zoneHan} ${r}`;
    return `${zoneHan} ${idx + 1}`;
  };
  const rows = [];
  for (let i = 0; i < 2; i++) {
    if (east[i]) rows.push({ team: east[i], confRankLabel: pill(east[i], "东部", i) });
  }
  for (let i = 0; i < 2; i++) {
    if (west[i]) rows.push({ team: west[i], confRankLabel: pill(west[i], "西部", i) });
  }
  return rows;
});
const avgLeague = computed(() => {
  const lb = store.leaderboard;
  if (!lb.length) return 0;
  return lb.reduce((s, r) => s + r.pts, 0) / lb.length;
});

const top10AvgPts = computed(() => {
  if (!top.value.length) return 0;
  return top.value.reduce((s, p) => s + p.pts, 0) / top.value.length;
});
const leaguePlayers = computed(() => store.leaderboard.length);
const teamCount = computed(() => store.teams.length);

function teamCardStyle(t) {
  const c1 = abbrColor(t.abbr);
  const logo = store.logoUrl(t.abbr);
  if (logo) {
    return `background:linear-gradient(180deg,${c1}62 0%,${c1}28 40%,rgba(10,12,20,0.94) 88%)`;
  }
  return `background-image:linear-gradient(135deg,${c1}99,${c1}50)`;
}

function onImgErr(e, fb, bg) {
  const img = e.target;
  const sp = document.createElement("span");
  sp.className = "avatar-jersey avatar-jersey--pop";
  sp.textContent = fb;
  sp.style.background = bg;
  img.replaceWith(sp);
}

function goPlayer(pid) {
  router.push("/players/" + pid);
}

</script>

<template>
  <div class="page-home">
    <section class="home-hero">
      <div class="home-hero-inner">
        <div class="home-hero-copy">
          <p class="eyebrow">赛季快照</p>
          <h1 class="home-hero-title">NBA <span class="home-hero-season">{{ sl }}</span> 球员数据</h1>
          <p class="home-hero-lead">赛季排行、球队与球员详情一站浏览。</p>
          <div class="hero-actions">
            <button type="button" class="btn-primary" @click="router.push('/teams')">探索球队 →</button>
            <button type="button" class="btn-ghost" @click="router.push('/players')">全部球员排行</button>
          </div>
        </div>
        <div class="home-hero-visual" aria-hidden="true">
          <div class="home-hero-ring" />
          <div class="home-hero-slice a" />
          <div class="home-hero-slice b" />
          <div class="home-hero-slice c" />
        </div>
      </div>
    </section>

    <div v-if="store.leaderboard.length" class="home-quick-stats">
      <div class="home-stat-tile" style="--tile-accent: #ff4a2c">
        <p class="tile-label">TOP10 场均得分</p>
        <p class="tile-value accent">{{ fmt(top10AvgPts) }}</p>
        <p class="tile-hint">本页榜单前十人简单平均</p>
      </div>
      <div class="home-stat-tile" style="--tile-accent: #f0b429">
        <p class="tile-label">登记球员</p>
        <p class="tile-value">{{ leaguePlayers }}</p>
        <p class="tile-hint">当前赛季联盟表人数</p>
      </div>
      <div class="home-stat-tile" style="--tile-accent: #ff7a18">
        <p class="tile-label">球队</p>
        <p class="tile-value">{{ teamCount }}</p>
        <p class="tile-hint">队卡与阵容覆盖</p>
      </div>
      <div class="home-stat-tile" style="--tile-accent: #ff335f">
        <p class="tile-label">赛季</p>
        <p class="tile-value accent">{{ sl }}</p>
        <p class="tile-hint">与顶部赛季选择一致</p>
      </div>
    </div>

    <section class="section section--focus">
      <div class="section-head">
        <div>
          <h2>得分榜 TOP 10</h2>
          <p class="sub" style="margin: 0">联盟表场均得分 · {{ sl }}</p>
        </div>
        <router-link class="link-action" to="/players">查看全部 →</router-link>
      </div>
      <div class="table-shell table-scroll">
        <table class="table-pro">
          <thead>
            <tr>
              <th>#</th>
              <th>球员</th>
              <th>球队</th>
              <th class="num">场均得分</th>
              <th class="num">篮板</th>
              <th class="num">助攻</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="p in top"
              :key="p.id"
              class="table-row-link"
              tabindex="0"
              :class="{ 'row-scoring-leader': p.rank === 1 }"
              @click="goPlayer(p.id)"
              @keydown.enter.prevent="goPlayer(p.id)"
              @keydown.space.prevent="goPlayer(p.id)"
            >
              <td class="num">{{ p.rank }}</td>
              <td>
                <div class="player-cell">
                  <img
                    v-if="store.headshot(p.brSlug)"
                    class="avatar-photo avatar-photo--pop"
                    :src="store.headshot(p.brSlug)"
                    alt=""
                    width="44"
                    height="44"
                    loading="lazy"
                    decoding="async"
                    @error="onImgErr($event, avatarFallbackChar(store.pZh(p.name)), abbrColor(p.team))"
                  />
                  <span
                    v-else
                    class="avatar-jersey avatar-jersey--pop"
                    :style="{ background: abbrColor(p.team) }"
                    >{{ p.rank }}</span
                  >
                  <div class="player-meta">
                    <div class="name">{{ store.pZh(p.name) }}</div>
                    <div class="sub">{{ store.tZh(p.team) }} · ID {{ p.id }}</div>
                  </div>
                </div>
              </td>
              <td>
                <span class="tag-team">{{ store.tZh(p.team) }}</span>
              </td>
              <td class="num col-pts"><strong>{{ fmt(p.pts) }}</strong></td>
              <td class="num">{{ fmt(p.reb) }}</td>
              <td class="num">{{ fmt(p.ast) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="avgLeague" class="sub footnote-league">
        本榜球员场均得分简单平均约 <strong>{{ fmt(avgLeague) }}</strong>（非官方联盟均值）。
      </p>
    </section>

    <section class="section">
      <div class="section-head">
        <div>
          <h2>球队一览</h2>
          <p class="sub" style="margin: 0">
            东、西部分区战绩前 2 名 · 共 {{ homeConferenceShowcase.length }} 支
          </p>
        </div>
        <router-link class="link-action" to="/teams">查看全部 →</router-link>
      </div>
      <div class="team-cards-grid team-cards-grid--compact">
        <button
          v-for="row in homeConferenceShowcase"
          :key="row.team.abbr"
          type="button"
          class="team-card-figma"
          @click="router.push('/teams/' + row.team.abbr)"
        >
          <div class="team-card-media" :style="teamCardStyle(row.team)">
            <img
              v-if="store.logoUrl(row.team.abbr) && !logoFailed[row.team.abbr]"
              class="team-card-logo-img"
              :src="store.logoUrl(row.team.abbr)"
              alt=""
              width="160"
              height="160"
              loading="lazy"
              crossorigin="anonymous"
              referrerpolicy="no-referrer-when-downgrade"
              @error="onLogoImgError(row.team.abbr, $event)"
            />
            <div
              v-if="logoFailed[row.team.abbr] || !store.logoUrl(row.team.abbr)"
              class="team-card-logo-fallback"
              aria-hidden="true"
            >
              <span class="team-card-logo-fallback-ring" :style="{ borderColor: abbrColor(row.team.abbr) }">
                <span
                  class="team-card-logo-fallback-text"
                  :style="{ color: abbrColor(row.team.abbr) }"
                  >{{ row.team.abbr }}</span
                >
              </span>
            </div>
            <div class="team-card-badges">
              <div class="team-card-badges-left">
                <span class="team-conf-rank-pill">{{ row.confRankLabel }}</span>
                <span class="team-abbr-badge" :style="{ background: abbrColor(row.team.abbr) }">{{
                  row.team.abbr
                }}</span>
              </div>
              <span class="conf-tag">{{ row.team.playerCount }} 名球员</span>
            </div>
          </div>
          <div class="team-card-body">
            <div class="team-card-city">NBA</div>
            <h3>{{ store.tZh(row.team.abbr) }}</h3>
            <div class="team-record-line">
              <span
                v-if="typeof row.team.wins === 'number'"
                class="team-standings-chip"
                >战绩 {{ row.team.wins }}-{{ row.team.losses }}</span
              >
              <span class="rec">球员场均均值 {{ fmt(row.team.avgPts) }}</span>
            </div>
            <div class="team-metrics">
              <div class="team-metric">
                <span class="lbl">头牌场均</span><span class="val">{{ fmt(row.team.topScorer?.pts) }}</span>
              </div>
              <div class="team-metric">
                <span class="lbl">头牌</span
                ><span class="val" style="font-size: 0.85rem">{{
                  store.pZh(row.team.topScorer?.name)
                }}</span>
              </div>
              <div class="team-metric">
                <span class="lbl">人数</span><span class="val">{{ row.team.playerCount }}</span>
              </div>
            </div>
            <div class="team-card-footer">
              <span class="cta">查看阵容 →</span>
            </div>
          </div>
        </button>
      </div>
    </section>
  </div>
</template>
