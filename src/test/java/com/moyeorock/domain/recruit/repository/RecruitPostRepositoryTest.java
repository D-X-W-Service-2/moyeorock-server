package com.moyeorock.domain.recruit.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.config.TestcontainersConfig;
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

/**
 * wanted_slots JSON 왕복과 목록 필터(특히 MySQL JSON_CONTAINS)를 실제 MySQL에서 검증한다.
 * {@code @DataJpaTest}는 {@code @Configuration}을 자동 로드하지 않아 auditing을 명시적으로 켠다.
 */
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class RecruitPostRepositoryTest {

    private static final PageRequest FIRST_PAGE = PageRequest.of(0, 20);

    @Autowired
    private RecruitPostRepository recruitPostRepository;

    private RecruitPost teamPost(String title, Region region, WantedSlot... slots) {
        return RecruitPost.create(1L, 5L, null, title, "본문", List.of(slots), region);
    }

    private RecruitPost groupPost(String title, Region region, WantedSlot... slots) {
        return RecruitPost.create(1L, null, 9L, title, "본문", List.of(slots), region);
    }

    private static WantedSlot slot(Instrument instrument) {
        return new WantedSlot(instrument, 1);
    }

    @Test
    @DisplayName("저장 후 조회하면 wantedSlots JSON이 원래 값 그대로 복원된다")
    void save_and_findById_preservesWantedSlots() {
        RecruitPost post = teamPost("베이스 구합니다", Region.SEOUL,
                new WantedSlot(Instrument.BASS, 1), new WantedSlot(Instrument.KEY, 2));
        Long id = recruitPostRepository.saveAndFlush(post).getId();

        RecruitPost found = recruitPostRepository.findById(id).orElseThrow();

        assertThat(found.getWantedSlots())
                .containsExactly(new WantedSlot(Instrument.BASS, 1), new WantedSlot(Instrument.KEY, 2));
    }

    @Test
    @DisplayName("조건을 안 주면(null) 전부 돌려준다")
    void search_withAllNullFilters_returnsEverything() {
        recruitPostRepository.saveAll(List.of(
                teamPost("공고1", Region.SEOUL, slot(Instrument.BASS)),
                groupPost("공고2", Region.BUSAN, slot(Instrument.DRUM))));
        recruitPostRepository.flush();

        Page<RecruitPost> result = recruitPostRepository.search(null, null, null, null, FIRST_PAGE);

        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("targetType=GROUP은 target_group_id가 채워진 공고만, TEAM은 target_team_id가 채워진 공고만 돌려준다")
    void search_byTargetType() {
        recruitPostRepository.saveAll(List.of(
                teamPost("팀 대상", Region.SEOUL, slot(Instrument.BASS)),
                groupPost("모임 대상", Region.SEOUL, slot(Instrument.BASS))));
        recruitPostRepository.flush();

        assertThat(recruitPostRepository.search(TargetType.GROUP, null, null, null, FIRST_PAGE).getContent())
                .extracting(RecruitPost::getTitle).containsExactly("모임 대상");
        assertThat(recruitPostRepository.search(TargetType.TEAM, null, null, null, FIRST_PAGE).getContent())
                .extracting(RecruitPost::getTitle).containsExactly("팀 대상");
    }

    @Test
    @DisplayName("instrument는 모집 슬롯에 그 악기가 하나라도 있는 공고만 돌려준다 (JSON_CONTAINS)")
    void search_byInstrument_matchesAnySlot() {
        recruitPostRepository.saveAll(List.of(
                teamPost("베이스만", Region.SEOUL, slot(Instrument.BASS)),
                teamPost("베이스+키보드", Region.SEOUL, slot(Instrument.BASS), new WantedSlot(Instrument.KEY, 3)),
                teamPost("드럼만", Region.SEOUL, slot(Instrument.DRUM))));
        recruitPostRepository.flush();

        assertThat(recruitPostRepository.search(null, null, Instrument.BASS, null, FIRST_PAGE).getContent())
                .extracting(RecruitPost::getTitle).containsExactlyInAnyOrder("베이스만", "베이스+키보드");
        assertThat(recruitPostRepository.search(null, null, Instrument.KEY, null, FIRST_PAGE).getContent())
                .extracting(RecruitPost::getTitle).containsExactly("베이스+키보드");
        assertThat(recruitPostRepository.search(null, null, Instrument.VOCAL, null, FIRST_PAGE).getContent())
                .isEmpty();
    }

    @Test
    @DisplayName("instrument 필터는 언더스코어가 든 악기(EL_GT)도 정확히 그 악기만 맞춘다")
    void search_byInstrument_withUnderscoreValue() {
        recruitPostRepository.saveAll(List.of(
                teamPost("일렉", Region.SEOUL, slot(Instrument.EL_GT)),
                teamPost("어쿠스틱", Region.SEOUL, slot(Instrument.AC_GT))));
        recruitPostRepository.flush();

        assertThat(recruitPostRepository.search(null, null, Instrument.EL_GT, null, FIRST_PAGE).getContent())
                .extracting(RecruitPost::getTitle).containsExactly("일렉");
    }

    @Test
    @DisplayName("region·status·instrument·targetType 조건을 모두 만족하는 공고만 돌려준다")
    void search_combinesAllFilters() {
        RecruitPost closed = teamPost("마감됨", Region.SEOUL, slot(Instrument.BASS));
        closed.close();
        recruitPostRepository.saveAll(List.of(
                teamPost("일치", Region.SEOUL, slot(Instrument.BASS)),
                teamPost("지역 다름", Region.BUSAN, slot(Instrument.BASS)),
                teamPost("악기 다름", Region.SEOUL, slot(Instrument.DRUM)),
                groupPost("대상 다름", Region.SEOUL, slot(Instrument.BASS)),
                closed));
        recruitPostRepository.flush();

        Page<RecruitPost> result = recruitPostRepository.search(
                TargetType.TEAM, Region.SEOUL, Instrument.BASS, RecruitStatus.OPEN, FIRST_PAGE);

        assertThat(result.getContent()).extracting(RecruitPost::getTitle).containsExactly("일치");
    }
}
