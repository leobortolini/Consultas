package com.fiap.consultas.infraestructure.http;

import com.fiap.consultas.application.dtos.MedicoDTO;
import com.fiap.consultas.application.ports.MedicoServicePort;
import com.fiap.consultas.domain.entities.HorarioTrabalho;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

@Service
public class MedicoServiceAdapter implements MedicoServicePort {

    private final RestTemplate restTemplate;
    private final String medicosServiceUrl;

    public MedicoServiceAdapter(RestTemplate restTemplate, @Value("${microservices.medicos.url}") String medicosServiceUrl) {
        this.restTemplate = restTemplate;
        this.medicosServiceUrl = medicosServiceUrl;
    }

    @Override
    public List<MedicoDTO> buscarMedicosPorEspecialidadeECidade(String especialidade, String cidade) {
        String url = UriComponentsBuilder
                .fromUriString(medicosServiceUrl + "/medicos")
                .queryParam("especialidade", especialidade)
                .queryParam("cidade", cidade)
                .toUriString();

        ResponseEntity<List<MedicoCustom>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() { }
        );

        List<MedicoCustom> medicos = response.getBody();
        return medicos != null ? medicos.stream().map(this::convertToMedicoDTO).toList() : Collections.emptyList();
    }

    private MedicoDTO convertToMedicoDTO(MedicoCustom medico) {
        MedicoDTO medicoDTO = new MedicoDTO();
        medicoDTO.setId(medico.getId().toString());
        medicoDTO.setNome(medico.getNome());
        medicoDTO.setEspecialidade(medico.getEspecialidade());
        medicoDTO.setCidade(medico.getCidade());

        List<HorarioTrabalho> horariosConvertidos = medico.getHorariosTrabalho().stream()
                .map(this::convertHorarioTrabalho)
                .toList();

        medicoDTO.setHorariosTrabalho(horariosConvertidos);

        return medicoDTO;
    }

    private HorarioTrabalho convertHorarioTrabalho(HorarioTrabalhoCustom horarioOriginal) {
        HorarioTrabalho horarioDTO = new HorarioTrabalho();

        horarioDTO.setHoraInicio(horarioOriginal.getHoraInicio());
        horarioDTO.setHoraFim(horarioOriginal.getHoraFim());
        horarioDTO.setDiaSemana(convertDiaDaSemana(horarioOriginal.getDiaDaSemana()));

        return horarioDTO;
    }

    private DayOfWeek convertDiaDaSemana(DiaDaSemanaCustom diaDaSemana) {
        return switch (diaDaSemana) {
            case SEGUNDA -> DayOfWeek.MONDAY;
            case TERCA -> DayOfWeek.TUESDAY;
            case QUARTA -> DayOfWeek.WEDNESDAY;
            case QUINTA -> DayOfWeek.THURSDAY;
            case SEXTA -> DayOfWeek.FRIDAY;
            case SABADO -> DayOfWeek.SATURDAY;
            case DOMINGO -> DayOfWeek.SUNDAY;
        };
    }

    @Getter
    @Setter
    public static class MedicoCustom {
        private Long id;
        private String nome;
        private String especialidade;
        private String cidade;
        private List<HorarioTrabalhoCustom> horariosTrabalho;
    }

    @Getter
    @Setter
    public static class HorarioTrabalhoCustom {
        private DiaDaSemanaCustom diaDaSemana;
        private LocalTime horaInicio;
        private LocalTime horaFim;
    }

    public enum DiaDaSemanaCustom {
        SEGUNDA, TERCA, QUARTA, QUINTA, SEXTA, SABADO, DOMINGO
    }
}