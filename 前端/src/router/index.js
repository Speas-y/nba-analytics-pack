/**
 * 路由：hash 模式（如 #/teams/LAL）无需服务端 rewrite，适合 Vercel 静态托管。
 * /team 与 /player 为短链别名，与 /teams、/players 同页。
 */
import { createRouter, createWebHashHistory } from "vue-router";
import HomeView from "../views/HomeView.vue";
import TeamsView from "../views/TeamsView.vue";
import TeamDetailView from "../views/TeamDetailView.vue";
import PlayersView from "../views/PlayersView.vue";
import PlayerDetailView from "../views/PlayerDetailView.vue";

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: "/", name: "home", component: HomeView },
    { path: "/teams", name: "teams", component: TeamsView },
    { path: "/teams/:abbr", name: "team", component: TeamDetailView, props: true },
    { path: "/team/:abbr", name: "team-alt", component: TeamDetailView, props: true },
    { path: "/players", name: "players", component: PlayersView },
    { path: "/players/:id", name: "player", component: PlayerDetailView, props: true },
    { path: "/player/:id", name: "player-alt", component: PlayerDetailView, props: true },
  ],
});

export default router;
