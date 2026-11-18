package cr.ac.ucr.if0009.insecurepay.controller;

import cr.ac.ucr.if0009.insecurepay.exception.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ProblemDetail> accesoDenegado(AccesoDenegadoException ex) {
        return problema(HttpStatus.FORBIDDEN, "Acceso no autorizado a recurso ajeno.", ex.getMessage());
    }

    @ExceptionHandler(NoAutenticadoException.class)
    public ResponseEntity<ProblemDetail> noAutenticado(NoAutenticadoException ex) {
        var body = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        body.setTitle("Autenticacion requerida.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ProblemDetail> noEncontrado(RecursoNoEncontradoException ex) {
        return problema(HttpStatus.NOT_FOUND, "Recurso no encontrado.", ex.getMessage());
    }

    private ResponseEntity<ProblemDetail> problema(HttpStatus status, String titulo, String detalle) {
        var body = ProblemDetail.forStatusAndDetail(status, detalle);
        body.setTitle(titulo);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
