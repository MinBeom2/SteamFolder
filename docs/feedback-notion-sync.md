# 피드백 Notion 동기화 API

Spring DB가 피드백 분석 상태의 기준입니다. n8n은 이 API에서 변경 작업을 가져와 [피드백 운영 보드](https://app.notion.com/p/cb5cc25fe2b744ed9573f71520d0bc6e)에 반영하고, 성공한 작업만 확인 처리합니다.

- Notion 데이터 소스 ID: `13b36519-26ce-4076-a869-4eba99be01dd`
- 기본값: 비활성화 (`FEEDBACK_NOTION_SYNC_ENABLED=false`)
- 활성화 시 `FEEDBACK_NOTION_SYNC_TOKEN`에 32자 이상의 임의 토큰을 설정합니다. n8n HTTP Request 자격 증명에도 같은 값을 저장합니다. Git이나 워크플로 본문에 토큰을 넣지 않습니다.
- 모든 요청에 `Authorization: Bearer <token>`을 보냅니다. 사용자 JWT로는 접근할 수 없습니다.

## 작업 조회

`GET /internal/v1/feedback-notion-sync/jobs?afterId=0&limit=50`

- `afterId`는 0 이상, `limit`은 1~100입니다. `nextCursor`가 있으면 그 값을 다음 요청의 `afterId`로 사용합니다. 다음 실행은 다시 0부터 시작합니다.
- 분석 완료, 재시도 횟수를 소진한 분석 실패, Discord 알림 시각 변경이 `UPSERT` 작업을 만듭니다. 회원 탈퇴는 `ARCHIVE` 작업을 만듭니다.
- 기존 완료/최종 실패 데이터는 기능 활성화 후 애플리케이션 시작 시 동기화 대상으로 등록합니다.
- 응답은 `Cache-Control: no-store`입니다. 피드백 원문과 사용자 식별 정보는 포함하지 않습니다.

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "feedbackId": 102,
        "version": 1,
        "action": "UPSERT",
        "notionPageId": null,
        "category": "BUG_REPORT",
        "analysisStatus": "COMPLETED",
        "priority": "P0",
        "aiSummary": "로그인 과정에서 오류가 반복됩니다.",
        "analysisFailureReason": null,
        "submittedAt": "2026-09-29T09:00:00+09:00",
        "analyzedAt": "2026-09-29T09:01:00+09:00",
        "discordNotifiedAt": null
      }
    ],
    "nextCursor": null
  }
}
```

`ARCHIVE` 작업에는 `feedbackId`, `version`, `action`, 기존 `notionPageId`만 필요하며 나머지 값은 `null`입니다. 응답 시각은 서버가 기록한 로컬 시각을 `Asia/Seoul` 오프셋으로 변환합니다.

## 성공 확인

`POST /internal/v1/feedback-notion-sync/jobs/{feedbackId}/ack`

```json
{"version":1,"action":"UPSERT","notionPageId":"Notion 페이지 ID"}
```

- `UPSERT`에는 실제 Notion 페이지 ID가 필요합니다. `ARCHIVE`에는 `version`과 `action`만 보냅니다.
- 현재 작업의 버전과 동작이 맞으면 `204`입니다. 동일한 성공 확인을 반복해도 `204`입니다.
- 그 사이 분석 결과나 알림 시각이 바뀌었다면 `409 FB002`입니다. 최신 작업을 다시 조회해 반영해야 합니다.
- 잘못된 입력은 `400 C001`, 없는 작업은 `404 C003`, 토큰 누락·오류는 `401 A001`입니다. 기능이 비활성화되면 컨트롤러가 등록되지 않습니다.
- Notion 작업이 실패하면 성공 확인을 보내지 않습니다. 작업은 DB에 남아 다음 조회에서 다시 반환됩니다.

## n8n 처리 순서

1. 5분 간격으로 작업 목록을 조회하고 `nextCursor`가 없어질 때까지 페이지를 순회합니다.
2. `UPSERT`: `notionPageId`가 있으면 그 페이지를 확인합니다. 없거나 페이지를 찾지 못하면 Notion 데이터베이스에서 `Feedback ID` 숫자로 검색합니다. 있으면 갱신하고, 없으면 생성합니다.
3. `ARCHIVE`: `notionPageId` 또는 `Feedback ID`로 페이지를 찾아 보관 처리합니다. 이미 없으면 성공으로 간주합니다.
4. Notion 작업에 성공한 항목만 `ack`를 호출합니다. `409`는 다음 실행에서 최신 버전으로 다시 처리합니다.

Notion의 `운영 처리 상태`는 사람이 관리하므로 n8n이 기존 페이지를 갱신할 때 덮어쓰지 않습니다. Notion의 보관 처리는 영구 삭제와 다르므로 실제 사용자 데이터 삭제 정책을 정할 때 별도로 확인해야 합니다. AI 요약에도 개인정보가 포함될 수 있어 외부 동기화 활성화 전에 운영 정책을 검토합니다.
