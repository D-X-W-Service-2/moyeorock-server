package com.moyeorock.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeorock.config.TestcontainersConfig;
import com.moyeorock.domain.user.entity.User;
import com.moyeorock.domain.user.entity.UserInstrument;
import com.moyeorock.domain.user.enums.UserStatus;
import com.moyeorock.global.common.enums.Genre;
import com.moyeorock.global.common.enums.Instrument;
import com.moyeorock.global.common.enums.Level;
import com.moyeorock.global.common.enums.Region;
import com.moyeorock.global.config.JpaAuditingConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

// 실제 MySQL(Testcontainers) + Flyway V2 위에서 돈다. 컨텍스트가 뜨는 것 자체가
// ddl-auto: validate(엔티티 ↔ V2 스키마 정합) 검증이다 — genres json, TINYINT(1) boolean 포함.
@DataJpaTest
@Import({JpaAuditingConfig.class, TestcontainersConfig.class})
class UserRepositoryTest {

    @Autowired
    TestEntityManager entityManager;
    @Autowired
    UserRepository userRepository;
    @Autowired
    UserInstrumentRepository userInstrumentRepository;

    private User newUser(String nickname) {
        return User.signupWithEmail(nickname + "@example.com", "hash", nickname, LocalDateTime.now());
    }

    private User saveUser(String nickname, UserStatus status) {
        User user = newUser(nickname);
        if (status != null) {
            ReflectionTestUtils.setField(user, "status", status);
        }
        return entityManager.persistAndFlush(user);
    }

    @Test
    @DisplayName("genres는 JSON 배열 문자열로 저장되고 List<Genre>로 복원된다")
    void genres_round_trip_as_json_array() {
        User user = newUser("서준");
        user.completeOnboarding("서준", Region.SEOUL, List.of(Genre.ROCK, Genre.INDIE));
        User saved = entityManager.persistAndFlush(user);
        entityManager.clear();

        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getGenres()).containsExactly(Genre.ROCK, Genre.INDIE);

        String raw = (String) entityManager.getEntityManager()
                .createNativeQuery("select genres from users where id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();
        assertThat(raw.replace(" ", "")).isEqualTo("[\"ROCK\",\"INDIE\"]");
    }

    @Test
    @DisplayName("genres가 null이면 null로, 빈 배열이면 빈 리스트로 복원된다")
    void genres_null_and_empty() {
        User nullGenres = entityManager.persistAndFlush(newUser("a"));
        User withEmptyGenres = newUser("b");
        withEmptyGenres.completeOnboarding("b", Region.SEOUL, List.of());
        User emptyGenres = entityManager.persistAndFlush(withEmptyGenres);
        entityManager.clear();

        assertThat(userRepository.findById(nullGenres.getId()).orElseThrow().getGenres()).isNull();
        assertThat(userRepository.findById(emptyGenres.getId()).orElseThrow().getGenres()).isEmpty();
    }

    @Test
    @DisplayName("createdAt·updatedAt이 자동으로 채워지고 기본값(USER·ACTIVE·공개)이 들어간다")
    void auditing_and_defaults() {
        User saved = saveUser("서준", null);
        entityManager.clear();

        User found = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getUpdatedAt()).isNotNull();
        assertThat(found.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(found.isRecommendable()).isTrue();
        assertThat(found.isActivityPublic()).isTrue();
        assertThat(found.isOnboardingCompleted()).isFalse();
    }

    @Test
    @DisplayName("findByIdAndStatus는 상태가 다르면 비어 있다")
    void find_by_id_and_status_filters_status() {
        User withdrawn = saveUser("탈퇴자", UserStatus.WITHDRAWN);

        assertThat(userRepository.findByIdAndStatus(withdrawn.getId(), UserStatus.ACTIVE)).isEmpty();
        assertThat(userRepository.findByIdAndStatus(withdrawn.getId(), UserStatus.WITHDRAWN)).isPresent();
    }

