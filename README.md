# room-reservation-saga

Saga Pattern, 보상 트랜잭션, 멱등성, 장애 복구를 연습하기 위한 Spring Boot 멀티 모듈 실습 프로젝트입니다.

실제 회사 시스템에서 내부 서비스가 외부 예약 API를 호출하고, 중간 실패나 응답 유실이 발생했을 때 어떻게 상태를 기록하고 복구할지 학습하는 것을 목표로 합니다.

자세한 요구사항은 [docs/prd.md](docs/prd.md)를 기준으로 관리합니다.

## 목표

- Saga Pattern 기반 예약 변경 흐름 구현
- Compensation Transaction 처리
- Idempotency-Key 기반 중복 요청 방지
- 외부 API 장애, 응답 유실, 재시도 시나리오 실습
- 상태 머신 기반 비즈니스 처리
- 관심사 단위 패키징으로 Saga 흐름을 읽기 쉽게 구성

## 아키텍처 방향

처음에는 `inner-system`을 Hexagonal Architecture로 구성하려 했지만, 이 프로젝트에서는 전면적인 Port/Adapter 분리보다 관심사 단위의 단순한 레이어드 구조를 사용합니다.

참고한 글: [Hexagonal Architecture, 진짜 하실 건가요?](https://tech.kakaopay.com/post/home-hexagonal-architecture/)

이 프로젝트의 핵심은 복잡한 아키텍처 구현 자체가 아니라, 외부 API 호출 중 실패가 발생했을 때 Saga 상태와 멱등성 키로 안전하게 복구하는 흐름입니다. 따라서 기능 단위 패키지 아래에 `web`, `service`, `infra`를 두어 하나의 예약 흐름을 빠르게 추적할 수 있게 합니다.

예상 패키지 구조:

```text
home.example.room_reserve_inner
├── reservation
│   ├── web
│   ├── service
│   └── infra
├── saga
│   ├── service
│   └── infra
└── common
```

패키지 역할:

- `web`: HTTP 요청/응답, 컨트롤러, 요청 DTO
- `service`: 유스케이스, 트랜잭션 경계, Saga 진행/복구 흐름
- `infra`: 외부 예약 API 클라이언트, DB 엔티티/리포지토리, 장애 시뮬레이션 연동
- `common`: 여러 관심사에서 공유하는 예외, 설정, 공통 타입

## 모듈 구조

```text
room-reservation-saga
├── inner-system
│   └── 내부 예약 API, Saga 흐름, Outer System 호출
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
- Outer System 호출

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
