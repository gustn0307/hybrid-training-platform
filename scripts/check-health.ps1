<#
.SYNOPSIS
실행 중인 서비스와 Vite 프록시의 HTTP 연결 계약을 확인한다.
.DESCRIPTION
실제 DB·AI 연결은 백엔드 의존성 API로 검사한다. HTML 응답 확인은 브라우저 렌더링 검증과 구분한다.
#>
param(
    [string]$BackendUrl = 'http://127.0.0.1:8080',
    [string]$AiUrl = 'http://127.0.0.1:8000',
    [string]$FrontendUrl = 'http://127.0.0.1:5173'
)

$ErrorActionPreference = 'Stop'
$backend = Invoke-RestMethod "$BackendUrl/api/v1/health" -TimeoutSec 15
if ($backend.status -ne 'UP' -or $backend.service -ne 'backend') { throw 'Backend health contract failed.' }
$ai = Invoke-RestMethod "$AiUrl/health" -TimeoutSec 15
if ($ai.status -ne 'UP' -or $ai.service -ne 'ai-service') { throw 'AI health contract failed.' }
# 개별 프로세스가 응답해도 DB 또는 내부 AI 연결이 실패하면 준비 완료로 판정하지 않는다.
$dependencies = Invoke-RestMethod "$BackendUrl/api/v1/health/dependencies" -TimeoutSec 15
if ($dependencies.status -ne 'UP' -or $dependencies.database -ne 'UP' -or $dependencies.aiService -ne 'UP') {
    throw 'Database or internal AI connection failed.'
}
$frontend = Invoke-WebRequest $FrontendUrl -TimeoutSec 15
if ($frontend.StatusCode -ne 200 -or $frontend.Content -notmatch 'id="root"') { throw 'Frontend document failed.' }
# Vite 경유 요청으로 프론트엔드 프록시 → Spring Boot → AI/DB 경로를 함께 확인한다.
$proxy = Invoke-RestMethod "$FrontendUrl/api/v1/health/dependencies" -TimeoutSec 15
if ($proxy.status -ne 'UP' -or $proxy.database -ne 'UP' -or $proxy.aiService -ne 'UP') { throw 'Frontend -> Spring -> AI/DB path failed.' }
'PASS: backend, AI, PostgreSQL dependency check, frontend HTTP and Vite -> Spring -> AI/DB path.'
