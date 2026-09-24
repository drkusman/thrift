package ng.asuu.thrift.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Without this, a thrown ResponseStatusException's reason never reaches the client - Spring's default
 * error page only returns {timestamp, status, error, path}, dropping every validation message the app
 * relies on (e.g. "Both guarantors must accept before this loan can be approved"). The frontend reads
 * the reason from a "message" field, so this guarantees it's always there.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handle(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason()));
    }
}
