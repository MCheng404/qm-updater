// 语言包完整性校验（Vue 版）：
//  1) 中英 key 必须一一对应
//  2) 所有 $t('...') / t('...') / 形如 命名空间.路径 的字面量都必须存在于语言包
//  3) 模板串 key（group.* / settings.preset.* / settings.pos.* / settings.render* / settings.material*）逐项展开核对
//
// 用法: node scripts/check-i18n.mjs
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

function walk(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name);
    if (statSync(p).isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}

const zhSrc = readFileSync('src/app/locales/zh-CN.ts', 'utf8');
const enSrc = readFileSync('src/app/locales/en-US.ts', 'utf8');

function keysOf(text) {
  const out = new Set();
  for (const m of text.matchAll(/'([A-Za-z0-9_.]+)':/g)) out.add(m[1]);
  return out;
}

const zh = keysOf(zhSrc);
const en = keysOf(enSrc);

let fail = 0;
console.log(`zh keys: ${zh.size}   en keys: ${en.size}`);
const onlyZh = [...zh].filter((k) => !en.has(k));
const onlyEn = [...en].filter((k) => !zh.has(k));
if (onlyZh.length) { console.log('❌ 英文缺失:', onlyZh); fail++; }
if (onlyEn.length) { console.log('❌ 仅英文有:', onlyEn); fail++; }
if (!fail) console.log('✅ 中英 key 完全对齐');

// ---- 收集引用（扫全部 .vue / .ts，跳过语言包本身） ----
const used = new Set();
const NS = 'common|app|nav|btn|env|status|empty|group|log|action|notify|settings|about';
const keyLike = new RegExp(`'((?:${NS})\\.[A-Za-z0-9_.]+)'`, 'g');

const sources = walk('src').filter((p) => /\.(vue|ts)$/.test(p) && !p.includes('locales'));

for (const f of sources) {
  const text = readFileSync(f, 'utf8');
  // $t('key') / t('key')（\b 防止误吃 onColorInput('accent') 这类函数名结尾的 t）
  for (const m of text.matchAll(/\bt\(\s*'([A-Za-z0-9_.]+)'/g)) {
    // 以 '.' 结尾的是动态前缀（'group.' + source），由下方展开逻辑覆盖
    if (!m[1].endsWith('.')) used.add(m[1]);
  }
  // 形如「命名空间.路径」的字符串字面量
  for (const m of text.matchAll(keyLike)) used.add(m[1]);
}

// 模板串 key —— 逐项展开
const KINDS = ['npm', 'winget', 'pip', 'openclaw'];
const PRESETS = ['teal', 'azure', 'violet', 'amber', 'rose', 'jade'];
const POS = ['topLeft', 'topCenter', 'topRight', 'bottomLeft', 'bottomCenter', 'bottomRight'];
for (const k of KINDS) used.add(`group.${k}`);
for (const p of PRESETS) used.add(`settings.preset.${p}`);
for (const p of POS) {
  // 位置名 + 位置提示
  used.add(`settings.pos.${p}`);
  used.add(`settings.pos.${p}Hint`);
}

const missing = [...used].filter((k) => {
  if (zh.has(k)) return false;
  // 注释里的示例 key（'key'）/ 动态前缀（'group.'）
  if (!k.includes('.') || k.endsWith('.')) return false;
  // 前缀 + 变量拼接（'settings.render' + suffix）—— 只要语言包里存在以其开头的 key 即视为已展开
  if ([...zh].some((x) => x.startsWith(k))) return false;
  return true;
});
console.log(`引用到的 key: ${used.size}`);
if (missing.length) { console.log('❌ 语言包中不存在:', missing); fail++; }
else console.log('✅ 所有引用的 key 都存在');

// 反向：语言包里几乎没被引用的 key（提示清理，不算失败）
const unused = [...zh].filter((k) => !used.has(k));
if (unused.length) console.log('ℹ️  未被引用（可考虑清理）:', unused);

process.exit(fail ? 1 : 0);
