param(
    [Parameter(Mandatory = $true)][uri]$BaseUrl,
    [ValidatePattern('^[1-9]\d*$')][string]$RoomId,
    [ValidateRange(4, 60)][int]$RequestCount = 24,
    [switch]$DirectBackend
)
$ErrorActionPreference = 'Stop'
if ($BaseUrl.Scheme -notin @('https', 'http') -or $BaseUrl.UserInfo -or
    $BaseUrl.AbsolutePath -ne '/' -or $BaseUrl.Query -or $BaseUrl.Fragment) {
    throw 'BaseUrl에는 경로 없이 서버 출처만 입력: https://서비스주소'
}
if ($BaseUrl.Scheme -eq 'http' -and -not $BaseUrl.IsLoopback) {
    throw '배포 서버에는 HTTPS 주소 사용 필요'
}
$taskRunId = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$taskName = "billim-deploy-read-$taskRunId"
$taskOutput = Join-Path $PSScriptRoot "local\$taskName"
New-Item -ItemType Directory -Path $taskOutput | Out-Null

function Read-SecretText([string]$Prompt) {
    $taskSecure = Read-Host $Prompt -AsSecureString
    $taskPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($taskSecure)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($taskPointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($taskPointer) }
}

$taskConfig = @{ sessionToken = Read-SecretText '테스트 계정의 BILLIM_SESSION 쿠키 값'; roomId = $RoomId }
if ($DirectBackend) {
    $taskConfig.nfSign = Read-SecretText '발급한 x-nf-sign 값'
}
$taskConfigPath = Join-Path $taskOutput 'session.json'
$taskConfig | ConvertTo-Json | Set-Content -LiteralPath $taskConfigPath -Encoding utf8NoBOM
$taskConfig.Clear()
$taskManifest = @{ version = 'v0.1.0'; origin = $BaseUrl.GetLeftPart([UriPartial]::Authority);
    requestCount = $RequestCount; vus = 1; intervalSeconds = 1; readOnly = $true;
    includesMessages = [bool]$RoomId; startedAt = (Get-Date).ToString('o') }
$taskManifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $taskOutput 'manifest.json') -Encoding utf8NoBOM
docker run -d --name $taskName --mount "type=bind,source=$PSScriptRoot,target=/scripts,readonly" `
    --mount "type=bind,source=$taskOutput,target=/results" `
    --mount "type=bind,source=$taskConfigPath,target=/config/session.json,readonly" `
    -e "BASE_URL=$($taskManifest.origin)" -e "REQUEST_COUNT=$RequestCount" `
    grafana/k6:0.54.0 run --summary-export /results/summary.json /scripts/deploy-read-smoke.js
if ($LASTEXITCODE -ne 0) { throw '테스트 컨테이너 실행 실패' }
Write-Output "실행: $taskName / 결과: $taskOutput\summary.json"
Write-Output '추적 중지. 종료 후 summary.json과 컨테이너 종료 코드 확인. session.json은 확인 후 삭제.'
