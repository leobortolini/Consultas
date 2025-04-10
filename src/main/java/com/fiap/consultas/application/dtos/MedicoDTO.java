package com.fiap.consultas.application.dtos;

import com.fiap.consultas.domain.entities.HorarioTrabalho;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MedicoDTO {
    private String id;
    private String nome;
    private String especialidade;
    private String cidade;
    private List<HorarioTrabalho> horariosTrabalho;
}