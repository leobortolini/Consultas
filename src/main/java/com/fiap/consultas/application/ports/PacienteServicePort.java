package com.fiap.consultas.application.ports;

import com.fiap.consultas.application.dtos.PacienteDTO;

public interface PacienteServicePort {
    PacienteDTO buscarPacientePorCpf(String cpf);
}