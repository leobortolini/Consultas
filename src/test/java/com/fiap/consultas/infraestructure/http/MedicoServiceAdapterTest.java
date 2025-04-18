package com.fiap.consultas.infraestructure.http;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fiap.consultas.application.dtos.MedicoDTO;
import com.fiap.consultas.domain.entities.HorarioTrabalho;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class MedicoServiceAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    private MedicoServiceAdapter medicoServiceAdapter;
    private final String MEDICOS_SERVICE_URL = "http://localhost:8082";

    @BeforeEach
    void setUp() {
        medicoServiceAdapter = new MedicoServiceAdapter(restTemplate, MEDICOS_SERVICE_URL);
    }

    @Test
    void buscarMedicosPorEspecialidadeECidade_QuandoExistiremMedicos_DeveRetornarListaDeMedicos() {
        // Arrange
        String especialidade = "Cardiologia";
        String cidade = "Campinas";

        // Criar uma resposta mock com médicos
        List<MedicoServiceAdapter.MedicoCustom> medicosMock = criarMedicosMock();

        // Configurar o mock do RestTemplate para retornar nossa lista de médicos
        ResponseEntity<List<MedicoServiceAdapter.MedicoCustom>> responseEntity =
                new ResponseEntity<>(medicosMock, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        // Act
        List<MedicoDTO> resultado = medicoServiceAdapter.buscarMedicosPorEspecialidadeECidade(especialidade, cidade);

        // Assert
        assertNotNull(resultado);
        assertEquals(2, resultado.size());

        // Verificar o primeiro médico
        MedicoDTO primeiroMedico = resultado.getFirst();
        assertEquals("1", primeiroMedico.getId());
        assertEquals("Dr. João Silva", primeiroMedico.getNome());
        assertEquals("Cardiologia", primeiroMedico.getEspecialidade());
        assertEquals("Campinas", primeiroMedico.getCidade());

        // Verificar horários do primeiro médico
        List<HorarioTrabalho> horarios = primeiroMedico.getHorariosTrabalho();
        assertEquals(6, horarios.size());
        assertHorario(horarios.getFirst(), DayOfWeek.MONDAY);
        assertHorario(horarios.get(1), DayOfWeek.WEDNESDAY);
        assertHorario(horarios.get(2), DayOfWeek.THURSDAY);
        assertHorario(horarios.get(3), DayOfWeek.FRIDAY);
        assertHorario(horarios.get(4), DayOfWeek.SATURDAY);
        assertHorario(horarios.getLast(), DayOfWeek.SUNDAY);


        // Capturar e verificar a URL construída
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(
                urlCaptor.capture(),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class));

        String urlCapturada = urlCaptor.getValue();
        assertTrue(urlCapturada.contains(MEDICOS_SERVICE_URL + "/medicos"));
        assertTrue(urlCapturada.contains("especialidade=" + especialidade));
        assertTrue(urlCapturada.contains("cidade=" + cidade));
    }

    private static void assertHorario(HorarioTrabalho horario, DayOfWeek dayOfWeek) {
        assertEquals(dayOfWeek, horario.getDiaSemana());
        assertEquals(LocalTime.of(9, 0), horario.getHoraInicio());
        assertEquals(LocalTime.of(18, 0), horario.getHoraFim());
    }

    @Test
    void buscarMedicosPorEspecialidadeECidade_QuandoNaoExistiremMedicos_DeveRetornarListaVazia() {
        // Arrange
        String especialidade = "Neurologia";
        String cidade = "Campinas";

        // Configurar o mock do RestTemplate para retornar lista vazia
        ResponseEntity<List<MedicoServiceAdapter.MedicoCustom>> responseEntity =
                new ResponseEntity<>(Collections.emptyList(), HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        // Act
        List<MedicoDTO> resultado = medicoServiceAdapter.buscarMedicosPorEspecialidadeECidade(especialidade, cidade);

        // Assert
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void buscarMedicosPorEspecialidadeECidade_QuandoBodyForNulo_DeveRetornarListaVazia() {
        // Arrange
        String especialidade = "Pediatria";
        String cidade = "Campinas";

        // Configurar o mock do RestTemplate para retornar body nulo
        ResponseEntity<List<MedicoServiceAdapter.MedicoCustom>> responseEntity =
                new ResponseEntity<>(null, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class)))
                .thenReturn(responseEntity);

        // Act
        List<MedicoDTO> resultado = medicoServiceAdapter.buscarMedicosPorEspecialidadeECidade(especialidade, cidade);

        // Assert
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty());
    }

    @Test
    void buscarMedicosPorEspecialidadeECidade_QuandoServicoRetornarErro_DeveLancarExcecao() {
        // Arrange
        String especialidade = "Oftalmologia";
        String cidade = "Campinas";

        // Configurar o mock do RestTemplate para lançar exceção
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                eq(null),
                any(ParameterizedTypeReference.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.NOT_FOUND));

        // Act & Assert
        assertThrows(HttpClientErrorException.class, () -> {
            medicoServiceAdapter.buscarMedicosPorEspecialidadeECidade(especialidade, cidade);
        });
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

        MedicoServiceAdapter.HorarioTrabalhoCustom horario3 = getHorarioTrabalhoCustom(MedicoServiceAdapter.DiaDaSemanaCustom.QUINTA, 9, 18);

        MedicoServiceAdapter.HorarioTrabalhoCustom horario4 = getHorarioTrabalhoCustom(MedicoServiceAdapter.DiaDaSemanaCustom.SEXTA, 9, 18);

        MedicoServiceAdapter.HorarioTrabalhoCustom horario5 = getHorarioTrabalhoCustom(MedicoServiceAdapter.DiaDaSemanaCustom.SABADO, 9, 18);

        MedicoServiceAdapter.HorarioTrabalhoCustom horario6 = getHorarioTrabalhoCustom(MedicoServiceAdapter.DiaDaSemanaCustom.DOMINGO, 9, 18);

        horarios1.add(horario1);
        horarios1.add(horario2);
        horarios1.add(horario3);
        horarios1.add(horario4);
        horarios1.add(horario5);
        horarios1.add(horario6);
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