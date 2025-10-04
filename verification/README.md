# 💳 verification-service

MSA 환경에서 **결제 내역 검증**을 담당하는 마이크로서비스입니다.

---

## 🚀 주요 기술 스택
- **Spring Boot 3.4**
- **WebFlux + R2DBC** – 비동기 논블로킹 처리
- **RabbitMQ (Spring Cloud Stream)** – 이벤트 기반 아키텍처
- **PortOne 결제 검증 API 연동**

---

## 🧩 주요 기능
- RabbitMQ 기반 **결제 검증 요청 메시지 수신**
- PortOne API를 통한 **결제 내역 검증 처리**
- Reactor 기반 **비동기 검증 로직**
- 검증 결과 메시지를 **RabbitMQ 이벤트로 송신**

---