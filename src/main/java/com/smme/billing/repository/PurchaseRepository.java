package com.smme.billing.repository;

import com.smme.billing.model.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseRepository extends JpaRepository<Purchase, Long> {
    List<Purchase> findByPurchaseDateBetweenOrderByPurchaseDateDescIdDesc(LocalDate from, LocalDate to);
    List<Purchase> findBySupplierIdOrderByPurchaseDateDescIdDesc(Long supplierId);
    List<Purchase> findAllByOrderByPurchaseDateDescIdDesc();
}
