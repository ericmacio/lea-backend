package emk.ai.chat.service;

import emk.ai.chat.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupTask.class);

    @Autowired
    private ChatSessionRepository sessionRepository;

    // Every hour, removed session that expired from more than 24h
    @Scheduled(fixedRate = 3_600_000)
    public void cleanExpiredSessions() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(24));
        int deleted = sessionRepository.deleteExpiredBefore(cutoff);
        log.info("Expired sessions deleted: {}", deleted);
    }
}
