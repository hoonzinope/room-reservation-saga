# room-reservation-saga

Saga Pattern, 보상 트랜잭션, 멱등성, 장애 복구를 연습하기 위한 Spring Boot 멀티 모듈 실습 프로젝트입니다.

실제 회사 시스템에서 내부 서비스가 외부 예약 API를 호출하고, 중간 실패나 응답 유실이 발생했을 때 어떻게 상태를 기록하고 복구할지 학습하는 것을 목표로 합니다.

자세한 요구사항은 [docs/prd.md](docs/prd.md)를 기준으로 관리합니다.

## 목표

- Hexagonal Architecture 적용
- Saga Pattern 기반 예약 변경 흐름 구현
- Compensation Transaction 처리
- Idempotency-Key 기반 중복 요청 방지
- 외부 API 장애, 응답 유실, 재시도 시나리오 실습
- 상태 머신 기반 비즈니스 처리

## 모듈 구조

```text
room-reservation-saga
├── inner-system
│   └── 실제 비즈니스 로직과 Saga 흐름을 담당하는 내부 시스템
├── outer-system
│   └── 외부 호텔/공급사 예약 API를 단순화한 외부 시스템
├── docs
│   └── 프로젝트 목표와 설계 문서
├── build.gradle
└── settings.gradle
```

## 기술 스택

- Java 8
- Spring Boot 2.7.5
- Gradle 멀티 프로젝트
- Spring Web
- Spring Data JPA / JDBC
- H2 In-Memory DB
- Lombok

## 실행 방법

전체 빌드:

```bash
./gradlew build
```

Inner System 실행:

```bash
./gradlew :inner-system:bootRun
```

Outer System 실행:

```bash
./gradlew :outer-system:bootRun
```

기본 포트:

```text
inner-system: 8080
outer-system: 8081
```

## Outer System

외부 예약 시스템 역할을 하는 모듈입니다.

현재 H2 In-Memory DB는 `schema.sql`, `data.sql`로 초기화합니다.

```text
outer-system/src/main/resources/schema.sql
outer-system/src/main/resources/data.sql
```

H2 Console:

```text
URL: http://localhost:8081/h2-console
JDBC URL: jdbc:h2:mem:outer_system
User Name: sa
Password:
```

현재 스키마:

- `rooms`: 방 정보
- `reservations`: 예약 정보
- `idempotency_records`: 멱등성 키 처리 기록

초기 방 데이터:

```text
101 STANDARD AVAILABLE
102 STANDARD AVAILABLE
201 DELUXE   AVAILABLE
202 DELUXE   MAINTENANCE
301 SUITE    AVAILABLE
```

PRD 기준 목표 API는 다음 형태입니다.

```http
GET    /rooms/{roomId}/availability
POST   /reservations
GET    /reservations/{reservationId}
DELETE /reservations/{reservationId}
```

## Inner System

내부 비즈니스 로직과 Saga 흐름을 구현할 모듈입니다.

주요 구현 대상:

- 방 예약
- 방 변경
- 방 취소
- Saga 상태 저장
- 실패 지점부터 재개
- 보상 트랜잭션
- Outer System 호출 어댑터

## 핵심 시나리오

방 변경은 하나의 Saga로 관리합니다.

```text
1. 새 방 가능 여부 확인
2. 새 예약 생성
3. 기존 예약 취소
4. 변경 완료
```

중점적으로 다룰 실패 상황:

```text
새 예약 생성 성공
기존 예약 취소 실패
```

이 경우 기존 예약 취소를 재시도할지, 새 예약을 취소하는 보상 트랜잭션을 수행할지 비교하며 구현합니다.
