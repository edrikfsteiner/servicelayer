package com.migration.servicelayer.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MappingStore {
    private final Map<String, Map<String, String>> store = new ConcurrentHashMap<>();

    public void saveMapping(String targetTable, Map<String, String> mapping) {
        store.put(targetTable, mapping);
    }

    public Map<String, String> getMapping(String targetTable) {
        return store.get(targetTable);
    }
}