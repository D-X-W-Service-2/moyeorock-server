package com.moyeorock.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    NO_PERMISSION(HttpStatus.FORBIDDEN, "권한이 없습니다."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 Content-Type입니다."),
    INVALID_STATE(HttpStatus.CONFLICT, "처리할 수 없는 상태입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다."),

    // user
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    // 회원 탈퇴 차단(팀장 위임 전·모임장 해체 전). 어느 팀·모임인지는 BusinessException의 message로 덧붙인다(api-spec §1 *)
    WITHDRAW_BLOCKED_BY_ROLE(HttpStatus.CONFLICT, "모임장·팀장 직책을 정리한 뒤 탈퇴할 수 있습니다."),

    // team
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 팀입니다."),
    NOT_TEAM_LEADER(HttpStatus.FORBIDDEN, "팀장만 가능합니다."),
    LEADER_MUST_DELEGATE_FIRST(HttpStatus.CONFLICT, "팀장을 위임한 뒤 탈퇴할 수 있습니다."),
    // 모임의 GROUP_MEMBER_NOT_FOUND(404)와 같은 상황이라 프론트가 둘을 같게 다룰 수 있도록 맞췄다(PR #86 리뷰)
    TEAM_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 팀의 팀원이 아닙니다."),
    // 팀원 추천(AI) — AI 서버 호출 실패·시간 초과·주소 미설정. 명세에 없는 코드라 노션 반영 필요(이슈 #150)
    RECOMMENDATION_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "추천을 지금은 사용할 수 없습니다. 잠시 후 다시 시도해 주세요."),

    // auth (노션 API 초안 로그인 4건, 2026-09-28 확정)
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    OAUTH_FAILED(HttpStatus.BAD_REQUEST, "카카오 인증에 실패했습니다."),
    INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED, "가입 요청이 만료되었습니다. 다시 시도해 주세요."),
    ALREADY_REGISTERED(HttpStatus.CONFLICT, "이미 가입된 카카오 계정입니다."),

    // 합주 (rehearsal)
    REHEARSAL_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 합주 일정입니다."),

    // 모집 공고 (recruit) — 코드 문자열은 명세에 없어 team 도메인 패턴(TEAM_NOT_FOUND 등)을 따랐다
    RECRUIT_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 모집 공고입니다."),

    // 가입 신청·초대 (join) — api-conventions.md §3 예시 코드
    JOIN_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 신청입니다."),
    JOIN_REQUEST_DUPLICATED(HttpStatus.CONFLICT, "이미 신청한 대상입니다."),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "이미 소속된 멤버입니다."),

    // 파일
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 파일입니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 파일 형식입니다."),
    FILE_SIZE_EXCEEDED(HttpStatus.BAD_REQUEST, "파일 크기가 허용 범위를 초과했습니다."),
    EMPTY_FILE(HttpStatus.BAD_REQUEST, "빈 파일은 업로드할 수 없습니다."),
    // S3도 만료된 presigned URL에 403을 준다 — 로컬 업로드 수신도 같은 상태 코드로 맞춘다
    UPLOAD_URL_EXPIRED(HttpStatus.FORBIDDEN, "업로드 URL이 만료되었습니다."),

    // 모임 (group) — 코드·메시지는 Notion API 초안 에러 응답 예시와 맞춘다
    GROUP_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 모임입니다."),
    GROUP_MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 모임의 모임원이 아닙니다."),
    NOT_GROUP_MEMBER(HttpStatus.FORBIDDEN, "모임원만 조회할 수 있습니다."),
    NOT_GROUP_OWNER(HttpStatus.FORBIDDEN, "모임장만 할 수 있습니다."),
    CANNOT_CHANGE_OWN_ROLE(HttpStatus.BAD_REQUEST, "자기 자신의 역할은 변경할 수 없습니다."),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "모임장은 위임 후 탈퇴할 수 있습니다."),

    // 공지 (notice)
    NOTICE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공지입니다."),

    // 저장 (bookmark)
    BOOKMARK_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 저장입니다."),
    BOOKMARK_DUPLICATED(HttpStatus.CONFLICT, "이미 저장한 대상입니다."),

    // 노래 (song) — 코드 이름은 노션에 없어 4팀이 정했다(이슈 #103)
    SONG_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 곡입니다."),
    // 곡 수동 동기화(이슈 #162) — 코드 이름은 4팀이 정했다
    SONG_SYNC_IN_PROGRESS(HttpStatus.CONFLICT, "곡 동기화가 이미 진행 중입니다."),
    SONG_SYNC_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "AI 서버에서 곡을 가져오지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // 셋리스트 (setlist) — SONG_NOT_TEAM_CANDIDATE만 노션에 있고 나머지 이름은 4팀이 정했다(이슈 #103)
    SONG_NOT_SELECTED_BY_TEAM(HttpStatus.CONFLICT, "팀 선택곡에 없는 곡은 셋리스트로 넘길 수 없습니다."),
    SONG_ALREADY_IN_SETLIST(HttpStatus.CONFLICT, "같은 공연의 다른 팀이 이미 넘긴 곡입니다."),
    SONG_NOT_TEAM_CANDIDATE(HttpStatus.CONFLICT, "팀이 셋리스트로 넘긴 곡만 확정할 수 있습니다."),

    // 공연 (performance) — 코드·메시지는 Notion API 초안 에러 응답 예시와 맞춘다
    PERFORMANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 공연입니다."),
    // 공연 팀원은 그 공연을 연 모임의 활동 모임원만 될 수 있다(#96). 코드 이름은 4팀이 정했다 — 기존 코드를 빌리면 프론트가 구분하기 어렵다
    NOT_PERFORMANCE_GROUP_MEMBER(HttpStatus.FORBIDDEN, "공연을 연 모임의 모임원만 공연 팀에 들어올 수 있습니다."),

    // 연습 (practice) — 코드 이름은 노션 API 초안 "연습"(2026-11-09)과 맞추고 메시지는 4팀이 정했다(이슈 #155)
    PRACTICE_RECORDING_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 연습 녹음입니다."),
    PRACTICE_ANALYSIS_IN_PROGRESS(HttpStatus.CONFLICT, "이미 분석 중인 연습 녹음이 있습니다.");

    private final HttpStatus status;
    private final String message;
}
