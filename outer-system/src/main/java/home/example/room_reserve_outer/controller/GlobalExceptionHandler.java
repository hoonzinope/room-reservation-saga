package home.example.room_reserve_outer.controller;

import home.example.room_reserve_outer.data.dto.ApiErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import java.time.OffsetDateTime;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception,
                                                                         HttpServletRequest request) {
        HttpStatus status = exception.getStatus();
        log.warn("request failed status={} path={} message={}",
                status.value(),
                request.getRequestURI(),
                exception.getReason());
        return ResponseEntity.status(status).body(buildErrorResponse(status, exception.getReason(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleException(Exception exception, HttpServletRequest request) {
        log.error("request failed status={} path={}", HttpStatus.INTERNAL_SERVER_ERROR.value(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", request));
    }

    private ApiErrorResponse buildErrorResponse(HttpStatus status, String message, HttpServletRequest request) {
        return ApiErrorResponse.builder()
                .timestamp(OffsetDateTime.now().toString())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .build();
    }
}
