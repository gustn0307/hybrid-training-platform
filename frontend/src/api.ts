/** React는 같은 출처의 Spring Boot API만 사용하며 토큰은 호출 인자로만 전달한다. */
export class ApiError extends Error {
  constructor(public status: number, message: string, public fields: Record<string, string> = {}) {
    super(message);
  }
}

export async function request<T>(path: string, init: RequestInit = {}, token?: string): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (token) headers.set('Authorization', `Bearer ${token}`);
  const response = await fetch(`/api/v1${path}`, { ...init, headers, cache: 'no-store', credentials: 'omit' });
  if (!response.ok) {
    const data = await response.json().catch(() => null) as { message?: string; fieldErrors?: Record<string, string> } | null;
    throw new ApiError(response.status, data?.message ?? '서버 요청에 실패했습니다.', data?.fieldErrors);
  }
  return response.status === 204 ? undefined as T : await response.json() as T;
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return [error.message, ...Object.values(error.fields)].join(' ');
  return '서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.';
}
