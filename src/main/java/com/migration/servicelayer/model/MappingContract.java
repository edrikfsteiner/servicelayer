package com.migration.servicelayer.model;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "mapping_contracts")
public class MappingContract {

    @Id
    private String id;

    private Map<String, String> mapping;
    private LocalDateTime updatedAt;
}
