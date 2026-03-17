package com.migration.servicelayer;

import com.migration.servicelayer.model.origin.ClienteLegado;
import com.migration.servicelayer.model.target.UserNovo;
import com.migration.servicelayer.repository.target.UserNovoRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class MigracaoConsumer {

    @Autowired
    private final UserNovoRepository targetRepository;

    public MigracaoConsumer(UserNovoRepository targetRepository) {
        this.targetRepository = targetRepository;
    }

    @RabbitListener(queues = "migration.data.queue")
    public void processarMensagem(ClienteLegado legado) {
        System.out.println("Migrando cliente: " + legado.getNmCompleto());

        // Transformação manual (depois integraremos com o mapa da IA)
        UserNovo novo = new UserNovo();
        novo.setFullName(legado.getNmCompleto());
        novo.setIsActive(legado.getStsAtivo() == 1);
        novo.setBirthDate(LocalDate.now()); // Exemplo fixo por enquanto

        targetRepository.save(novo);
    }
}