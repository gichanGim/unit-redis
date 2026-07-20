# unit-redis

> Java로 밑바닥부터 직접 만들어보는 인메모리 데이터베이스 — CS 지식 자가학습 프로젝트

실제 `redis-cli`로 접속 가능한 RESP 호환 TCP 서버를 목표로, 해시 테이블부터 프로토콜 파서, 동시성 모델, 메모리 관리, 영속화까지 한 겹씩 직접 구현합니다. 라이브러리로 때우지 않고 "왜 이렇게 동작하는가"를 실측하며 배우는 것이 목적입니다.

## 목표

- **자료구조** — 해시 테이블(리사이징/rehash), 이중 연결 리스트, skip list를 직접 구현
- **네트워크** — TCP 소켓, 바이트 스트림, RESP 프로토콜 파싱 ([공식 스펙](https://redis.io/docs/latest/develop/reference/protocol-spec/))
- **동시성** — 가상 스레드(JEP 444), JMM, 이벤트 루프 vs 스레드 풀 아키텍처 비교
- **메모리** — 객체 레이아웃(JOL 실측), GC 동작 관찰, off-heap 버퍼, eviction(근사 LRU), OS 가상 메모리
- **영속화** — 스냅샷(RDB식), append-only 로그(AOF식), fsync와 내구성

## 아키텍처

코어 엔진(임베디드 라이브러리)을 먼저 만들고, 그 위에 RESP 서버 레이어를 얹는 구조입니다. 실제 Redis와 동일한 레이어링입니다.

```
┌─────────────────────────────┐
│  RESP Server Layer          │  TCP 소켓, RESP2 파서/직렬화,
│  (redis-cli 호환)            │  connection-per-virtual-thread
├─────────────────────────────┤
│  Command Processor          │  명령 디스패치, 단일 처리 스레드 + 커맨드 큐
├─────────────────────────────┤
│  Core Engine (embeddable)   │  자료구조, TTL/만료, eviction
└─────────────────────────────┘
```

## 로드맵

| Phase | 만드는 것 | 핵심 학습 주제 |
|-------|----------|--------------|
| 1 | 코어 key-value 엔진 (GET/SET/DEL/EXISTS) | 해시 테이블 직접 구현, Java 객체 레이아웃 |
| 2 | RESP 파서 + TCP 서버 | 프로토콜 설계, 가상 스레드, 버퍼 관리 |
| 3 | TTL / 만료 (EXPIRE, TTL) | lazy vs active expiration, GC 로그 |
| 4 | 동시성 모델 실험 | JMM, 싱글 스레드 아키텍처, JMH 벤치마크 |
| 5 | 컬렉션 (List/Hash/Set/Sorted Set) | skip list, 캐시 지역성, encoding |
| 6 | maxmemory + eviction | 근사 LRU, GC 튜닝, 메모리 단편화 |
| 7 | 영속화 (RDB/AOF) | fsync, WAL, Copy-on-Write |

## 실행

```bash
./gradlew run
# 다른 터미널에서
redis-cli -p 6379 PING
```

## 환경

- Java 21+ (Virtual Threads)
- Gradle
- 외부 런타임 의존성 없음 — 자료구조와 프로토콜은 전부 직접 구현

## 원칙

1. **직접 구현한다** — `HashMap`, Netty 같은 기성품 대신 밑바닥부터 만든다 (테스트/벤치마크 도구는 예외)
2. **실측한다** — "무겁다더라"가 아니라 JOL, JFR, GC 로그로 직접 잰다
3. **공식 문서를 기준으로 한다** — Redis docs, OpenJDK JEP, Oracle Java docs

## 참고 문서

- [Redis serialization protocol (RESP) spec](https://redis.io/docs/latest/develop/reference/protocol-spec/)
- [Redis commands reference](https://redis.io/docs/latest/commands/)
- [Redis key eviction](https://redis.io/docs/latest/develop/reference/eviction/)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [Oracle Java 21 GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)
- [JOL (Java Object Layout)](https://openjdk.org/projects/code-tools/jol/)
