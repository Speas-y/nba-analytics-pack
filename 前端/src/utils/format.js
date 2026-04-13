/** 数字展示：整数直出，一位小数保留一位；null → — */
export function fmt(n) {
  if (n == null) return "—";
  return typeof n === "number" && n % 1 ? n.toFixed(1) : String(n);
}

/** 无品牌色时，用缩写哈希生成稳定 HSL 作点缀色 */
export function abbrColor(abbr) {
  let h = 0;
  const s = String(abbr || "X");
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
  return `hsl(${h} 42% 42%)`;
}

/** 起始年 → 「2024–25」赛季标签 */
export function seasonLabelFromYear(y) {
  const n = Number(y);
  const end = String((n + 1) % 100).padStart(2, "0");
  return `${n}–${end}`;
}

/** 头像加载失败时圆圈内的首字（中文取一字，英文取首字母大写） */
export function avatarFallbackChar(zhOrEn) {
  const s = String(zhOrEn || "").trim();
  if (!s) return "—";
  const ch = s[0];
  return /[\u4e00-\u9fff]/.test(ch) ? ch : s.slice(0, 1).toUpperCase();
}
