package cl.duoc.pedidos360.streaming.exception;

import java.util.Map;
import java.util.TreeMap;


import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Errores en formato problem+json (RFC 7807). Los de validación incluyen un mapa "errores" campo → motivo
 * para que el frontend pueda mostrarlos junto a cada campo.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(OperacionRechazadaException.class)
    ProblemDetail rechazada(OperacionRechazadaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(OperacionInvalidaException.class)
    ProblemDetail invalida(OperacionInvalidaException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(ClusterNoDisponibleException.class)
    ProblemDetail clusterCaido(ClusterNoDisponibleException ex) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class).warn("Kafka no disponible: {}", ex.getMessage(),
                ex.getCause());
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Kafka no está disponible: " + ex.getMessage());
    }

    /** Errores de los DTO (@Valid @RequestBody). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new TreeMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errores.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        ex.getBindingResult().getGlobalErrors().forEach(ge -> errores.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        return ResponseEntity.badRequest().body(problemaValidacion(errores));
    }

    /** Errores de parámetros de ruta o query (@PathVariable / @RequestParam con @Pattern). */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errores = new TreeMap<>();
        // Si la ruta también tiene restricciones, los errores del cuerpo (@Valid) llegan aquí: se informan por campo.
        ex.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e -> errores.putIfAbsent(
                e instanceof FieldError fe ? fe.getField() : r.getMethodParameter().getParameterName(),
                e.getDefaultMessage())));
        return ResponseEntity.badRequest().body(problemaValidacion(errores));
    }

    private static ProblemDetail problemaValidacion(Map<String, String> errores) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Datos inválidos");
        pd.setProperty("errores", errores);
        return pd;
    }
}
