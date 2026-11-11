package com.smme.billing.service;

import com.smme.billing.NotFoundException;
import com.smme.billing.model.*;
import com.smme.billing.repository.BillRepository;
import com.smme.billing.repository.CustomerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Service
public class BillService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final BillRepository billRepo;
    private final CustomerRepository customerRepo;
    private final SettingService settingService;

    public BillService(BillRepository billRepo, CustomerRepository customerRepo, SettingService settingService) {
        this.billRepo = billRepo;
        this.customerRepo = customerRepo;
        this.settingService = settingService;
    }

    /** A bill the user tried to save is not valid; the message is shown on the form. */
    public static class InvalidBillException extends RuntimeException {
        public InvalidBillException(String message) { super(message); }
    }

    public Bill newBill() {
        ShopSetting s = settingService.get();
        Bill b = new Bill();
        long no = s.getNextBillNo() == null ? 1L : s.getNextBillNo();
        while (billRepo.existsByBillNo(no)) no++;
        b.setBillNo(no);
        b.setBillDate(LocalDate.now());
        b.setGstPercent(nz(s.getDefaultGst()));
        b.getItems().add(new BillItem());
        return b;
    }

    public Bill copyOf(Long id) {
        Bill old = get(id);
        Bill b = newBill();
        b.getItems().clear();
        b.setCustomerName(old.getCustomerName());
        b.setCustomerAddress(old.getCustomerAddress());
        b.setCustomerGstin(old.getCustomerGstin());
        b.setCustomerPhone(old.getCustomerPhone());
        b.setBillType(old.getBillType());
        b.setTaxType(old.getTaxType());
        b.setGstPercent(old.getGstPercent());
        b.setDiscount(old.getDiscount());
        b.setByName(old.getByName());
        for (BillItem oi : old.getItems()) {
            BillItem i = new BillItem();
            i.setParticulars(oi.getParticulars());
            i.setDetails(oi.getDetails());
            i.setHsn(oi.getHsn());
            i.setQty(oi.getQty());
            i.setUnit(oi.getUnit());
            i.setRate(oi.getRate());
            i.setAmount(oi.getAmount());
            b.getItems().add(i);
        }
        if (b.getItems().isEmpty()) b.getItems().add(new BillItem());
        return b;
    }

    public Bill get(Long id) {
        return billRepo.findById(id).orElseThrow(() -> new NotFoundException("Bill not found"));
    }

    @Transactional
    public Bill save(Bill form) {
        validate(form);
        Bill bill;
        if (form.getId() != null) {
            bill = get(form.getId());
            if (bill.isCancelled()) throw new InvalidBillException("A cancelled bill cannot be edited. Restore it first.");
        } else {
            bill = new Bill();
            bill.setCreatedAt(LocalDateTime.now());
            ShopSetting s = settingService.get();
            long no = s.getNextBillNo() == null ? 1L : s.getNextBillNo();
            while (billRepo.existsByBillNo(no)) no++;
            bill.setBillNo(no);
            s.setNextBillNo(no + 1);
            settingService.saveRaw(s);
        }

        bill.setBillDate(form.getBillDate() != null ? form.getBillDate() : LocalDate.now());
        bill.setCustomerName(trim(form.getCustomerName()));
        bill.setCustomerAddress(trim(form.getCustomerAddress()));
        bill.setCustomerGstin(upper(form.getCustomerGstin()));
        bill.setCustomerPhone(trim(form.getCustomerPhone()));
        bill.setBillType("CREDIT".equals(form.getBillType()) ? "CREDIT" : "CASH");
        bill.setTaxType(switch (String.valueOf(form.getTaxType())) {
            case "IGST" -> "IGST";
            case "NONE" -> "NONE";
            default -> "CGST_SGST";
        });
        bill.setGstPercent(nz(form.getGstPercent()));
        bill.setDiscount(nz(form.getDiscount()));
        bill.setNotes(trim(form.getNotes()));
        bill.setByName(trim(form.getByName()));

        if ("CREDIT".equals(bill.getBillType())) {
            if (form.getId() == null) {
                bill.setPaid(false);
                bill.setPaidDate(null);
            }
        } else {
            bill.setPaid(true);
            if (bill.getPaidDate() == null) bill.setPaidDate(bill.getBillDate());
        }

        bill.getItems().clear();
        for (BillItem fi : form.getItems()) {
            if (fi.getParticulars() == null || fi.getParticulars().isBlank()) continue;
            BillItem i = new BillItem();
            i.setParticulars(fi.getParticulars().trim());
            i.setDetails(trim(fi.getDetails()));
            i.setHsn(trim(fi.getHsn()));
            i.setUnit(trim(fi.getUnit()));
            i.setQty(nz(fi.getQty()));
            i.setRate(nz(fi.getRate()));
            i.setBill(bill);
            bill.getItems().add(i);
        }

        calculate(bill);
        saveCustomerIfNew(bill);
        return billRepo.save(bill);
    }

    /** Rejects bills that would print wrong or negative invoices. */
    static void validate(Bill form) {
        if (form.getCustomerName() == null || form.getCustomerName().isBlank())
            throw new InvalidBillException("Customer name is required");
        BigDecimal sub = BigDecimal.ZERO;
        int count = 0;
        for (BillItem i : form.getItems()) {
            if (i.getParticulars() == null || i.getParticulars().isBlank()) continue;
            count++;
            String name = i.getParticulars().trim();
            if (nz(i.getQty()).signum() <= 0)
                throw new InvalidBillException("Quantity must be more than 0 for \"" + name + "\"");
            if (nz(i.getRate()).signum() < 0)
                throw new InvalidBillException("Rate cannot be negative for \"" + name + "\"");
            sub = sub.add(nz(i.getQty()).multiply(nz(i.getRate())).setScale(2, RoundingMode.HALF_UP));
        }
        if (count == 0) throw new InvalidBillException("Add at least one item to the bill");
        BigDecimal gst = nz(form.getGstPercent());
        if (gst.signum() < 0 || gst.compareTo(HUNDRED) > 0)
            throw new InvalidBillException("GST rate must be between 0 and 100");
        BigDecimal disc = nz(form.getDiscount());
        if (disc.signum() < 0) throw new InvalidBillException("Discount cannot be negative");
        if (disc.compareTo(sub) > 0) throw new InvalidBillException("Discount cannot be more than the sub total");
    }

    public static void calculate(Bill b) {
        BigDecimal sub = BigDecimal.ZERO;
        for (BillItem i : b.getItems()) {
            BigDecimal amt = nz(i.getQty()).multiply(nz(i.getRate())).setScale(2, RoundingMode.HALF_UP);
            i.setAmount(amt);
            sub = sub.add(amt);
        }
        b.setSubTotal(sub);
        BigDecimal taxable = sub.subtract(nz(b.getDiscount()));
        b.setTaxableAmount(taxable);

        BigDecimal gst = nz(b.getGstPercent());
        b.setCgst(BigDecimal.ZERO);
        b.setSgst(BigDecimal.ZERO);
        b.setIgst(BigDecimal.ZERO);
        if ("CGST_SGST".equals(b.getTaxType())) {
            BigDecimal half = taxable.multiply(gst).divide(new BigDecimal("200"), 2, RoundingMode.HALF_UP);
            b.setCgst(half);
            b.setSgst(half);
        } else if ("IGST".equals(b.getTaxType())) {
            b.setIgst(taxable.multiply(gst).divide(HUNDRED, 2, RoundingMode.HALF_UP));
        }

        BigDecimal total = taxable.add(b.getCgst()).add(b.getSgst()).add(b.getIgst());
        BigDecimal rounded = total.setScale(0, RoundingMode.HALF_UP);
        b.setRoundOff(rounded.subtract(total));
        b.setGrandTotal(rounded.setScale(2, RoundingMode.HALF_UP));
    }

    private void saveCustomerIfNew(Bill b) {
        if (b.getCustomerName() == null || b.getCustomerName().isBlank()) return;
        if (customerRepo.findFirstByNameIgnoreCase(b.getCustomerName()).isPresent()) return;
        Customer c = new Customer();
        c.setName(b.getCustomerName());
        c.setAddress(b.getCustomerAddress());
        c.setGstin(b.getCustomerGstin());
        c.setPhone(b.getCustomerPhone());
        customerRepo.save(c);
    }

    @Transactional
    public void markPaid(Long id, boolean paid) {
        Bill b = get(id);
        if (b.isCancelled()) throw new InvalidBillException("A cancelled bill cannot be marked paid or unpaid");
        b.setPaid(paid);
        b.setPaidDate(paid ? LocalDate.now() : null);
        billRepo.save(b);
    }

    @Transactional
    public void setCancelled(Long id, boolean cancelled) {
        Bill b = get(id);
        b.setCancelled(cancelled);
        billRepo.save(b);
    }

    public List<Bill> search(String q, LocalDate from, LocalDate to, String status) {
        if (q != null && q.trim().matches("\\d{1,18}")) {
            List<Bill> one = new ArrayList<>();
            billRepo.findByBillNo(Long.parseLong(q.trim())).ifPresent(one::add);
            if (!one.isEmpty()) return one;
        }
        LocalDate f = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate t = to != null ? to : LocalDate.of(2100, 12, 31);
        List<Bill> list = billRepo.findByBillDateBetweenAndCustomerNameContainingIgnoreCaseOrderByBillNoDesc(f, t, q == null ? "" : q.trim());
        if (status == null || status.isBlank() || "ALL".equals(status)) return list;
        return list.stream().filter(b -> switch (status) {
            case "PAID" -> b.isPaid() && !b.isCancelled();
            case "UNPAID" -> !b.isPaid() && !b.isCancelled();
            case "CANCELLED" -> b.isCancelled();
            default -> true;
        }).toList();
    }

    public List<Bill> forReport(LocalDate from, LocalDate to) {
        return billRepo.findByBillDateBetweenAndCancelledFalseOrderByBillNoAsc(from, to);
    }

    public List<Bill> unpaid() {
        return billRepo.findByPaidFalseAndCancelledFalseOrderByBillNoDesc();
    }

    /** Names used before in the "By" field, for the drop-down suggestion. */
    public List<String> byNames() {
        return billRepo.findDistinctByNames();
    }

    public List<Bill> recent() {
        return billRepo.findTop10ByOrderByIdDesc();
    }

    public static BigDecimal sum(List<Bill> bills, Function<Bill, BigDecimal> field) {
        return bills.stream().map(field).map(BillService::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String upper(String s) {
        String t = trim(s);
        return t == null ? null : t.toUpperCase();
    }
}
