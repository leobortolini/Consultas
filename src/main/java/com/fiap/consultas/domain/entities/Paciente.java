package com.fiap.consultas.domain.entities;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Paciente {
    private String cpf;
    private String nome;
    private String email;
    private String telefone;
    private String cidade;
}