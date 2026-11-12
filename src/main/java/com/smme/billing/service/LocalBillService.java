package com.smme.billing.service;

import com.smme.billing.NotFoundException;
import com.smme.billing.model.LocalBill;
import com.smme.billing.model.LocalBillItem;
import com.smme.billing.model.ShopSetting;
import com.smme.billing.model.Customer;
import com.smme.billing.repository.CustomerRepository;
import com.smme.billing.repository.LocalBillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Local (retail counter) billing: MRP / rate / savings receipt, numbered 0001/2026 per year. */
@Service
public class LocalBillService {

    private final LocalBillRepository repo;
    private final SettingService settingService;
    private final CustomerRepository customerRepo;
    private final StockService stock;
    private final ActivityService activity;

    public LocalBillService(LocalBillRepository repo, SettingService settingService, CustomerRepository customerRepo,
                            StockService stock, ActivityService activity) {
        this.repo = repo;
        this.settingService = settingService;
        this.customerRepo = customerRepo;
        this.stock = stock;
        this.activity = activity;
    }

    static java.util.Map<String, BigDecimal> qty(LocalBill b) {
        java.util.Map<String, BigDecimal> m = StockService.totals();
        for (LocalBillItem i : b.getItems()) StockService.add(m, i.getName(), i.getQty());
        return m;
    }

    public record ItemForm(String name, BigDecimal qty, BigDecimal mrp, BigDecimal rate) {}

    public record BillForm(Long id, LocalDate billDate, String customerName, String custMobile, String paymentMode,
                           List<ItemForm> items) {}

    public LocalBill get(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Bill not found"));
    }

    /** The number the next bill dated in this year will get (shown on a new bill before it is saved). */
    public String peekNextBillNo(int year) {
        return format(nextSeq(year), year);
    }

    private int nextSeq(int year) {
        Integer max = repo.maxSeqForYear(year);
        int next = max == null ? 1 : max + 1;
        ShopSetting s = settingService.get();
        if (s.getLocalBillYear() != null && s.getLocalBillYear() == year && s.getNextLocalBillNo() != null) {
            next = Math.max(next, s.getNextLocalBillNo());
        }
        return next;
    }

    public int nextSeqForSettings() {
        return nextSeq(LocalDate.now().getYear());
    }

    static String format(int seq, int year) {
        return String.format("%04d/%d", seq, year);
    }

    @Transactional
    public synchronized LocalBill save(BillForm form) {
        LocalBill bill;
        LocalDate date = form.billDate() != null ? form.billDate() : LocalDate.now();
        if (form.id() != null) {
            bill = get(form.id());
            if (bill.isCancelled()) throw new BillService.InvalidBillException("A cancelled bill cannot be changed. Restore it first.");
            stock.apply(qty(bill), +1);   // old lines back into stock; the new lines are taken out below
        } else {
            bill = new LocalBill();
            bill.setCreatedAt(LocalDateTime.now());
            int year = date.getYear();
            int seq = nextSeq(year);
            bill.setBillYear(year);
            bill.setSeqNo(seq);
            bill.setBillNo(format(seq, year));
            ShopSetting s = settingService.get();
            if (s.getLocalBillYear() == null || year >= s.getLocalBillYear()) {
                s.setLocalBillYear(year);
                s.setNextLocalBillNo(seq + 1);
                settingService.saveRaw(s);
            }
        }

        bill.setBillDate(date);
        bill.setCustomerName(trim(form.customerName()));
        bill.setCustMobile(trim(form.custMobile()));
        bill.setPaymentMode(BillService.mode(form.paymentMode()));

        List<LocalBillItem> items = new ArrayList<>();
        if (form.items() != null) {
            for (ItemForm f : form.items()) {
                if (f == null || f.name() == null || f.name().isBlank()) continue;
                String name = f.name().trim();
                if (nz(f.qty()).signum() <= 0)
                    throw new BillService.InvalidBillException("Quantity must be more than 0 for \"" + name + "\"");
                if (nz(f.rate()).signum() < 0 || nz(f.mrp()).signum() < 0)
                    throw new BillService.InvalidBillException("MRP and rate cannot be negative for \"" + name + "\"");
                LocalBillItem i = new LocalBillItem();
                i.setName(name);
                i.setQty(nz(f.qty()));
                i.setMrp(nz(f.mrp()));
                i.setRate(nz(f.rate()));
                i.setLocalBill(bill);
                items.add(i);
            }
        }
        if (items.isEmpty()) throw new BillService.InvalidBillException("Add at least one item to the bill");

        bill.getItems().clear();
        bill.getItems().addAll(items);
        calculate(bill);
        stock.apply(qty(bill), -1);
        saveCustomerIfNew(bill.getCustomerName(), bill.getCustMobile());
        return repo.save(bill);
    }

