package com.mmo.module.fb.monetization.repository;

import com.mmo.module.fb.monetization.entity.TipTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TipTransactionRepository extends JpaRepository<TipTransaction, Long> {
}
