# 랭킹 전체 플레이 시간과 폴더 복사 API

## 랭킹

GET /api/v1/rankings/showcases

응답의 data.content[]와 data.myRanking에 totalPlaytimeForever를 추가한다.

- JSON 정수이며 단위는 분이다. DB에 마지막으로 동기화된 보유 게임 전체의 playtimeForever 합계다.
- 보유 게임이 없으면 0이다. Java long으로 합산하며 게임 목록을 페이징해 합산하지 않는다.
- 폴더에 같은 게임을 여러 번 배치해도 합계에 영향을 주지 않는다.
- 첫 페이지의 내 랭킹도 같은 집계에 포함한다. 비로그인·커서·순위·좋아요 계약은 기존과 같다.
- 이 API는 별도 응답 캐시를 사용하지 않는다. Steam 동기화 이후 재조회하면 변경된 시간이 반영된다.
- 프론트는 받은 분 값을 시간으로 변환해 표시하면 된다.

## 폴더 전체 복사

POST /api/v1/folders/{folderId}/copy

인증된 사용자의 폴더를 같은 사용자의 다른 위치에 복사한다. 요청 본문은 필수이며 바탕화면 자동 배치는 빈 객체 {}로 요청한다.

요청 필드:

| 필드 | 타입 | 생략/null |
| --- | --- | --- |
| targetParentFolderId | 양의 정수 | 바탕화면에 붙여넣기 |
| targetPositionX | 0 이상의 정수 | 좌표 중 하나라도 없으면 빈 칸 자동 선택 |
| targetPositionY | 0 이상의 정수 | 좌표 중 하나라도 없으면 빈 칸 자동 선택 |
| name | 1~100자 문자열, 공백만 입력 불가 | 원본 이름 사용, 중복 시 복사본 이름 자동 생성 |

두 좌표가 모두 있고 폴더나 게임과 겹치면 빈 칸을 자동 선택한다.
이름 자동 생성 규칙은 원본 이름, 원본 이름 - 복사본, 원본 이름 - 복사본 (2), ... 이다.
접미사를 붙일 때 최대 100자에 맞춰 원본 이름 부분을 줄인다.
name을 명시하면 자동 변경 없이 중복 오류를 반환한다.

복사 범위:

- 모든 깊이의 하위 폴더와 각 폴더의 게임 배치를 복제한다.
- 폴더 이름·색상·아이콘·열림 상태와 내부의 게임/폴더 좌표를 유지한다.
- 대상 위치에 맞게 depth를 다시 계산한다.
- 폴더 및 FolderGame ID는 새로 발급한다. Game 메타데이터와 UserGame 플레이 시간은 기존 것을 참조한다.
- 원본의 수정·삭제와 복사본의 수정·삭제는 서로 영향을 주지 않는다.
- 복사 후 총 폴더 100개, 최대 깊이 5단계(depth 0~4), 폴더당 게임 배치 102개 제한을 검사한다.
- 자체 하위 경로에 붙여넣기도 깊이 제한 안에서는 가능하다. 새 ID를 사용하므로 순환 참조가 생기지 않는다.
- 검증 실패 또는 저장 중 오류가 나면 전체 트랜잭션을 취소한다.

성공: 201 Created, 기존 ApiResponse<FolderResponse> 형식.
data.id가 새 루트 폴더 ID다. data.games는 직접 포함된 게임이고 data.childFolders는 직접 하위 폴더다.
전체 하위 트리는 DB에 저장되며, 기존 GET /api/v1/folders/{id}로 각 폴더를 열어 조회한다.

오류:

| HTTP | 코드 | 상황 |
| --- | --- | --- |
| 400 | C001 | 잘못된 요청·음수 좌표·유효하지 않은 이름/대상 ID |
| 404 | U001 / F001 | 사용자 / 원본·대상 폴더 없음 |
| 403 | F002 | 원본 또는 대상 폴더가 타인 소유 |
| 409 | F004 | 명시한 name이 대상 경로와 중복 |
| 400 | F005 | 복사할 전체 트리가 최대 깊이 초과 |
| 400 | F006 | 복사할 전체 트리를 포함하면 총 폴더 수 초과 |
| 400 | F008 | 원본에 폴더당 게임 제한을 넘는 데이터 존재 |

인증 오류와 rate limit 응답은 기존 보안 필터 계약을 따른다.
복사는 캐시하지 않는다. POST 요청마다 새 복사본을 생성하므로 자동 재시도하지 않는다.
응답을 받지 못했다면 대상 위치를 먼저 새로고침해 생성 여부를 확인한다.

## 프론트 연동

현재 프론트의 src/store/useShowcaseStore.ts에서 pasteClipboard의 FOLDER/copy 분기가
createFolderApi로 빈 폴더만 생성하고 있다. 이 호출을 새 복사 API 호출로 교체해야 UI에 반영된다.

1. src/api/folder.ts에 copyFolderApi(folderId, request)를 추가한다.
2. pasteClipboard의 action === 'copy' 분기에서 아래 요청을 한 번 호출한다.

    POST /api/v1/folders/{clipboard 항목의 id}/copy

    {
      "targetParentFolderId": currentFolderId,
      "targetPositionX": x,
      "targetPositionY": y
    }

3. 이름·색상·아이콘을 따로 보내거나 하위 항목을 프론트에서 재생성하지 않는다. 서버가 원본 전체를 복제한다.
4. 성공하면 기존 refreshCurrentView()로 새로고침한다. 기존 잘라내기/이동 분기는 유지한다.
5. F005/F006 등의 오류 메시지를 표시한다. 서버가 전체 하위 깊이와 폴더 수를 검사한다.

프론트 저장소는 현재 쓰기 허용 범위 밖이므로 이 백엔드 변경에 포함하지 않았다.

## 검증 결과 (2026-10-27)

- JDK 21로 전체 main/test Java 소스 컴파일 성공. Spring Boot 기본 빌드와 같은 -parameters 옵션과 프로젝트에서 해석한 의존성을 사용했다.
- JUnit Platform Launcher로 관련 테스트 58개 실행, 58개 성공, 실패 0개.
- 대상: ShowcaseRankingServiceTest, RankingControllerTest, UserGamePlaytimeRepositoryTest, FolderControllerTest, FolderCopyIntegrationTest, FolderServiceTest, ShowcaseServiceTest.
- 실제 DB 저장·다단계 복제·원본과 복사본의 독립성·깊이/개수/권한·저장 후 오류 발생 시 롤백을 H2에서 검증했다.
- gradlew.bat test도 전체 실행을 시도했으나 compileJava 작업의 Gradle Worker가 127.0.0.1에 접속할 때 SocketException: Permission denied: getsockopt로 차단됐다. 전체 Gradle 테스트 통과를 확인한 상태는 아니다.
- git diff --check 통과.
