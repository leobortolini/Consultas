package bdd;

import com.fiap.consultas.application.dtos.RespostaAgendamentoDTO;
import com.fiap.consultas.application.dtos.SolicitacaoAgendamentoDTO;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.pt.Então;
import io.cucumber.java.pt.Quando;
import io.restassured.response.Response;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class DefinicaoPassos {

    @LocalServerPort
    private int port;

    private WireMockServer wireMockServer;
    private RespostaAgendamentoDTO respostaAgendamentoDTO;

    @Before
    public void setup() {
        wireMockServer = new WireMockServer(8082);
        wireMockServer.start();
        configureFor("localhost", 8082);
    }

    @After
    public void tearDown() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
        }
    }

    @Quando("criar nova consulta")
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

    @Então("o sistema deve retornar a consulta criada")
    public void consultaCriada() {
        assertNotNull(respostaAgendamentoDTO.getConsultaId());
    }

    private String getPacienteMock() {
        return """
                   {
                        "cpf": "11111111111",
                        "nome": "Leonardo Bortolini",
                        "email": "email@email.com",
                        "telefone": "54 99999 9999",
                        "cidade": "cidade1"
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