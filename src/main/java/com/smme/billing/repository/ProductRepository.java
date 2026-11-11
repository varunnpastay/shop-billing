package com.smme.billing.repository;

import com.smme.billing.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByNameAsc();
    List<Product> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
    Optional<Product> findFirstByNameIgnoreCase(String name);
}
