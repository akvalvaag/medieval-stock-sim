package com.medievalmarket.controller;

import com.medievalmarket.service.ServiceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<?> handleServiceException(ServiceException e) {
        if ("INVALID_SESSION".equals(e.getMessage())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
