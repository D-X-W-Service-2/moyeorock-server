package com.moyeorock.domain.recruit.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.group.service.GroupMemberService;
import com.moyeorock.domain.recruit.dto.request.RecruitPostCreateRequest;
import com.moyeorock.domain.team.service.TeamMemberService;
import com.moyeorock.domain.team.service.TeamService;
import com.moyeorock.domain.user.service.UserService;
import com.moyeorock.domain.recruit.dto.request.RecruitPostUpdateRequest;
import com.moyeorock.domain.recruit.dto.request.WantedSlotRequest;
import com.moyeorock.domain.recruit.dto.response.RecruitPostDetailResponse;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.common.enums.TargetType;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// Mockito 기반 RecruitPostServiceTest는 영속성 컨텍스트가 없어 flush 시점에 갱신되는
// updatedAt(@LastModifiedDate)을 검증할 수 없다.
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class, RecruitPostService.class})
class RecruitPostServiceUpdatedAtTest {

    @Autowired
    private RecruitPostService recruitPostService;

    // 권한·요약 조회는 이 테스트의 관심사가 아니다(updatedAt 갱신만 본다). 협력 서비스는 목으로 대체한다.
    @MockitoBean
    private UserService userService;

    @MockitoBean
    private TeamService teamService;

    @MockitoBean
    private TeamMemberService teamMemberService;

    @MockitoBean
    private GroupMemberService groupMemberService;

    @Test
    @DisplayName("수정 응답의 updatedAt은 수정 전 값이 아니라 갱신된 값이고 createdAt은 그대로다")
    void update_returnsRefreshedUpdatedAt() throws InterruptedException {
        RecruitPostDetailResponse created = recruitPostService.create(1L, new RecruitPostCreateRequest(
                TargetType.TEAM, 5L, "제목", "본문",
                List.of(new WantedSlotRequest(Instrument.BASS, 1)), Region.SEOUL));
        Thread.sleep(10);

        RecruitPostDetailResponse updated = recruitPostService.update(1L, created.id(),
                new RecruitPostUpdateRequest("수정된 제목", "수정된 본문",
                        List.of(new WantedSlotRequest(Instrument.DRUM, 1)), Region.SEOUL));

        assertThat(updated.updatedAt()).isAfter(created.updatedAt());
        assertThat(updated.createdAt()).isEqualTo(created.createdAt());
    }
}
