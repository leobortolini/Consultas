package com.fiap.consultas.infraestructure.api;

import com.fiap.consultas.application.dtos.RespostaAgendamentoDTO;
import com.fiap.consultas.application.dtos.SolicitacaoAgendamentoDTO;
import com.fiap.consultas.application.usecases.SolicitarAgendamentoUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/consultas")
@RequiredArgsConstructor
public class AgendamentoController {

    private final SolicitarAgendamentoUseCase solicitarAgendamentoUseCase;

    @PostMapping
    public ResponseEntity<RespostaAgendamentoDTO> solicitarAgendamento(@RequestBody SolicitacaoAgendamentoDTO solicitacao) {
        RespostaAgendamentoDTO resposta = solicitarAgendamentoUseCase.executar(solicitacao);
        return ResponseEntity.status(HttpStatus.CREATED).body(resposta);
    }
}