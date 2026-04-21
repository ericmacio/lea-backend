package emk.ai.chat.service;

import emk.ai.chat.entity.ChatSession;
import emk.ai.chat.exceptions.InvalidSessionException;
import emk.ai.chat.exceptions.QuotaExceededException;
import emk.ai.chat.exceptions.SessionExpiredException;
import emk.ai.chat.exceptions.TooManySessionsException;
import emk.ai.chat.repository.ChatSessionRepository;
import emk.ai.chat.util.Utils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@Transactional
public class ChatSessionService {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionService.class);

    @Value("${chat.session.messages-per-session}")
    private int messagePerSessionLimit;

    @Value("${chat.session.session-per-ip}")
    private int sessionPerIpLimit;

    @Value("${chat.session.ttl-minute}")
    private int sessionTTL;

    private static final int MAX_TOKENS = 50_000;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    public ChatSession createChatSession(HttpServletRequest httpRequest, String fingerprint) {

        // Check current sessions for this IP
        // Get remote address
        String ip = Utils.getClientIp(httpRequest);
        log.info("ip: {}", ip);
        long activeSessions = chatSessionRepository.countActiveSessionsByIp(ip, Instant.now());
        if (activeSessions > sessionPerIpLimit) {
            log.error("ERROR: max number of sessions exceeded");
            throw new TooManySessionsException("Désolé vous avez atteint le nombre maximal de sessions autorisées. Re-essayez dans quelques minutes");
        }

        Instant instant = Instant.now();
        ChatSession chatSession = new ChatSession(generateSecureToken(), fingerprint, ip, 0, messagePerSessionLimit, 0,
                MAX_TOKENS, instant, instant.plus(Duration.ofMinutes(sessionTTL)), instant, false);

        return chatSessionRepository.save(chatSession);
    }

    // Check is done before each message
    public ChatSession validateAndConsume(String token, String ip) {

        ChatSession chatSession = chatSessionRepository.findByToken(token)
                .orElseThrow(() -> new InvalidSessionException("Token inconnu"));

        // Chained checks
        if (chatSession.isRevoked()) {
            throw new InvalidSessionException("Désolé mais votre session a été révoquée");
        }
        if (Instant.now().isAfter(chatSession.getExpiresAt())) {
            throw new SessionExpiredException("Désolé mais votre session a expirée");
        }
        if (!chatSession.getIpAddress().equals(ip)) {
            // IP may have been stolen
            chatSession.setRevoked(true);
            chatSessionRepository.save(chatSession);
            throw new SecurityException("IP non valide, session révoquée");
        }
        if (chatSession.getMessagesUsed() >= chatSession.getMessagesLimit()) {
            throw new QuotaExceededException("Désolé vous avez atteint le quota de messages autorisé");
        }
        if (chatSession.getTokensUsed() >= chatSession.getTokensLimit()) {
            throw new QuotaExceededException("Désolé vous avez atteint votre quota de tokens autorisé");
        }

        // Increase messages count
        chatSession.setMessagesUsed(chatSession.getMessagesUsed() + 1);
        chatSession.setLastUsedAt(Instant.now());

        return chatSessionRepository.save(chatSession);
    }

    // Tokens update
    public void updateTokensUsed(String token, int tokensConsumed) {
        chatSessionRepository.findByToken(token).ifPresent(session -> {
            session.setTokensUsed(session.getTokensUsed() + tokensConsumed);
            chatSessionRepository.save(session);
        });
    }

    private String generateSecureToken() {
        // Token generation
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
