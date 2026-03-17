package com.migration.servicelayer.model.origin;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "TB_CLIENTES") // Nome da tabela no MySQL
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class ClienteLegado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID_CLI")
    private Long idCli;

    @Column(name = "NM_COMPLETO")
    private String nmCompleto;

    @Column(name = "DT_NASCIMENTO")
    private String dtNascimento; // No legado costuma ser String

    @Column(name = "STS_ATIVO")
    private Integer stsAtivo; // 0 ou 1
}