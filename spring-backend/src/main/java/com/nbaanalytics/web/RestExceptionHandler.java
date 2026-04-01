package com.nbaanalytics.web;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class RestExceptionHandler {

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> status(ResponseStatusException ex) {
    Map<String, Object> body = new HashMap<>();
    body.put("statusCode", ex.getStatusCode().value());
    body.put("message", ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString());
    return ResponseEntity.status(ex.getStatusCode()).body(body);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> valid(MethodArgumentNotValidException ex) {
    var msgs =
        ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());
    Map<String, Object> body = new HashMap<>();
    body.put("statusCode", HttpStatus.BAD_REQUEST.value());
    body.put("message", msgs);
    body.put("error", "Bad Request");
    return ResponseEntity.badRequest().body(body);
  }
}
