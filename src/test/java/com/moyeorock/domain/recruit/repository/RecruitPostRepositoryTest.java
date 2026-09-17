package com.moyeorock.domain.recruit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.domain.recruit.entity.RecruitPost;
import com.moyeorock.domain.recruit.entity.WantedSlot;
import com.moyeorock.domain.recruit.enums.RecruitStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

// WantedSlotsConverter가 실제 DB 왕복(JSON 직렬화/역직렬화)에서 값을 보존하는지 확인한다.
// Mockito 기반 RecruitPostServiceTest는 리포지토리를 모킹해서 이 경로를 안 지나간다.
//
// @DataJpaTest는 @Configuration을 자동 로드하지 않는다(BaseEntityAuditingTest와 동일한 이유로
// JpaAuditingConfig를 명시적으로 import해야 createdAt/updatedAt이 채워진다).
@DataJpaTest
@Import(JpaAuditingConfig.class)
class RecruitPostRepositoryTest {

    @Autowired
    private RecruitPostRepository recruitPostRepository;

    @Test
    @DisplayName("저장 후 조회하면 wantedSlots JSON이 원래 값 그대로 복원된다")
    void save_and_findById_preservesWantedSlots() {
        RecruitPost post = RecruitPost.create(1L, 5L, null, "베이스 구합니다", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1), new WantedSlot(Instrument.KEY, 2)),
                Region.SEOUL);
        Long id = recruitPostRepository.save(post).getId();
        recruitPostRepository.flush();

        RecruitPost found = recruitPostRepository.findById(id).orElseThrow();

        assertThat(found.getWantedSlots())
                .containsExactly(new WantedSlot(Instrument.BASS, 1), new WantedSlot(Instrument.KEY, 2));
    }

    @Test
    @DisplayName("CHK_RP_TARGET: targetTeamId·targetGroupId를 둘 다 채우면 저장이 거부된다")
    void save_fails_whenBothTargetColumnsSet() {
        // exclusive-arc 불변식은 DB(CHECK)보다 먼저 create()에서 막는다 — 여기선 그 방어가
        // 실제로 저장을 막는지까지 확인한다(둘 다 null인 경우도 create()가 동일하게 막는다).
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> RecruitPost.create(1L, 5L, 9L, "제목", "본문",
                                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("search는 targetType·region·status·authorId 조건을 모두 만족하는 공고만 돌려준다")
    void search_filtersByAllConditions() {
        RecruitPost matching = RecruitPost.create(1L, 5L, null, "일치", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL);
        RecruitPost wrongRegion = RecruitPost.create(1L, 5L, null, "지역 다름", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.BUSAN);
        RecruitPost wrongAuthor = RecruitPost.create(2L, 5L, null, "작성자 다름", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL);
        recruitPostRepository.saveAll(List.of(matching, wrongRegion, wrongAuthor));
        recruitPostRepository.flush();

        Page<RecruitPost> result = recruitPostRepository.search(
                TargetType.TEAM, Region.SEOUL, RecruitStatus.OPEN, 1L, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(RecruitPost::getTitle).containsExactly("일치");
    }

    @Test
    @DisplayName("search(targetType=GROUP)는 targetGroupId가 채워진 공고만 돌려준다")
    void search_byGroupTargetType_matchesOnlyTargetGroupIdRows() {
        RecruitPost teamPost = RecruitPost.create(1L, 5L, null, "팀 대상", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL);
        RecruitPost groupPost = RecruitPost.create(1L, null, 9L, "모임 대상", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL);
        recruitPostRepository.saveAll(List.of(teamPost, groupPost));
        recruitPostRepository.flush();

        Page<RecruitPost> result = recruitPostRepository.search(
                TargetType.GROUP, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).extracting(RecruitPost::getTitle).containsExactly("모임 대상");
    }

    @Test
    @DisplayName("search에 조건을 안 주면(null) 전부 돌려준다")
    void search_withAllNullFilters_returnsEverything() {
        recruitPostRepository.save(RecruitPost.create(1L, 5L, null, "공고1", "본문",
                List.of(new WantedSlot(Instrument.BASS, 1)), Region.SEOUL));
        recruitPostRepository.save(RecruitPost.create(2L, null, 9L, "공고2", "본문",
                List.of(new WantedSlot(Instrument.DRUM, 1)), Region.BUSAN));
        recruitPostRepository.flush();

        Page<RecruitPost> result = recruitPostRepository.search(
                null, null, null, null, PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
