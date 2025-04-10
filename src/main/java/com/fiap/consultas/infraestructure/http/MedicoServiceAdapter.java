package com.fiap.consultas.infraestructure.http;

import com.fiap.consultas.application.dtos.MedicoDTO;
import com.fiap.consultas.application.ports.MedicoServicePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
public class MedicoServiceAdapter implements MedicoServicePort {

    private final RestTemplate restTemplate;
    private final String medicosServiceUrl;

    public MedicoServiceAdapter(RestTemplate restTemplate,
                                @Value("${microservices.medicos.url}") String medicosServiceUrl) {
        this.restTemplate = restTemplate;
        this.medicosServiceUrl = medicosServiceUrl;
    }

    @Override
    public List<MedicoDTO> buscarMedicosPorEspecialidadeECidade(String especialidade, String cidade) {
        String url = UriComponentsBuilder
                .fromUriString(medicosServiceUrl + "/api/medicos")
                .queryParam("especialidade", especialidade)
                .queryParam("cidade", cidade)
                .toUriString();

        ResponseEntity<List<MedicoDTO>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {
                }
        );

        return response.getBody();
    }
}
