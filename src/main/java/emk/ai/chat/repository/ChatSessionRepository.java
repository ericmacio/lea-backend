package emk.ai.chat.repository;

import emk.ai.chat.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, String> {

    Optional<ChatSession> findByToken(String token);

    // Active session by IP
    @Query("""
        SELECT COUNT(s) FROM ChatSession s
        WHERE s.ipAddress = :ip
          AND s.expiresAt > :now
          AND s.revoked = false
        """)
    long countActiveSessionsByIp(
            @Param("ip") String ip,
            @Param("now") Instant now
    );

    // Cleanup of expired sessions
    // Called by scheduler
    @Modifying
    @Query("""
        DELETE FROM ChatSession s
        WHERE s.expiresAt < :cutoff
        """)
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    // Revocation of all IP sessions
    @Modifying
    @Query("""
        UPDATE ChatSession s
        SET s.revoked = true
        WHERE s.ipAddress = :ip
        """)
    int revokeAllByIp(@Param("ip") String ip);

    // Monitoring of sessions created
    @Query("""
        SELECT COUNT(s) FROM ChatSession s
        WHERE s.createdAt > :since
          AND s.revoked = false
        """)
    long countSessionsCreatedSince(@Param("since") Instant since);
}
