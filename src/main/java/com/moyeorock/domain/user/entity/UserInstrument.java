package com.moyeorock.domain.user.entity;

import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 연주 세션 + 실력 (erd.md §2 user_instruments). 타임스탬프 컬럼이 없어 상속하지 않는다.
 * user 참조는 @ManyToOne이 아니라 Long userId (architecture.md §5, DB FK 없음).
 * customInstrument는 없다 — 악기 ETC 제거 결정(2026-09-28).
 */
@Entity
@Table(name = "user_instruments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_instruments_user_id_instrument",
                columnNames = {"user_id", "instrument"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Level level;

    public static UserInstrument create(Long userId, Instrument instrument, Level level) {
        UserInstrument userInstrument = new UserInstrument();
        userInstrument.userId = userId;
        userInstrument.instrument = instrument;
        userInstrument.level = level;
        return userInstrument;
    }
}
