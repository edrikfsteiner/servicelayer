package com.migration.servicelayer.service;

import com.migration.servicelayer.origin.model.ClienteLegado;
import com.migration.servicelayer.origin.repository.ClienteLegadoRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class MigrationService {

    private final ClienteLegadoRepository repository;
    private final RabbitTemplate rabbitTemplate;

    public MigrationService(ClienteLegadoRepository repository, RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void startMassMigration() {
        List<ClienteLegado> clientes = repository.findAll();

        clientes.forEach(cliente -> {
            rabbitTemplate.convertAndSend("migration.exchange", "migration.routing.key", cliente);
            System.out.println("Enviado para a fila: " + cliente.getNmCompleto());
        });
    }
}