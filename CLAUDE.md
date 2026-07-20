# CLAUDE.md — 단위 레디스 (unit-redis)

> 이 문서는 Claude가 이 저장소에서 작업할 때 항상 먼저 읽는 프로젝트 설명서다.

## 프로젝트 개요

Java로 인메모리 데이터베이스(미니 Redis)를 밑바닥부터 직접 구현하며 CS 지식을 자가학습하는 **1인 학습 프로젝트**.
목표는 완성도 높은 제품이 아니라, 구현 과정에서 자료구조·네트워크·동시성·메모리·영속화를 몸으로 이해하는 것.

- **학습 트랙**: 자료구조 · 네트워크 · 동시성 · **메모리(전 단계 관통)** · 영속화
- **환경**: Java 21+ · Gradle · 패키지 `unitredis` · 외부 라이브러리 최소화(JOL/JMH 등 측정 도구 제외)
- **빌드/테스트**: `./gradlew build` (컴파일 + 테스트)

## Claude의 역할 (중요)

1. **코드는 학습자가 직접 작성하는 것이 원칙.** Claude는 방향 제시 · 개념 설명 · 코드 리뷰를 맡는다. 명시적으로 요청받지 않는 한 구현 코드를 통째로 대신 작성하지 않는다.
2. **답변은 공식 문서 기반.** Redis docs · OpenJDK JEP · Oracle Java docs를 근거로 답하고 출처 링크를 명시한다. (하단 링크 목록 참조)
3. **스펙이 애매하면 실제 Redis가 정답.** 실제 Redis를 오라클(정답지)로 쓰는 차분 테스트로 스펙 오독을 잡는다.

## 아키텍처

실제 Redis와 동일한 레이어 구조. **코어 엔진을 먼저 만들고, 그 위에 프로토콜 서버 레이어를 얹는다.**

```
[클라이언트 (redis-cli)]
        │ RESP2 over TCP
[서버 레이어]  ─ TCP 수신 · RESP 파싱 · 에러 응답 · 커넥션 관리
        │
[코어 엔진]    ─ 자료구조(해시 테이블 등) · 명령 처리 · TTL · eviction · 영속화
```

- 코어 엔진은 네트워크를 모른다 (Phase 1은 순수 인메모리 저장소 + 단위 테스트)
- 저장소는 인터페이스로 추상화 — Phase 4에서 동시성 모델별 구현 교체 대비

## 로드맵 요약 (상세는 Claude 프로젝트의 `claude/roadmap.md`)

| Phase | 만드는 것 | 상태 |
| --- | --- | --- |
| 1 | 코어 KV 엔진: GET/SET/DEL/EXISTS + INCR/DECR, 해시 테이블 직접 구현 | **진행 중** |
| 2 | RESP2 파서 + TCP 서버 (가상 스레드, 임시 전역 락, 프로토콜 견고성) | |
| 3 | TTL/만료 — lazy expiration까지 (만료 시각은 절대 Unix ms 저장) | |
| 4 | 동시성 3모델 비교(전역 락/CHM/단일 스레드+큐) + active expiration + 파이프라이닝 | |
| 5 | 컬렉션(List/Hash/Set/Sorted Set) + SCAN | |
| 6 | maxmemory + eviction (근사 LRU) — 메모리 트랙 집중 | |
| 7 | 영속화: RDB식 스냅샷 → AOF (fork 없는 일관된 스냅샷 설계) | |
| 8 | (선택) MULTI/EXEC → Pub/Sub → BLPOP → 복제 | |

각 Phase에는 **자가 점검 체크리스트**가 있다 — Phase 완료 판단 기준은 코드가 아니라 자가 점검 통과.

## 저장소 구조

```
src/main/java/unitredis/   구현 코드
src/test/java/unitredis/   단위 테스트 · (Phase 2+) 오라클 차분 테스트
docs/git-convention.md     Git 컨벤션 (커밋·브랜치·PR 규칙)
.github/                   이슈/PR 템플릿
```

## 작업 규칙

- **커밋·브랜치·PR은 `docs/git-convention.md`를 따른다.** 요점: Angular 형식 커밋(`type(scope): subject`) · main 단일 브랜치(소규모는 main 직접 push, Phase 단위만 `feat/*` 브랜치 + PR) · 커밋 메시지에 `Co-Authored-By` 금지 · 모든 커밋은 `./gradlew build` 통과 상태
- PR 리뷰는 Claude가 담당(1인 프로젝트) — 리뷰 시 공식 문서를 근거로 지적한다
- 커밋 body에는 설계 결정의 이유를 남긴다 (예: "synchronized 대신 ReentrantLock — JEP 444 pinning 회피")

## 참고 공식 문서

- RESP 스펙: https://redis.io/docs/latest/develop/reference/protocol-spec/
- 명령 레퍼런스: https://redis.io/docs/latest/commands/
- EXPIRE(만료·전파): https://redis.io/docs/latest/commands/expire/
- SCAN 보장: https://redis.io/docs/latest/commands/scan/
- Client handling(버퍼 한도): https://redis.io/docs/latest/develop/reference/clients/
- Pipelining: https://redis.io/docs/latest/develop/use/pipelining/
- Persistence: https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/
- Transactions: https://redis.io/docs/latest/develop/interact/transactions/
- Eviction: https://redis.io/docs/latest/develop/reference/eviction/
- JEP 444(가상 스레드): https://openjdk.org/jeps/444 · JEP 491: https://openjdk.org/jeps/491 · JEP 180: https://openjdk.org/jeps/180 · JEP 454(FFM): https://openjdk.org/jeps/454
- Java 21 가상 스레드 가이드: https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
- JMM (JLS §17.4): https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html
- GC 튜닝: https://docs.oracle.com/en/java/javase/21/gctuning/
- JOL: https://openjdk.org/projects/code-tools/jol/ · JFR: https://docs.oracle.com/en/java/javase/21/jfapi/
