package com.smme.billing.service;

import com.smme.billing.NotFoundException;
import com.smme.billing.model.Payment;
import com.smme.billing.model.Purchase;
import com.smme.billing.model.PurchaseItem;
import com.smme.billing.model.Supplier;
import com.smme.billing.repository.PaymentRepository;
import com.smme.billing.repository.PurchaseRepository;
import com.smme.billing.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Supplier invoices (goods received add to stock) and payments made to suppliers. */
@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepo;
    private final SupplierRepository supplierRepo;
    private final PaymentRepository paymentRepo;
    private final StockService stock;
    private final ActivityService activity;

    public PurchaseService(PurchaseRepository purchaseRepo, SupplierRepository supplierRepo, PaymentRepository paymentRepo,
                           StockService stock, ActivityService activity) {
        this.purchaseRepo = purchaseRepo;
        this.supplierRepo = supplierRepo;
        this.paymentRepo = paymentRepo;
        this.stock = stock;
        this.activity = activity;
    }

    public Purchase get(Long id) {
        return purchaseRepo.findById(id).orElseThrow(() -> new NotFoundException("Purchase not found"));
    }

    public Supplier supplier(Long id) {
        return supplierRepo.findById(id).orElseThrow(() -> new NotFoundException("Supplier not found"));
    }

    static Map<String, BigDecimal> qty(Purchase p) {
        Map<String, BigDecimal> m = StockService.totals();
        for (PurchaseItem i : p.getItems()) StockService.add(m, i.getProductName(), i.getQty());
        return m;
    }

    @Transactional
    public Purchase save(Purchase form) {
        if (form.getSupplierId() == null) throw new BillService.InvalidBillException("Choose the supplier");
        Supplier s = supplier(form.getSupplierId());
        List<PurchaseItem> lines = new ArrayList<>();
        for (PurchaseItem fi : form.getItems()) {
            if (fi.getProductName() == null || fi.getProductName().isBlank()) continue;
            String name = fi.getProductName().trim();
            if (nz(fi.getQty()).signum() <= 0)
                throw new BillService.InvalidBillException("Quantity must be more than 0 for \"" + name + "\"");
            if (nz(fi.getRate()).signum() < 0)
                throw new BillService.InvalidBillException("Rate cannot be negative for \"" + name + "\"");
            PurchaseItem i = new PurchaseItem();
            i.setProductName(name);
            i.setQty(fi.getQty());
            i.setRate(nz(fi.getRate()));
            i.setAmount(fi.getQty().multiply(nz(fi.getRate())).setScale(2, RoundingMode.HALF_UP));
            lines.add(i);
        }
        if (lines.isEmpty()) throw new BillService.InvalidBillException("Add at least one item");
        if (nz(form.getOtherCharges()).signum() < 0) throw new BillService.InvalidBillException("Other charges cannot be negative");

        Purchase p;
        boolean edit = form.getId() != null;
        if (edit) {
            p = get(form.getId());
            stock.apply(qty(p), -1);   // take the old quantities out; the new ones are added below
        } else {
            p = new Purchase();
            p.setCreatedAt(LocalDateTime.now());
        }
        p.setSupplierId(s.getId());
        p.setSupplierName(s.getName());
        p.setInvoiceNo(trim(form.getInvoiceNo()));
        p.setPurchaseDate(form.getPurchaseDate() != null ? form.getPurchaseDate() : LocalDate.now());
        p.setOtherCharges(nz(form.getOtherCharges()));
        p.setNotes(trim(form.getNotes()));
        p.getItems().clear();
        BigDecimal total = p.getOtherCharges();
        for (PurchaseItem i : lines) {
            i.setPurchase(p);
            p.getItems().add(i);
            total = total.add(i.getAmount());
        }
        p.setTotal(total);
        stock.apply(qty(p), +1);
        p = purchaseRepo.save(p);
        activity.log(edit ? "Purchase edited" : "Purchase added",
                s.getName() + (p.getInvoiceNo() == null ? "" : " inv " + p.getInvoiceNo()) + " – ₹" + total);
        return p;
    }

    @Transactional
    public void delete(Long id) {
        Purchase p = get(id);
        stock.apply(qty(p), -1);
        purchaseRepo.delete(p);
        activity.log("Purchase deleted", p.getSupplierName() + (p.getInvoiceNo() == null ? "" : " inv " + p.getInvoiceNo())
                + " – ₹" + p.getTotal() + " (stock reduced back)");
    }

    @Transactional
    public Payment pay(Long supplierId, BigDecimal amount, String mode, LocalDate date, String note) {
        Supplier s = supplier(supplierId);
        if (amount == null || amount.signum() <= 0) throw new BillService.InvalidBillException("Amount must be more than 0");
        Payment p = new Payment();
        p.setKind(Payment.SUPPLIER);
        p.setPartyId(s.getId());
        p.setPartyName(s.getName());
        p.setAmount(amount);
        p.setMode(BillService.mode(mode));
        p.setPayDate(date != null ? date : LocalDate.now());
        p.setNote(trim(note));
        p.setCreatedAt(LocalDateTime.now());
        activity.log("Supplier paid", s.getName() + " – ₹" + amount + " (" + p.getMode() + ")");
        return paymentRepo.save(p);
    }

    @Transactional
    public void deletePayment(Long id) {
        paymentRepo.findById(id).ifPresent(p -> {
            paymentRepo.delete(p);
            activity.log("Payment deleted", (Payment.SUPPLIER.equals(p.getKind()) ? "To supplier " : "From customer ")
                    + p.getPartyName() + " – ₹" + p.getAmount() + " on " + p.getPayDate());
        });
    }

    public List<Purchase> ofSupplier(Long supplierId) {
        return purchaseRepo.findBySupplierIdOrderByPurchaseDateDescIdDesc(supplierId);
    }

    public List<Payment> paymentsTo(Long supplierId) {
        return paymentRepo.findByKindAndPartyIdOrderByPayDateDescIdDesc(Payment.SUPPLIER, supplierId);
    }

    /** What the shop still owes this supplier: purchases minus payments. */
    public BigDecimal balance(Long supplierId) {
        BigDecimal bought = ofSupplier(supplierId).stream().map(Purchase::getTotal).map(PurchaseService::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = paymentsTo(supplierId).stream().map(Payment::getAmount).map(PurchaseService::nz)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return bought.subtract(paid);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
