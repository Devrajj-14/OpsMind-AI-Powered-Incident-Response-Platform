package com.opsmind.core;

import jakarta.persistence.OptimisticLockException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import java.util.*;

class ApiException extends RuntimeException {
    final HttpStatus status;
    ApiException(HttpStatus status, String message) { super(message); this.status=status; }
    static ApiException notFound(String what) { return new ApiException(HttpStatus.NOT_FOUND,what+" not found"); }
    static ApiException conflict(String message) { return new ApiException(HttpStatus.CONFLICT,message); }
}

@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(ApiException.class) ResponseEntity<ProblemDetail> api(ApiException e) {
        var p=ProblemDetail.forStatusAndDetail(e.status,e.getMessage()); p.setTitle(e.status.getReasonPhrase()); return ResponseEntity.status(e.status).body(p);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException e) {
        var p=ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"One or more fields are invalid"); p.setTitle("Validation failed");
        p.setProperty("errors",e.getBindingResult().getFieldErrors().stream().map(x->Map.of("field",x.getField(),"message",Objects.toString(x.getDefaultMessage(),"invalid"))).toList());
        return ResponseEntity.badRequest().body(p);
    }
    @ExceptionHandler(AccessDeniedException.class) ResponseEntity<ProblemDetail> forbidden() { return ResponseEntity.status(403).body(ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"You do not have permission for this action")); }
    @ExceptionHandler({OptimisticLockException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class}) ResponseEntity<ProblemDetail> conflict() { return ResponseEntity.status(409).body(ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"The incident was changed by another user; reload and retry")); }
}
