package com.migration.servicelayer.controller;

import com.migration.servicelayer.MigracaoService;
import com.migration.servicelayer.dto.MapeamentoRequest;
import com.migration.servicelayer.service.AiMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/migracao")
public class MigracaoController {

    private final AiMappingService aiMappingService;

    @Autowired
    private MigracaoService migracaoService;

    // Injeção de dependência do serviço de IA que criámos anteriormente
    public MigracaoController(AiMappingService aiMappingService) {
        this.aiMappingService = aiMappingService;
    }

    @PostMapping("/iniciar")
    public ResponseEntity<String> iniciar() {
        migracaoService.iniciarMigracaoEmMassa();
        return ResponseEntity.ok("Processo de migração enviado para a fila!");
    }

    @PostMapping("/mapear")
    public ResponseEntity<String> gerarMapeamentoAutomatico(@RequestBody MapeamentoRequest request) {
        try {
            // Chama a IA passando os schemas recebidos no corpo do pedido
            String mapeamentoJson = aiMappingService.gerarMapeamentoDePara(
                    request.schemaOrigem(),
                    request.schemaDestino()
            );

            return ResponseEntity.ok(mapeamentoJson);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Erro ao processar com a IA: " + e.getMessage());
        }
    }
}