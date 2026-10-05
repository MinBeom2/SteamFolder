# appId 2688840 업적 조회 점검

## 실제 관측 결과 (2026-10-04)

사용자가 제공한 SteamID와 로컬 Steam API 키로 `GetPlayerAchievements/v0001`을 직접 조회했다. 키와 로그인 토큰은 출력하거나 이 문서에 저장하지 않았다.

- HTTP 상태: `400`
- Content-Type: `application/json; charset=UTF-8`
- 본문: `{"playerstats":{"error":"Requested app has no stats","success":false}}`
- 이 응답은 해당 앱에 stats가 없음을 명시하므로 정상 업적 0개로 처리한다. 특정 appId를 예외로 하드코딩하지 않는다.
- 원본 응답은 `src/test/resources/steam/2688840-no-stats.json`에 보존했다. 테스트에는 실제 HTTP 상태와 Content-Type도 사용한다.

## 실행 버전과 진단의 한계

- 작업 시작 시 체크아웃은 변경 사항 없는 `develop`, HEAD는 `c151a3c`였다. 프로젝트 버전은 `0.0.1-SNAPSHOT`, Spring Boot는 `3.5.16`이다.
- 로컬 8080 포트의 서버는 PID `42040`, 시작 시각은 `2026-10-04 18:54:07 KST`였다. 이 저장소의 `build/classes/java/main`과 `build/resources/main`을 사용했다.
- 수정 전 해당 경로의 `SteamApiClient.class`에서 HTTP 400의 `Mono.empty()` 처리와 비공개 오류 분기를 확인했다. 해당 클래스의 수정 시각은 `2026-10-03 19:58:08 KST`였다. `bin/main`의 클래스와는 해시가 달랐으며 실행 클래스 경로는 `build/classes`였다.
- 런타임에 Git SHA가 제공되지 않아 로드된 클래스의 정확한 커밋 SHA까지는 확인할 수 없다. 체크아웃 HEAD와 런타임 버전은 구분해야 한다.
- 관측한 400 JSON no-stats 형식은 기존 코드도 처리할 수 있는 형식이다. 따라서 과거 G003을 이번 응답의 Content-Type이나 오래된 코드 실행 탓으로 확정할 수 없다. 당시 원본 응답이 기록되지 않아 과거 실패 원인은 미확정이다.
- 로컬 DB에서 해당 게임 보유 기록을 찾지 못했고 오류가 발생한 백엔드 주소는 아직 확인되지 않았다. 실제 Steam 응답 조회와 테스트 검증을 완료했지만, 원래 오류가 난 서버에서 인증된 GET 요청을 다시 호출한 것은 아니다.

## 수정한 판정 규칙

| Steam 응답 | 처리 |
| --- | --- |
| HTTP 200, `success=true`, 유효한 업적 배열 | 정상 업적과 통계 반환 |
| HTTP 200, `success=true`, 빈 업적 배열 | 정상 0개 반환 |
| HTTP 200/400, `success=false`, 정확한 `Requested app has no stats` 오류 | 정상 0개 반환 |
| `success=false`, 기존 비공개 오류 문구 (`private` 또는 `not public`) | 기존 G004 유지 |
| 빈 HTTP 403 | 기존 G004 유지 |
| 키 누락, 그 밖의 `success=false`, 빈 400, 비정상 스키마 | G003 |
| 외부 HTTP 장애, timeout, JSON 디코딩 실패, 네트워크/미확인 예외 | G003 |

본문을 문자열로 받은 뒤 JSON을 검증하므로 JSON 오류 응답의 Content-Type이 달라도 내용을 판정할 수 있다. HTML이나 잘못된 JSON을 업적 없음으로 취급하지 않는다. HTTP 5xx/429 등에 no-stats 본문이 있어도 정상 0개로 바꾸지 않는다.

로그는 `appId`, 판정 이유, HTTP 상태, Content-Type, 제한된 길이의 가려진 오류 본문, 예외 **종류**를 남긴다. HTTP 응답을 받기 전 실패하면 상태는 `null`이다. API 키·Bearer 토큰·토큰 필드·JWT를 가리고, 키가 포함될 수 있는 요청 URI·예외 메시지·스택은 기록하지 않는다.

## 응답 계약과 데이터 보존

정상 0개 응답은 기존 계약을 유지한다.

```json
{
  "totalAchievements": 0,
  "achievedAchievements": 0,
  "achievementRate": null,
  "achievements": []
}
```

실제 API에서는 위 필드가 기존 `data` 객체에 포함되며 `appId`, 게임명, 동기화 시각 등 기존 필드도 유지한다. G003은 HTTP 503, G004는 HTTP 400이다. 정상 응답의 필드·자료형은 변경하지 않았다. 미확인 빈 400과 기타 실패를 정상 0개로 간주하던 처리는 제거했다. 본문이 있는 미확인 403도 비공개로 단정하지 않고 G003으로 처리한다.

업적 통계·성공 시각·메모리 캐시는 클라이언트가 정상 결과를 반환한 뒤에만 갱신한다. 실패 시 예외를 전달하며 기존 DB 통계, `achievementsSyncedAt`, 메모리 캐시를 보존한다. 캐시 유효기간은 기존 60분을 유지한다.

## 검증 결과

- 2026-10-05 재검증: `./gradlew.bat test --rerun-tasks --console=plain`: **272개 통과, 실패 0, 오류 0, 생략 0**.
- 실제 no-stats fixture를 클라이언트와 동기화 서비스에 연결해 0개 통계·null 달성률·빈 목록과 캐시 재사용을 검증했다.
- 정상 업적, 정상 빈 배열, 비공개, 키 누락, 미확인 사용자 stats 오류, HTTP 장애, 실제 응답 지연에 의한 timeout, 디코딩 실패, 로그의 키·토큰 제거를 검증했다.
- G003/G004 발생 시 기존 통계·성공 시각·캐시를 보존하는 테스트를 추가했다.
- MockMvc로 GET의 HTTP 200 및 `achievementRate` 필드의 명시적 null, HTTP 503 G003, HTTP 400 G004 계약을 검증했다.
- 컴파일 오류는 없었다. 기존 JVM 클래스 공유 경고는 있었지만 테스트는 통과했다.

원래 요청 대상 서버에서 수정된 코드를 실행한 뒤 동일 GET을 다시 확인해야 한다. 코드 반영 이력과 다른 기기에서 이어갈 작업은 `docs/handoff-2026-10-05.md` 및 Notion 인수인계 기록을 참고한다.
