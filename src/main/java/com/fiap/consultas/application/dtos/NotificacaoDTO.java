package com.fiap.consultas.application.dtos;

import com.fiap.consultas.domain.enums.TipoNotificacao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificacaoDTO {
    private String nomePaciente;
    private String email;
    private String telefone;
    private String consulta;
    private String localConsulta;
    private String nomeMedico;
    private String dataConsulta;
    private TipoNotificacao tipoNotificacao;
}
