#requires -Version 7.2
param([string]$Project = ('ai-love-workout-auth-' + (Get-Date -Format 'yyyyMMddHHmmss')))

# 매 실행의 별도 Compose 프로젝트·DB를 사용하며 기존 컨테이너·볼륨은 삭제하지 않는다.
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$names = @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD', 'JWT_SECRET', 'DB_PORT', 'BACKEND_PORT', 'FRONTEND_PORT', 'AI_PORT', 'AUTH_TEST_DB_URL', 'AUTH_TEST_DB_USER', 'AUTH_TEST_DB_PASSWORD', 'GRADLE_USER_HOME')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
Push-Location $repoRoot
try {
    $existing = @(docker ps -aq --filter "label=com.docker.compose.project=$Project")
    if ($LASTEXITCODE -ne 0) { throw 'Docker 엔진에 연결할 수 없습니다.' }
    if ($existing.Count -gt 0) { throw '같은 프로젝트가 이미 존재합니다. 다른 -Project 값을 사용하세요.' }
    # 실제 키·비밀번호는 프로세스 환경과 테스트 컨테이너에만 전달하고 파일·콘솔에 쓰지 않는다.
    $env:POSTGRES_DB = 'ai_love_workout'
    $env:POSTGRES_USER = 'ai_love_workout'
    $env:POSTGRES_PASSWORD = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $env:DB_PORT = '15432'; $env:BACKEND_PORT = '18080'; $env:FRONTEND_PORT = '15173'; $env:AI_PORT = '18000'
    Write-Output "Verification project: $Project"
    docker compose -p $Project -f infra/compose.yaml config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Compose 설정 검증 실패' }
    docker compose -p $Project -f infra/compose.yaml up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Compose 빌드·시작 실패' }

    $ready = $false
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try {
            $health = Invoke-RestMethod 'http://127.0.0.1:18080/api/v1/health/dependencies' -TimeoutSec 3
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw '서비스 준비 상태 검증 실패' }
    docker compose -p $Project -f infra/compose.yaml ps

    # 통합 테스트용 DB도 새 프로젝트 안에만 생성한다. Flyway가 세 테이블을 적용한다.
    docker compose -p $Project -f infra/compose.yaml exec -T postgres createdb -U $env:POSTGRES_USER auth_integration
    if ($LASTEXITCODE -ne 0) { throw '별도 테스트 DB 생성 실패' }
    $env:AUTH_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:15432/auth_integration'
    $env:AUTH_TEST_DB_USER = $env:POSTGRES_USER
    $env:AUTH_TEST_DB_PASSWORD = $env:POSTGRES_PASSWORD
    $env:GRADLE_USER_HOME = Join-Path $repoRoot 'backend/.gradle/user-home'
    ./backend/gradlew.bat -p backend test bootJar --no-daemon --rerun-tasks
    if ($LASTEXITCODE -ne 0) { throw 'Spring Boot PostgreSQL 테스트·빌드 실패' }
    npm.cmd --prefix frontend run typecheck
    if ($LASTEXITCODE -ne 0) { throw 'React 타입 검사 실패' }
    npm.cmd --prefix frontend run build
    if ($LASTEXITCODE -ne 0) { throw 'React 빌드 실패' }

    # Compose 백엔드에서도 실제 흐름과 재시작 후 무효화를 별도로 확인한다.
    $base = 'http://127.0.0.1:18080/api/v1'
    $email = 'verify-' + [Guid]::NewGuid().ToString('N') + '@example.com'
    $password = 'verify-' + [Guid]::NewGuid().ToString('N')
    $signup = @{ email = $email; password = $password; nickname = '통합검증' } | ConvertTo-Json -Compress
    $created = Invoke-WebRequest "$base/auth/signup" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($signup))
    if ($created.StatusCode -ne 201) { throw '회원가입 검증 실패' }
    $loginBody = @{ email = $email; password = $password } | ConvertTo-Json -Compress
    $token = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody
    $headers = @{ Authorization = 'Bearer ' + $token.accessToken }
    $initial = Invoke-RestMethod "$base/users/me/workout-profile" -Headers $headers
    if ($initial.configured) { throw '미설정 상태 검증 실패' }
    $profile = @{ goal = 'FITNESS'; experienceLevel = 'BEGINNER'; preferredTypes = @('RUNNING'); equipment = @('NONE') } | ConvertTo-Json -Compress
    $saved = Invoke-RestMethod "$base/users/me/workout-profile" -Method Patch -Headers $headers -ContentType 'application/json' -Body $profile
    if (-not $saved.configured) { throw '최초 설정 검증 실패' }
    $changed = Invoke-RestMethod "$base/users/me/workout-profile" -Method Patch -Headers $headers -ContentType 'application/json' -Body '{"goal":"HABIT"}'
    if ($changed.goal -ne 'HABIT' -or $changed.experienceLevel -ne 'BEGINNER') { throw '부분 수정 검증 실패' }
    $read = Invoke-RestMethod "$base/users/me/workout-profile" -Headers $headers
    if ($read.goal -ne 'HABIT') { throw '저장 후 조회 검증 실패' }
    $loggedOut = Invoke-WebRequest "$base/auth/logout" -Method Post -Headers $headers
    if ($loggedOut.StatusCode -ne 204) { throw '로그아웃 검증 실패' }
    $reused = Invoke-WebRequest "$base/users/me" -Headers $headers -SkipHttpErrorCheck
    if ($reused.StatusCode -ne 401) { throw '로그아웃 토큰 재사용 차단 실패' }
    docker compose -p $Project -f infra/compose.yaml restart backend
    if ($LASTEXITCODE -ne 0) { throw '백엔드 재시작 실패' }
    $restarted = $false
    for ($attempt = 0; $attempt -lt 45; $attempt++) {
        try {
            $result = Invoke-WebRequest "$base/users/me" -Headers $headers -SkipHttpErrorCheck -TimeoutSec 3
            if ($result.StatusCode -eq 401) { $restarted = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $restarted) { throw '재시작 후 무효화 유지 검증 실패' }
    # 실제 DB 장애 중 로그아웃은 성공하면 안 된다. 복구 후 같은 토큰은 계속 유효해야 한다.
    $retryToken = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType 'application/json' -Body $loginBody
    $retryHeaders = @{ Authorization = 'Bearer ' + $retryToken.accessToken }
    docker compose -p $Project -f infra/compose.yaml stop postgres
    if ($LASTEXITCODE -ne 0) { throw '테스트 DB 중지 실패' }
    try {
        $failedLogout = Invoke-WebRequest "$base/auth/logout" -Method Post -Headers $retryHeaders -SkipHttpErrorCheck -TimeoutSec 15
        if ($failedLogout.StatusCode -ne 503) { throw 'DB 장애 중 로그아웃 실패 계약 검증 실패' }
    } finally {
        # 검증이 실패하더라도 이 프로젝트의 PostgreSQL을 반드시 다시 시작한다.
        docker compose -p $Project -f infra/compose.yaml start postgres
        if ($LASTEXITCODE -ne 0) { throw '테스트 DB 복구 시작 실패' }
    }
    $recovered = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $account = Invoke-WebRequest "$base/users/me" -Headers $retryHeaders -SkipHttpErrorCheck -TimeoutSec 5
            if ($account.StatusCode -eq 200) { $recovered = $true; break }
        } catch { }
        Start-Sleep -Seconds 2
    }
    if (-not $recovered) { throw 'DB 복구 후 인증 상태 유지 검증 실패' }
    $retryLogout = Invoke-WebRequest "$base/auth/logout" -Method Post -Headers $retryHeaders
    if ($retryLogout.StatusCode -ne 204) { throw 'DB 복구 후 로그아웃 실패' }
    docker compose -p $Project -f infra/compose.yaml exec -T postgres psql -U $env:POSTGRES_USER -d $env:POSTGRES_DB -c 'SELECT (SELECT count(*) FROM app_user) AS users, (SELECT count(*) FROM workout_profile) AS profiles, (SELECT count(*) FROM revoked_token) AS revoked;'
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL 실제 조회 실패' }
    Write-Output 'PASS: Compose health, signup/login/profile/logout, PostgreSQL persistence, restart/reuse rejection, DB outage logout failure and recovery.'
    Write-Output 'Browser URL: http://127.0.0.1:15173 (browser interaction is a separate verification).'
    Write-Output "Containers remain available. Stop only this project when finished: docker compose -p $Project -f infra/compose.yaml stop"
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
    Pop-Location
}
