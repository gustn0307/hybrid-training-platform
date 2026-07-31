# Sprint 0 운동 계획 후보 재생성 학습

## 오늘 진행할 작업

오늘은 휴가 이후 AI Love Workout 프로젝트 작업을 재개한다.

이전 작업 내용을 다시 확인하고, FR-PLAN-008 운동 계획 후보 재생성 기능 요구사항을 작성하고 검토한다.

---

# 오늘 학습한 내용

재생성은 기존 객체를 수정하는 것이 아니라
새로운 객체를 생성한 뒤 현재 객체를 교체하는 패턴이다.

이 패턴은 기존 객체를 보존하여
이력 관리와 실패 시 복구를 단순하게 만든다.

또한 현재(Current)와 이전(Previous)을
상태(State)가 아니라 역할(Role)로 구분하면
요구사항이 더 명확해진다.

## Current와 Previous를 명확히 구분하기

운동 계획 후보는 수정하는 객체가 아니라 시간에 따라 역할이 변경되는 객체이다.

재생성 성공 시 기존 Current Candidate는 Previous Candidate가 되고, 새롭게 생성된 Candidate가 Current Candidate가 된다.

이처럼 객체를 변경하기보다 새로운 객체를 생성하고 역할을 전환하는 방식은 이력 관리와 실패 복구를 단순하게 만들 수 있다.

---

# AI Love Workout에 적용한 내용

* FR-PLAN-008 운동 계획 후보 재생성 기능 요구사항 작성
* Current Candidate와 Previous Candidate를 역할(Role) 개념으로 정의
* 재생성 시 기존 Current Candidate는 Previous Candidate로 전환하고 새로운 Current Candidate를 생성하도록 정책 확정
* 재생성 실패 시 기존 Current Candidate를 유지하도록 정책 정의
* 재생성 성공 시 이전 후보는 수정, 확정, 거절, 재생성할 수 없도록 정책 정의

---

# Codex 리뷰에서 배운 점

* 단일 기능 요구사항이 올바르더라도 도메인 전체 정책과 충돌할 수 있으므로 통합 리뷰가 필요하다.
* Related Functional Requirements는 단순 나열보다 실제 의존 관계를 기준으로 관리하는 것이 좋다.
* 상태 전이와 권한 정책은 하나의 문장보다 각각 분리하여 작성하는 것이 더 명확하다.
* 현재(Current)와 이전(Previous) 후보를 구분함으로써 요구사항과 테스트 시나리오를 일관성 있게 작성할 수 있었다.

---

# 오늘의 한 문장

기능 요구사항은 개별 기능의 완성도보다 도메인 전체의 정책 일관성이 더 중요하다는 것을 배웠다.