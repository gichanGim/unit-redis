# unit-redis

> Java로 밑바닥부터 직접 만들어보는 인메모리 데이터베이스 — CS 지식 자가학습 프로젝트

실제 `redis-cli`로 접속 가능한 RESP 호환 TCP 서버를 목표로, 해시 테이블부터 프로토콜 파서, 동시성 모델, 메모리 관리, 영속화까지 한 겹씩 직접 구현합니다. 라이브러리로 때우지 않고 "왜 이렇게 동작하는가"를 실측하며 배우는 것이 목적입니다.

## 목표

- **자료구조** — 해시 테이블(리사이징/rehash), 이중 연결 리스트, skip list, rehash 중에도 안전한 커서 순회(SCAN)를 직접 구현
- **네트워크** — TCP 소켓, 바이트 스트림, RESP 프로토콜 파싱과 견고성(에러 응답, 부분 읽기, 입력 크기 제한) ([공식 스펙](https://redis.io/docs/latest/develop/reference/protocol-spec/))
- **동시성** — 가상 스레드(JEP 444), JMM, 세 가지 동시성 모델(전역 락 / ConcurrentHashMap / 단일 스레드 + 커맨드 큐) 비교
- **메모리** — 객체 레이아웃(JOL 실측), GC 동작 관찰, off-heap 버퍼, eviction(근사 LRU), OS 가상 메모리
- **영속화** — 스냅샷(RDB식)과 fork 없는 일관된 스냅샷 설계, append-only 로그(AOF식), fsync와 내구성

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
| 1 | 코어 key-value 엔진 (GET/SET/DEL/EXISTS/INCR/DECR) | 해시 테이블 직접 구현, 해시 플러딩 DoS, Java 객체 레이아웃 |
| 2 | RESP 파서 + TCP 서버 (에러 처리, 임시 전역 락) | 프로토콜 설계·견고성, 가상 스레드, 버퍼 관리 |
| 3 | TTL / 만료 — lazy expiration | 시계 선택, 만료 시각의 저장 표현, GC 로그 |
| 4 | 동시성 모델 3종 비교 + active expiration + 파이프라이닝 | JMM, 싱글 스레드 아키텍처, redis-benchmark 실측 |
| 5 | 컬렉션 (List/Hash/Set/Sorted Set) + SCAN | skip list, 캐시 지역성, encoding, rehash 중 순회 |
| 6 | maxmemory + eviction | 근사 LRU, GC 튜닝, 메모리 단편화 |
| 7 | 영속화 (RDB/AOF) | fsync, WAL, fork 없는 일관된 스냅샷 설계 |
| 8 | (선택) MULTI/EXEC · Pub/Sub · BLPOP · 복제 | 트랜잭션, push 프로토콜, 분산 시스템 입문 |

Phase 2의 connection-per-thread 서버는 스레드-안전하지 않은 엔진을 동시에 접근하므로, 동시성 모델이 확정되는 Phase 4까지는 전역 락으로 명령 실행을 직렬화합니다. 이 락은 Phase 4 벤치마크의 베이스라인이 됩니다. active expiration은 실제 Redis처럼 이벤트 루프 안의 주기 작업으로 구현하기 위해 Phase 4에서 다룹니다.

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
4. **실제 Redis를 오라클로 쓴다** — 같은 명령 시퀀스를 실제 Redis와 unit-redis에 날려 응답을 diff하는 차분 테스트로 스펙 오독을 잡는다

## 참고 문서

- [Redis serialization protocol (RESP) spec](https://redis.io/docs/latest/develop/reference/protocol-spec/)
- [Redis commands reference](https://redis.io/docs/latest/commands/)
- [EXPIRE — 만료 동작과 복제 전파](https://redis.io/docs/latest/commands/expire/)
- [SCAN — 반복 보장](https://redis.io/docs/latest/commands/scan/)
- [Redis client handling — 버퍼 한도](https://redis.io/docs/latest/develop/reference/clients/)
- [Redis pipelining](https://redis.io/docs/latest/develop/use/pipelining/)
- [Redis persistence](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)
- [Redis key eviction](https://redis.io/docs/latest/develop/reference/eviction/)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444)
- [JEP 491: Synchronize Virtual Threads without Pinning](https://openjdk.org/jeps/491)
- [JEP 180: Handle Frequent HashMap Collisions with Balanced Trees](https://openjdk.org/jeps/180)
- [Oracle Java 21 GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)
- [JOL (Java Object Layout)](https://openjdk.org/projects/code-tools/jol/)
