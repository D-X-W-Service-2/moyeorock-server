package com.moyeorock.domain.team.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.domain.team.enums.MemberStatus;
import com.moyeorock.domain.team.enums.TeamRole;
import com.moyeorock.global.common.enums.Instrument;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class TeamMemberTest {

    @Test
    @DisplayName("rejoin은 나갔던 팀원을 ACTIVE MEMBER로 되살리고 세션과 합류 시각을 새로 정한다")
    void rejoin_reactivatesMemberWithNewInstrument() {
        TeamMember member = TeamMember.create(1L, 2L, TeamRole.MEMBER, Instrument.BASS);
        member.updateStatus(MemberStatus.REMOVED);
        LocalDateTime oldJoinedAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        ReflectionTestUtils.setField(member, "joinedAt", oldJoinedAt);

        member.rejoin(Instrument.DRUM);

        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(member.getInstrument()).isEqualTo(Instrument.DRUM);
        assertThat(member.getJoinedAt()).isAfter(oldJoinedAt);
    }

    @Test
    @DisplayName("changeInstrument는 세션만 바꾼다")
    void changeInstrument_changesOnlyInstrument() {
        TeamMember member = TeamMember.create(1L, 2L, TeamRole.MEMBER, Instrument.BASS);

        member.changeInstrument(Instrument.EL_GT);

        assertThat(member.getInstrument()).isEqualTo(Instrument.EL_GT);
        assertThat(member.getRole()).isEqualTo(TeamRole.MEMBER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
    }
}
