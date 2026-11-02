package com.moyeorock.domain.team.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamStatus;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TeamRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    @DisplayName("팀 생성: 이름·세션만 있으면 통과하고 소개·지역·장르는 비워도 된다")
    void create_passes_withOnlyRequiredFields() {
        var request = new TeamCreateRequest("무명밴드", null, null, null, Instrument.BASS);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("팀 생성: 이름이 비었거나 51자면 거절된다")
    void create_rejects_blankOrTooLongName() {
        var blank = new TeamCreateRequest(" ", null, Region.SEOUL, List.of(), Instrument.BASS);
        var tooLong = new TeamCreateRequest("가".repeat(51), null, Region.SEOUL, List.of(), Instrument.BASS);

        assertThat(validator.validate(blank)).hasSize(1);
        assertThat(validator.validate(tooLong)).hasSize(1);
    }

    @Test
    @DisplayName("팀 생성: 소개 1001자, 장르 6개, 세션 누락은 각각 거절된다")
    void create_rejects_longDescription_manyGenres_missingInstrument() {
        var longDescription = new TeamCreateRequest("팀", "가".repeat(1001), null, null, Instrument.BASS);
        var sixGenres = new TeamCreateRequest("팀", null, null, List.of(Genre.ROCK, Genre.INDIE, Genre.PUNK,
                Genre.METAL, Genre.POP, Genre.JAZZ), Instrument.BASS);
        var noInstrument = new TeamCreateRequest("팀", null, null, null, null);

        assertThat(validator.validate(longDescription)).hasSize(1);
        assertThat(validator.validate(sixGenres)).hasSize(1);
        assertThat(validator.validate(noInstrument)).hasSize(1);
    }

    @Test
    @DisplayName("팀 정보 수정: 이름이 비었으면 거절되고 나머지는 선택이다")
    void update_requiresName_only() {
        assertThat(validator.validate(new TeamUpdateRequest("유명밴드", null, null, null))).isEmpty();
        assertThat(validator.validate(new TeamUpdateRequest("", null, null, null))).hasSize(1);
    }

    @Test
    @DisplayName("상태 변경 요청은 status가 null이면 거절된다")
    void statusRequests_rejectNullStatus() {
        assertThat(validator.validate(new TeamStatusUpdateRequest(TeamStatus.DISBANDED))).isEmpty();
        assertThat(validator.validate(new TeamStatusUpdateRequest(null))).hasSize(1);
        assertThat(validator.validate(new TeamMemberStatusUpdateRequest(MemberStatus.LEFT))).isEmpty();
        assertThat(validator.validate(new TeamMemberStatusUpdateRequest(null))).hasSize(1);
    }

    @Test
    @DisplayName("팀원 변경 요청은 role·instrument를 모두 비워도 형식상 통과한다 (보낸 것만 반영)")
    void memberUpdate_allowsAllNull() {
        assertThat(validator.validate(new TeamMemberUpdateRequest(null, null))).isEmpty();
    }
}
