package com.smme.billing.repository;

import com.smme.billing.model.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    List<Customer> findAllByOrderByNameAsc();
    List<Customer> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
    Optional<Customer> findFirstByNameIgnoreCase(String name);
}
