package com.example.shop.common.web;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.shop.common.exception.BadRequestException;
import com.example.shop.common.exception.BusinessRuleException;
import com.example.shop.common.exception.ConflictException;
import com.example.shop.common.exception.ResourceNotFoundException;
import com.example.shop.common.web.ErrorResponse.FieldViolation;

// Turns every exception into the same ErrorResponse JSON with a matching HTTP status
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // ---- security (Practice 7, lecture week 12) ----

    // 401. Reached two ways: from the login endpoint (wrong password, unknown or disabled user) and from the
    // filter chain's entry point (no token, bad token), which hands its exception to this advice so every
    // error keeps one body. Login failures share ONE message, so valid usernames cannot be guessed (slide 10).
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest request,
                                                              HttpServletResponse response) {
        String message;
        if (ex instanceof InvalidBearerTokenException) {
            message = "Invalid or expired token";
        } else if (ex instanceof InsufficientAuthenticationException
                || ex instanceof AuthenticationCredentialsNotFoundException) {
            message = "Authentication is required: send Authorization: Bearer <token>";
        } else {
            message = "Invalid username or password";
        }
        return unauthorized(message, request, response);
    }

    // 403: a known user without the needed role, from the filter chain or from @PreAuthorize on a service
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request,
                                                            HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null || current instanceof AnonymousAuthenticationToken) {
            // Nobody is signed in, so the honest answer is "who are you?", not "no"
            return unauthorized("Authentication is required: send Authorization: Bearer <token>", request, response);
        }
        return build(HttpStatus.FORBIDDEN, "You do not have permission to do this", request, List.of());
    }

    // ---- domain errors ----

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        // Course rule (week 4, slide 20): 400 for invalid input, 409 for business-rule conflicts
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    // A database constraint caught what the service checks missed, e.g. two requests creating the same SKU
    // at the same moment: both pass the "is it free?" check, the unique index rejects the second insert
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                             HttpServletRequest request) {
        log.warn("Constraint violation on {} {}: {}", request.getMethod(), request.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "Request conflicts with existing data", request, List.of());
    }

    // @Version check failed: someone else changed the same row between our read and our write
    // (lecture week 5, slide 19: "the loser of the race ... -> rollback -> 409")
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "The data was changed by another request; please try again",
                request, List.of());
    }

    // ?sort=password: the entity has no such field (lecture week 5, slide 27: map it to 400)
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ErrorResponse> handleUnknownSortField(PropertyReferenceException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Unknown sort field '" + ex.getPropertyName() + "'", request,
                List.of());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    // A domain object refused an impossible value (e.g. price 0). Bean Validation normally stops it earlier.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    // ---- validation of request bodies (@Valid @RequestBody) ----

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException ex,
                                                              HttpServletRequest request) {
        List<FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Request body is invalid", request, violations);
    }

    // ---- validation of @RequestParam / @PathVariable constraints ----

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(HandlerMethodValidationException ex,
                                                                   HttpServletRequest request) {
        List<FieldViolation> violations = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                result.getArgument(),
                                error.getDefaultMessage())))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Request parameters are invalid", request, violations);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest request) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "another type";
        FieldViolation violation = new FieldViolation(ex.getName(), ex.getValue(), "must be of type " + expected);
        return build(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getName() + "' has a wrong type", request,
                List.of(violation));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Required parameter '" + ex.getParameterName() + "' is missing",
                request, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException ex,
                                                              HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Malformed JSON or wrong value type in request body", request,
                List.of());
    }

    // ---- HTTP-level errors ----

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoEndpoint(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No endpoint " + request.getMethod() + " " + request.getRequestURI(),
                request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex,
                                                                    HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json", request,
                List.of());
    }

    // Client asked for a format we cannot produce (e.g. Accept: application/xml) -> 406, not 500
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleNotAcceptable(HttpMediaTypeNotAcceptableException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.NOT_ACCEPTABLE, "Only application/json responses are supported", request,
                List.of());
    }

    // ---- anything unexpected: log it, never leak internals to the client ----

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error", request, List.of());
    }

    // A 401 must tell the client how to authenticate (RFC 9110). The filter chain's entry point already set a
    // detailed WWW-Authenticate header; a 401 raised inside MVC (the login endpoint) gets the plain scheme.
    private ResponseEntity<ErrorResponse> unauthorized(String message, HttpServletRequest request,
                                                       HttpServletResponse response) {
        ResponseEntity<ErrorResponse> body = build(HttpStatus.UNAUTHORIZED, message, request, List.of());
        if (response.containsHeader(HttpHeaders.WWW_AUTHENTICATE)) {
            return body;
        }
        return ResponseEntity.status(body.getStatusCode())
                .headers(body.getHeaders())
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .body(body.getBody());
    }

    private FieldViolation toViolation(FieldError error) {
        return new FieldViolation(error.getField(), error.getRejectedValue(), error.getDefaultMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, HttpServletRequest request,
                                                List<FieldViolation> violations) {
        ErrorResponse body = new ErrorResponse(
                Instant.now(clock),
                status.value(),
                status.getReasonPhrase(),
                message,
                request.getRequestURI(),
                violations);
        // Content type is set explicitly, so the error body is written as JSON even when the
        // client's Accept header does not list JSON (otherwise a 406 would turn into a 500)
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
