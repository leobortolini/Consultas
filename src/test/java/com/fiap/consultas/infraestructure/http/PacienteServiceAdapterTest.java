package com.fiap.consultas.infraestructure.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import com.fiap.consultas.application.dtos.PacienteDTO;

@ExtendWith(MockitoExtension.class)
class PacienteServiceAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private PacienteServiceAdapter pacienteServiceAdapter;
    private static final String PACIENTES_SERVICE_URL = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        pacienteServiceAdapter = new PacienteServiceAdapter(restTemplate, PACIENTES_SERVICE_URL);
    }

    @Test
    void deveRetornarPacienteQuandoCpfExistir() {
        // Arrange
        String cpf = "12345678900";
        String url = PACIENTES_SERVICE_URL + "/api/v1/pacientes/" + cpf;

        PacienteDTO pacienteEsperado = PacienteDTO.builder()
                .cpf(cpf)
                .nome("José Silva")
                .email("jose.silva@example.com")
                .telefone("11999998888")
                .cidade("São Paulo")
                .build();

        when(restTemplate.getForObject(url, PacienteDTO.class)).thenReturn(pacienteEsperado);

        // Act
        PacienteDTO resultado = pacienteServiceAdapter.buscarPacientePorCpf(cpf);

        // Assert
        assertEquals(pacienteEsperado, resultado);
        verify(restTemplate, times(1)).getForObject(url, PacienteDTO.class);
    }

    @Test
    void deveFormarUrlCorretamente() {
        // Arrange
        String cpf = "12345678900";
        String urlEsperada = PACIENTES_SERVICE_URL + "/api/v1/pacientes/" + cpf;

        // Mock any response
        when(restTemplate.getForObject(urlEsperada, PacienteDTO.class))
                .thenReturn(new PacienteDTO());

        // Act
        pacienteServiceAdapter.buscarPacientePorCpf(cpf);

        // Assert
        verify(restTemplate, times(1)).getForObject(urlEsperada, PacienteDTO.class);
    }
}