package com.store.huyen.module.customer.repository;

import com.store.huyen.module.customer.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@Repository
@RepositoryRestResource(path = CustomerRepository.PATH)
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    String PATH = "customers";
}
