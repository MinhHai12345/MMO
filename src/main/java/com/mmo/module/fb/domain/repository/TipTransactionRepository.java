package com.mmo.module.fb.domain.repository;

import com.mmo.module.fb.domain.entity.TipTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TipTransactionRepository extends JpaRepository<TipTransaction, Long> {
}