    /** Remembers a named customer (with mobile) so the next bill fills the mobile number automatically. */
    private void saveCustomerIfNew(String name, String mobile) {
        if (name == null || mobile == null || "cash".equalsIgnoreCase(name)) return;
        if (customerRepo.findFirstByNameIgnoreCase(name).isPresent()) return;
        Customer c = new Customer();
        c.setName(name);
        c.setPhone(mobile);
        customerRepo.save(c);
    }

    /** Same maths as the old Smart Billing page: amount = qty x rate, savings = MRP total - sale, pay the sale total. */
    public static void calculate(LocalBill b) {
        BigDecimal qty = BigDecimal.ZERO;
        BigDecimal sale = BigDecimal.ZERO;
        BigDecimal mrpTotal = BigDecimal.ZERO;
        for (LocalBillItem i : b.getItems()) {
            BigDecimal amt = nz(i.getQty()).multiply(nz(i.getRate())).setScale(2, RoundingMode.HALF_UP);
            i.setAmount(amt);
            qty = qty.add(nz(i.getQty()));
            sale = sale.add(amt);
            mrpTotal = mrpTotal.add(nz(i.getQty()).multiply(nz(i.getMrp())).setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal savings = mrpTotal.subtract(sale);
        b.setTotalQty(qty);
        b.setTotalSale(sale);
        b.setTotalSavings(savings.signum() > 0 ? savings : BigDecimal.ZERO.setScale(2));
        b.setNetPayable(sale);
    }

    @Transactional
    public void setCancelled(Long id, boolean cancelled) {
        LocalBill b = get(id);
        if (b.isCancelled() == cancelled) return;
        b.setCancelled(cancelled);
        stock.apply(qty(b), cancelled ? +1 : -1);
        repo.save(b);
        activity.log(cancelled ? "Bill cancelled" : "Bill restored",
                "Local bill " + b.getBillNo() + " – " + (b.getCustomerName() == null ? "Cash" : b.getCustomerName()) + " – ₹" + b.getNetPayable());
    }

    public List<LocalBill> savedList() {
        return repo.findTop200ByOrderByIdDesc();
    }

    public List<LocalBill> recent() {
        return repo.findTop10ByOrderByIdDesc();
    }

    /** Bills in the period, cancelled ones excluded. */
    public List<LocalBill> forReport(LocalDate from, LocalDate to) {
        return repo.findByBillDateBetweenAndCancelledFalseOrderByIdAsc(from, to);
    }

    public List<LocalBill> search(String q, LocalDate from, LocalDate to, String status) {
        if (q != null && !q.isBlank()) {
            String t = q.trim();
            var exact = repo.findByBillNo(t);
            if (exact.isPresent()) return List.of(exact.get());
        }
        LocalDate f = from != null ? from : LocalDate.of(2000, 1, 1);
        LocalDate t = to != null ? to : LocalDate.of(2100, 12, 31);
        String needle = q == null ? "" : q.trim().toLowerCase();
        return repo.findByBillDateBetweenOrderByIdDesc(f, t).stream()
                .filter(b -> needle.isEmpty()
                        || contains(b.getBillNo(), needle)
                        || contains(b.getCustomerName(), needle)
                        || contains(b.getCustMobile(), needle))
                .filter(b -> switch (status == null ? "ALL" : status) {
                    case "ACTIVE" -> !b.isCancelled();
                    case "CANCELLED" -> b.isCancelled();
                    default -> true;
                })
                .toList();
    }

    private static boolean contains(String v, String needle) {
        return v != null && v.toLowerCase().contains(needle);
    }

    public static BigDecimal sum(List<LocalBill> bills, Function<LocalBill, BigDecimal> field) {
        return bills.stream().map(field).map(LocalBillService::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
