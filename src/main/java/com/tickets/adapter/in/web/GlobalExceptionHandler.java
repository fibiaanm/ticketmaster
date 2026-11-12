package com.tickets.adapter.in.web;

import com.tickets.adapter.in.web.dto.ErrorResponse;
import com.tickets.domain.exception.*;
import com.tickets.shared.log.Log;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RecursoNoEncontradoException.class)
    ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException e, HttpServletRequest r) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage(), r.getRequestURI());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalidaException e, HttpServletRequest r) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage(), r.getRequestURI());
    }

    @ExceptionHandler(ConflictoConcurrenciaException.class)
    ResponseEntity<ErrorResponse> conflicto(ConflictoConcurrenciaException e, HttpServletRequest r) {
        return respuesta(HttpStatus.CONFLICT, e.getMessage(), r.getRequestURI());
    }

    @ExceptionHandler(AccesoDenegadoException.class)
    ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException e, HttpServletRequest r) {
        return respuesta(HttpStatus.FORBIDDEN, e.getMessage(), r.getRequestURI());
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    ResponseEntity<ErrorResponse> credenciales(CredencialesInvalidasException e, HttpServletRequest r) {
        return respuesta(HttpStatus.UNAUTHORIZED, e.getMessage(), r.getRequestURI());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> tipoInvalido(MethodArgumentTypeMismatchException e, HttpServletRequest r) {
        return respuesta(HttpStatus.BAD_REQUEST, "Valor inválido para el parámetro '%s'".formatted(e.getName()),
                r.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> inesperado(Exception e, HttpServletRequest r) {
        Log.error("Error interno no controlado", Map.of("path", r.getRequestURI()), e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", r.getRequestURI());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage()).sorted().collect(Collectors.joining("; "));
        return cuerpo(HttpStatus.BAD_REQUEST, mensaje, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        return cuerpo(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud falta o está mal formado", request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        HttpStatus status = HttpStatus.valueOf(statusCode.value());
        return cuerpo(status, status.is5xxServerError() ? "Error interno del servidor" : mensajeDe(ex), request);
    }

    private static String mensajeDe(Exception ex) {
        return ex instanceof ErrorResponseException ere && ere.getBody().getDetail() != null
                ? ere.getBody().getDetail() : "Solicitud inválida";
    }

    private ResponseEntity<Object> cuerpo(HttpStatus status, String mensaje, WebRequest request) {
        String path = request instanceof ServletWebRequest swr ? swr.getRequest().getRequestURI() : "";
        return ResponseEntity.status(status).body(nuevo(status, mensaje, path));
    }

    static ResponseEntity<ErrorResponse> respuesta(HttpStatus status, String mensaje, String path) {
        return ResponseEntity.status(status).body(nuevo(status, mensaje, path));
    }

    public static ErrorResponse nuevo(HttpStatus status, String mensaje, String path) {
        return new ErrorResponse(LocalDateTime.now(ZoneOffset.UTC).withNano(0), status.value(), status.name(),
                mensaje, path);
    }
}
