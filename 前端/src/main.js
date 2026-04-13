/**
 * 应用入口：挂载 Vue，启用 Pinia（全局状态）与 Vue Router（hash 路由，利于纯静态部署如 Vercel）。
 */
import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import "./assets/styles.css";

createApp(App).use(createPinia()).use(router).mount("#app");
