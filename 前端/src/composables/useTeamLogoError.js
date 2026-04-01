import { reactive } from "vue";
import { teamLogoUrlNba } from "../utils/nbaI18n";

/**
 * 球队卡片 logo：先 ESPN，失败再 NBA CDN，仍失败则 failed[abbr]=true 显示字母占位
 */
export function useTeamCardLogo() {
  const failed = reactive({});

  function onLogoImgError(abbr, e) {
    const img = e.target;
    if (!img || img.tagName !== "IMG") return;
    const nba = teamLogoUrlNba(abbr);
    if (nba && img.dataset.logoTier !== "nba") {
      img.dataset.logoTier = "nba";
      img.removeAttribute("crossorigin");
      img.src = nba;
      return;
    }
    const key = String(abbr);
    failed[key] = true;
  }

  return { failed, onLogoImgError };
}
