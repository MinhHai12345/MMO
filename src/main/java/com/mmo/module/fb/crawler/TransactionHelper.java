package com.mmo.module.fb.crawler;

import org.springframework.stereotype.Component;

import java.util.function.BiConsumer;

@Component
public class TransactionHelper {

    public <E, D> void executeInNewTransaction(E entity, D dto, BiConsumer<E, D> saveConsumer) {
        saveConsumer.accept(entity, dto);
    }

}