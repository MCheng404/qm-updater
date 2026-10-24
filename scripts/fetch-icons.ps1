# 继续批量下载 simple-icons 图标（使用 GITHUB_TOKEN 走 GitHub raw，避免匿名限流）
# 跳过已存在文件；结果记录到 icons/_fetch_log.txt

$dir = "D:\QM Updater\qm-updater\src\assets\icons"
$token = [Environment]::GetEnvironmentVariable('GITHUB_TOKEN')
if ([string]::IsNullOrEmpty($token)) { throw 'GITHUB_TOKEN 环境变量不存在' }

$slugs = @(
  # ---- 前端 / JS 生态 ----
  'astro','babel','biome','cypress','deno','electron','esbuild','expo','gatsby','gulp','grunt',
  'mocha','nextdotjs','nuxtdotjs','pinia','playwright','postcss','redux','rollupdotjs','rxjs','sass',
  'solid','storybook','svelte','tailwindcss','turbo','vueuse','zod','nx','remix','hono','reactrouter',
  'vuequery','vitest','vuedotjs','lodash','moment','axios',
  # ---- Python 生态 ----
  'anaconda','apacheairflow','celery','fastapi','jupyter','matplotlib','mypy','poetry','pytest',
  'pytorch','ruff','scikitlearn','scipy','seaborn','selenium','tensorflow','tox','uvicorn','uv','opencv',
  # ---- 语言 / 运行时 ----
  'go','rust','java','kotlin','swift','ruby','php','dart','flutter','dotnet','zig','elixir','scala',
  'lua','powershell','gnubash','cmake','gradle','maven','perl','cplusplus','csharp','julia',
  # ---- 编辑器 / IDE ----
  'androidstudio','intellijidea','pycharm','goland','webstorm','rubymine','phpstorm','jetbrains',
  'sublime','neovim','vim','atom','notepadplusplus','vscodium','zed',
  # ---- 浏览器 ----
  'brave','opera','chromium','arc','torproject','vivaldi','safari',
  # ---- 通讯 / 协作 ----
  'slack','zoom','signal','whatsapp','wechat','tencentqq','mattermost','rocketdotchat','element',
  'skype','teamspeak','discord','telegram','matrix',
  # ---- 云 / DevOps / 数据库 ----
  'amazonwebservices','googlecloud','cloudflare','vercel','netlify','heroku','firebase','supabase',
  'digitalocean','nginx','apache','docker','kubernetes','terraform','ansible','prometheus','grafana',
  'vault','postgresql','mysql','mariadb','sqlite','mongodb','redis','elasticsearch','clickhouse',
  'rabbitmq','apachekafka','github','gitlab','bitbucket','gitkraken','sourcetree','postman','insomnia',
  'ngrok','curl','jq','rclone','syncthing','putty','winrar','bandizip','snyk','ghostty',
  # ---- 媒体 / 设计 / 娱乐 ----
  'vlcmediaplayer','audacity','blender','gimp','inkscape','krita','figma','sketch','davinciresolve',
  'handbrake','shotcut','kdenlive','godotengine','unity','unrealengine','spotify','epicgames',
  'gogdotcom','riotgames','xbox','transmission','obsidian','notion','anki','joplin','plex','twitch',
  # ---- 系统 / 发行版 ----
  'linux','ubuntu','fedora','archlinux','debian','linuxmint','kalilinux','alpine','opensuse','centos',
  'manjaro','linuxcontainers','tux'
)

$headers = @{ 'Authorization' = "Bearer $token" }
$log = @()
$okCount = 0
$failCount = 0
$skipCount = 0
foreach ($s in $slugs) {
  $out = Join-Path $dir "$s.svg"
  if (Test-Path -LiteralPath $out) { $skipCount++; $log += "skip  $s"; continue }
  try {
    $r = Invoke-WebRequest -Uri "https://raw.githubusercontent.com/simple-icons/simple-icons/master/icons/$s.svg" -Headers $headers -UseBasicParsing -TimeoutSec 30
    $c = [string]$r.Content
    if ($c -match '<svg' -and $c.Length -gt 100) {
      [IO.File]::WriteAllText($out, $c)
      $okCount++; $log += "ok    $s ($($c.Length)B)"
    } else { $failCount++; $log += "bad   $s ($($c.Length)B)" }
  } catch {
    $failCount++; $log += "fail  $s"
  }
}
$log += "---- ok=$okCount fail=$failCount skip=$skipCount"
[IO.File]::WriteAllText((Join-Path $dir "_fetch_log.txt"), ($log -join "`n"))
"done ok=$okCount fail=$failCount skip=$skipCount"
