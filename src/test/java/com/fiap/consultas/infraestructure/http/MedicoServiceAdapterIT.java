package com.fiap.consultas.infraestructure.http;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fiap.consultas.application.dtos.MedicoDTO;
import com.fiap.consultas.domain.entities.HorarioTrabalho;

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
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;

@SpringBootTest
@EnableTestBinder
class MedicoServiceAdapterIT {

    @Autowired
    private MedicoServiceAdapter medicoServiceAdapter;

    private ObjectMapper objectMapper;

    private WireMockServer wireMockServer;


    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        wireMockServer = new WireMockServer(9192, 0);
        wireMockServer.start();
        WireMock.configureFor("localhost", 9192);
    }

    @AfterEach
    void tearDown() {
        resetAllRequests();
        WireMock.reset();
        wireMockServer.stop();
    }

    @Test
    void buscarMedicosPorEspecialidadeECidade_QuandoServicoExternoRetornarDados_DeveRetornarMedicos() throws JsonProcessingException {
        // Arrange
        String especialidade = "Cardiologia";
        String cidade = "Campinas";

        // Criar mock de resposta
        List<MedicoServiceAdapter.MedicoCustom> medicosMock = criarMedicosMock();

        // Configurar o WireMock para responder à solicitação
        stubFor(get(urlPathEqualTo("/medicos"))
                .withQueryParam("especialidade", equalTo(especialidade))
                .withQueryParam("cidade", equalTo(cidade))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody(objectMapper.writeValueAsString(medicosMock))));

        // Act
        List<MedicoDTO> resultado = medicoServiceAdapter.buscarMedicosPorEspecialidadeECidade(especialidade, cidade);

        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.size());

        // Verificar primeiro médico
        MedicoDTO primeiroMedico = resultado.getFirst();
        assertEquals("1", primeiroMedico.getId());
        assertEquals("Dr. João Silva", primeiroMedico.getNome());
        assertEquals("Cardiologia", primeiroMedico.getEspecialidade());
        assertEquals("Campinas", primeiroMedico.getCidade());

        // Verificar horários do primeiro médico
        List<HorarioTrabalho> horarios = primeiroMedico.getHorariosTrabalho();
        assertEquals(2, horarios.size());
        assertEquals(DayOfWeek.MONDAY, horarios.getFirst().getDiaSemana());
        assertEquals(LocalTime.of(9, 0), horarios.getFirst().getHoraInicio());
        assertEquals(LocalTime.of(18, 0), horarios.getFirst().getHoraFim());

        // Verificar que a requisição foi feita corretamente
        verify(getRequestedFor(urlPathEqualTo("/medicos"))
                .withQueryParam("especialidade", equalTo(especialidade))
                .withQueryParam("cidade", equalTo(cidade)));
    }

    @Test
    void buscarMedicosPorEspecialidadeECidade_QuandoNaoHouverMedicos_DeveRetornarListaVazia() {
        // Arrange
        String especialidade = "Dermatologia";
        String cidade = "Curitiba";

        // Configurar o WireMock para responder com lista vazia
        stubFor(get(urlPathEqualTo("/medicos"))
                .withQueryParam("especialidade", equalTo(especialidade))
                .withQueryParam("cidade", equalTo(cidade))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("[]")));

        // Act
        List<MedicoDTO> resultado = medicoServiceAdapter.buscarMedicosPorEspecialidadeECidade(especialidade, cidade);

        // Assert
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());

        // Verificar que a requisição foi feita corretamente
        verify(getRequestedFor(urlPathEqualTo("/medicos"))
                .withQueryParam("especialidade", equalTo(especialidade))
                .withQueryParam("cidade", equalTo(cidade)));
    }

    @Test
    void buscarMedicosPorEspecialidadeECidade_QuandoServicoExternoRetornar500_DeveLancarExcecao() {
        // Arrange
        String especialidade = "Pediatria";
        String cidade = "Salvador";

        // Configurar o WireMock para responder com erro 500
        stubFor(get(urlPathEqualTo("/medicos"))
                .withQueryParam("especialidade", equalTo(especialidade))
                .withQueryParam("cidade", equalTo(cidade))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                        .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .withBody("{\"mensagem\": \"Erro interno do servidor\"}")));

        // Act & Assert
        assertThrows(HttpServerErrorException.InternalServerError.class, () -> {
            medicoServiceAdapter.buscarMedicosPorEspecialidadeECidade(especialidade, cidade);
        });

        // Verificar que a requisição foi feita corretamente
        verify(getRequestedFor(urlPathEqualTo("/medicos"))
                .withQueryParam("especialidade", equalTo(especialidade))
                .withQueryParam("cidade", equalTo(cidade)));
    }

    private List<MedicoServiceAdapter.MedicoCustom> criarMedicosMock() {
        List<MedicoServiceAdapter.MedicoCustom> medicos = new ArrayList<>();

        // Primeiro médico
        MedicoServiceAdapter.MedicoCustom medico1 = new MedicoServiceAdapter.MedicoCustom();
        medico1.setId(1L);
        medico1.setNome("Dr. João Silva");
        medico1.setEspecialidade("Cardiologia");
        medico1.setCidade("Campinas");

        // Horários do primeiro médico
        List<MedicoServiceAdapter.HorarioTrabalhoCustom> horarios1 = new ArrayList<>();

        MedicoServiceAdapter.HorarioTrabalhoCustom horario1 = getHorarioTrabalhoCustom(MedicoServiceAdapter.DiaDaSemanaCustom.SEGUNDA, 9, 18);

        MedicoServiceAdapter.HorarioTrabalhoCustom horario2 = getHorarioTrabalhoCustom(MedicoServiceAdapter.DiaDaSemanaCustom.QUARTA, 9, 18);

        horarios1.add(horario1);
        horarios1.add(horario2);
        medico1.setHorariosTrabalho(horarios1);

        // Segundo médico
        MedicoServiceAdapter.MedicoCustom medico2 = new MedicoServiceAdapter.MedicoCustom();
        medico2.setId(2L);
        medico2.setNome("Dra. Maria Oliveira");
        medico2.setEspecialidade("Cardiologia");
        medico2.setCidade("Campinas");

        // Horários do segundo médico
        List<MedicoServiceAdapter.HorarioTrabalhoCustom> horarios2 = new ArrayList<>();

        MedicoServiceAdapter.HorarioTrabalhoCustom horario = getHorarioTrabalhoCustom(MedicoServiceAdapter.DiaDaSemanaCustom.TERCA, 10, 19);

        horarios2.add(horario);
        medico2.setHorariosTrabalho(horarios2);

        medicos.add(medico1);
        medicos.add(medico2);

        return medicos;
    }

    private static MedicoServiceAdapter.HorarioTrabalhoCustom getHorarioTrabalhoCustom(MedicoServiceAdapter.DiaDaSemanaCustom segunda, int inicio, int fim) {
        MedicoServiceAdapter.HorarioTrabalhoCustom horario1 = new MedicoServiceAdapter.HorarioTrabalhoCustom();
        horario1.setDiaDaSemana(segunda);
        horario1.setHoraInicio(LocalTime.of(inicio, 0));
        horario1.setHoraFim(LocalTime.of(fim, 0));
        return horario1;
    }
}