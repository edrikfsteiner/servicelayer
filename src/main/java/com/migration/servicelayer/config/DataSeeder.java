package com.migration.servicelayer.config;

import com.migration.servicelayer.model.ApiClient;
import com.migration.servicelayer.repository.ApiClientRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    public CommandLineRunner initDatabase(ApiClientRepository repository, PasswordEncoder passwordEncoder) {
        return args -> {
            // Só cria se a collection estiver vazia
            if (repository.count() == 0) {
                ApiClient client = new ApiClient();
                client.setClientId("cliente-teste");
                // Aqui o Spring já gera o Hash correto para o banco
                client.setClientSecret(passwordEncoder.encode("senha123")); 
                client.setTenantId("empresa-teste");

                repository.save(client);
                System.out.println("Cliente de teste criado com sucesso no MongoDB Atlas!");
            }
        };
    }
}