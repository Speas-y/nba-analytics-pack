<script setup>
/** 球员列表：客户端在 leaderboard 中筛选 */
import { ref, computed, watch, nextTick } from "vue";
import { useRouter } from "vue-router";
import { useNbaStore } from "../stores/nba";
import { fmt, abbrColor, seasonLabelFromYear, avatarFallbackChar } from "../utils/format";

const store = useNbaStore();
const router = useRouter();
const q = ref("");
const sl = computed(() => seasonLabelFromYear(store.seasonYear));

const rows = computed(() => {
  const qq = q.value.trim().toLowerCase();
  let list = store.leaderboard;
  if (qq) {
    list = list.filter((p) => {
      const en = String(p.name).toLowerCase();
      const tm = String(p.team).toLowerCase();
      const zhN = store.pZh(p.name);
      const zhT = store.tZh(p.team);
      return (
        en.includes(qq) ||
        tm.includes(qq) ||
        zhN.includes(q.value.trim()) ||
        zhT.includes(q.value.trim())
      );
    });
  }
  return list;
});

watch(
  () => router.currentRoute.value.name,
  async (n) => {
    if (n === "players") {
      await nextTick();
      const inp = document.getElementById("player-search");
      if (inp instanceof HTMLInputElement) {
        const len = inp.value.length;
        inp.focus();
        try {
          inp.setSelectionRange(len, len);
        } catch (_) {}
      }
    }
  },
  { immediate: true },
);

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
  <div class="page-players">
    <div class="players-page-head">
      <h1>NBA 球员</h1>
      <p>{{ sl }} · 共 {{ store.leaderboard.length }} 条</p>
    </div>
    <div class="toolbar">
      <input
        id="player-search"
        v-model="q"
        type="search"
        class="search-input"
        placeholder="搜索球员或球队…"
      />
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
            <th class="num">抢断</th>
            <th class="num">盖帽</th>
            <th class="num">投篮%</th>
            <th class="num">三分%</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="(p, idx) in rows"
            :key="p.id"
            class="table-row-link"
            tabindex="0"
            @click="goPlayer(p.id)"
            @keydown.enter.prevent="goPlayer(p.id)"
            @keydown.space.prevent="goPlayer(p.id)"
          >
            <td class="num">{{ q.trim() ? idx + 1 : p.rank }}</td>
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
                  @error="onImgErr($event, avatarFallbackChar(store.pZh(p.name)), abbrColor(p.team))"
                />
                <span
                  v-else
                  class="avatar-jersey avatar-jersey--pop"
                  :style="{ background: abbrColor(p.team) }"
                  >{{ q.trim() ? idx + 1 : p.rank }}</span
                >
                <div class="player-meta">
                  <div class="name">{{ store.pZh(p.name) }}</div>
                  <div class="sub">{{ store.tZh(p.team) }} · ID {{ p.id }}</div>
                </div>
              </div>
            </td>
            <td><span class="tag-team">{{ store.tZh(p.team) }}</span></td>
            <td class="num">
              <strong>{{ fmt(p.pts) }}</strong>
            </td>
            <td class="num">{{ fmt(p.reb) }}</td>
            <td class="num">{{ fmt(p.ast) }}</td>
            <td class="num">{{ p.stl != null ? fmt(p.stl) : "—" }}</td>
            <td class="num">{{ p.blk != null ? fmt(p.blk) : "—" }}</td>
            <td class="num">{{ p.fgPct != null ? fmt(p.fgPct) + "%" : "—" }}</td>
            <td class="num">{{ p.tpPct != null ? fmt(p.tpPct) + "%" : "—" }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="10" style="text-align: center; padding: 2rem">无匹配</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>
