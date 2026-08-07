package com.splitopt.backend.global.exception;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.response.ErrorField;
import org.springframework.validation.FieldError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.List;

/**
 * 프로젝트 전역 예외 처리. 여기서 모든 예외를 공통 응답 포맷으로 변환한다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final String DEFAULT_MESSAGE = "입력값을 확인해주세요.";

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        log.warn("BusinessException: {} - {}", code.name(), e.getMessage());

        ErrorField error = new ErrorField(code.getField(), code.getCode(), e.getMessage());
        String topMessage = (code.getField() != null) ? DEFAULT_MESSAGE : e.getMessage();

        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(topMessage, List.of(error)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        List<ErrorField> errors = e.getBindingResult().getFieldErrors().stream()
                .map(this::toErrorField)
                .toList();
        if (errors.isEmpty()) {
            errors = List.of(new ErrorField(null, "INVALID_INPUT", DEFAULT_MESSAGE));
        }
        log.warn("ValidationException: {}", errors);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(DEFAULT_MESSAGE, errors));
    }

    // @RequestParam / @PathVariable 등 파라미터 검증 실패 (Spring Boot 4)
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleParameterValidation(HandlerMethodValidationException e) {
        String message = e.getAllErrors().stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .orElse(ErrorCode.INVALID_INPUT.getMessage());
        log.warn("ParameterValidationException: {}", message);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(DEFAULT_MESSAGE,
                        List.of(new ErrorField(null, "INVALID_INPUT", message))));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException e) {
        boolean duplicateEmail = String.valueOf(e.getMostSpecificCause().getMessage())
                .contains("uk_users_email");
        log.warn("DataIntegrityViolation: {}", duplicateEmail ? "uk_users_email" : "other");

        if (duplicateEmail) {
            ErrorCode code = ErrorCode.DUPLICATE_EMAIL;
            return ResponseEntity.status(code.getStatus())
                    .body(ApiResponse.fail(DEFAULT_MESSAGE,
                            List.of(new ErrorField(code.getField(), code.getCode(), code.getMessage()))));
        }
        ErrorCode code = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(code.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    private ErrorField toErrorField(FieldError err) {
        String field = err.getField();
        String annotation = err.getCode(); // Email, Size, NotBlank...
        String code = resolveValidationCode(field, annotation);
        String message = err.getDefaultMessage() != null ? err.getDefaultMessage() : DEFAULT_MESSAGE;
        return new ErrorField(field, code, message);
    }

    private String resolveValidationCode(String field, String annotation) {
        if ("email".equals(field)) {
            return "EMAIL_INVALID";
        }
        if ("password".equals(field) && "Size".equals(annotation)) {
            return "PASSWORD_TOO_SHORT";
        }
        return "INVALID_INPUT";
    }
}
