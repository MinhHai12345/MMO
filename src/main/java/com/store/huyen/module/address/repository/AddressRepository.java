package com.store.huyen.module.address.repository;

import com.store.huyen.module.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

@Repository
@RepositoryRestResource(path = AddressRepository.PATH, collectionResourceRel = "data",
        itemResourceRel = "address")
public interface AddressRepository extends JpaRepository<Address, Long> {
    String PATH = "addresses";
}
