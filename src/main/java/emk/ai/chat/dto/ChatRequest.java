package emk.ai.chat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ChatRequest(
        @NotNull(message = "Session token absent. Votre requête est invalide")
        String token,
        @NotEmpty(message = "La liste de message ne doit pas être vide")
        @Size(max = 20, message = "Votre requête contient trop de messages (>20)")
        List<@Valid MessageDto> messages
) {
    public record MessageDto(
            @NotEmpty(message = "Le role est absent")
            String role,
            @Size(max = 1000, message = "Votre message est trop long (>1000)")
            String content
    ) {
    }
}
