<script setup>
/** 球队列表：按东西部分区与战绩/场均排序展示 */
import { computed } from "vue";
import { useRouter } from "vue-router";
import { useNbaStore } from "../stores/nba";
import { useTeamCardLogo } from "../composables/useTeamLogoError";
import { fmt, abbrColor, seasonLabelFromYear } from "../utils/format";
import { partitionTeamsByConference } from "../utils/nbaI18n";

const store = useNbaStore();
const router = useRouter();
const { failed: logoFailed, onLogoImgError } = useTeamCardLogo();
const sl = computed(() => seasonLabelFromYear(store.seasonYear));

const conferenceSections = computed(() => {
  const { east, west } = partitionTeamsByConference(store.teams);
  return [
    {
      key: "east",
      title: "东部联盟",
      shortLabel: "东部",
      teams: east,
    },
    {
      key: "west",
      title: "西部联盟",
      shortLabel: "西部",
      teams: west,
    },
  ];
});

function teamCardStyle(t) {
  const c1 = abbrColor(t.abbr);
  const logo = store.logoUrl(t.abbr);
  if (logo) {
    return `background:linear-gradient(180deg,${c1}62 0%,${c1}28 40%,rgba(10,12,20,0.94) 88%)`;
  }
  return `background-image:linear-gradient(135deg,${c1}99,${c1}50)`;
}

</script>

<template>
  <div class="page-teams">
    <div class="section-head">
      <div>
        <h1 class="page-title-lg">NBA 球队</h1>
        <p class="sub" style="margin: 0">
          {{ sl }} · 分区内按战绩排名 · 共 {{ store.teams.length }} 支
        </p>
      </div>
    </div>

    <template v-for="block in conferenceSections" :key="block.key">
      <div class="conf-section-head">
        <h2 class="conf-section-title">{{ block.title }}</h2>
        <p class="sub conf-section-sub">按分区内名次 · 本区 {{ block.teams.length }} 支</p>
      </div>
      <div class="team-cards-grid">
        <button
          v-for="(t, idx) in block.teams"
          :key="t.abbr"
          type="button"
          class="team-card-figma"
          @click="router.push('/teams/' + t.abbr)"
        >
          <div class="team-card-media" :style="teamCardStyle(t)">
            <img
              v-if="store.logoUrl(t.abbr) && !logoFailed[t.abbr]"
              class="team-card-logo-img"
              :src="store.logoUrl(t.abbr)"
              alt=""
              width="160"
              height="160"
              loading="lazy"
              crossorigin="anonymous"
              referrerpolicy="no-referrer-when-downgrade"
              @error="onLogoImgError(t.abbr, $event)"
            />
            <div
              v-if="logoFailed[t.abbr] || !store.logoUrl(t.abbr)"
              class="team-card-logo-fallback"
              aria-hidden="true"
            >
              <span class="team-card-logo-fallback-ring" :style="{ borderColor: abbrColor(t.abbr) }">
                <span class="team-card-logo-fallback-text" :style="{ color: abbrColor(t.abbr) }">{{
                  t.abbr
                }}</span>
              </span>
            </div>
            <div class="team-card-badges">
              <div class="team-card-badges-left">
                <span class="team-conf-rank-pill"
                  >{{ block.shortLabel }}
                  {{ Number.isFinite(Number(t.confRank)) ? t.confRank : idx + 1 }}</span
                >
                <span class="team-abbr-badge" :style="{ background: abbrColor(t.abbr) }">{{ t.abbr }}</span>
              </div>
              <span class="conf-tag">{{ t.playerCount }} 名球员</span>
            </div>
          </div>
          <div class="team-card-body">
            <div class="team-card-city">NBA</div>
            <h3>{{ store.tZh(t.abbr) }}</h3>
            <div class="team-record-line">
              <span v-if="typeof t.wins === 'number'" class="team-standings-chip"
                >战绩 {{ t.wins }}-{{ t.losses }}</span
              >
              <span class="rec">球员场均均值 {{ fmt(t.avgPts) }}</span>
            </div>
            <div class="team-metrics">
              <div class="team-metric">
                <span class="lbl">头牌场均</span><span class="val">{{ fmt(t.topScorer?.pts) }}</span>
              </div>
              <div class="team-metric">
                <span class="lbl">头牌</span
                ><span class="val" style="font-size: 0.85rem">{{ store.pZh(t.topScorer?.name) }}</span>
              </div>
              <div class="team-metric">
                <span class="lbl">人数</span><span class="val">{{ t.playerCount }}</span>
              </div>
            </div>
            <div class="team-card-footer">
              <span class="cta">查看阵容 →</span>
            </div>
          </div>
        </button>
      </div>
    </template>
  </div>
</template>
