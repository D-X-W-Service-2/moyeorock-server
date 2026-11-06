-- songs.external_id(AI 서버 쪽 곡 id)를 대소문자를 구분하는 콜레이션으로 바꾼다(PR #148 리뷰, 이슈 #147).
--
-- 서버 기본 콜레이션(docker-compose utf8mb4_unicode_ci, MySQL 기본 utf8mb4_0900_ai_ci)은 대소문자·악센트를 무시하고
-- unicode_ci는 끝 공백까지 무시한다. 그래서 YouTube 영상 id처럼 대소문자만 다른 서로 다른 곡이 uk_songs_external_id에서
-- 같은 값으로 부딪혀, 곡 일괄 등록·갱신(PUT /v1/internal/songs)이 기존 곡을 못 찾고 새로 넣다가 유니크 위반으로 실패했다.
--
-- utf8mb4_0900_bin은 코드 포인트로 비교하고 끝 공백도 구분해서(NO PAD) SongService의 Java 비교(String.equals)와 기준이 같다.
-- 기존 값은 더 느슨한 ci 유니크를 통과한 값이라 bin으로 좁혀도 중복이 생기지 않는다. 유니크 인덱스는 그대로 유지된다.
ALTER TABLE songs
    MODIFY COLUMN external_id VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin;
