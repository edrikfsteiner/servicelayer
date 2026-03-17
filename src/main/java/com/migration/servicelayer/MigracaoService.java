package com.migration.servicelayer;

import com.migration.servicelayer.model.origin.ClienteLegado;
import com.migration.servicelayer.repository.ClienteLegadoRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MigracaoService {

    private final ClienteLegadoRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public MigracaoService(ClienteLegadoRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void iniciarMigracaoEmMassa() {
        // 1. Busca os dados do banco legado (MySQL)
        List<ClienteLegado> clientes = repository.findAll();

        // 2. Envia cada um para a fila do RabbitMQ
        clientes.forEach(cliente -> {
            rabbitTemplate.convertAndSend("migration.exchange", "migration.routing.key", cliente);
            System.out.println("Enviado para a fila: " + cliente.getNmCompleto());
        });
    }
}