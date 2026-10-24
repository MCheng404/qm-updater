# 重试失败的 slug，记录 HTTP 状态码，区分 404（不存在）与临时失败
$dir = "D:\QM Updater\qm-updater\src\assets\icons"
$token = [Environment]::GetEnvironmentVariable('GITHUB_TOKEN')

$retry = @('nuxtdotjs','playwright','rxjs','vuequery','moment','matplotlib','mypy','seaborn','tox','uvicorn',
  'java','powershell','maven','csharp','sublime','atom','zed','chromium','slack','tencentqq','skype',
  'amazonwebservices','heroku','jq','putty','winrar','bandizip','handbrake','shotcut','xbox','alpine','tux')

$headers = @{ 'Authorization' = "Bearer $token" }
$lines = @()
foreach ($s in $retry) {
  $out = Join-Path $dir "$s.svg"
  try {
    $r = Invoke-WebRequest -Uri "https://raw.githubusercontent.com/simple-icons/simple-icons/master/icons/$s.svg" -Headers $headers -UseBasicParsing -TimeoutSec 30
    $c = [string]$r.Content
    if ($c -match '<svg' -and $c.Length -gt 100) { [IO.File]::WriteAllText($out, $c); $lines += "ok    $s $($r.StatusCode) ($($c.Length)B)" }
    else { $lines += "bad   $s $($r.StatusCode)" }
  } catch {
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $lines += "fail  $s HTTP$code"
  }
}
[IO.File]::WriteAllText((Join-Path $dir "_retry_log.txt"), ($lines -join "`n"))
"retry done: " + (Get-ChildItem -LiteralPath $dir -Filter *.svg | Measure-Object).Count + " icons total"
