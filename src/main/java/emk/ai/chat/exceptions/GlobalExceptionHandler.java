package emk.ai.chat.exceptions;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.List;

@RestControllerAdvice //Just need to return a java body object instead of a ResponseEntity
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({TooManySessionsException.class, TooManyMessagesException.class, QuotaExceededException.class})
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public @ResponseBody ErrorMessage handleLimitExceededException(HttpServletRequest request, Exception e) {
        log.warn("handleLimitExceededException. Build HTTP TOO_MANY_REQUESTS response");
        return createErrorMessage(HttpStatus.TOO_MANY_REQUESTS, request, e.getMessage());
    }

    @ExceptionHandler({SessionExpiredException.class, InvalidSessionException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public @ResponseBody ErrorMessage handleInvalidSessionException(HttpServletRequest request, Exception e) {
        log.warn("handleInvalidSessionException. Build HTTP UNAUTHORIZED response");
        return createErrorMessage(HttpStatus.UNAUTHORIZED, request, e.getMessage());
    }

    @ExceptionHandler(UnAuthorizedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public @ResponseBody ErrorMessage handleUnAuthorizedException(HttpServletRequest request, Exception e) {
        log.warn("handleUnAuthorizedException. Build UNAUTHORIZED response");
        return createErrorMessage(HttpStatus.UNAUTHORIZED, request, e.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public @ResponseBody ErrorMessage handleSecurityException(HttpServletRequest request, Exception e) {
        log.warn("handleSecurityException. Build UNAUTHORIZED response");
        return createErrorMessage(HttpStatus.BAD_REQUEST, request, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public @ResponseBody ErrorMessage handleNotValidException(HttpServletRequest request, MethodArgumentNotValidException e) {
        List<String> errors = getErrorsMsgList(e);
        log.warn("handleNotValidException. Build BAD_REQUEST response");
        return createErrorMessage(HttpStatus.BAD_REQUEST, request, errors.toString());
    }

    private ErrorMessage createErrorMessage(HttpStatus httpStatus, HttpServletRequest request, String message) {
        final String path = request.getServletPath();
        log.warn("Returning HTTP status: {} for path: {}, message: {}", httpStatus, path, message);
        return new ErrorMessage(httpStatus, path, message);
    }

    private static List<String> getErrorsMsgList(MethodArgumentNotValidException e) {
        return e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .toList();
    }

}
