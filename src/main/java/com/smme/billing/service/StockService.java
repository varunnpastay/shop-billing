package com.smme.billing.service;

import com.smme.billing.model.Product;
import com.smme.billing.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keeps item stock in step with bills, purchases and returns. Bill lines are matched to items by name
 * (the name picked from the item list); lines with a name that is not in Items do not touch stock.
 */
@Service
public class StockService {

    private final ProductRepository productRepo;

    public StockService(ProductRepository productRepo) {
        this.productRepo = productRepo;
    }

    /** Adds (positive) or removes (negative) quantity per item name. */
    public void apply(Map<String, BigDecimal> qtyByName, int sign) {
        qtyByName.forEach((name, qty) -> change(name, sign > 0 ? qty : qty.negate()));
    }

    public void change(String name, BigDecimal delta) {
        if (name == null || delta == null || delta.signum() == 0) return;
        productRepo.findFirstByNameIgnoreCase(name.trim()).ifPresent(p -> {
            p.setStockQty(p.stock().add(delta));
            productRepo.save(p);
        });
    }

    /** Collects "item name -> total quantity" from bill lines. */
    public static Map<String, BigDecimal> totals() {
        return new LinkedHashMap<>();
    }

    public static void add(Map<String, BigDecimal> m, String name, BigDecimal qty) {
        if (name == null || name.isBlank() || qty == null) return;
        m.merge(name.trim().toLowerCase(), qty, BigDecimal::add);
    }

    /** Purchase price of the item with this name, or null. */
    public BigDecimal costOf(String name, Map<String, Product> byName) {
        Product p = name == null ? null : byName.get(name.trim().toLowerCase());
        return p == null ? null : p.getPurchasePrice();
    }
}
