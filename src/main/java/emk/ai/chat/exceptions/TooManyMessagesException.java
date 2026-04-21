package emk.ai.chat.exceptions;

public class TooManyMessagesException extends RuntimeException {
    public TooManyMessagesException(String message) {
        super(message);
    }
}
