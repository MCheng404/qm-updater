# 从 gilbarbara/logos 补 simple-icons 已移除的品牌图标
$dir = "D:\QM Updater\qm-updater\src\assets\icons"
$token = [Environment]::GetEnvironmentVariable('GITHUB_TOKEN')
$headers = @{ 'Authorization' = "Bearer $token" }

# 候选（logos 仓库文件名猜测）
$map = @{
  'slack'            = 'slack'
  'java'             = 'java'
  'amazonwebservices'= 'amazon-aws'
  'heroku'           = 'heroku'
  'chromium'         = 'chromium'
  'xbox'             = 'xbox'
  'winrar'           = 'winrar'
  'putty'            = 'putty'
  'atom'             = 'atom'
  'sublime'          = 'sublime'
  'handbrake'        = 'handbrake'
  'shotcut'          = 'shotcut'
  'alpine'           = 'alpine-linux'
  'skype'            = 'skype'
  'tencentqq'        = 'qq'
  'powershell'       = 'powershell'
  'csharp'           = 'csharp'
  'maven'            = 'maven'
}

$lines = @()
foreach ($k in $map.Keys) {
  $out = Join-Path $dir "$k.svg"
  if (Test-Path -LiteralPath $out) { $lines += "skip  $k"; continue }
  try {
    $r = Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gilbarbara/logos/master/logos/$($map[$k]).svg" -Headers $headers -UseBasicParsing -TimeoutSec 30
    $c = [string]$r.Content
    if ($c -match '<svg' -and $c.Length -gt 100) { [IO.File]::WriteAllText($out, $c); $lines += "ok    $k ($($c.Length)B)" }
    else { $lines += "bad   $k ($($c.Length)B)" }
  } catch {
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $lines += "fail  $k HTTP$code"
  }
}
[IO.File]::WriteAllText((Join-Path $dir "_logos_log.txt"), ($lines -join "`n"))
"logos done: " + (Get-ChildItem -LiteralPath $dir -Filter *.svg | Measure-Object).Count + " icons total"
