# 💳 payment-service

Spring WebFlux 기반의 **비동기 결제 처리 서비스**입니다.  
결제 요청을 `PENDING` 상태로 저장하고, 결제 검증 및 상태 전환, 취소 처리까지 담당합니다.

[![codecov](https://codecov.io/gh/Team-Study-Bridge/payment-service/branch/develop/graph/badge.svg)](https://app.codecov.io/gh/Team-Study-Bridge/payment-service/branch/develop)

---

## 🚀 주요 기술 스택
- **Spring Boot 3.4**
- **WebFlux + R2DBC** – 논블로킹 비동기 처리
- **RabbitMQ (Spring Cloud Stream)** – 이벤트 기반 결제 흐름
- **PortOne 결제/환불 API 연동**
- **JaCoCo + GitHub Actions + Codecov** – 테스트 커버리지 자동화

---

## 🧩 주요 기능
- 사용자의 결제 요청 저장 (초기 `PENDING` 상태)
- 결제 검증 요청 이벤트 발행 및 외부 검증 API 연동
- 검증 완료 후 `COMPLETED`, 실패 시 `FAILED` 상태 전환
- 사용자가 본인의 결제 내역을 조회할 수 있는 API 제공 (`/pay/read`)
- 사용자가 자신의 결제 내역에 대해 직접 결제 취소 요청 가능 (`/pay/cancel`)
- 결제 취소 시 상태를 `CANCELED`로 변경하고 환불 요청 수행
- 결제 검증 또는 보상 실패 시 `ROLLBACK_REQUESTED` 또는 `REFUNDED` 상태 처리
- 상태 변화에 따른 SAGA 이벤트 발행 및 다른 서비스(RabbitMQ)와 비동기 연동

### ⚠ 예외 처리 및 관리 기능
- `FAILED` 상태 결제에 대해 **자동 재검증 스케줄러 실행**
- 관리자에 의한 **수동 재검증 요청 기능**
- 보상 실패 시 **자동 환불 재시도** 및 **관리자 수동 환불 요청 기능**
- 결제 취소 후 DB 상태 업데이트 실패 시 **관리자 수동 업데이트 처리 기능**

---

## ✅ 테스트 및 커버리지

- 모든 테스트는 **GitHub Actions** 기반 CI로 자동 실행됩니다.
- 테스트 완료 후, `Jacoco`가 커버리지 리포트를 생성하고 `Codecov`에 업로드됩니다.
- PR 생성 시, Codecov 봇이 커버리지 변화 분석 결과를 댓글로 제공합니다.

### ✅ 현재 테스트 대상 기능

- `PurchaseService` – 결제 저장 및 취소 처리 단위 테스트
- `PurchaseApiController` – 사용자의 결제 내역 조회 API 테스트
- Mock 기반 외부 연동 처리 (`PortOneClient`, `CancelFailureRepository`)
- 상태 전이 및 예외 흐름 테스트 (e.g. `FORBIDDEN`, `NOT_FOUND`, `BAD_REQUEST`)

### 🎯 커버리지 기준
| 항목         | 기준 (Target) | 허용 오차 (Threshold) |
|--------------|---------------|------------------------|
| 전체 커버리지 | 80% 이상      | ±1%                    |
| 변경된 코드   | 70% 이상      | ±1%                    |

📌 기준 미달 시 PR이 실패 처리되며, 커버리지 감소 시 경고가 발생합니다.
