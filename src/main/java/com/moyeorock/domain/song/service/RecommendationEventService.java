package com.moyeorock.domain.song.service;

import com.moyeorock.global.exception.BusinessException;
import com.moyeorock.global.exception.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Records served recommendations and authenticated feedback without sending user IDs to AI. */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationEventService {

    private final JdbcTemplate jdbcTemplate;

    public record ServedSong(Long songId, int position, double score) {
    }

    public String recordServed(Long actorUserId, Long teamId, String mode, String ranker,
                               List<ServedSong> songs) {
        String requestId = UUID.randomUUID().toString();
        if (songs.isEmpty()) {
            return requestId;
        }
        try {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO recommendation_events
                        (request_id, actor_user_id, team_id, mode, candidate_type, candidate_id,
                         event_type, position, score, ranker)
                    VALUES (?, ?, ?, ?, 'SONG', ?, 'SERVED', ?, ?, ?)
                    """, songs, songs.size(), (statement, song) -> {
                statement.setString(1, requestId);
                statement.setLong(2, actorUserId);
                if (teamId == null) {
                    statement.setNull(3, java.sql.Types.BIGINT);
                } else {
                    statement.setLong(3, teamId);
                }
                statement.setString(4, mode);
                statement.setLong(5, song.songId());
                statement.setInt(6, song.position());
                statement.setDouble(7, song.score());
                statement.setString(8, ranker);
            });
        } catch (DataAccessException error) {
            // Recommendation delivery remains available if the event store is unavailable.
            log.warn("Recommendation event logging unavailable: {}", error.toString());
        }
        return requestId;
    }

    public void recordFeedback(Long actorUserId, String requestId, Long songId, SongFeedbackAction action) {
        if (requestId == null || songId == null || songId <= 0 || action == null) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        try {
            UUID.fromString(requestId);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        int written = jdbcTemplate.update("""
                INSERT IGNORE INTO recommendation_events
                    (request_id, actor_user_id, team_id, mode, candidate_type, candidate_id,
                     event_type, position, score, ranker)
                SELECT request_id, actor_user_id, team_id, mode, candidate_type, candidate_id,
                       ?, position, score, ranker
                FROM recommendation_events
                WHERE request_id = ? AND actor_user_id = ? AND candidate_type = 'SONG'
                  AND candidate_id = ? AND event_type = 'SERVED'
                LIMIT 1
                """, action.name(), requestId, actorUserId, songId);
        if (written == 0) {
            Integer served = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM recommendation_events
                    WHERE request_id = ? AND actor_user_id = ? AND candidate_type = 'SONG'
                      AND candidate_id = ? AND event_type = 'SERVED'
                    """, Integer.class, requestId, actorUserId, songId);
            if (served == null || served == 0) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED);
            }
        }
    }

    /** An actual team invitation outcome, not proof that a recommendation was shown. */
    public void recordTeamInvitation(Long joinRequestId, Long inviterId, Long teamId, Long inviteeId,
                                     String eventType) {
        if (teamId == null) {
            return;
        }
        String requestId = UUID.nameUUIDFromBytes(("team-invite:" + joinRequestId)
                .getBytes(StandardCharsets.UTF_8)).toString();
        try {
            jdbcTemplate.update("""
                    INSERT IGNORE INTO recommendation_events
                        (request_id, actor_user_id, team_id, mode, candidate_type, candidate_id,
                         event_type, ranker)
                    VALUES (?, ?, ?, 'TEAM_MEMBER', 'USER', ?, ?, 'ACTUAL_INVITATION')
                    """, requestId, inviterId, teamId, inviteeId, eventType);
        } catch (DataAccessException error) {
            log.warn("Team invitation event logging unavailable: {}", error.toString());
        }
    }

    /** Newly added team choices are positives; they are not attributed to a recommendation request. */
    public void recordTeamSongSelections(Long actorUserId, Long teamId, List<Long> songIds) {
        if (songIds.isEmpty()) {
            return;
        }
        String requestId = UUID.randomUUID().toString();
        try {
            jdbcTemplate.batchUpdate("""
                    INSERT INTO recommendation_events
                        (request_id, actor_user_id, team_id, mode, candidate_type, candidate_id,
                         event_type, ranker)
                    VALUES (?, ?, ?, 'TEAM', 'SONG', ?, 'SELECTED', 'ACTUAL_TEAM_CHOICE')
                    """, songIds, songIds.size(), (statement, songId) -> {
                statement.setString(1, requestId);
                statement.setLong(2, actorUserId);
                statement.setLong(3, teamId);
                statement.setLong(4, songId);
            });
        } catch (DataAccessException error) {
            log.warn("Team choice event logging unavailable: {}", error.toString());
        }
    }

    /** A real saved-song action, with no inferred exposure or ranking attribution. */
    public void recordSongBookmark(Long actorUserId, Long songId) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO recommendation_events
                        (request_id, actor_user_id, mode, candidate_type, candidate_id,
                         event_type, ranker)
                    VALUES (?, ?, 'PERSONAL', 'SONG', ?, 'BOOKMARKED', 'ACTUAL_BOOKMARK')
                    """, UUID.randomUUID().toString(), actorUserId, songId);
        } catch (DataAccessException error) {
            log.warn("Song bookmark event logging unavailable: {}", error.toString());
        }
    }

    public enum SongFeedbackAction {
        VIEWED, CLICKED, BOOKMARKED, SELECTED
    }
}
