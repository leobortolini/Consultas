package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.MedicoDTO;
import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.application.dtos.PacienteDTO;
import com.fiap.consultas.application.ports.MedicoServicePort;
import com.fiap.consultas.application.ports.NotificacaoServicePort;
import com.fiap.consultas.application.ports.PacienteServicePort;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.entities.Medico;
import com.fiap.consultas.domain.entities.Paciente;
import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.enums.TipoNotificacao;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import com.fiap.consultas.domain.services.AgendamentoService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessarConsultasPendentesUseCase {

    private final ConsultaRepository consultaRepository;
    private final PacienteServicePort pacienteServicePort;
    private final MedicoServicePort medicoServicePort;
    private final NotificacaoServicePort notificacaoServicePort;
    private final AgendamentoService agendamentoService;

    @Scheduled(fixedRate = 10000)
    @Transactional
    public void executar() {
        List<Consulta> consultasPendentes = consultaRepository.buscarConsultasPendentesAgendamento();

        consultasPendentes.sort(Comparator.comparing(Consulta::getPrioridade).reversed().thenComparing(Consulta::getDataCriacao));

        for (Consulta consulta : consultasPendentes) {
            try {
                if (consulta.isPrioridadeUrgente()) {
                    processarConsultaUrgente(consulta);
                } else {
                    processarConsultaNormal(consulta);
                }
            } catch (Exception e) {
                log.error("Erro ao processar consulta {}: {}", consulta.getId(), e.getMessage(), e);
            }
        }
    }

    private void processarConsultaUrgente(Consulta consulta) {
        log.info("Processando consulta urgente: {}", consulta.getId());
        PacienteDTO pacienteDTO = pacienteServicePort.buscarPacientePorCpf(consulta.getPacienteCpf());
        Paciente paciente = converterParaPaciente(pacienteDTO);
        List<MedicoDTO> medicosDTO = medicoServicePort.buscarMedicosPorEspecialidadeECidade(consulta.getEspecialidade(), paciente.getCidade());
        List<Medico> medicos = medicosDTO.stream().map(this::converterParaMedico).toList();

        if (medicos.isEmpty()) {
            log.warn("Não há médicos disponíveis para a consulta: {}", consulta.getId());
            notificarEntradaNaListaDeEspera(consulta, paciente);
            return;
        }

        LocalDateTime dataHoraProximoVago = agendamentoService.encontrarProximoHorarioDisponivel(medicos, consulta.getEspecialidade(), paciente.getCidade());
        List<Consulta> consultasParaRemarcar = agendamentoService.buscarConsultasParaReagendar(consulta.getEspecialidade(), paciente.getCidade(), PrioridadeConsulta.URGENTE);

        if (!consultasParaRemarcar.isEmpty()) {
            Consulta consultaNaoConfirmada = consultasParaRemarcar.getFirst();

            if (dataHoraProximoVago == null || (consultaNaoConfirmada.getDataHora() != null && consultaNaoConfirmada.getDataHora().isBefore(dataHoraProximoVago))) {
                agendarConsultaUrgentePorRemanejamento(consulta, consultaNaoConfirmada, paciente, medicos);
                return;
            }
        }

        if (dataHoraProximoVago != null) {
            agendarConsultaEmHorarioVago(consulta, dataHoraProximoVago, paciente, medicos);
        } else {
            log.warn("Não foi possível encontrar horário para a consulta urgente: {}", consulta.getId());
            notificarEntradaNaListaDeEspera(consulta, paciente);
        }
    }

    private void agendarConsultaUrgentePorRemanejamento(Consulta consultaUrgente, Consulta consultaParaRemarcar, Paciente paciente, List<Medico> medicos) {
        log.info("Agendando consulta urgente {} por remanejamento da consulta {}",
                consultaUrgente.getId(), consultaParaRemarcar.getId());
        Medico medico = medicos.stream()
                .filter(m -> m.getId().equals(consultaParaRemarcar.getMedicoId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Médico não encontrado"));
        LocalDateTime dataHoraOriginal = consultaParaRemarcar.getDataHora();
        consultaUrgente.setMedicoId(consultaParaRemarcar.getMedicoId());
        consultaUrgente.setDataHora(dataHoraOriginal);
        consultaUrgente.setLocalConsulta(consultaParaRemarcar.getLocalConsulta());
        consultaUrgente.setStatus(StatusConsulta.AGENDADA);
        consultaRepository.salvar(consultaUrgente);

        LocalDateTime novoHorario = agendamentoService.encontrarProximoHorarioDisponivel(medicos, consultaParaRemarcar.getEspecialidade(), paciente.getCidade());

        if (novoHorario != null) {
            consultaParaRemarcar.reagendar(novoHorario);
            consultaRepository.salvar(consultaParaRemarcar);

            notificarReagendar(consultaParaRemarcar);
        } else {
            consultaParaRemarcar.marcarParaRemanejo();
            consultaRepository.salvar(consultaParaRemarcar);
            notificarEntradaNaListaDeEspera(consultaParaRemarcar, paciente);
        }
        enviarNotificacaoConsultaAgendada(consultaUrgente, paciente, medico);
    }

    private void agendarConsultaEmHorarioVago(Consulta consulta, LocalDateTime dataHora, Paciente paciente, List<Medico> medicos) {
        log.info("Agendando consulta {} em horário vago: {}", consulta.getId(), dataHora);
        List<Medico> medicosDisponiveis = medicos.stream()
                .filter(medico -> agendamentoService.isHorarioDisponivel(medico, dataHora))
                .toList();

        if (medicosDisponiveis.isEmpty()) {
            log.error("Não foi possível encontrar médico disponível para a data: {}", dataHora);
            notificarEntradaNaListaDeEspera(consulta, paciente);
            return;
        }

        Medico medicoSelecionado = selecionarMedicoComMenosCarga(medicosDisponiveis, dataHora);

        consulta.setMedicoId(medicoSelecionado.getId());
        consulta.setDataHora(dataHora);
        consulta.setLocalConsulta("Consultório " + medicoSelecionado.getNome());
        consulta.setStatus(StatusConsulta.AGENDADA);
        consultaRepository.salvar(consulta);
        enviarNotificacaoConsultaAgendada(consulta, paciente, medicoSelecionado);
    }

    private Medico selecionarMedicoComMenosCarga(List<Medico> medicosDisponiveis, LocalDateTime dataHora) {
        if (medicosDisponiveis.size() == 1) {
            return medicosDisponiveis.getFirst();
        }
        LocalDateTime inicioDoDia = dataHora.toLocalDate().atStartOfDay();
        LocalDateTime fimDoDia = inicioDoDia.plusDays(1);
        Map<String, Long> consultasPorMedico = new HashMap<>();

        for (Medico medico : medicosDisponiveis) {
            List<Consulta> consultasDoMedico = consultaRepository.buscarConsultasPorMedicoEIntervalo(medico.getId(), inicioDoDia, fimDoDia);

            consultasPorMedico.put(medico.getId(), (long) consultasDoMedico.size());
        }

        return medicosDisponiveis.stream()
                .min(Comparator.comparing(m -> consultasPorMedico.get(m.getId())))
                .orElse(medicosDisponiveis.getFirst());
    }

    private void processarConsultaNormal(Consulta consulta) {
        log.info("Processando consulta normal: {}", consulta.getId());
        PacienteDTO pacienteDTO = pacienteServicePort.buscarPacientePorCpf(consulta.getPacienteCpf());
        Paciente paciente = converterParaPaciente(pacienteDTO);
        List<MedicoDTO> medicosDTO = medicoServicePort.buscarMedicosPorEspecialidadeECidade(consulta.getEspecialidade(), paciente.getCidade());
        List<Medico> medicos = medicosDTO.stream().map(this::converterParaMedico).toList();
        LocalDateTime dataHoraConsulta = agendamentoService.encontrarProximoHorarioDisponivel(medicos, consulta.getEspecialidade(),
                paciente.getCidade());

        if (dataHoraConsulta == null) {
            notificarEntradaNaListaDeEspera(consulta, paciente);
            return;
        }

        Medico medicoSelecionado = selecionarMedicoComMenosCarga(medicos, dataHoraConsulta);

        consulta.setMedicoId(medicoSelecionado.getId());
        consulta.setDataHora(dataHoraConsulta);
        consulta.setLocalConsulta("Consultório " + medicoSelecionado.getNome());
        consulta.setStatus(StatusConsulta.AGENDADA);
        consultaRepository.salvar(consulta);
        enviarNotificacaoConsultaAgendada(consulta, paciente, medicoSelecionado);
    }

    private Medico converterParaMedico(MedicoDTO dto) {
        return Medico.builder()
                .id(dto.getId())
                .nome(dto.getNome())
                .especialidade(dto.getEspecialidade())
                .cidade(dto.getCidade())
                .horariosTrabalho(dto.getHorariosTrabalho())
                .build();
    }

    private Paciente converterParaPaciente(PacienteDTO pacienteDTO) {
        return Paciente.builder()
                .cpf(pacienteDTO.getCpf())
                .nome(pacienteDTO.getNome())
                .cidade(pacienteDTO.getCidade())
                .email(pacienteDTO.getEmail())
                .telefone(pacienteDTO.getTelefone())
                .build();
    }

    private void notificarEntradaNaListaDeEspera(Consulta consulta, Paciente paciente) {
        NotificacaoDTO notificacao = NotificacaoDTO.builder()
                .consultaId(consulta.getId())
                .nomePaciente(paciente.getNome())
                .email(paciente.getEmail())
                .telefone(paciente.getTelefone())
                .consulta(consulta.getId().toString())
                .tipoNotificacao(TipoNotificacao.ENTRADA_LISTA_ESPERA)
                .build();

        notificacaoServicePort.enviarNotificacao(notificacao);
    }

    private void notificarReagendar(Consulta consulta) {
        PacienteDTO paciente = pacienteServicePort.buscarPacientePorCpf(consulta.getPacienteCpf());
        List<MedicoDTO> medicosDTO = medicoServicePort.buscarMedicosPorEspecialidadeECidade(consulta.getEspecialidade(), paciente.getCidade());
        MedicoDTO medicoDTO = medicosDTO.stream()
                .filter(m -> m.getId().equals(consulta.getMedicoId()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Médico não encontrado"));
        NotificacaoDTO notificacao = NotificacaoDTO.builder()
                .consultaId(consulta.getId())
                .nomePaciente(paciente.getNome())
                .email(paciente.getEmail())
                .telefone(paciente.getTelefone())
                .consulta(consulta.getId().toString())
                .localConsulta(consulta.getLocalConsulta())
                .nomeMedico(medicoDTO.getNome())
                .tipoNotificacao(TipoNotificacao.REMANEJO_CONSULTA)
                .dataConsulta(consulta.getDataHora().toString())
                .build();

        notificacaoServicePort.enviarNotificacao(notificacao);
    }

    private void enviarNotificacaoConsultaAgendada(Consulta consulta, Paciente paciente, Medico medico) {
        NotificacaoDTO notificacao = NotificacaoDTO.builder()
                .consultaId(consulta.getId())
                .nomePaciente(paciente.getNome())
                .email(paciente.getEmail())
                .telefone(paciente.getTelefone())
                .consulta(consulta.getId().toString())
                .localConsulta(consulta.getLocalConsulta())
                .nomeMedico(medico.getNome())
                .tipoNotificacao(TipoNotificacao.CONSULTA_AGENDADA)
                .dataConsulta(consulta.getDataHora().toString())
                .build();

        notificacaoServicePort.enviarNotificacao(notificacao);
    }
}