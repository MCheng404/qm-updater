/**
 * 内置图标主动匹配层。
 *
 * 为常见软件 / 包内置品牌 SVG 图标（来源：simple-icons 与 gilbarbara/logos，
 * 各自按项目品牌图标授权条款使用），渲染时**同步**按名称本地匹配，
 * 优先于 fetch_icons 的被动获取：命中内置图标的项无需等待网络 / 注册表扫描，
 * 未命中项才继续走原有异步获取通道。
 *
 * 匹配策略：
 * 1. 名称 / pkgId 归一化（小写 + 去非字母数字）后精确命中 ALIASES；
 * 2. 多词名称（如 "Microsoft Visual Studio Code"）按**别名长度降序**做包含匹配，
 *    避免短别名误配长名称（如 "php" 抢先命中 "phpstorm"）。
 */

import type { UpdateItem } from './types';

// Vite 将 src/assets/icons/*.svg 打包为可引用 URL（<4KB 内联为 data URL）
const modules = import.meta.glob('../assets/icons/*.svg', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;

const ICONS = new Map<string, string>();
for (const [path, url] of Object.entries(modules)) {
  const file = path.slice(path.lastIndexOf('/') + 1).replace(/\.svg$/, '');
  ICONS.set(file, url);
}

/** 归一化：小写 + 去除非字母数字 */
function norm(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9]/g, '');
}

