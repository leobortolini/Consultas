package com.fiap.consultas.application.usecases;

import com.fiap.consultas.application.dtos.ConfirmacaoConsultaDTO;
import com.fiap.consultas.domain.entities.Consulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import com.fiap.consultas.domain.repositories.ConsultaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReceberConfirmacaoConsultaUseCase {

    private final ConsultaRepository consultaRepository;

    @Transactional
    public void executar(ConfirmacaoConsultaDTO confirmacao) {
        UUID consultaId = UUID.fromString(confirmacao.getConsultaId());
        Optional<Consulta> optionalConsulta = consultaRepository.buscarPorId(consultaId);

        if (optionalConsulta.isEmpty()) {
            throw new RuntimeException("Consulta não encontrada");
        }

        Consulta consulta = optionalConsulta.get();

        processarConfirmacaoConsulta(consulta, confirmacao.isConfirmada());
    }

    private void processarConfirmacaoConsulta(Consulta consulta, boolean confirmada) {
        if (confirmada) {
            consulta.confirmar();
            consultaRepository.salvar(consulta);
        } else {
            // Se recusar, cancela a consulta
            consulta.setStatus(StatusConsulta.CANCELADA);
            consultaRepository.salvar(consulta);
        }
    }
}
