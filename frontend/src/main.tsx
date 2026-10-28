import { StrictMode, useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

type Health = { status: string; database: string; aiService: string };

/** 개발 환경의 연결 상태를 표시하며 운동 기록·추천 기능의 완료 상태와 구분한다. */
function App() {
  const [health, setHealth] = useState<Health | null>(null);
  const [message, setMessage] = useState('서비스 연결을 확인하고 있습니다.');
  const [loading, setLoading] = useState(true);

  async function checkHealth(signal?: AbortSignal) {
    setLoading(true);
    try {
      // 같은 출처의 /api 프록시로 Spring Boot에만 요청하며 FastAPI를 직접 호출하지 않는다.
      const response = await fetch('/api/v1/health/dependencies', { signal });
      const data = (await response.json()) as Health;
      // TypeScript의 타입 단언은 실제 응답을 검증하지 않으므로 상태 값도 확인한다.
      if (!['UP', 'DOWN'].includes(data.status) || !['UP', 'DOWN'].includes(data.database)
        || !['UP', 'DOWN'].includes(data.aiService)) throw new Error('Unexpected health response');
      setHealth(data);
      setMessage(response.ok && data.status === 'UP'
        ? '기록을 연결할 준비가 되었습니다.' : '일부 서비스가 준비되지 않았습니다.');
    } catch (error) {
      // Effect 정리로 취소된 요청은 연결 장애로 표시하거나 최신 상태를 덮어쓰지 않는다.
      if (signal?.aborted) return;
      setHealth(null);
      setMessage('서버에 연결할 수 없습니다. 개발 환경 실행 상태를 확인해 주세요.');
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }

  useEffect(() => {
    // 화면 해제 또는 StrictMode의 Effect 재실행 시 이전 요청의 상태 갱신을 막는다.
    const controller = new AbortController();
    void checkHealth(controller.signal);
    return () => controller.abort();
  }, []);

  const label = (status?: string) => status === 'UP' ? '연결됨' : status === 'DOWN' ? '연결 대기' : '확인 필요';

  return (
    <main>
      <header><span className="brand">AI LOVE WORKOUT</span><span className="tag">개발 중 · 1차 MVP</span></header>
      <section className="intro">
        <p className="eyebrow">RUNNING + CALISTHENICS</p>
        <h1>기록하면,<br />다음 운동이 보인다.</h1>
        <p className="description">오늘의 러닝과 맨몸운동을 한곳에.<br />직접 확인한 기록으로 다음 운동을 준비합니다.</p>
      </section>
      <section className="panel" aria-labelledby="connection-title">
        <div className="panel-heading"><h2 id="connection-title">개발 환경 연결</h2><button disabled={loading} onClick={() => void checkHealth()}>{loading ? '확인 중…' : '다시 확인'}</button></div>
        <p role="status" aria-live="polite">{message}</p>
        <dl>
          <div><dt>기록 API</dt><dd>{health ? '연결됨' : '확인 필요'}</dd></div>
          <div><dt>데이터베이스</dt><dd>{label(health?.database)}</dd></div>
          <div><dt>AI 서비스</dt><dd>{label(health?.aiService)}</dd></div>
        </dl>
        <p className="note">현재는 실행 환경을 확인하는 단계입니다. 회원가입·운동 기록·추천 기능은 다음 단계에서 구현합니다.</p>
      </section>
      <footer>사용자가 확인한 기록에서 시작하는 운동 계획</footer>
    </main>
  );
}

createRoot(document.getElementById('root')!).render(<StrictMode><App /></StrictMode>);
