package com.moyeorock.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserInstrumentTest {

    @Test
    @DisplayName("create는 악기·실력만 받는다 (ETC·customInstrument 폐지, 2026-09-28)")
    void create_withoutCustomInstrument() {
        UserInstrument userInstrument = UserInstrument.create(1L, Instrument.BASS, Level.INTERMEDIATE);

        assertThat(userInstrument.getUserId()).isEqualTo(1L);
        assertThat(userInstrument.getInstrument()).isEqualTo(Instrument.BASS);
        assertThat(userInstrument.getLevel()).isEqualTo(Level.INTERMEDIATE);
    }

    @Test
    @DisplayName("changeLevel로 실력만 바꿀 수 있다")
    void changeLevel_updatesLevelOnly() {
        UserInstrument userInstrument = UserInstrument.create(1L, Instrument.BASS, Level.BEGINNER);

        userInstrument.changeLevel(Level.ADVANCED);

        assertThat(userInstrument.getLevel()).isEqualTo(Level.ADVANCED);
        assertThat(userInstrument.getInstrument()).isEqualTo(Instrument.BASS);
    }
}
