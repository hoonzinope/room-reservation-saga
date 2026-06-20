# Room Reservation Saga

## 목표

본 프로젝트는 다음 개념을 학습하기 위한 실습 프로젝트이다.

* Hexagonal Architecture
* Saga Pattern
* Compensation Transaction (보상 트랜잭션)
* Idempotency (멱등성)
* 외부 API 연동
* 장애 복구 및 재시도
* 상태 머신 기반 비즈니스 처리

실제 회사 프로젝트에서 여러 단계의 외부 API 호출을 수행하며 안전성을 보장해야 하는 상황을 가정한다.

---

# 전체 구조

```text
room-reservation-saga
│
├── inner-system
│
└── outer-system
```

## Outer System

외부 예약 시스템 역할.

실제 호텔 예약 시스템 또는 외부 공급사 API를 단순화한 형태.

### 기술 스택

* Spring Boot
* H2 In-Memory DB

### 기능

```text
예약 생성
예약 취소
예약 조회
방 상태 조회
```

### API

```http
GET    /rooms/{roomId}/availability

POST   /reservations

GET    /reservations/{reservationId}

DELETE /reservations/{reservationId}
```

---

## Inner System

실제 비즈니스 로직을 수행하는 시스템.

Hexagonal Architecture의 적용 대상.

### 기술 스택

* Spring Boot
* H2 In-Memory DB

### 기능

```text
방 예약

방 변경

방 취소
```

---

# 프로젝트 목표

## 방 예약

```text
1. 방 가능 여부 확인
2. 예약 생성
3. 예약 완료
```

---

## 방 변경

```text
1. 새 방 확인
2. 새 예약 생성
3. 기존 예약 취소
4. 변경 완료
```

### 핵심 시나리오

```text
새 예약 생성 성공

↓

기존 예약 취소 실패
```

이 상황을 어떻게 처리할지 실습한다.

---

## 방 취소

```text
1. 예약 존재 확인
2. 예약 취소
3. 취소 완료
```

---

# Hexagonal Architecture

## 패키지 구조

```text
inner-system

com.example.inner

├── domain
│
├── application
│
├── adapter
│   ├── in
│   └── out
│
└── config
```

---

## Domain

비즈니스 규칙.

```text
Reservation

ReservationStatus

ReservationPolicy
```

Spring Framework에 의존하지 않는다.

---

## Application

유스케이스.

```text
ReserveRoomUseCase

ChangeRoomUseCase

CancelRoomUseCase
```

---

## Inbound Adapter

외부 요청 진입점.

```text
REST Controller

Scheduler

Message Consumer
```

---

## Outbound Adapter

외부 자원 연결.

```text
Outer Reservation API

Persistence

Message Queue
```

---

# Saga Pattern

예약 변경은 하나의 Saga로 관리한다.

```text
확인

↓

생성

↓

취소

↓

완료
```

각 단계는 DB에 저장된다.

---

## Saga 상태 예시

```text
PENDING

CHECKING_ROOM

CREATING_RESERVATION

CANCELLING_OLD_RESERVATION

COMPLETED

FAILED

MANUAL_REVIEW
```

---

# 보상 트랜잭션

예시:

```text
새 예약 생성 성공

↓

기존 예약 취소 실패
```

선택 가능한 전략

### 전략 A

```text
기존 예약 취소 재시도
```

### 전략 B

```text
새 예약 취소
```

학습 목적으로 두 전략 모두 구현 가능하도록 설계한다.

---

# 멱등성

외부 API는 멱등성 키를 지원한다.

## 예약 생성

```http
POST /reservations

Idempotency-Key:
3f74d8d4-a0f5-42c4
```

---

## 예약 취소

```http
DELETE /reservations/{id}

Idempotency-Key:
c95fca2d-f9d2-4562
```

---

## 예약 조회

```http
GET /reservations/{id}
```

조회는 원래 멱등적이므로 키를 사용하지 않는다.

---

# Inner DB 설계

## reservation_saga

```text
id

saga_type

business_key

status

current_step

retry_count

last_error

created_at

updated_at
```

---

## reservation_saga_step

```text
id

saga_id

step_name

status

idempotency_key

request_payload

response_payload

retry_count

error_message

created_at

updated_at
```

---

# 장애 시뮬레이션

Outer System은 랜덤 장애를 발생시킨다.

```yaml
reservation:
  failure-rate: 0.2
```

20% 확률로

```text
500 Error

Timeout

Connection Reset
```

발생.

---

# 검증 목표

## 1차 목표

정상 플로우

```text
예약 생성

예약 변경

예약 취소
```

---

## 2차 목표

장애 대응

```text
Timeout

500 Error

중복 요청

응답 유실
```

---

## 3차 목표

복구

```text
재시도

보상 트랜잭션

Saga 재실행

중단 지점부터 재개
```

---

# 학습 포인트

본 프로젝트의 핵심은 예약 시스템이 아니다.

핵심은 다음과 같다.

```text
Hexagonal Architecture

+
Saga Pattern

+
Compensation Transaction

+
Idempotency

+
외부 API 장애 대응
```

정상 케이스 구현보다

"중간에 실패했을 때 어떻게 복구할 것인가"

를 학습하는 것이 최종 목표이다.
