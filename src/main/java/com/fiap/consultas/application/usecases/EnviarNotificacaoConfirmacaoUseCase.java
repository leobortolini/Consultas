package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.NotificacaoDTO;
import com.fiap.consultas.application.dtos.PacienteDTO;
import com.fiap.consultas.application.ports.NotificacaoServicePort;
import com.fiap.consultas.application.ports.PacienteServicePort;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.enums.TipoNotificacao;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnviarNotificacaoConfirmacaoUseCase {

    private final ConsultaRepository consultaRepository;
    private final PacienteServicePort pacienteServicePort;
    private final NotificacaoServicePort notificacaoServicePort;

    @Scheduled(fixedRate = 10000)
    public void executar() {
        LocalDateTime dataAtual = LocalDateTime.now();
        LocalDateTime duasSemanasFuturo = dataAtual.plusWeeks(2);

        List<Consulta> consultasAgendadas = consultaRepository.buscarPorStatus(StatusConsulta.AGENDADA);

        for (Consulta consulta : consultasAgendadas) {
            if (estaProximoDuasSemanas(consulta.getDataHora(), duasSemanasFuturo)) {
                enviarNotificacaoConfirmacao(consulta);
            }
        }
    }

    @Scheduled(fixedRate = 10000)
    public void enviarLembreteDiaAnterior() {
        LocalDateTime dataAtual = LocalDateTime.now();
        LocalDateTime amanha = dataAtual.plusDays(1);
        List<Consulta> consultasConfirmadas = consultaRepository.buscarPorStatus(StatusConsulta.CONFIRMADA);

        for (Consulta consulta : consultasConfirmadas) {
            if (ehAmanha(consulta.getDataHora(), amanha)) {
                enviarNotificacaoDiaAnterior(consulta);
            }
        }
    }

    private boolean estaProximoDuasSemanas(LocalDateTime dataConsulta, LocalDateTime duasSemanasFuturo) {
        return dataConsulta.isAfter(LocalDateTime.now()) &&
            (dataConsulta.isBefore(duasSemanasFuturo) || dataConsulta.isEqual(duasSemanasFuturo));
    }
    private boolean ehAmanha(LocalDateTime dataConsulta, LocalDateTime amanha) {
        return dataConsulta.toLocalDate().equals(amanha.toLocalDate());
    }

    private void enviarNotificacaoConfirmacao(Consulta consulta) {
        PacienteDTO paciente = pacienteServicePort.buscarPacientePorCpf(consulta.getPacienteCpf());

        NotificacaoDTO notificacao = NotificacaoDTO.builder()
                .consultaId(consulta.getId())
                .nomePaciente(paciente.getNome())
                .email(paciente.getEmail())
                .telefone(paciente.getTelefone())
                .consulta(consulta.getId().toString())
                .localConsulta(consulta.getLocalConsulta())
                .dataConsulta(consulta.getDataHora().toString())
                .tipoNotificacao(TipoNotificacao.CONFIRMACAO_CONSULTA)
                .build();

        notificacaoServicePort.enviarNotificacao(notificacao);
    }

    private void enviarNotificacaoDiaAnterior(Consulta consulta) {
        PacienteDTO paciente = pacienteServicePort.buscarPacientePorCpf(consulta.getPacienteCpf());

        NotificacaoDTO notificacao = NotificacaoDTO.builder()
                .consultaId(consulta.getId())
                .nomePaciente(paciente.getNome())
                .email(paciente.getEmail())
                .telefone(paciente.getTelefone())
                .consulta(consulta.getId().toString())
                .localConsulta(consulta.getLocalConsulta())
                .dataConsulta(consulta.getDataHora().toString())
                .tipoNotificacao(TipoNotificacao.AVISO_UM_DIA_ANTES)
                .build();

        notificacaoServicePort.enviarNotificacao(notificacao);
    }
}