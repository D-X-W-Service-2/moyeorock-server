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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// erd.md §2: created_at·updated_at 없음 — BaseEntity/BaseTimeEntity 상속 없음(conventions.md 상속 규칙).
@Entity
@Table(name = "user_instruments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserInstrument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Instrument instrument;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private Level level;

    public static UserInstrument create(Long userId, Instrument instrument, Level level) {
        UserInstrument userInstrument = new UserInstrument();
        userInstrument.userId = userId;
        userInstrument.instrument = instrument;
        userInstrument.level = level;
        return userInstrument;
    }

    public void changeLevel(Level level) {
        this.level = level;
    }
}
