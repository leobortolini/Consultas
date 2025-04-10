package com.fiap.consultas.infraestructure.persistence.entities;

import com.fiap.consultas.domain.enums.PrioridadeConsulta;
import com.fiap.consultas.domain.enums.StatusConsulta;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "consultas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultaJpaEntity {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "paciente_cpf", nullable = false)
    private String pacienteCpf;

    @Column(name = "medico_id")
    private String medicoId;

    @Column(name = "especialidade", nullable = false)
    private String especialidade;

    @Column(name = "cidade", nullable = false)
    private String cidade;

    @Column(name = "data_hora")
    private LocalDateTime dataHora;

    @Column(name = "local_consulta")
    private String localConsulta;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false)
    private PrioridadeConsulta prioridade;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StatusConsulta status;

    @Column(name = "data_criacao", nullable = false)
    private LocalDateTime dataCriacao;

    @Column(name = "data_atualizacao", nullable = false)
    private LocalDateTime dataAtualizacao;
}
