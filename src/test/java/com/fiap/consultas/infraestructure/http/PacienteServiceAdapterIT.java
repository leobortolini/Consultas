package com.fiap.consultas.infraestructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.resetAllRequests;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.consultas.application.dtos.PacienteDTO;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.EnableTestBinder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.io.IOException;

@SpringBootTest
@EnableTestBinder
class PacienteServiceAdapterIT {

    @Autowired
    private PacienteServiceAdapter pacienteServiceAdapter;

    @Autowired
    private ObjectMapper objectMapper;

    private PacienteDTO pacienteMock;

    private WireMockServer wireMockServer;

    @BeforeEach
    void setUp() {
        pacienteMock = PacienteDTO.builder()
                .cpf("12345678900")
                .nome("José Silva")
                .email("jose.silva@example.com")
                .telefone("11999998888")
                .cidade("São Paulo")
                .build();
        wireMockServer = new WireMockServer(9191, 0);
        wireMockServer.start();
        WireMock.configureFor("localhost", 9191);
    }

    @AfterEach
    void tearDown() {
        // Reset WireMock after each test
        resetAllRequests();
        WireMock.reset();
        wireMockServer.stop();
    }

    @Test
    void buscarPacientePorCpf_QuandoServicoExternoRetornarDados_DeveRetornarPaciente() throws IOException {
        // Arrange
        String cpf = "12345678900";

        stubFor(get(urlEqualTo("/api/v1/pacientes/" + cpf))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(pacienteMock))));

        // Act
        PacienteDTO resultado = pacienteServiceAdapter.buscarPacientePorCpf(cpf);

        // Assert
        assertNotNull(resultado);
        assertEquals(pacienteMock.getCpf(), resultado.getCpf());
        assertEquals(pacienteMock.getNome(), resultado.getNome());
        assertEquals(pacienteMock.getEmail(), resultado.getEmail());
        assertEquals(pacienteMock.getTelefone(), resultado.getTelefone());
        assertEquals(pacienteMock.getCidade(), resultado.getCidade());

        // Verify request was made
        verify(getRequestedFor(urlEqualTo("/api/v1/pacientes/" + cpf)));
    }

    @Test
    void buscarPacientePorCpf_QuandoPacienteNaoEncontrado_DeveLancarExcecao() {
        // Arrange
        String cpf = "00000000000";

        stubFor(get(urlEqualTo("/api/v1/pacientes/" + cpf))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.NOT_FOUND.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"mensagem\": \"Paciente não encontrado\"}")));

        // Act & Assert
        assertThrows(HttpClientErrorException.NotFound.class, () -> {
            pacienteServiceAdapter.buscarPacientePorCpf(cpf);
        });

        // Verify request was made
        verify(getRequestedFor(urlEqualTo("/api/v1/pacientes/" + cpf)));
    }

    @Test
    void buscarPacientePorCpf_QuandoServicoExternoRetornarErroInterno_DeveLancarExcecao() {
        // Arrange
        String cpf = "12345678900";

        stubFor(get(urlEqualTo("/api/v1/pacientes/" + cpf))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"mensagem\": \"Erro interno do servidor\"}")));

        // Act & Assert
        assertThrows(HttpServerErrorException.InternalServerError.class, () -> {
            pacienteServiceAdapter.buscarPacientePorCpf(cpf);
        });

        // Verify request was made
        verify(getRequestedFor(urlEqualTo("/api/v1/pacientes/" + cpf)));
    }
}