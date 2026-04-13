/** 前端构建配置：Vue 插件 + 本地 dev 端口（与后端 CORS 中 localhost 对应） */
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
  },
});