/** 常见名称（归一化 key）→ 图标文件名（必须存在于 src/assets/icons/） */
const ALIASES: Record<string, string> = {
  // ---- npm 全局包 ----
  typescript: 'typescript',
  pnpm: 'pnpm',
  eslint: 'eslint',
  prettier: 'prettier',
  vite: 'vite',
  vitest: 'vitest',
  nodejs: 'nodedotjs',
  node: 'nodedotjs',
  npm: 'npm',
  yarn: 'yarn',
  webpack: 'webpack',
  react: 'react',
  reactrouter: 'reactrouter',
  vue: 'vuedotjs',
  vueuse: 'vueuse',
  jest: 'jest',
  mocha: 'mocha',
  cypress: 'cypress',
  sass: 'sass',
  postcss: 'postcss',
  babel: 'babel',
  esbuild: 'esbuild',
  rollup: 'rollupdotjs',
  gulp: 'gulp',
  grunt: 'grunt',
  next: 'nextdotjs',
  remix: 'remix',
  astro: 'astro',
  svelte: 'svelte',
  solid: 'solid',
  tailwind: 'tailwindcss',
  tailwindcss: 'tailwindcss',
  redux: 'redux',
  axios: 'axios',
  lodash: 'lodash',
  zod: 'zod',
  turbo: 'turbo',
  nx: 'nx',
  storybook: 'storybook',
  electron: 'electron',
  deno: 'deno',
  biome: 'biome',
  hono: 'hono',
  pinia: 'pinia',
  expo: 'expo',
  gatsby: 'gatsby',
  // ---- pip / Python 生态 ----
  python: 'python',
  numpy: 'numpy',
  pandas: 'pandas',
  flask: 'flask',
  django: 'django',
  fastapi: 'fastapi',
  celery: 'celery',
  pytorch: 'pytorch',
  torch: 'pytorch',
  tensorflow: 'tensorflow',
  scikitlearn: 'scikitlearn',
  scikit: 'scikitlearn',
  sklearn: 'scikitlearn',
  scipy: 'scipy',
  jupyter: 'jupyter',
  anaconda: 'anaconda',
  conda: 'anaconda',
  pytest: 'pytest',
  poetry: 'poetry',
  ruff: 'ruff',
  opencv: 'opencv',
  selenium: 'selenium',
  airflow: 'apacheairflow',
  uv: 'uv',
  // ---- 语言 / 运行时 ----
  go: 'go',
  golang: 'go',
  rust: 'rust',
  cargo: 'rust',
  java: 'java',
  kotlin: 'kotlin',
  swift: 'swift',
  ruby: 'ruby',
  php: 'php',
  dart: 'dart',
  flutter: 'flutter',
  dotnet: 'dotnet',
  zig: 'zig',
  elixir: 'elixir',
  scala: 'scala',
  lua: 'lua',
  bash: 'gnubash',
  cmake: 'cmake',
  gradle: 'gradle',
  perl: 'perl',
  cplusplus: 'cplusplus',
  julia: 'julia',
  // ---- 编辑器 / IDE ----
  visualstudiocode: 'visualstudiocode',
  vscode: 'visualstudiocode',
  visualstudio: 'visualstudio',
  vscodium: 'vscodium',
  intellij: 'intellijidea',
  pycharm: 'pycharm',
  goland: 'goland',
  webstorm: 'webstorm',
  rubymine: 'rubymine',
  phpstorm: 'phpstorm',
  jetbrains: 'jetbrains',
  androidstudio: 'androidstudio',
  atom: 'atom',
  vim: 'vim',
  neovim: 'neovim',
  notepadplusplus: 'notepadplusplus',
  notepad: 'notepadplusplus',
  // ---- 浏览器 ----
  googlechrome: 'googlechrome',
  chrome: 'googlechrome',
  firefox: 'firefox',
  microsoftedge: 'microsoftedge',
  edge: 'microsoftedge',
  brave: 'brave',
  opera: 'opera',
  vivaldi: 'vivaldi',
  arc: 'arc',
  tor: 'torproject',
  safari: 'safari',
  // ---- 通讯 / 协作 ----
  discord: 'discord',
  telegram: 'telegram',
  slack: 'slack',
  zoom: 'zoom',
  signal: 'signal',
  whatsapp: 'whatsapp',
  wechat: 'wechat',
  skype: 'skype',
  teamspeak: 'teamspeak',
  matrix: 'matrix',
  element: 'element',
  mattermost: 'mattermost',
  rocket: 'rocketdotchat',
  // ---- 云 / DevOps / 数据库 ----
  googlecloud: 'googlecloud',
  cloudflare: 'cloudflare',
  vercel: 'vercel',
  netlify: 'netlify',
  heroku: 'heroku',
  firebase: 'firebase',
  supabase: 'supabase',
  digitalocean: 'digitalocean',
  nginx: 'nginx',
  apache: 'apache',
  docker: 'docker',
  kubernetes: 'kubernetes',
  k8s: 'kubernetes',
  terraform: 'terraform',
  ansible: 'ansible',
  prometheus: 'prometheus',
  grafana: 'grafana',
  vault: 'vault',
  postgresql: 'postgresql',
  postgres: 'postgresql',
  mysql: 'mysql',
  mariadb: 'mariadb',
  sqlite: 'sqlite',
  mongodb: 'mongodb',
  mongo: 'mongodb',
  redis: 'redis',
  elasticsearch: 'elasticsearch',
  clickhouse: 'clickhouse',
  rabbitmq: 'rabbitmq',
  kafka: 'apachekafka',
  github: 'github',
  gitlab: 'gitlab',
  bitbucket: 'bitbucket',
  gitkraken: 'gitkraken',
  sourcetree: 'sourcetree',
  postman: 'postman',
  insomnia: 'insomnia',
  ngrok: 'ngrok',
  curl: 'curl',
  rclone: 'rclone',
  syncthing: 'syncthing',
  snyk: 'snyk',
  ghostty: 'ghostty',
  // ---- 媒体 / 设计 / 娱乐 ----
  vlcmediaplayer: 'vlcmediaplayer',
  vlc: 'vlcmediaplayer',
  audacity: 'audacity',
  blender: 'blender',
  gimp: 'gimp',
  inkscape: 'inkscape',
  krita: 'krita',
  figma: 'figma',
  sketch: 'sketch',
  davinci: 'davinciresolve',
  kdenlive: 'kdenlive',
  godot: 'godotengine',
  unity: 'unity',
  unreal: 'unrealengine',
  spotify: 'spotify',
  epic: 'epicgames',
  gog: 'gogdotcom',
  riot: 'riotgames',
  transmission: 'transmission',
  obsidian: 'obsidian',
  notion: 'notion',
  anki: 'anki',
  joplin: 'joplin',
  plex: 'plex',
  twitch: 'twitch',
  // ---- 系统 / 发行版 ----
  linux: 'linux',
  ubuntu: 'ubuntu',
  fedora: 'fedora',
  archlinux: 'archlinux',
  debian: 'debian',
  mint: 'linuxmint',
  kali: 'kalilinux',
  opensuse: 'opensuse',
  centos: 'centos',
  manjaro: 'manjaro',
  lxc: 'linuxcontainers',
  // ---- 工具软件 ----
  '7zip': '7zip',
  obsstudio: 'obsstudio',
  git: 'git',
  steam: 'steam',
};

/** 按别名长度降序（长别名优先匹配，避免短别名误配） */
const SORTED_ALIASES = Object.entries(ALIASES).sort((a, b) => b[0].length - a[0].length);

/**
 * 匹配内置图标。
 * 匹配优先级：包名（npm/pip 取 @scope/ 之后一段）→ 注册表名 → pkgId。
 */
export function matchBuiltinIcon(item: Pick<UpdateItem, 'name' | 'pkgId'>): string | null {
  const keys = [norm(item.name)];
  if (item.pkgId) {
    const tail = item.pkgId.split('/').pop();
    if (tail) keys.push(norm(tail));
  }

  for (const key of keys) {
    // 1) 精确命中
    const slug = ALIASES[key];
    if (slug) {
      const url = ICONS.get(slug);
      if (url) return url;
    }
    // 2) 多词名称包含别名（长别名优先）
    for (const [alias, file] of SORTED_ALIASES) {
      if (alias.length >= 3 && key.includes(alias)) {
        const url = ICONS.get(file);
        if (url) return url;
      }
    }
  }
  return null;
}
