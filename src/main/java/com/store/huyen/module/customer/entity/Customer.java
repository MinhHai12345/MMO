package com.store.huyen.module.customer.entity;

import com.store.huyen.entity.AbstractEntity;
import com.store.huyen.module.address.entity.Address;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(name = "customers")
public class Customer extends AbstractEntity {

    @NotBlank(message = "First name is required")
    @Column(name = "first_name", nullable = false)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Email(message = "Email should be valid")
    @NotBlank(message = "Email is required")
    @Column(nullable = false, unique = true)
    private String email;

    @Pattern(regexp = "\\+?\\d+", message = "Phone number is invalid")
    @Column(name = "phone_number")
    private String phoneNumber;

    @NotBlank(message = "Password is required")
    @Column(name = "password", nullable = false)
    private String password;

    @Past(message = "Date of birth must be in the past")
    @Column(name = "date_of_birth")
    private ZonedDateTime dateOfBirth;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<Address> addresses = new HashSet<>();

    @Column(name = "loyalty_points")
    private Integer loyaltyPoints = 0;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

}
