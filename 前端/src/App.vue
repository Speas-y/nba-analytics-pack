<script setup>
import { onMounted, watch } from "vue";
import { useNbaStore } from "./stores/nba";
const store = useNbaStore();

function onSeasonChange(e) {
  const y = Number(e.target.value);
  if (!Number.isFinite(y)) return;
  store.changeSeason(y);
}

async function share() {
  const u = window.location.href;
  try {
    await navigator.clipboard.writeText(u);
    window.alert("链接已复制到剪贴板");
  } catch {
    window.prompt("复制链接：", u);
  }
}

onMounted(() => store.loadBundle());

watch(
  () => store.status,
  (s) => {
    if (s === "ready" && store.seasons.length && store.seasonYear == null) {
      store.setSeasonYear(store.seasons[0].startYear);
    }
  },
);
</script>

<template>
  <div class="app-root">
    <header class="site-header">
      <div class="header-inner">
        <router-link to="/" class="brand">
          <span class="brand-icon" aria-hidden="true">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none">
              <path
                d="M4 12c2-6 6-9 8-9s4 3 4 8-2 8-4 8-4-2-8-7z"
                stroke="currentColor"
                stroke-width="2"
                stroke-linecap="round"
              />
              <path d="M4 12h16" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
            </svg>
          </span>
          <span class="brand-text">NBA 数据分析</span>
        </router-link>
        <nav class="main-nav" aria-label="主导航">
          <router-link to="/" class="nav-pill" data-nav="home">
            <span class="nav-ico">⌂</span> 首页
          </router-link>
          <router-link to="/teams" class="nav-pill" data-nav="teams">
            <span class="nav-ico">🏆</span> 球队
          </router-link>
          <router-link to="/players" class="nav-pill" data-nav="players">
            <span class="nav-ico">📊</span> 球员
          </router-link>
        </nav>
        <div class="header-right">
          <div v-if="store.status === 'ready'" class="season-pill">
            <span class="live-dot" aria-hidden="true"></span>
            <label class="sr-only" for="global-season">赛季</label>
            <select
              id="global-season"
              class="season-select-native"
              :value="String(store.seasonYear)"
              @change="onSeasonChange"
            >
              <option v-for="s in store.seasons" :key="s.startYear" :value="String(s.startYear)">
                {{ String(s.label).replace(/(\d{4})-(\d{2})/, "$1–$2") }} 赛季
              </option>
            </select>
          </div>
          <button type="button" class="btn-share" @click="share">分享</button>
        </div>
      </div>
    </header>
    <main id="app-main" aria-live="polite">
      <div v-if="store.status === 'loading'" class="page-loading-msg">
        正在从后端加载爬虫数据…
      </div>
      <div v-else-if="store.status === 'error'" class="page-error glass-card">
        <h1 class="page-error-title">无法加载 NBA 数据</h1>
        <p class="page-error-text">{{ store.error }}</p>
        <p class="page-error-text page-error-hint">
          <strong>请按顺序检查：</strong><br />
          1. 启动 Spring 后端：<code class="page-error-code">cd spring-backend && mvn spring-boot:run</code><br />
          2. 在 <code>爬虫</code> 运行爬虫生成 <code>output/player_stats_*_regular_season_pergame.json</code><br />
          3. 配置 <code>.env</code> 中 <code>VITE_API_BASE</code>（默认 http://localhost:3000/api）
        </p>
        <button type="button" class="btn-primary" @click="store.loadBundle()">重试</button>
      </div>
      <router-view v-else />
    </main>
    <footer v-if="store.status === 'ready'" class="site-data-footer">
      <div class="site-data-footer-inner">
        <p class="site-data-footer-text">
          <strong>数据说明：</strong>
          球员与赛季场均等统计来自抓取的 Basketball-Reference 常规赛 per game 数据；球队分区战绩与排名来自
          NBA 统计接口；API 由 Spring Boot 提供。数据由运营侧更新后部署；中文名与头像 ID 映射优先从 API
          <code>/public/nba/i18n/*</code> 读取，无则回退打包的 <code>public</code> 静态 JSON。
        </p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.app-root {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.app-root > main {
  flex: 1;
}
</style>
