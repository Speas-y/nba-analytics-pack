import { Chart } from "chart.js/auto";

export function applyChartDefaults() {
  if (typeof Chart === "undefined") return;
  Chart.defaults.color = "#8b9bb8";
  Chart.defaults.borderColor = "rgba(255, 255, 255, 0.1)";
  Chart.defaults.font.family =
    '"DM Sans", "PingFang SC", "Microsoft YaHei", system-ui, sans-serif';
}
