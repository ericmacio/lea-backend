package emk.ai.chat.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.http.HttpStatus;

public record ErrorMessage(
        @JsonProperty("status_code")
        HttpStatus statusCode,
        String path,
        String error
) {
}
