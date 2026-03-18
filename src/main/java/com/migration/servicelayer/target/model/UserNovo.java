package com.migration.servicelayer.target.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "users") // Nome da tabela no Postgres
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class UserNovo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "birth_date")
    private LocalDate birthDate; // Tipo moderno de data

    @Column(name = "is_active")
    private Boolean isActive; // Tipo booleano real
}