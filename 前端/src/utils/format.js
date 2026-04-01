export function fmt(n) {
  if (n == null) return "—";
  return typeof n === "number" && n % 1 ? n.toFixed(1) : String(n);
}

export function abbrColor(abbr) {
  let h = 0;
  const s = String(abbr || "X");
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) % 360;
  return `hsl(${h} 42% 42%)`;
}

export function seasonLabelFromYear(y) {
  const n = Number(y);
  const end = String((n + 1) % 100).padStart(2, "0");
  return `${n}–${end}`;
}

export function avatarFallbackChar(zhOrEn) {
  const s = String(zhOrEn || "").trim();
  if (!s) return "—";
  const ch = s[0];
  return /[\u4e00-\u9fff]/.test(ch) ? ch : s.slice(0, 1).toUpperCase();
}
