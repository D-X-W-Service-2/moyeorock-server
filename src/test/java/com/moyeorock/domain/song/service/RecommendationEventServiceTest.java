package com.moyeorock.domain.song.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.moyeorock.domain.song.service.RecommendationEventService.SongFeedbackAction;
import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class RecommendationEventServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;
    @InjectMocks private RecommendationEventService service;

    @Test
    void rejectsMalformedRequestBeforeReadingEventStore() {
        assertThatThrownBy(() -> service.recordFeedback(7L, "not-a-uuid", 3L, SongFeedbackAction.CLICKED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void rejectsFeedbackForCandidateNotServedToThisUser() {
        assertThatThrownBy(() -> service.recordFeedback(7L,
                "19bdaf21-a3ec-4765-8845-5b3e4e222cc6", 3L, SongFeedbackAction.SELECTED))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
    }
}
