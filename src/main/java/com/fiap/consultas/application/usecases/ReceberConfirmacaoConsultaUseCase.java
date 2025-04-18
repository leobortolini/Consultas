package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.ConfirmacaoConsultaDTO;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceberConfirmacaoConsultaUseCase {

    private final ConsultaRepository consultaRepository;

    @Transactional
    public boolean executar(ConfirmacaoConsultaDTO confirmacao) {
        UUID consultaId = UUID.fromString(confirmacao.getConsultaId());
        Optional<Consulta> optionalConsulta = consultaRepository.buscarPorId(consultaId);

        if (optionalConsulta.isEmpty()) {
            log.error("Confirmacao recebida para consulta inexistente %s".formatted(confirmacao.getConsultaId()));
            return false;
        }
        Consulta consulta = optionalConsulta.get();

        processarConfirmacaoConsulta(consulta, confirmacao.isConfirmada());

        return true;
    }

    private void processarConfirmacaoConsulta(Consulta consulta, boolean confirmada) {
        if (confirmada) {
            consulta.confirmar();
            consultaRepository.salvar(consulta);
        } else {
            consulta.setStatus(StatusConsulta.CANCELADA);
            consultaRepository.salvar(consulta);
        }
    }
}
