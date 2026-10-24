// 找出 CSS 里"没人用"的类选择器（Vue 版：扫 .vue 模板 + .ts 脚本）。
//
// 为什么需要：改模板/脚本里的类名时很容易漏改 CSS（或反过来）。本项目就踩过 ——
// 浅色模式的两条 .pos-mini .screen / .dot 规则，实际类名是 .pos-mini-frame / .pos-dot，
// 于是整个通知位置示意图在浅色下样式全丢，肉眼还看不出是"规则没生效"。
//
// 用法: node scripts/check-css-classes.mjs
// 退出码 1 表示发现可疑选择器。
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

const css = readFileSync('src/styles.css', 'utf8');

// ---- 1. 收集 CSS 里出现的类选择器 ----
// ⚠️ 必须能进入 @media / @supports 等 at-rule 的花括号内部。
const cssClasses = new Set();
const withoutComments = css.replace(/\/\*[\s\S]*?\*\//g, '');

function collectSelectors(text) {
  for (const m of text.matchAll(/(^|[{}])\s*([^{}@]+?)\s*(?=\{)/g)) {
    const selector = m[2];
    if (selector.startsWith('@')) continue;
    for (const c of selector.matchAll(/\.(-?[_a-zA-Z][\w-]*)/g)) {
      cssClasses.add(c[1]);
    }
  }
}

collectSelectors(withoutComments);
// 递归剥掉 at-rule 的外层花括号，把内部内容再送进同一个收集器
let inner = withoutComments;
for (let i = 0; i < 12; i++) {
  const next = inner.replace(/@(?:media|supports|container|layer)[^{]*\{([^{}]*\{[^{}]*\}[^{}]*)\}/g, '$1');
  if (next === inner) break;
  collectSelectors(next);
  inner = next;
}

// ---- 2. 收集模板 / 脚本里真正用到的类名 ----
function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

const used = new Set();
const TOKEN = /^[a-z][\w-]*$/i;
const sources = walk('src').filter((p) => /\.(vue|ts)$/.test(p));

for (const f of sources) {
  const text = readFileSync(f, 'utf8');

  // 静态 class="..." 与绑定 :class="..."（含数组/对象/字符串形式）
  for (const m of text.matchAll(/(?:^|\s):?class="([^"]*)"/g)) {
    const v = m[1];
    // 引号里的字符串：:class="['a', 'b']" / :class="{ 'a': x }"
    for (const s of v.matchAll(/'([^']+)'|"([^"]+)"/g)) {
      const raw = s[1] ?? s[2] ?? '';
      for (const tok of raw.split(/\s+/)) if (TOKEN.test(tok)) used.add(tok);
    }
    // 无引号的静态类名
    for (const tok of v.split(/\s+/)) if (TOKEN.test(tok)) used.add(tok);
    // 对象字面量的裸键：:class="{ active: x, spin: y }"
    for (const k of v.matchAll(/(?:^|[{,\s])([a-z][\w-]*)\s*:/g)) used.add(k[1]);
  }

  // classList.add|remove|toggle|contains('...')
  for (const m of text.matchAll(/classList\.(?:add|remove|toggle|contains)\(\s*'([^']+)'/g)) used.add(m[1]);

  // pulse(node, 'box-pop') 这类一次性动画类
  for (const m of text.matchAll(/pulse\([^)]*'([a-z][\w-]*)'/g)) used.add(m[1]);

  // className = '...'
  for (const m of text.matchAll(/className\s*=\s*[`'"]([^`'"]*)/g)) {
    for (const tok of m[1].split(/\s+/)) if (TOKEN.test(tok)) used.add(tok);
  }

  // 模板串里拼出来的类名（`chip ${ok ? 'on' : 'off'}`）
  for (const m of text.matchAll(/`([^`$]*)`/g)) {
    for (const tok of m[1].split(/[^A-Za-z0-9_-]+/)) if (tok) used.add(tok);
  }
}

// HTML 入口只剩 #app 挂载点，仍扫一遍以防将来放回静态类名
for (const f of ['index.html', 'settings.html', 'notify.html', 'about.html']) {
  const html = readFileSync(f, 'utf8');
  for (const m of html.matchAll(/class="([^"]*)"/g)) {
    for (const tok of m[1].split(/\s+/)) if (tok) used.add(tok);
  }
}

// ---- 3. 报告 ----
const IGNORE = new Set(['on', 'off']);

// 「前缀 + 变量」拼出来的类名家族（如 `toast lv-${level}`、`toast-root dir-${x}`），
// 静态分析拿不到全名，按家族放行。**新的这类家族要显式加进来**，别去放宽 IGNORE。
const COMPOSED_PREFIXES = ['lv-', 'dir-', 'align-'];

const suspects = [...cssClasses]
  .filter(
    (c) =>
      !used.has(c) &&
      !IGNORE.has(c) &&
      !COMPOSED_PREFIXES.some((p) => c.startsWith(p)) &&
      c.length >= 4,
  )
  .sort();

console.log(`CSS 类选择器: ${cssClasses.size}   模板/脚本中出现的类名: ${used.size}`);
if (suspects.length === 0) {
  console.log('✅ 没有发现"没人用"的类选择器');
  process.exit(0);
}
console.log(`⚠️  以下 ${suspects.length} 个类在模板/脚本里找不到（可能是类名改漏了）：`);
for (const s of suspects) console.log('   .' + s);
process.exit(1);
