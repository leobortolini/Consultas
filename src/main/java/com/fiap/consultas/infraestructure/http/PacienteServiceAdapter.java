package com.fiap.consultas.infraestructure.http;

import com.fiap.consultas.application.dtos.PacienteDTO;
import com.fiap.consultas.application.ports.PacienteServicePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PacienteServiceAdapter implements PacienteServicePort {

    private final RestTemplate restTemplate;
    private final String pacientesServiceUrl;

    public PacienteServiceAdapter(RestTemplate restTemplate,
                                  @Value("${microservices.pacientes.url}") String pacientesServiceUrl) {
        this.restTemplate = restTemplate;
        this.pacientesServiceUrl = pacientesServiceUrl;
    }

    @Override
    public PacienteDTO buscarPacientePorCpf(String cpf) {
        String url = pacientesServiceUrl + "/api/v1/pacientes/" + cpf;
        return restTemplate.getForObject(url, PacienteDTO.class);
    }
}