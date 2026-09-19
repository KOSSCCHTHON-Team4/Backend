package team4.emotionmap.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import team4.emotionmap.contracts.error.ApiError;
import team4.emotionmap.contracts.error.ContractError;
import team4.emotionmap.contracts.error.ErrorCode;
import team4.emotionmap.contracts.error.FieldErrorReason;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * 모든 실패를 {@link ApiError} 한 형태로 통일한다(C03). 매핑 요약:
 * <ul>
 *   <li>{@link ContractError} → 자기 코드·상태</li>
 *   <li>JSON 파싱 실패 → 400 INVALID_JSON / 중복 키 → 400 DUPLICATE_JSON_KEY</li>
 *   <li>모르는 속성·타입 불일치·필수 누락·null → 400 INVALID_REQUEST + fieldErrors</li>
 *   <li>Bean Validation 실패 → 422 VALIDATION_ERROR + fieldErrors</li>
 *   <li>{@code Idempotency-Key} 누락 → 400 IDEMPOTENCY_KEY_REQUIRED, 다른 헤더/파트 누락 → 400 INVALID_REQUEST</li>
 *   <li>multipart 크기 초과 → 413 IMAGE_TOO_LARGE</li>
 *   <li>없는 경로·미지원 메서드 → 404 RESOURCE_NOT_FOUND(존재 여부 비노출)</li>
 *   <li>그 외 → 500 SERVER_ERROR (스택은 requestId 와 함께 서버 로그에만)</li>
 * </ul>
 * 오류 메시지·로그에 요청 본문 원문을 넣지 않는다.
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final ApiErrorWriter writer;

    @ExceptionHandler(ContractError.class)
    public ResponseEntity<ApiError> onContractError(ContractError e, HttpServletRequest request) {
        if (e.httpStatus() >= 500) {
            log.warn("contract error {} requestId={}", e.code(), RequestIds.current(request), e.getCause());
        }
        return writer.toResponse(e, request);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> onResponseStatus(ResponseStatusException e, HttpServletRequest request) {
        return onContractError(ApiErrorWriter.fromResponseStatus(e), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onNotReadable(HttpMessageNotReadableException e, HttpServletRequest request) {
        return writer.toResponse(translateUnreadable(e), request);
    }

    /** 역직렬화기가 던진 ContractError 가 Spring 변환기 밖으로 그대로 나온 경우도 위 핸들러가 받는다. */
    static ContractError translateUnreadable(Throwable e) {
        Optional<ContractError> contract = findCause(e, ContractError.class);
        if (contract.isPresent()) {
            return contract.get();
        }
        Optional<ResponseStatusException> responseStatus = findCause(e, ResponseStatusException.class);
        if (responseStatus.isPresent()) {
            return ApiErrorWriter.fromResponseStatus(responseStatus.get());
        }
        Optional<UnrecognizedPropertyException> unknown = findCause(e, UnrecognizedPropertyException.class);
        if (unknown.isPresent()) {
            return ContractError.of(ErrorCode.INVALID_REQUEST,
                    team4.emotionmap.contracts.error.FieldError.unknown(pathOf(unknown.get())));
        }
        Optional<InvalidNullException> invalidNull = findCause(e, InvalidNullException.class);
        if (invalidNull.isPresent()) {
            return ContractError.of(ErrorCode.INVALID_REQUEST,
                    team4.emotionmap.contracts.error.FieldError.required(pathOf(invalidNull.get())));
        }
        Optional<InvalidFormatException> format = findCause(e, InvalidFormatException.class);
        if (format.isPresent()) {
            return ContractError.of(ErrorCode.INVALID_REQUEST,
                    team4.emotionmap.contracts.error.FieldError.invalid(pathOf(format.get())));
        }
        Optional<MismatchedInputException> mismatch = findCause(e, MismatchedInputException.class);
        if (mismatch.isPresent()) {
            MismatchedInputException m = mismatch.get();
            String original = Objects.requireNonNullElse(m.getOriginalMessage(), "");
            FieldErrorReason reason = original.startsWith("Missing required") || original.contains("null")
                    ? FieldErrorReason.REQUIRED : FieldErrorReason.INVALID_VALUE;
            return ContractError.of(ErrorCode.INVALID_REQUEST,
                    new team4.emotionmap.contracts.error.FieldError(pathOf(m), reason));
        }
        Optional<StreamReadException> stream = findCause(e, StreamReadException.class);
        if (stream.isPresent()) {
            String original = Objects.requireNonNullElse(stream.get().getOriginalMessage(), "");
            if (original.contains("Duplicate")) {
                return ContractError.of(ErrorCode.DUPLICATE_JSON_KEY);
            }
            return ContractError.of(ErrorCode.INVALID_JSON);
        }
        return ContractError.of(ErrorCode.INVALID_JSON);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onBeanValidation(MethodArgumentNotValidException e, HttpServletRequest request) {
        List<team4.emotionmap.contracts.error.FieldError> errors = new ArrayList<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.add(new team4.emotionmap.contracts.error.FieldError(fe.getField(), reasonOf(fe.getCode())));
        }
        return writer.toResponse(ContractError.of(ErrorCode.VALIDATION_ERROR, errors), request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> onMethodValidation(HandlerMethodValidationException e, HttpServletRequest request) {
        List<team4.emotionmap.contracts.error.FieldError> errors = new ArrayList<>();
        e.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(err -> {
            String field = err instanceof FieldError fe ? fe.getField() : result.getMethodParameter().getParameterName();
            String code = err.getCodes() != null && err.getCodes().length > 0 ? err.getCodes()[err.getCodes().length - 1] : null;
            errors.add(new team4.emotionmap.contracts.error.FieldError(
                    field == null ? "request" : field, reasonOf(code)));
        }));
        return writer.toResponse(ContractError.of(ErrorCode.VALIDATION_ERROR, errors), request);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> onMissingHeader(MissingRequestHeaderException e, HttpServletRequest request) {
        if (IDEMPOTENCY_KEY_HEADER.equalsIgnoreCase(e.getHeaderName())) {
            return writer.toResponse(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, request);
        }
        return writer.toResponse(ContractError.of(ErrorCode.INVALID_REQUEST,
                team4.emotionmap.contracts.error.FieldError.required("header." + e.getHeaderName())), request);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> onMissingPart(Exception e, HttpServletRequest request) {
        String name = e instanceof MissingServletRequestPartException p ? p.getRequestPartName()
                : ((MissingServletRequestParameterException) e).getParameterName();
        return writer.toResponse(ContractError.of(ErrorCode.INVALID_REQUEST,
                team4.emotionmap.contracts.error.FieldError.required(name)), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> onTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        String name = e.getName() == null ? "request" : e.getName();
        // UUID 경로 변수 형식 오류는 "없는 대상"과 같은 404 로 통일해 존재 여부를 노출하지 않는다.
        if (e.getRequiredType() == UUID.class && e.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return writer.toResponse(ErrorCode.RESOURCE_NOT_FOUND, request);
        }
        return writer.toResponse(ContractError.of(ErrorCode.INVALID_REQUEST,
                team4.emotionmap.contracts.error.FieldError.invalid(name)), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> onMediaType(HttpMediaTypeNotSupportedException e, HttpServletRequest request) {
        return writer.toResponse(ErrorCode.INVALID_REQUEST, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> onUploadSize(MaxUploadSizeExceededException e, HttpServletRequest request) {
        return writer.toResponse(ErrorCode.IMAGE_TOO_LARGE, request);
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class,
            HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<ApiError> onNoRoute(Exception e, HttpServletRequest request) {
        return writer.toResponse(ErrorCode.RESOURCE_NOT_FOUND, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> onUnexpected(Exception e, HttpServletRequest request) {
        ResponseStatus status = AnnotatedElementUtils.findMergedAnnotation(e.getClass(), ResponseStatus.class);
        if (status != null) {
            return onResponseStatus(new ResponseStatusException(status.code(), status.reason(), e), request);
        }
        log.error("unhandled exception requestId={}", RequestIds.current(request), e);
        return writer.toResponse(ErrorCode.SERVER_ERROR, request);
    }

    // ------------------------------------------------------------------ helpers

    static <T extends Throwable> Optional<T> findCause(Throwable root, Class<T> type) {
        Throwable current = root;
        int depth = 0;
        while (current != null && depth++ < 16) {
            if (type.isInstance(current)) {
                return Optional.of(type.cast(current));
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    /** Jackson 경로(Reference 목록) → {@code a.b[2].c}. 값은 넣지 않는다. */
    static String pathOf(JacksonException e) {
        StringBuilder sb = new StringBuilder();
        for (JacksonException.Reference ref : e.getPath()) {
            if (ref.getPropertyName() != null) {
                if (!sb.isEmpty()) {
                    sb.append('.');
                }
                sb.append(ref.getPropertyName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.isEmpty() ? "request" : sb.toString();
    }

    static FieldErrorReason reasonOf(String constraintCode) {
        if (constraintCode == null) {
            return FieldErrorReason.INVALID_VALUE;
        }
        return switch (constraintCode) {
            case "NotNull", "NotBlank", "NotEmpty" -> FieldErrorReason.REQUIRED;
            case "Size", "Max", "DecimalMax", "Length" -> FieldErrorReason.TOO_LONG;
            case "Min", "DecimalMin", "Range", "Positive", "PositiveOrZero" -> FieldErrorReason.OUT_OF_RANGE;
            default -> FieldErrorReason.INVALID_VALUE;
        };
    }
}
