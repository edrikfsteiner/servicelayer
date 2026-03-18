package com.migration.servicelayer.consumer;

import com.migration.servicelayer.origin.model.ClienteLegado;
import com.migration.servicelayer.target.model.UserNovo;
import com.migration.servicelayer.target.repository.UserNovoRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class MigrationConsumer {

    @Autowired
    private final UserNovoRepository targetRepository;

    public MigrationConsumer(UserNovoRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    @RabbitListener(queues = "migration.data.queue")
    public void processarMensagem(ClienteLegado legado) {
        System.out.println("Migrando cliente: " + legado.getNmCompleto());

        UserNovo novo = new UserNovo();
        novo.setFullName(legado.getNmCompleto());
        novo.setIsActive(legado.getStsAtivo() == 1);
        novo.setBirthDate(LocalDate.now());

        targetRepository.save(novo);
    }
}