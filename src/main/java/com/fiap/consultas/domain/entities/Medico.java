package com.fiap.consultas.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Medico {
    private String id;
    private String nome;
    private String especialidade;
    private String cidade;
    private List<HorarioTrabalho> horariosTrabalho;
}