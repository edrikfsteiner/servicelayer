package com.migration.servicelayer.origin.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TB_CLIENTES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ClienteLegado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CLI")
    private Long idCli;

    @Column(name = "NM_COMPLETO")
    private String nmCompleto;

    @Column(name = "DT_NASCIMENTO")
    private String dtNascimento;

    @Column(name = "STS_ATIVO")
    private Integer stsAtivo;
}