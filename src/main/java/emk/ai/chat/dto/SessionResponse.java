package emk.ai.chat.dto;

import java.time.Instant;

public record SessionResponse(
        String token,
        Instant expiresAt,
        int maxMessages
) {
}
