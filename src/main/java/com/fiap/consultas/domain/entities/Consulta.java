package com.fiap.consultas.domain.entities;

import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Consulta {
    private UUID id;
    private String pacienteCpf;
    private String medicoId;
    private String especialidade;
    private String cidade;
    private LocalDateTime dataHora;
    private String localConsulta;
    private PrioridadeConsulta prioridade;
    private StatusConsulta status;
    private LocalDateTime dataCriacao;
    private LocalDateTime dataAtualizacao;

    public boolean isRemarcavel() {
        return StatusConsulta.AGENDADA.equals(this.status);
    }

    public boolean isPrioridadeUrgente() {
        return PrioridadeConsulta.URGENTE.equals(this.prioridade);
    }

    public void confirmar() {
        this.status = StatusConsulta.CONFIRMADA;
        this.dataAtualizacao = LocalDateTime.now();
    }

    public void reagendar(LocalDateTime novaDataHora) {
        this.dataHora = novaDataHora;
        this.status = StatusConsulta.AGENDADA;
        this.dataAtualizacao = LocalDateTime.now();
    }

    public void marcarParaRemanejo() {
        this.status = StatusConsulta.PENDENTE_AGENDAMENTO;
        this.dataAtualizacao = LocalDateTime.now();
    }
}