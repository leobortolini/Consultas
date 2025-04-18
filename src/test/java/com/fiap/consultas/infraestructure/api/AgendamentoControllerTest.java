package com.fiap.consultas.infraestructure.api;

import com.fiap.consultas.application.dtos.RespostaAgendamentoDTO;
import com.fiap.consultas.application.dtos.SolicitacaoAgendamentoDTO;
import com.fiap.consultas.application.usecases.SolicitarAgendamentoUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AgendamentoControllerTest {

    @Mock
    private SolicitarAgendamentoUseCase solicitarAgendamentoUseCase;

    @InjectMocks
    private AgendamentoController agendamentoController;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(agendamentoController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void deveRetornarStatusCreated() throws Exception {
        // Arrange
        SolicitacaoAgendamentoDTO solicitacao = criarSolicitacaoDTO();
        RespostaAgendamentoDTO respostaEsperada = criarRespostaDTO();

        when(solicitarAgendamentoUseCase.executar(any(SolicitacaoAgendamentoDTO.class)))
                .thenReturn(respostaEsperada);

        // Act & Assert
        mockMvc.perform(post("/api/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitacao)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.consultaId").value(respostaEsperada.getConsultaId().toString()))
                .andExpect(jsonPath("$.status").value(respostaEsperada.getStatus().toString()))
                .andExpect(jsonPath("$.mensagem").value(respostaEsperada.getMensagem()));

        verify(solicitarAgendamentoUseCase).executar(any(SolicitacaoAgendamentoDTO.class));
    }

    @Test
    void deveEnviarParametrosCorretosParaUseCase() throws Exception {
        // Arrange
        SolicitacaoAgendamentoDTO solicitacao = criarSolicitacaoDTO();
        RespostaAgendamentoDTO respostaEsperada = criarRespostaDTO();

        when(solicitarAgendamentoUseCase.executar(any(SolicitacaoAgendamentoDTO.class)))
                .thenReturn(respostaEsperada);

        // Act
        mockMvc.perform(post("/api/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(solicitacao)))
                .andExpect(status().isCreated());

        // Assert
        verify(solicitarAgendamentoUseCase).executar(any(SolicitacaoAgendamentoDTO.class));
    }

    private SolicitacaoAgendamentoDTO criarSolicitacaoDTO() {
        SolicitacaoAgendamentoDTO solicitacao = new SolicitacaoAgendamentoDTO();
        solicitacao.setCpfPaciente("12345678900");
        solicitacao.setEspecialidade("Cardiologia");
        solicitacao.setCidade("São Paulo");
        solicitacao.setPrioridade(PrioridadeConsulta.MEDIA);
        return solicitacao;
    }

    private RespostaAgendamentoDTO criarRespostaDTO() {
        UUID consultaId = UUID.randomUUID();
        RespostaAgendamentoDTO resposta = new RespostaAgendamentoDTO();
        resposta.setConsultaId(consultaId);
        resposta.setStatus(StatusConsulta.AGENDADA);
        resposta.setMensagem("Consulta agendada com sucesso");
        return resposta;
    }
}