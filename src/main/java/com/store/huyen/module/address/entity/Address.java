package com.store.huyen.module.address.entity;

import com.store.huyen.entity.AbstractEntity;
import com.store.huyen.module.customer.entity.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "addresses")
public class Address extends AbstractEntity {

    @Column
    @NotBlank(message = "Street is required")
    private String street;

    @Column(name = "city")
    @NotBlank(message = "City is required")
    private String city;

    @Column(name = "state")
    @NotBlank(message = "State/Province is required")
    private String state;

    @Column(name = "country")
    @NotBlank(message = "Country is required")
    private String country;

    @Column(name = "postal_code")
    @NotBlank(message = "Postal code is required")
    private String postalCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;
}
