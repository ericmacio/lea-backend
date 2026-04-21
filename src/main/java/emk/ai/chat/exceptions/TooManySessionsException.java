package emk.ai.chat.exceptions;

public class TooManySessionsException extends RuntimeException {
    public TooManySessionsException(String message) {
        super(message);
    }
}
