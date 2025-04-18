package com.fiap.consultas.infraestructure.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntimeException_ShouldReturnInternalServerError() {
        // Arrange
        RuntimeException exception = new RuntimeException("Erro de teste");

        // Act
        ResponseEntity<String> response = handler.handleRuntimeException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Erro ao processar a requisição: Erro de teste", response.getBody());
    }

    @Test
    void handleIllegalArgumentException_ShouldReturnBadRequest() {
        // Arrange
        IllegalArgumentException exception = new IllegalArgumentException("Argumento inválido");

        // Act
        ResponseEntity<String> response = handler.handleIllegalArgumentException(exception);

        // Assert
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Requisição inválida: Argumento inválido", response.getBody());
    }
}