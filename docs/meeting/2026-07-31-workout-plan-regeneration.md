# 운동 계획 후보 재생성 요구사항 작성 회의

## 문서 정보

* 프로젝트: AI Love Workout
* 문서: Meeting
* 작성일: 2026-07-31

---

# 오늘 논의할 내용

* 휴가 전 작업 상태 확인
* FR-PLAN-008 운동 계획 후보 재생성 작성
* 기존 기능 요구사항과의 충돌 검토
* 현재 후보와 이전 후보의 정책 확인

---

# 주요 논의 사항

* FR-PLAN-008 운동 계획 후보 재생성 정책 검토
* Current Candidate와 Previous Candidate의 역할 정의
* 재생성과 수정의 책임 구분
* 재생성 성공 시 기존 후보 보존 정책
* FR-PLAN-005, FR-PLAN-006과 정책 충돌 여부 검토
* 기능 요구사항 간 Related FR 정리
* Codex 최종 감사 결과 검토

---

# 결정 사항

* Current와 Previous는 상태가 아니라 역할(Role)로 정의한다.
* 재생성은 기존 후보를 수정하지 않고 새로운 후보를 생성한다.
* 재생성 성공 시 기존 Current Candidate는 Previous Candidate로 전환한다.
* Previous Candidate는 수정, 확정, 거절, 재생성할 수 없다.
* FR-PLAN-005는 현재 후보만 확정 가능하도록 수정한다.
* FR-PLAN-006은 현재 후보만 수정 가능하도록 수정한다.
* PLAN 도메인 기능 요구사항을 최종 확정한다.

---

# 보류 사항

* 운동 계획 후보 생성에 사용할 최근 운동 기록 기간
* 추천 이유를 확정 운동 계획에 보존할지 여부
* 운동 계획 수정 가능 항목의 세부 범위

---

# 영향받는 문서

* functional-requirements.md
* ADR-003-workout-plan-lifecycle.md
* ADR-004-workout-plan-candidate-policy.md

---

# 다음 작업

* PLAN 도메인 Commit 및 Push
* FR-SESSION 기능 요구사항 작성
* 운동 세션 도메인 정책 정의