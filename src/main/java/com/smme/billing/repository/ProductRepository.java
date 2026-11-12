package com.smme.billing.repository;

import com.smme.billing.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByOrderByNameAsc();
    List<Product> findByNameContainingIgnoreCaseOrderByNameAsc(String name);
    Optional<Product> findFirstByNameIgnoreCase(String name);

    @org.springframework.data.jpa.repository.Query("select p from Product p where lower(p.name) like lower(concat('%', :q, '%')) or lower(p.code) like lower(concat('%', :q, '%')) or lower(p.category) like lower(concat('%', :q, '%')) or lower(p.fabric) like lower(concat('%', :q, '%')) or lower(p.color) like lower(concat('%', :q, '%')) or p.barcode = :q order by p.name")
    List<Product> search(@org.springframework.data.repository.query.Param("q") String q);

    @org.springframework.data.jpa.repository.Query("select distinct p.category from Product p where p.category is not null order by p.category")
    List<String> categories();

    @org.springframework.data.jpa.repository.Query("select count(p) from Product p where p.minStock > 0 and coalesce(p.stockQty, 0) <= p.minStock")
    long countLowStock();
}
