package bank.maggy.identity.api;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldError(error.getField(), error.getDefaultMessage()))
                .distinct().toList();
        return response(HttpStatus.BAD_REQUEST, "Verifique os campos informados.", request, errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> unreadable(HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Corpo da requisição ausente ou JSON inválido.", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "Parâmetro inválido: " + ex.getName() + ".", request, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMethod(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return response(HttpStatus.METHOD_NOT_ALLOWED, "Método HTTP não permitido para esta rota.", request, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ApiError> unsupportedMedia(HttpServletRequest request) {
        return response(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de conteúdo não suportado.", request, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> notFound(HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "Rota não encontrada.", request, List.of());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException ex, HttpServletRequest request) {
        String message = ex.getReason() == null ? "Não foi possível processar a requisição." : ex.getReason();
        return response(ex.getStatusCode(), message, request, List.of());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    ResponseEntity<ApiError> notImplemented(HttpServletRequest request) {
        return response(HttpStatus.NOT_IMPLEMENTED, "Esta funcionalidade ainda não foi implementada.", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado ao processar requisição", ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Ocorreu um erro interno. Tente novamente mais tarde.", request, List.of());
    }

    private ResponseEntity<ApiError> response(HttpStatusCode status, String message, HttpServletRequest request,
                                               List<ApiError.FieldError> errors) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), message, request.getRequestURI(), errors));
    }
}
