import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, errorMessage, request } from './api';

type Session = { token: string; expiresAt: number };
type Account = { id: string; email: string; nickname: string };
type Profile = { configured: boolean; goal: string | null; experienceLevel: string | null; preferredTypes: string[] | null; equipment: string[] | null };
type Option = { value: string; label: string };
type Options = { goals: Option[]; experienceLevels: Option[]; preferredTypes: Option[]; equipment: Option[] };
type Fields = { goal: string; experienceLevel: string; preferredTypes: string[]; equipment: string[] };

/** 인증 수명은 이 컴포넌트의 메모리 상태에만 둔다. 페이지 재실행 시 항상 로그인에서 시작한다. */
export default function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [screen, setScreen] = useState<'login' | 'signup'>('login');
  const [account, setAccount] = useState<Account | null>(null);
  const [profile, setProfile] = useState<Profile | null>(null);
  const [options, setOptions] = useState<Options | null>(null);
  const [editing, setEditing] = useState(false);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);

  function clearSession(notice: string) {
    setSession(null); setAccount(null); setProfile(null); setOptions(null);
    setScreen('login'); setMessage(notice); setError(''); setEditing(false);
  }

  function handleError(cause: unknown) {
    if (cause instanceof ApiError && cause.status === 401) clearSession('인증이 만료되었거나 사용할 수 없습니다. 다시 로그인해 주세요.');
    else setError(errorMessage(cause));
  }

  useEffect(() => {
    if (!session) return;
    const timeout = window.setTimeout(() => clearSession('로그인 유효시간이 지났습니다. 다시 로그인해 주세요.'), Math.max(0, session.expiresAt - Date.now()));
    return () => window.clearTimeout(timeout);
  }, [session]);

  useEffect(() => {
    if (!session) return;
    const controller = new AbortController();
    setLoading(true);
    // 초기 조회를 묶되 화면 해제·로그아웃 후 이전 요청이 새 상태를 덮어쓰지 못하게 한다.
    void Promise.all([
      request<Account>('/users/me', { signal: controller.signal }, session.token),
      request<Profile>('/users/me/workout-profile', { signal: controller.signal }, session.token),
      request<Options>('/users/me/workout-profile/options', { signal: controller.signal }, session.token),
    ]).then(([me, workout, choices]) => {
      if (controller.signal.aborted) return;
      setAccount(me); setProfile(workout); setOptions(choices); setEditing(!workout.configured); setError('');
    }).catch(cause => { if (!controller.signal.aborted) handleError(cause); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [session, reload]);

  async function authenticate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setError(''); setMessage('');
    const data = new FormData(event.currentTarget);
    const email = String(data.get('email'));
    const password = String(data.get('password'));
    const nickname = String(data.get('nickname') ?? '');
    // 형식은 서버의 Bean Validation을 기준으로 하며 Unicode 이메일을 별도 정규식으로 제한하지 않는다.
    if (!email.trim()) { setError('이메일을 입력해 주세요.'); return; }
    if ([...email].length > 254) { setError('이메일은 254자 이하여야 합니다.'); return; }
    // HTML maxlength는 UTF-16 단위이므로 승인된 Unicode 문자 수는 별도로 검사한다.
    if ([...password].length < 8 || [...password].length > 72) { setError('비밀번호는 8~72자입니다.'); return; }
    if (screen === 'signup' && ([...nickname].length < 2 || [...nickname].length > 20)) { setError('닉네임은 2~20자입니다.'); return; }
    setBusy(true);
    try {
      if (screen === 'signup') {
        await request<Account>('/auth/signup', { method: 'POST', body: JSON.stringify({ email, password, nickname }) });
        setScreen('login'); setMessage('회원가입이 완료되었습니다. 이메일과 비밀번호로 로그인해 주세요.');
      } else {
        const token = await request<{ accessToken: string; expiresIn: number }>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
        setSession({ token: token.accessToken, expiresAt: Date.now() + token.expiresIn * 1000 });
        setMessage('로그인되었습니다. 나에게 맞는 운동 프로필을 확인해 주세요.');
      }
    } catch (cause) { setError(errorMessage(cause)); }
    finally { setBusy(false); }
  }

  async function saveProfile(fields: Fields) {
    if (!session || !profile) return;
    // 이미 설정한 프로필은 바뀐 필드만 보내며, 누락 항목은 서버에서 유지한다.
    const patch: Partial<Fields> = {};
    if (fields.goal !== profile.goal) patch.goal = fields.goal;
    if (fields.experienceLevel !== profile.experienceLevel) patch.experienceLevel = fields.experienceLevel;
    for (const name of ['preferredTypes', 'equipment'] as const) {
      if (JSON.stringify([...fields[name]].sort()) !== JSON.stringify([...(profile[name] ?? [])].sort())) patch[name] = fields[name];
    }
    if (Object.keys(patch).length === 0) { setMessage('변경한 항목이 없습니다.'); return; }
    setBusy(true); setError(''); setMessage('');
    try {
      const saved = await request<Profile>('/users/me/workout-profile', { method: 'PATCH', body: JSON.stringify(patch) }, session.token);
      // 만료 후 늦게 도착한 응답으로 인증 화면을 되돌리지 않게 한다.
      if (Date.now() >= session.expiresAt) { clearSession('다시 로그인하여 저장 결과를 확인해 주세요.'); return; }
      setProfile(saved); setEditing(false); setMessage('운동 프로필을 저장했습니다.');
    } catch (cause) { handleError(cause); }
    finally { setBusy(false); }
  }

  async function logout() {
    if (!session) return;
    setBusy(true); setError(''); setMessage('');
    try {
      await request<void>('/auth/logout', { method: 'POST' }, session.token);
      clearSession('로그아웃되었습니다.');
    } catch (cause) {
      // 서버 무효화 성공을 확인하기 전에는 토큰을 지우거나 로그아웃 성공으로 안내하지 않는다.
      if (cause instanceof ApiError && cause.status === 401) handleError(cause);
      else setError(`로그아웃에 실패했습니다. ${errorMessage(cause)}`);
    } finally { setBusy(false); }
  }

  const label = (values: Option[], value: string | null) => values.find(item => item.value === value)?.label ?? '미설정';
  return <main>
    <header><span className="brand">AI LOVE WORKOUT</span><span className="tag">러닝 + 맨몸운동</span></header>
    <section className="intro"><p className="eyebrow">YOUR NEXT WORKOUT STARTS HERE</p><h1>나에게 맞는 운동,<br />프로필에서 시작해요.</h1><p className="description">운동 목표와 경험을 알려주세요.<br />오늘의 출발점을 함께 준비합니다.</p></section>
    {message && <p role="status" aria-live="polite" className="notice">{message}</p>}
    {error && <p role="alert" className="error">{error}</p>}
    {!session ? <section className="panel" aria-labelledby="auth-title">
      <h2 id="auth-title">{screen === 'login' ? '로그인' : '회원가입'}</h2>
      <form key={screen} onSubmit={event => void authenticate(event)}>
        <label>이메일<input name="email" type="text" inputMode="email" autoComplete="email" required disabled={busy} /></label>
        <label>비밀번호<input name="password" type="password" autoComplete={screen === 'signup' ? 'new-password' : 'current-password'} required disabled={busy} /><small>8~72자 · 문자 조합 제한 없음</small></label>
        {screen === 'signup' && <label>닉네임<input name="nickname" autoComplete="nickname" required disabled={busy} /><small>2~20자 · 중복 사용 가능</small></label>}
        <button disabled={busy} type="submit">{busy ? '처리 중…' : screen === 'login' ? '로그인' : '회원가입'}</button>
      </form>
      <button className="secondary" disabled={busy} onClick={() => { setScreen(screen === 'login' ? 'signup' : 'login'); setError(''); setMessage(''); }}>{screen === 'login' ? '처음 오셨나요? 회원가입' : '이미 계정이 있나요? 로그인'}</button>
      <p className="note">로그인은 30분 동안 유지되며 새로고침하면 다시 로그인해야 합니다.</p>
    </section> : <section className="panel" aria-labelledby="profile-title">
      <div className="panel-heading"><h2 id="profile-title">운동 프로필</h2><button className="secondary" disabled={busy} onClick={() => void logout()}>로그아웃</button></div>
      {account && <p className="account">{account.nickname} 님 · {account.email}</p>}
      {loading && <p role="status">프로필을 불러오고 있습니다.</p>}
      {!loading && (!profile || !options || !account) && <button disabled={busy} onClick={() => setReload(value => value + 1)}>프로필 다시 불러오기</button>}
      {!loading && account && profile && options && (editing
        ? <ProfileForm profile={profile} options={options} busy={busy} onSave={saveProfile} onCancel={() => setEditing(false)} />
        : <><dl>
          <div><dt>운동 목표</dt><dd>{label(options.goals, profile.goal)}</dd></div>
          <div><dt>운동 경험</dt><dd>{label(options.experienceLevels, profile.experienceLevel)}</dd></div>
          <div><dt>주요 운동 유형</dt><dd>{profile.preferredTypes?.map(value => label(options.preferredTypes, value)).join(', ')}</dd></div>
          <div><dt>사용 가능한 장비</dt><dd>{profile.equipment?.map(value => label(options.equipment, value)).join(', ')}</dd></div>
        </dl><button disabled={busy} onClick={() => { setEditing(true); setMessage(''); }}>프로필 수정</button></>)}
    </section>}
    <HealthPanel />
    <footer>사용자가 확인한 기록에서 시작하는 운동 계획</footer>
  </main>;
}

/** 서버의 허용값으로 폼을 만들고 장비 없음 선택은 UI와 서버 양쪽에서 검증한다. */
function ProfileForm({ profile, options, busy, onSave, onCancel }: { profile: Profile; options: Options; busy: boolean; onSave: (fields: Fields) => Promise<void>; onCancel: () => void }) {
  const [fields, setFields] = useState<Fields>({ goal: profile.goal ?? '', experienceLevel: profile.experienceLevel ?? '', preferredTypes: profile.preferredTypes ?? [], equipment: profile.equipment ?? [] });
  const [validation, setValidation] = useState('');
  function toggle(name: 'preferredTypes' | 'equipment', value: string, checked: boolean) {
    setFields(previous => {
      let values = checked ? [...previous[name], value] : previous[name].filter(item => item !== value);
      if (name === 'equipment' && checked) values = value === 'NONE' ? ['NONE'] : values.filter(item => item !== 'NONE');
      return { ...previous, [name]: values };
    });
  }
  function submit(event: FormEvent) {
    event.preventDefault();
    if (!fields.goal || !fields.experienceLevel || fields.preferredTypes.length === 0 || fields.equipment.length === 0) { setValidation('목표·경험·운동 유형·장비를 모두 선택해 주세요.'); return; }
    setValidation(''); void onSave(fields);
  }
  return <form onSubmit={submit}>
    <p>{profile.configured ? '변경할 항목을 수정해 주세요.' : '첫 운동 프로필을 설정해 주세요.'}</p>
    {validation && <p role="alert" className="error">{validation}</p>}
    <label>운동 목표<select value={fields.goal} required disabled={busy} onChange={event => setFields({ ...fields, goal: event.target.value })}><option value="">선택해 주세요</option>{options.goals.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
    <label>운동 경험<select value={fields.experienceLevel} required disabled={busy} onChange={event => setFields({ ...fields, experienceLevel: event.target.value })}><option value="">선택해 주세요</option>{options.experienceLevels.map(item => <option key={item.value} value={item.value}>{item.label}</option>)}</select></label>
    {(['preferredTypes', 'equipment'] as const).map(name => <fieldset disabled={busy} key={name}><legend>{name === 'preferredTypes' ? '주요 운동 유형 (하나 이상)' : '사용 가능한 장비 (하나 이상)'}</legend><div className="choices">{options[name].map(item => <label className="choice" key={item.value}><input type="checkbox" checked={fields[name].includes(item.value)} onChange={event => toggle(name, item.value, event.target.checked)} />{item.label}</label>)}</div></fieldset>)}
    <div className="actions"><button type="submit" disabled={busy}>{busy ? '저장 중…' : '프로필 저장'}</button>{profile.configured && <button type="button" className="secondary" disabled={busy} onClick={onCancel}>취소</button>}</div>
  </form>;
}

/** 프로세스 응답과 DB·AI 의존성 준비 상태를 사용자 기능 검증과 구별한다. */
function HealthPanel() {
  const [health, setHealth] = useState<{ status: string; database: string; aiService: string } | null>(null);
  const [loading, setLoading] = useState(false);
  const [notice, setNotice] = useState('연결 상태를 확인해 주세요.');
  async function check() {
    setLoading(true);
    try {
      const result = await fetch('/api/v1/health/dependencies', { cache: 'no-store', credentials: 'omit' });
      const data = await result.json() as { status: string; database: string; aiService: string };
      if (![data.status, data.database, data.aiService].every(value => ['UP', 'DOWN'].includes(value))) throw new Error();
      setHealth(data); setNotice(result.ok ? '서비스가 연결되었습니다.' : '일부 서비스가 준비되지 않았습니다.');
    } catch { setHealth(null); setNotice('서버 연결을 확인해 주세요.'); }
    finally { setLoading(false); }
  }
  return <details className="panel health"><summary>서비스 연결 상태</summary><p role="status">{notice}</p>{health && <dl><div><dt>데이터베이스</dt><dd>{health.database}</dd></div><div><dt>AI 서비스</dt><dd>{health.aiService}</dd></div></dl>}<button className="secondary" disabled={loading} onClick={() => void check()}>{loading ? '확인 중…' : '연결 확인'}</button></details>;
}
