package bdd;

import com.fiap.consultas.application.dtos.ConfirmacaoConsultaDTO;
import com.fiap.consultas.application.dtos.RespostaAgendamentoDTO;
import com.fiap.consultas.application.dtos.SolicitacaoAgendamentoDTO;
import com.fiap.consultas.application.usecases.ProcessarConsultasPendentesUseCase;
import com.fiap.consultas.application.usecases.ReceberConfirmacaoConsultaUseCase;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.infraestructure.persistence.entities.ConsultaJpaEntity;
import com.fiap.consultas.infraestructure.persistence.repositories.ConsultaJpaRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Dado;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
public class DefinicaoPassos {

    @LocalServerPort
    private int port;

    private WireMockServer wireMockServer;
    private RespostaAgendamentoDTO respostaAgendamentoDTO;

    @Autowired
    private ProcessarConsultasPendentesUseCase processarConsultasPendentesUseCase;

    @Autowired
    private ConsultaJpaRepository consultaJpaRepository;

    @Autowired
    private ReceberConfirmacaoConsultaUseCase receberConfirmacaoConsultaUseCase;

    @Autowired
    private InputDestination input;

    @Before
    public void setup() {
        wireMockServer = new WireMockServer(8082);
        wireMockServer.start();
        configureFor("localhost", 8082);
    }

    @After
    public void tearDown() {
        wireMockServer.stop();
    }

    @Quando("criar nova consulta")
    @Dado("que exista uma consulta")
    public void criarNovaConsulta() {
        configureStubs();

        SolicitacaoAgendamentoDTO solicitacao = new SolicitacaoAgendamentoDTO(
                "11111111111", "Cardiologista", "Campinas", PrioridadeConsulta.BAIXA
        );

        String endpoint = "http://localhost:" + port + "/api/consultas";

        Response response = given()
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .body(solicitacao)
                .when()
                .post(endpoint);

        respostaAgendamentoDTO = response.then().extract().as(RespostaAgendamentoDTO.class);
    }

    @Quando("processar as consultas pendentes")
    public void processarConsultasPendentes() {
        configureStubs();
        processarConsultasPendentesUseCase.executar();
    }

    @Então("a consulta deve ser agendada")
    public void verificarConsultaAgendada() {
        Optional<ConsultaJpaEntity> consulta = consultaJpaRepository.findById(respostaAgendamentoDTO.getConsultaId());

        assertTrue(consulta.isPresent());
        assertSame(StatusConsulta.AGENDADA, consulta.get().getStatus());
    }

    @Então("o sistema deve retornar a consulta criada")
    public void consultaCriada() {
        assertNotNull(respostaAgendamentoDTO.getConsultaId());
    }

    @Dado("que exista uma consulta agendada")
    public void consultaAgendada() {
        criarNovaConsulta();
        processarConsultasPendentes();
        verificarConsultaAgendada();
    }

    @Quando("receber confirmacao")
    public void receberConfirmacao() {
        enviarEventoDeConfirmacao(true);
    }

    @Quando("receber confirmacao negativa")
    public void receberConfirmacaoNegativa() {
        enviarEventoDeConfirmacao(false);
    }

    @Então("a consulta deve ser cancelada")
    public void verificarConsultaCancelada() {
        assertConsultaStatus(StatusConsulta.CANCELADA);
    }

    @Então("a consulta deve ser confirmada")
    public void verificarConsultaConfirmada() {
        assertConsultaStatus(StatusConsulta.CONFIRMADA);
    }

    private void enviarEventoDeConfirmacao(boolean confirmada) {
        ConfirmacaoConsultaDTO confirmacao = new ConfirmacaoConsultaDTO();

        confirmacao.setConfirmada(confirmada);
        confirmacao.setConsultaId(respostaAgendamentoDTO.getConsultaId().toString());

        Message<ConfirmacaoConsultaDTO> message = MessageBuilder.withPayload(confirmacao).build();
        input.send(message, "receberConfirmacaoConsulta-in-0");
    }

    private void assertConsultaStatus(StatusConsulta statusConsulta) {
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<ConsultaJpaEntity> consultaAtualizada = consultaJpaRepository.findById(respostaAgendamentoDTO.getConsultaId());
            assertThat(consultaAtualizada).isPresent();
            assertThat(consultaAtualizada.get().getStatus()).isEqualTo(statusConsulta);
        });
    }

    private void configureStubs() {
        stubFor(get(urlPathMatching("/medicos"))
                .withQueryParam("especialidade", equalTo("Cardiologista"))
                .withQueryParam("cidade", equalTo("Campinas"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getMedicosMock())));

        stubFor(get(urlPathMatching("/api/v1/pacientes/11111111111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(getPacienteMock())));
    }

    private String getPacienteMock() {
        return """
                   {
                        "cpf": "11111111111",
                        "nome": "Leonardo Bortolini",
                        "email": "email@email.com",
                        "telefone": "54 99999 9999",
                        "cidade": "Campinas"
                    }
                    """;
    }

    private String getMedicosMock() {
        return """
                    [{
                        "id": 1,
                        "nome": "nome medico",
                        "especialidade": "Cardiologista",
                        "cidade": "Campinas",
                        "horariosTrabalho": [
                            {
                                "diaDaSemana": "SEGUNDA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            },
                            {
                                "diaDaSemana": "TERCA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            },
                            {
                                "diaDaSemana": "QUARTA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            },
                            {
                                "diaDaSemana": "QUINTA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            },
                            {
                                "diaDaSemana": "SEXTA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            }
                        ]
                    },
                    {
                        "id": 2,
                        "nome": "nome medico2",
                        "especialidade": "Cardiologista",
                        "cidade": "Campinas",
                        "horariosTrabalho": [
                            {
                                "diaDaSemana": "SEGUNDA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            },
                            {
                                "diaDaSemana": "TERCA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            },
                            {
                                "diaDaSemana": "QUARTA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            },
                            {
                                "diaDaSemana": "QUINTA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            },
                            {
                                "diaDaSemana": "SEXTA",
                                "horaInicio": "08:00:00",
                                "horaFim": "18:00:00"
                            }
                        ]
                    }]
                    """;
    }
}