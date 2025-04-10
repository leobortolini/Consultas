package com.fiap.consultas.application.ports;

import com.fiap.consultas.application.dtos.MedicoDTO;

import java.util.List;

public interface MedicoServicePort {
    List<MedicoDTO> buscarMedicosPorEspecialidadeECidade(String especialidade, String cidade);
}
