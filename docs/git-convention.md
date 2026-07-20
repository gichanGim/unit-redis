# Git 컨벤션 — 단위 레디스 (unit-redis)

> 커밋(Angular 기반) · 브랜치(main 단일 + Phase 브랜치) · 이슈/PR 규칙.
> 커밋·브랜치 생성·PR 작성 시 이 문서를 따른다.
> 1인 학습 프로젝트 특성 반영: **소규모 작업은 main 직접 push, Phase 단위 큰 작업만 브랜치 + PR.**

---

## 1. 커밋 메시지

### 기본 구조 (싱글 라인)

```bash
git commit -m "<type>(<scope>): <subject> (#issue)"
```

- **type**: 커밋 종류 (필수)
- **scope**: 변경 영역 (선택, 아래 §2의 컴포넌트명)
- **subject**: 간결한 설명 (필수)
- **(#issue)**: 관련 이슈가 있을 때만
- body / footer: 상세·이슈 참조 (선택)

### Type 종류

| Type | 설명 | 예시 |
| --- | --- | --- |
| `feat` | 새 기능 | `feat(core): INCR/DECR 명령 추가` |
| `fix` | 버그 수정 | `fix(resp): 부분 읽기 시 파서 무한 대기 수정` |
| `docs` | 문서 | `docs(roadmap): Phase 4 자가 점검 항목 갱신` |
| `refactor` | 리팩토링 | `refactor(core): 저장소 인터페이스 추출 (구현 교체 대비)` |
| `perf` | 성능 | `perf(core): incremental rehash 스텝 크기 조정` |
| `test` | 테스트 | `test(oracle): 실제 Redis 차분 테스트 케이스 추가` |
| `build` | 빌드·의존성 | `build(deps): JOL 의존성 추가` |
| `ci` | CI 설정 | `ci(github): 빌드·테스트 워크플로우 추가` |
| `chore` | 기타(소스 변경 X) | `chore: .gitignore 업데이트` |
| `revert` | 되돌리기 | `revert: feat(core) 되돌림` |

### 작성 규칙

- **Subject**: 50자 이내 · 마침표 금지 · 명령문("추가함" → "추가") · 한글 통일(기술 용어는 영문 허용)
- **Body**: 무엇을·왜(어떻게 X). 학습 프로젝트이므로 **설계 결정의 이유**(예: "synchronized 대신 ReentrantLock — JEP 444 pinning 회피")를 남기면 좋다
- **Footer**: 이슈 참조 `Closes #123` / `Fixes #456`
- **MUST NOT**: 커밋 메시지에 `Co-Authored-By` 트레일러를 넣지 않는다.

### 좋은 예 / 나쁜 예

```text
✅ feat(resp): 재귀 하강 파서 구현 (#12)
✅ fix(expire): 만료 시각을 nanoTime → 절대 Unix ms로 변경
✅ test(bench): 3모델 INCR 10만 회 정확성 테스트 추가

❌ 수정함        ❌ Update code      ❌ feat: 작업
❌ 버그수정.     ❌ FIX: BUG FIX!!!
```

---

## 2. Scope = 컴포넌트명

로드맵의 레이어/Phase 구조를 따른다.

| scope | 범위 | 주 Phase |
| --- | --- | --- |
| `core` | key-value 엔진 · 해시 테이블 · INCR encoding | 1 |
| `resp` | RESP 파서 · 프로토콜 견고성(에러 3종, 입력 한도) | 2 |
| `server` | TCP 서버 · 커넥션/가상 스레드 · 출력 버퍼 | 2 |
| `expire` | TTL · lazy/active expiration | 3, 4 |
| `concurrency` | 동시성 모델(전역 락/CHM/커맨드 큐) · 파이프라이닝 | 4 |
| `collection` | List/Hash/Set/Sorted Set · SCAN | 5 |
| `eviction` | maxmemory · LRU/LFU · 메모리 회계 | 6 |
| `persist` | RDB 스냅샷 · AOF · fsync | 7 |
| `ext` | MULTI/EXEC · Pub/Sub · BLPOP · 복제 | 8 |
| `bench` | JMH · redis-benchmark · JOL/JFR 실측 | 전체 |
| `oracle` | 실제 Redis 오라클 차분 테스트 하네스 | 2+ |
| `config` | Gradle·프로젝트 설정 | - |

> 컴포넌트가 아직 패키지로 분리되기 전이라도 scope는 위 기준으로 붙인다. 어느 scope에도 안 걸치면 생략 가능.

### 커밋 단위 규칙

1. **scope 하나 = 커밋 하나** — 한 컴포넌트 안에서 끝낸다
2. type 하나로 설명되면 적정 — 두 type 필요하면 쪼갠다 (예: 리팩토링 후 기능 추가 → 2커밋)
3. 각 커밋은 **`./gradlew build` (컴파일 + 테스트) 통과** — 빨간 상태 커밋 금지
4. 여러 컴포넌트에 걸치면 **의존 방향대로** — 인터페이스/하위 레이어(core) 먼저, 사용하는 상위 레이어(server) 나중
5. **인터페이스(추상화) 변경은 독립 커밋** — Phase 4 구현 교체 대비 인터페이스는 영향이 넓으니 다른 기능에 안 섞음

```text
feat(core): 저장소 인터페이스에 만료 시각 조회 추가
feat(expire): lazy expiration 구현 (#23)
test(oracle): EXPIRE/TTL 차분 테스트 추가
```

---

## 3. 브랜치 전략 (main 단일)

develop 없이 **main 하나**를 기준으로 한다.

| 작업 규모 | 방식 |
| --- | --- |
| **소규모** (Phase 내부의 일상 커밋, 문서, 설정, 버그 수정) | `main`에서 직접 커밋 & push |
| **대규모** (**Phase 단위** — 예: Phase 2 RESP 서버 전체) | `feat/*` 브랜치 → PR → `main` 머지 |

| 브랜치 | 역할 | 분기 | 머지 |
| --- | --- | --- | --- |
| `main` | 기준 브랜치 (항상 빌드·테스트 통과 상태 유지) | - | - |
| `feat/*` | Phase 단위 기능 개발 | `main` | `main` (PR) |

### 네이밍

```text
feat/#{이슈번호}/phase{N}-{주제}    예: feat/#12/phase2-resp-server
                                    예: feat/#30/phase4-concurrency-models
```

- ✅ `feat/#12/phase2-resp-server`
- ❌ `feat/resp`(이슈 번호 없음)  ❌ `Feat/Phase2`(대문자)

### 규칙

- Phase 시작 시 해당 Phase의 **이슈를 먼저 만들고** 그 번호로 브랜치를 판다
- 작업 전 항상 `git pull`
- 한 브랜치 = 하나의 Phase
- 머지된 브랜치는 즉시 삭제
- 브랜치 이동 시 `git stash` 활용
- main 직접 push라도 **커밋 단위 규칙(§2)은 동일하게 적용** — 커밋 잘게, 항상 초록 상태

---

## 4. 이슈 / PR

### 이슈

- 제목: `[TYPE] 작업 내용` (예: `[Feat] Phase 2 — RESP 파서 + TCP 서버`)
- Phase 단위 작업은 이슈 본문에 로드맵의 해당 Phase **자가 점검 체크리스트**를 옮겨 적고, 완료 시 체크한다

### PR 규칙 (Phase 단위 작업만 해당)

- [ ] PR 제목은 이슈 제목과 같은 `[Type] 작업 내용 (#이슈번호)` 형식 (커밋 메시지의 Angular 형식과 다름)
- [ ] PR 템플릿 모든 항목 작성
- [ ] **Claude 코드 리뷰**: 머지 전 Claude에게 PR 리뷰를 요청하고, 리뷰 반영 후 셀프 머지 (1인 프로젝트 — 사람 Approve 대체)
- [ ] 로컬 `./gradlew build` 통과 + **오라클 차분 테스트 통과** (Phase 2 이후) — CI 구축 시 CI 체크로 대체
- [ ] 머지 후 브랜치 삭제

### PR 본문 항목

작업 내용(What) · 변경 사항(Details) · **학습 정리(이 Phase에서 배운 것 — 자가 점검 결과 링크)** · 주의 사항 · 관련 이슈(`Closes #N`) · 체크리스트(빌드/테스트 완료, Claude 리뷰 반영).

> GitHub 이슈/PR 템플릿 파일은 `.github/`에 둔다(`ISSUE_TEMPLATE`, `pull_request_template.md`).

---

## 5. Claude 작업 시 준수 사항

- 커밋을 대신 만들 때 이 문서의 형식을 그대로 따른다 (`Co-Authored-By` 금지 포함)
- 코드 리뷰 요청을 받으면 로드맵의 "공식 문서 기반" 원칙에 따라 Redis docs / JEP / Oracle Java docs를 근거로 리뷰한다
- 커밋·PR 규모 판단이 애매하면: Phase 전체를 관통하는 작업 → 브랜치, 그 외 → main 직접
