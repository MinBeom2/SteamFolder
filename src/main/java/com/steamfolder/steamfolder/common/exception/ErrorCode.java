package com.steamfolder.steamfolder.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "지원하지 않는 HTTP 메서드입니다."),
    ENTITY_NOT_FOUND(HttpStatus.NOT_FOUND, "C003", "대상을 찾을 수 없습니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C004", "서버 내부 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C005", "잘못된 타입의 값입니다."),
    HANDLE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "C006", "접근 권한이 없습니다."),

    // User & Auth
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "존재하지 않는 유저입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증 정보가 유효하지 않습니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "만료된 토큰입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "유효하지 않은 토큰입니다."),
    STEAM_AUTH_FAILED(HttpStatus.BAD_REQUEST, "A004", "스팀 OpenID 인증에 실패했습니다."),

    // Folder
    FOLDER_NOT_FOUND(HttpStatus.NOT_FOUND, "F001", "존재하지 않는 폴더입니다."),
    FOLDER_ACCESS_DENIED(HttpStatus.FORBIDDEN, "F002", "해당 폴더에 대한 권한이 없습니다."),
    CIRCULAR_FOLDER_REFERENCE(HttpStatus.BAD_REQUEST, "F003", "자식 폴더를 상위 폴더로 지정할 수 없습니다."),
    DUPLICATE_FOLDER_NAME(HttpStatus.CONFLICT, "F004", "동일한 경로에 이미 같은 이름의 폴더가 존재합니다."),
    MAX_FOLDER_DEPTH_EXCEEDED(HttpStatus.BAD_REQUEST, "F005", "폴더의 깊이는 최대 5개 까지 가능합니다."),
    MAX_FOLDER_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "F006", "생성 가능한 최대 폴더 개수(100개)를 초과했습니다."),
    GAME_ALREADY_IN_FOLDER(HttpStatus.CONFLICT, "F007", "해당 폴더에 이미 추가된 게임입니다."),
    MAX_FOLDER_GAME_COUNT_EXCEEDED(HttpStatus.BAD_REQUEST, "F008", "폴더당 배치할 수 있는 최대 게임 개수(102개)를 초과했습니다."),

    // Game
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "존재하지 않는 게임입니다."),
    USER_GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "G002", "보유하지 않은 게임입니다."),
    STEAM_API_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "G003", "스팀 외부 API 호출 중 오류가 발생했습니다."),
    STEAM_PROFILE_PRIVATE(HttpStatus.BAD_REQUEST, "G004", "스팀 프로필 또는 게임 목록이 비공개 상태입니다."),
    SYNC_COOLDOWN_ACTIVE(HttpStatus.TOO_MANY_REQUESTS, "G005", "동기화 쿨다운(5분) 제한 중입니다."),

    // Feedback
    INVALID_FEEDBACK_CONTENT(HttpStatus.BAD_REQUEST, "FB001", "피드백 내용은 공백을 제외하고 10자 이상 3,000자 이하여야 합니다."),

    // Showcase & Social
    CANNOT_LIKE_OWN_SHOWCASE(HttpStatus.BAD_REQUEST, "S001", "본인의 쇼케이스에는 좋아요를 누를 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
