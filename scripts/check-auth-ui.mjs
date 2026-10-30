import assert from 'node:assert/strict';
import { spawn } from 'node:child_process';
import { randomUUID } from 'node:crypto';
import { mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { setTimeout as delay } from 'node:timers/promises';

// 추가 패키지 없이 설치된 Chromium 브라우저의 headless 모드로 실제 React DOM 흐름을 검증한다.
// 기본 대상은 별도 Compose 검증 서버이며 운영 URL·사용자 계정으로 실행하지 않는다.
const url = process.env.AUTH_UI_URL ?? 'http://127.0.0.1:15173';
assert(['127.0.0.1', 'localhost'].includes(new URL(url).hostname), 'Only local verification servers are allowed');
const executable = process.env.BROWSER_PATH ?? 'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe';
const profile = await mkdtemp(join(tmpdir(), 'ai-love-workout-ui-'));
const emailKind = process.env.AUTH_UI_UNICODE_EMAIL === '1' ? 'unicode-email' : 'ascii-email';
const output = resolve(dirname(fileURLToPath(import.meta.url)), '../tmp/auth-user-ui', emailKind);
await mkdir(output, { recursive: true });
const browser = spawn(executable, ['--headless=new', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
  `--user-data-dir=${profile}`, '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=0', 'about:blank'],
{ windowsHide: true, stdio: ['ignore', 'ignore', 'ignore'] });
let launchError;
browser.on('error', error => { launchError = error; });
let socket;
let nextId = 0;
const pending = new Map();
const exceptions = [];
const patchFields = [];
const authResponses = [];

async function until(check, description) {
  for (let attempt = 0; attempt < 120; attempt++) {
    if (launchError) throw new Error('Browser launch failed', { cause: launchError });
    if (await check()) return;
    await delay(250);
  }
  throw new Error(`Timed out: ${description}`);
}

function cdp(method, params = {}) {
  const id = ++nextId;
  return new Promise((resolveResult, reject) => {
    const timeout = setTimeout(() => { pending.delete(id); reject(new Error(`CDP timeout: ${method}`)); }, 15000);
    pending.set(id, { resolve: value => { clearTimeout(timeout); resolveResult(value); }, reject: error => { clearTimeout(timeout); reject(error); } });
    socket.send(JSON.stringify({ id, method, params }));
  });
}

async function evaluate(expression) {
  const reply = await cdp('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
  if (reply.exceptionDetails) throw new Error('Browser DOM evaluation failed');
  return reply.result.value;
}
async function textContains(text) { return evaluate(`document.body.innerText.includes(${JSON.stringify(text)})`); }
async function click(text) {
  assert(await evaluate(`(() => { const button = [...document.querySelectorAll('button')].find(item => item.textContent === ${JSON.stringify(text)}); if (!button) return false; button.click(); return true; })()`), `Button missing: ${text}`);
}
async function login(email, password) {
  // 비밀번호는 테스트 중 생성한 값이며 로그·스크린샷 파일명·브라우저 저장소에 기록하지 않는다.
  await evaluate(`document.querySelector('[name=email]').value = ${JSON.stringify(email)}; document.querySelector('[name=password]').value = ${JSON.stringify(password)}; document.querySelector('form').requestSubmit();`);
  await until(() => textContains('프로필 수정'), 'existing profile after login');
}
async function screenshot(name) {
  const image = await cdp('Page.captureScreenshot', { format: 'png', captureBeyondViewport: true });
  await writeFile(join(output, name), Buffer.from(image.data, 'base64'));
}

/** 두 인증 화면에서 클라이언트의 필수·길이 검사와 서버의 최종 형식 검증을 구분해 확인한다. */
async function checkEmailValidation() {
  assert.equal(await evaluate("document.querySelector('[name=email]').type"), 'text');
  assert.equal(await evaluate("document.querySelector('[name=email]').inputMode"), 'email');
  async function submit(email) {
    await evaluate(`document.querySelector('[name=email]').value = ${JSON.stringify(email)}; document.querySelector('[name=password]').value = 'validation-only'; if (document.querySelector('[name=nickname]')) document.querySelector('[name=nickname]').value = '브라우저검증'; document.querySelector('form').requestSubmit();`);
  }
  const beforeLocalValidation = authResponses.length;
  await submit(' ');
  await until(() => textContains('이메일을 입력해 주세요.'), 'blank email message');
  await submit('a'.repeat(255) + '@example.com');
  await until(() => textContains('이메일은 254자 이하여야 합니다.'), 'email length message');
  assert.equal(authResponses.length, beforeLocalValidation);
  const beforeServerValidation = authResponses.filter(item => item.status === 400).length;
  await submit('not-an-email');
  await until(() => Promise.resolve(authResponses.filter(item => item.status === 400).length > beforeServerValidation), 'server email validation response');
  await until(() => textContains('입력값을 확인해 주세요.'), 'visible server email validation message');
}

try {
  let port;
  await until(async () => { try { port = Number((await readFile(join(profile, 'DevToolsActivePort'), 'utf8')).split('\n')[0]); return port > 0; } catch { return false; } }, 'browser debugger');
  const pages = await (await fetch(`http://127.0.0.1:${port}/json/list`)).json();
  const page = pages.find(item => item.type === 'page');
  assert(page, 'Fresh browser page not found');
  socket = new WebSocket(page.webSocketDebuggerUrl);
  await new Promise((resolveOpen, reject) => { socket.addEventListener('open', resolveOpen, { once: true }); socket.addEventListener('error', reject, { once: true }); });
  socket.addEventListener('message', event => {
    const message = JSON.parse(String(event.data));
    if (message.id && pending.has(message.id)) {
      const waiter = pending.get(message.id); pending.delete(message.id);
      if (message.error) waiter.reject(new Error('Browser protocol error')); else waiter.resolve(message.result);
    }
    if (message.method === 'Runtime.exceptionThrown') exceptions.push('Uncaught browser exception');
    // 이메일 검증의 HTTP 상태만 기록하고 인증 응답 본문·헤더·토큰은 수집하지 않는다.
    if (message.method === 'Network.responseReceived') {
      const response = message.params.response;
      if (response.url.endsWith('/auth/signup') || response.url.endsWith('/auth/login')) authResponses.push({ status: response.status });
    }
    // 인증 요청·Authorization 헤더를 수집하지 않고 프로필 PATCH의 필드명만 확인한다.
    if (message.method === 'Network.requestWillBeSent') {
      const request = message.params.request;
      if (request.method === 'PATCH' && request.url.endsWith('/users/me/workout-profile') && request.postData) patchFields.push(Object.keys(JSON.parse(request.postData)).sort());
    }
  });
  await cdp('Page.enable'); await cdp('Runtime.enable'); await cdp('Network.enable');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 1280, height: 1000, deviceScaleFactor: 1, mobile: false });
  await cdp('Page.navigate', { url });
  await until(() => evaluate("document.querySelector('#auth-title')?.textContent === '로그인'"), 'login page');
  await screenshot('login-desktop.png');
  await checkEmailValidation();
  await click('처음 오셨나요? 회원가입');
  await until(() => evaluate("document.querySelector('[name=nickname]') !== null"), 'signup form');
  await checkEmailValidation();
  const email = `${emailKind === 'unicode-email' ? '운동' : 'browser'}-${randomUUID()}@EXAMPLE.COM`, password = `browser-${randomUUID()}`;
  await evaluate(`document.querySelector('[name=email]').value = ${JSON.stringify(email)}; document.querySelector('[name=password]').value = ${JSON.stringify(password)}; document.querySelector('[name=nickname]').value = '브라우저검증'; document.querySelector('form').requestSubmit();`);
  await until(() => textContains('회원가입이 완료되었습니다.'), 'signup success without automatic login');
  assert.equal(await evaluate("document.querySelector('#auth-title')?.textContent"), '로그인');
  await evaluate(`document.querySelector('[name=email]').value = ${JSON.stringify(email)}; document.querySelector('[name=password]').value = ${JSON.stringify(password)}; document.querySelector('form').requestSubmit();`);
  await until(() => textContains('첫 운동 프로필을 설정해 주세요.'), 'initial profile form');
  assert(await textContains(email.toLowerCase()));
  await evaluate("{ const selects = document.querySelectorAll('select'); selects[0].value = 'FITNESS'; selects[0].dispatchEvent(new Event('change', {bubbles:true})); }");
  await evaluate("{ const selects = document.querySelectorAll('select'); selects[1].value = 'BEGINNER'; selects[1].dispatchEvent(new Event('change', {bubbles:true})); }");
  await evaluate("[...document.querySelectorAll('.choice')].find(item => item.textContent === '러닝').querySelector('input').click();");
  await evaluate("[...document.querySelectorAll('.choice')].find(item => item.textContent === '장비 없음').querySelector('input').click();");
  await click('프로필 저장');
  await until(() => textContains('프로필 수정'), 'saved profile view');
  assert(await textContains('체력 향상'));
  await click('프로필 수정');
  await until(() => evaluate("document.querySelector('select') !== null"), 'profile edit form');
  await evaluate("{ const select = document.querySelector('select'); select.value = 'HABIT'; select.dispatchEvent(new Event('change', {bubbles:true})); }");
  await click('프로필 저장');
  await until(() => evaluate("document.querySelector('form') === null && [...document.querySelectorAll('button')].some(item => item.textContent === '프로필 수정')"), 'updated profile view');
  assert(await textContains('운동 습관 형성'));
  assert(await textContains('초급')); assert(await textContains('러닝')); assert(await textContains('장비 없음'));
  assert.deepEqual(patchFields.at(-1), ['goal']);
  await screenshot('profile-desktop.png');
  assert.equal(await evaluate('localStorage.length + sessionStorage.length'), 0);
  await cdp('Page.reload');
  await until(() => evaluate("document.querySelector('#auth-title')?.textContent === '로그인'"), 'refresh requires login');
  assert.equal(await evaluate('localStorage.length + sessionStorage.length'), 0);
  assert.equal((await cdp('Network.getCookies', { urls: [url] })).cookies.length, 0);
  await login(email, password);
  assert(await textContains('운동 습관 형성'));
  await cdp('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  assert(await evaluate('document.documentElement.scrollWidth <= window.innerWidth'), 'Mobile profile layout overflows horizontally');
  await screenshot('profile-mobile.png');
  await click('로그아웃');
  await until(() => textContains('로그아웃되었습니다.'), 'logout success');
  assert.equal(await evaluate("document.querySelector('#auth-title')?.textContent"), '로그인');
  await cdp('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 1, mobile: true });
  assert(await evaluate('document.documentElement.scrollWidth <= window.innerWidth'), 'Mobile layout overflows horizontally');
  await screenshot('login-mobile.png');
  assert.equal(exceptions.length, 0, 'Uncaught browser errors');
  console.log(`PASS (${emailKind}): actual signup/login form submission, local/server email validation, first profile, read/edit, partial PATCH, refresh/relogin, logout, memory-only storage, responsive layout.`);
  console.log(`Screenshots: ${output}`);
} finally {
  socket?.close(); browser.kill();
}