    @Test
    @DisplayName("existsByNickname은 탈퇴 사용자의 닉네임도 점유로 본다")
    void exists_by_nickname_includes_withdrawn() {
        saveUser("서준", UserStatus.WITHDRAWN);

        assertThat(userRepository.existsByNickname("서준")).isTrue();
        assertThat(userRepository.existsByNickname("없는닉네임")).isFalse();
    }

    @Test
    @DisplayName("검색은 ACTIVE만, 닉네임 부분 일치, 닉네임 오름차순 페이징")
    void search_active_only_partial_match_paged() {
        saveUser("서준밴드", UserStatus.ACTIVE);
        saveUser("서준", UserStatus.ACTIVE);
        saveUser("서준탈퇴", UserStatus.WITHDRAWN);
        saveUser("민서", UserStatus.ACTIVE);

        Page<User> page = userRepository.findByStatusAndNicknameContaining(
                UserStatus.ACTIVE, "서준", PageRequest.of(0, 1, Sort.by("nickname").ascending()));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(User::getNickname).containsExactly("서준");
        assertThat(page.isLast()).isFalse();
    }

    @Test
    @DisplayName("같은 사용자에게 같은 세션을 두 번 넣으면 UNIQUE(user_id, instrument)에 걸린다")
    void duplicate_instrument_violates_unique() {
        User user = saveUser("서준", UserStatus.ACTIVE);
        userInstrumentRepository.saveAndFlush(UserInstrument.create(user.getId(), Instrument.BASS, Level.NOVICE));

        assertThatThrownBy(() -> userInstrumentRepository.saveAndFlush(
                UserInstrument.create(user.getId(), Instrument.BASS, Level.ADVANCED)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("벌크 삭제 후 같은 세션을 한 트랜잭션 안에서 다시 넣어도 UNIQUE에 걸리지 않는다")
    void delete_all_then_reinsert_same_instrument_in_one_transaction() {
        User user = saveUser("서준", UserStatus.ACTIVE);
        userInstrumentRepository.saveAll(List.of(
                UserInstrument.create(user.getId(), Instrument.BASS, Level.NOVICE),
                UserInstrument.create(user.getId(), Instrument.KEY, Level.BEGINNER)));
        entityManager.flush();

        userInstrumentRepository.deleteAllByUserId(user.getId());
        List<UserInstrument> replaced = userInstrumentRepository.saveAll(List.of(
                UserInstrument.create(user.getId(), Instrument.BASS, Level.ADVANCED)));
        entityManager.flush();
        entityManager.clear();

        List<UserInstrument> found = userInstrumentRepository.findAllByUserIdOrderByIdAsc(user.getId());
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getId()).isEqualTo(replaced.get(0).getId());
        assertThat(found.get(0).getLevel()).isEqualTo(Level.ADVANCED);
    }

    @Test
    @DisplayName("이메일 회원이 탈퇴하면 상태·시각·닉네임 치환에 더해 email·passwordHash가 비워진다")
    void withdraw_clears_personal_data_and_frees_nickname() {
        User user = entityManager.persistAndFlush(newUser("서준"));

        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(found.getWithdrawnAt()).isNotNull();
        assertThat(found.getNickname()).isEqualTo("탈퇴회원_" + user.getId());
        assertThat(found.getEmail()).isNull();
        assertThat(found.getKakaoId()).isNull();
        assertThat(found.getPasswordHash()).isNull();
        // 원래 닉네임·이메일이 풀려 재사용 가능
        assertThat(userRepository.existsByNickname("서준")).isFalse();
        entityManager.persistAndFlush(newUser("서준"));
    }

    @Test
    @DisplayName("카카오 회원이 탈퇴하면 kakaoId가 비워져 같은 카카오 계정으로 재가입할 수 있다")
    void withdraw_clears_kakao_id_and_allows_rejoin() {
        User user = entityManager.persistAndFlush(User.signupWithKakao("k-1", "서준", LocalDateTime.now()));

        user.withdraw();
        entityManager.flush();
        entityManager.clear();

        User found = userRepository.findById(user.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo(UserStatus.WITHDRAWN);
        assertThat(found.getKakaoId()).isNull();
        entityManager.persistAndFlush(User.signupWithKakao("k-1", "서준", LocalDateTime.now()));
    }

    @Test
    @DisplayName("findAllByIdInAndStatusNot은 탈퇴 사용자만 빼고 정지 사용자는 돌려준다")
    void find_all_by_id_in_excluding_withdrawn() {
        User active = saveUser("서준", null);
        User suspended = saveUser("현빈", UserStatus.SUSPENDED);
        User withdrawn = saveUser("준호", null);
        withdrawn.withdraw();
        entityManager.flush();
        entityManager.clear();

        List<User> found = userRepository.findAllByIdInAndStatusNot(
                List.of(active.getId(), suspended.getId(), withdrawn.getId(), 999_999L), UserStatus.WITHDRAWN);

        assertThat(found).extracting(User::getId).containsExactlyInAnyOrder(active.getId(), suspended.getId());
    }

    @Test
    @DisplayName("findAllByUserIdInOrderByUserIdAscIdAsc는 넘긴 사용자의 세션만 userId → id 순으로 돌려준다")
    void find_instruments_by_user_id_in_ordered() {
        User a = saveUser("a", null);
        User b = saveUser("b", null);
        User c = saveUser("c", null);
        User outsider = saveUser("d", null);
        UserInstrument aBass = userInstrumentRepository.save(UserInstrument.create(a.getId(), Instrument.BASS, Level.NOVICE));
        UserInstrument bDrum = userInstrumentRepository.save(UserInstrument.create(b.getId(), Instrument.DRUM, Level.ADVANCED));
        UserInstrument aKey = userInstrumentRepository.save(UserInstrument.create(a.getId(), Instrument.KEY, Level.BEGINNER));
        userInstrumentRepository.save(UserInstrument.create(outsider.getId(), Instrument.VOCAL, Level.NOVICE));
        entityManager.flush();
        entityManager.clear();

        List<UserInstrument> found = userInstrumentRepository.findAllByUserIdInOrderByUserIdAscIdAsc(
                List.of(a.getId(), b.getId(), c.getId()));

        // a(id 작은 순: BASS → KEY) 다음 b. 세션 없는 c와 범위 밖 d는 없다
        assertThat(found).extracting(UserInstrument::getId)
                .containsExactly(aBass.getId(), aKey.getId(), bDrum.getId());
    }

    @Test
    @DisplayName("findAllByIdInAndStatusAndNicknameContaining은 넘긴 id 중 ACTIVE·부분 일치만 돌려준다")
    void filter_by_nickname_within_ids_active_only() {
        User matched = saveUser("서준밴드", UserStatus.ACTIVE);
        User suspended = saveUser("서준", UserStatus.SUSPENDED);
        User withdrawn = saveUser("서준탈퇴", UserStatus.WITHDRAWN);
        User noMatch = saveUser("민서", UserStatus.ACTIVE);
        User outsider = saveUser("서준아웃", UserStatus.ACTIVE);
        entityManager.clear();

        List<User> found = userRepository.findAllByIdInAndStatusAndNicknameContaining(
                List.of(matched.getId(), suspended.getId(), withdrawn.getId(), noMatch.getId(), 999_999L),
                UserStatus.ACTIVE, "서준");

        assertThat(found).extracting(User::getId).containsExactly(matched.getId());
        assertThat(found).extracting(User::getId).doesNotContain(outsider.getId());
    }

    @Test
    @DisplayName("닉네임 부분 일치의 %·_는 와일드카드가 아니라 글자 그대로 비교된다")
    void nickname_containing_escapes_like_wildcards() {
        User underscore = saveUser("a_b", UserStatus.ACTIVE);
        User plain = saveUser("acb", UserStatus.ACTIVE);
        entityManager.clear();
        List<Long> ids = List.of(underscore.getId(), plain.getId());

        assertThat(userRepository.findAllByIdInAndStatusAndNicknameContaining(ids, UserStatus.ACTIVE, "_"))
                .extracting(User::getId).containsExactly(underscore.getId());
        assertThat(userRepository.findAllByIdInAndStatusAndNicknameContaining(ids, UserStatus.ACTIVE, "%"))
                .isEmpty();
    }
}
