package com.splitopt.backend.global.exception;

import com.splitopt.backend.global.response.ApiResponse;
import com.splitopt.backend.global.response.ErrorField;
import org.springframework.validation.FieldError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
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

    /**
     * 요청 본문을 읽지 못한 경우 (잘못된 JSON · 형식이 맞지 않는 날짜 · 없는 enum 값 등).
     *
     * <p>이 핸들러가 없으면 아래 {@code Exception} 핸들러로 떨어져 <b>500 "서버 오류가
     * 발생했습니다"</b>가 나간다. 원인은 요청 쪽에 있는데 서버 장애처럼 보여, 프론트가 무엇을
     * 잘못 보냈는지 알 방법이 없다. 실제로 지출 등록 연동이 이 증상으로 막혔다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("HttpMessageNotReadable: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(DEFAULT_MESSAGE,
                        List.of(new ErrorField(null, "INVALID_INPUT",
                                "요청 형식이 올바르지 않습니다."))));
    }

    /**
     * 필수 파라미터 누락·타입 불일치 (예: {@code ?page=abc}).
     * 같은 이유로 400이어야 하며, 어떤 파라미터가 문제인지 알려준다.
     */
    @ExceptionHandler({ServletRequestBindingException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleBinding(Exception e) {
        String field = (e instanceof MethodArgumentTypeMismatchException mismatch) ? mismatch.getName() : null;
        log.warn("RequestBindingException: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(DEFAULT_MESSAGE,
                        List.of(new ErrorField(field, "INVALID_INPUT",
                                "요청 파라미터가 올바르지 않습니다."))));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

    private ErrorField toErrorField(FieldError err) {
        String field = err.getField();
        String code = resolveValidationCode(err);
        String message = err.getDefaultMessage() != null ? err.getDefaultMessage() : DEFAULT_MESSAGE;
        return new ErrorField(field, code, message);
    }

    private String resolveValidationCode(FieldError err) {
        String field = err.getField();
        String annotation = err.getCode(); // Email, Size, NotBlank...

        if ("email".equals(field)) {
            return "EMAIL_INVALID";
        }
        if ("password".equals(field) && "Size".equals(annotation)) {
            Object rejected = err.getRejectedValue();
            if (rejected instanceof String s && s.length() > 64) {
                return "PASSWORD_TOO_LONG";
            }
            return "PASSWORD_TOO_SHORT";
        }
        return "INVALID_INPUT";
    }
}
