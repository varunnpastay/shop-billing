package com.smme.billing.service;

import com.smme.billing.model.*;
import com.smme.billing.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

/** Figures for the dashboard, accounts page and reports: sales, profit, stock value, best sellers, cash flow. */
@Service
@Transactional(readOnly = true)
public class InsightService {

    public static final List<String> MODES = List.of("CASH", "UPI", "BANK");

    private final BillService billService;
    private final LocalBillService localBillService;
    private final ProductRepository productRepo;
    private final PurchaseRepository purchaseRepo;
    private final PaymentRepository paymentRepo;
    private final ExpenseRepository expenseRepo;
    private final SalesReturnRepository returnRepo;
    private final SupplierRepository supplierRepo;
    private final PurchaseService purchaseService;

    public InsightService(BillService billService, LocalBillService localBillService, ProductRepository productRepo,
                          PurchaseRepository purchaseRepo, PaymentRepository paymentRepo, ExpenseRepository expenseRepo,
                          SalesReturnRepository returnRepo, SupplierRepository supplierRepo, PurchaseService purchaseService) {
        this.billService = billService;
        this.localBillService = localBillService;
        this.productRepo = productRepo;
        this.purchaseRepo = purchaseRepo;
        this.paymentRepo = paymentRepo;
        this.expenseRepo = expenseRepo;
        this.returnRepo = returnRepo;
        this.supplierRepo = supplierRepo;
        this.purchaseService = purchaseService;
    }

    // ---------------- items ----------------

    public record ItemSale(String name, BigDecimal qty, BigDecimal amount) {}

    /** Items sold in the period (wholesale + local, cancelled bills left out), most sold first by amount. */
    public List<ItemSale> bestSellers(LocalDate from, LocalDate to, int limit) {
        Map<String, ItemSale> m = new LinkedHashMap<>();
        for (Bill b : billService.forReport(from, to))
            for (BillItem i : b.getItems()) addSale(m, i.getParticulars(), i.getQty(), i.getAmount());
        for (LocalBill b : localBillService.forReport(from, to))
            for (LocalBillItem i : b.getItems()) addSale(m, i.getName(), i.getQty(), i.getAmount());
        return m.values().stream()
                .sorted(Comparator.comparing(ItemSale::amount).reversed())
                .limit(limit).toList();
    }

    private static void addSale(Map<String, ItemSale> m, String name, BigDecimal qty, BigDecimal amount) {
        if (name == null || name.isBlank()) return;
        m.merge(name.trim().toLowerCase(), new ItemSale(name.trim(), nz(qty), nz(amount)),
                (a, b) -> new ItemSale(a.name(), a.qty().add(b.qty()), a.amount().add(b.amount())));
    }

    /** Best sellers grouped by a product field (category or fabric); items not in the item list go under "Other". */
    public List<ItemSale> salesBy(LocalDate from, LocalDate to, Function<Product, String> field) {
        Map<String, Product> products = productsByName();
        Map<String, ItemSale> m = new LinkedHashMap<>();
        for (ItemSale s : bestSellers(from, to, Integer.MAX_VALUE)) {
            Product p = products.get(s.name().toLowerCase());
            String key = p == null || field.apply(p) == null || field.apply(p).isBlank() ? "Other" : field.apply(p);
            addSale(m, key, s.qty(), s.amount());
        }
        return m.values().stream().sorted(Comparator.comparing(ItemSale::amount).reversed()).toList();
    }

    public Map<String, Product> productsByName() {
        Map<String, Product> m = new HashMap<>();
        for (Product p : productRepo.findAll()) m.putIfAbsent(p.getName().trim().toLowerCase(), p);
        return m;
    }

    public record StockSummary(int items, BigDecimal qty, BigDecimal value, BigDecimal saleValue, List<Product> low) {}

    public StockSummary stock() {
        List<Product> all = productRepo.findAllByOrderByNameAsc();
        BigDecimal qty = BigDecimal.ZERO, value = BigDecimal.ZERO, sale = BigDecimal.ZERO;
        List<Product> low = new ArrayList<>();
        for (Product p : all) {
            if (p.stock().signum() > 0) {
                qty = qty.add(p.stock());
                sale = sale.add(p.stock().multiply(p.getRate() == null ? BigDecimal.ZERO : p.getRate()));
            }
            value = value.add(p.stockValue());
            if (p.isLowStock()) low.add(p);
        }
        return new StockSummary(all.size(), qty, value, sale, low);
    }

    // ---------------- profit & loss ----------------

    public record Summary(
            BigDecimal wholesaleSales, BigDecimal wholesaleGst, BigDecimal localSales, BigDecimal returns,
            BigDecimal netSales, BigDecimal cogs, BigDecimal grossProfit, BigDecimal expenses, BigDecimal netProfit,
            BigDecimal purchases, BigDecimal supplierPaid, BigDecimal customerReceipts,
            Map<String, BigDecimal> moneyIn, Map<String, BigDecimal> moneyOut,
            Map<String, BigDecimal> expenseByCategory, int itemsWithoutCost) {

        public BigDecimal totalIn() { return moneyIn.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
        public BigDecimal totalOut() { return moneyOut.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add); }
    }

    public Summary summary(LocalDate from, LocalDate to) {
        Map<String, Product> products = productsByName();
        List<Bill> bills = billService.forReport(from, to);
        List<LocalBill> local = localBillService.forReport(from, to);

        BigDecimal wholesale = BillService.sum(bills, Bill::getTaxableAmount);
        BigDecimal gst = BillService.sum(bills, Bill::getCgst).add(BillService.sum(bills, Bill::getSgst))
                .add(BillService.sum(bills, Bill::getIgst)).add(BillService.sum(bills, Bill::getRoundOff));
        BigDecimal localSales = LocalBillService.sum(local, LocalBill::getNetPayable);

        // cost of goods sold, from each item's purchase price
        BigDecimal cogs = BigDecimal.ZERO;
        Set<String> noCost = new TreeSet<>();
        for (Bill b : bills)
            for (BillItem i : b.getItems()) cogs = cogs.add(cost(products, i.getParticulars(), i.getQty(), noCost));
        for (LocalBill b : local)
            for (LocalBillItem i : b.getItems()) cogs = cogs.add(cost(products, i.getName(), i.getQty(), noCost));

        List<SalesReturn> returns = returnRepo.findByReturnDateBetweenOrderByReturnDateDescIdDesc(from, to);
        BigDecimal refunds = BigDecimal.ZERO;
        for (SalesReturn r : returns) {
            refunds = refunds.add(nz(r.getRefund()));
            cogs = cogs.subtract(cost(products, r.getProductName(), r.getQty(), new HashSet<>()));
        }

        List<Expense> expenses = expenseRepo.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to);
        BigDecimal expenseTotal = expenses.stream().map(Expense::getAmount).map(InsightService::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> byCat = new TreeMap<>();
        for (Expense e : expenses) byCat.merge(e.getCategory() == null ? "Other" : e.getCategory(), nz(e.getAmount()), BigDecimal::add);

        BigDecimal purchases = purchaseRepo.findByPurchaseDateBetweenOrderByPurchaseDateDescIdDesc(from, to).stream()
                .map(Purchase::getTotal).map(InsightService::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Payment> supplierPay = paymentRepo.findByKindAndPayDateBetweenOrderByPayDateAscIdAsc(Payment.SUPPLIER, from, to);
        List<Payment> receipts = paymentRepo.findByKindAndPayDateBetweenOrderByPayDateAscIdAsc(Payment.CUSTOMER, from, to);

        // money actually received / paid, by mode
        Map<String, BigDecimal> in = modeMap(), out = modeMap();
        for (Bill b : bills) if ("CASH".equals(b.getBillType())) in.merge(BillService.mode(b.getPaymentMode()), nz(b.getGrandTotal()), BigDecimal::add);
        for (LocalBill b : local) in.merge(BillService.mode(b.getPaymentMode()), nz(b.getNetPayable()), BigDecimal::add);
        for (Payment p : receipts) in.merge(BillService.mode(p.getMode()), nz(p.getAmount()), BigDecimal::add);
        for (Expense e : expenses) out.merge(BillService.mode(e.getMode()), nz(e.getAmount()), BigDecimal::add);
        for (Payment p : supplierPay) out.merge(BillService.mode(p.getMode()), nz(p.getAmount()), BigDecimal::add);
        for (SalesReturn r : returns) out.merge(BillService.mode(r.getMode()), nz(r.getRefund()), BigDecimal::add);

        BigDecimal netSales = wholesale.add(localSales).subtract(refunds);
        BigDecimal gross = netSales.subtract(cogs);
        return new Summary(wholesale, gst, localSales, refunds, netSales, cogs, gross, expenseTotal, gross.subtract(expenseTotal),
                purchases, sumPay(supplierPay), sumPay(receipts), in, out, byCat, noCost.size());
    }

    private static BigDecimal cost(Map<String, Product> products, String name, BigDecimal qty, Set<String> noCost) {
        if (name == null || name.isBlank()) return BigDecimal.ZERO;
        Product p = products.get(name.trim().toLowerCase());
        if (p == null || p.getPurchasePrice() == null) {
            noCost.add(name.trim().toLowerCase());
            return BigDecimal.ZERO;
        }
        return nz(qty).multiply(p.getPurchasePrice());
    }

    private static Map<String, BigDecimal> modeMap() {
        Map<String, BigDecimal> m = new LinkedHashMap<>();
        for (String k : MODES) m.put(k, BigDecimal.ZERO);
        return m;
    }

    private static BigDecimal sumPay(List<Payment> list) {
        return list.stream().map(Payment::getAmount).map(InsightService::nz).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------- day / month / year tables ----------------

    public record PeriodRow(String label, LocalDate start, int bills, BigDecimal wholesale, BigDecimal local,
                            BigDecimal received, BigDecimal expenses, BigDecimal paidOut) {
        public BigDecimal total() { return wholesale.add(local); }
        public BigDecimal net() { return received.subtract(expenses).subtract(paidOut); }
    }

    /** One row per day, month or year (group = DAY, MONTH, YEAR). */
    public List<PeriodRow> byPeriod(LocalDate from, LocalDate to, String group) {
        DateTimeFormatter fmt = switch (group) {
            case "YEAR" -> DateTimeFormatter.ofPattern("yyyy");
            case "MONTH" -> DateTimeFormatter.ofPattern("MMM yyyy");
            default -> DateTimeFormatter.ofPattern("dd/MM/yyyy (EEE)");
        };
        Function<LocalDate, LocalDate> key = switch (group) {
            case "YEAR" -> d -> d.withDayOfYear(1);
            case "MONTH" -> d -> d.withDayOfMonth(1);
            default -> d -> d;
        };
        Map<LocalDate, BigDecimal[]> m = new TreeMap<>();   // bills, wholesale, local, received, expenses, paidOut
        Function<LocalDate, BigDecimal[]> row = d -> m.computeIfAbsent(key.apply(d), k -> new BigDecimal[]{
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
        for (Bill b : billService.forReport(from, to)) {
            BigDecimal[] r = row.apply(b.getBillDate());
            r[0] = r[0].add(BigDecimal.ONE);
            r[1] = r[1].add(nz(b.getGrandTotal()));
            if ("CASH".equals(b.getBillType())) r[3] = r[3].add(nz(b.getGrandTotal()));
        }
        for (LocalBill b : localBillService.forReport(from, to)) {
            BigDecimal[] r = row.apply(b.getBillDate());
            r[0] = r[0].add(BigDecimal.ONE);
            r[2] = r[2].add(nz(b.getNetPayable()));
            r[3] = r[3].add(nz(b.getNetPayable()));
        }
        for (Payment p : paymentRepo.findByKindAndPayDateBetweenOrderByPayDateAscIdAsc(Payment.CUSTOMER, from, to))
            row.apply(p.getPayDate())[3] = row.apply(p.getPayDate())[3].add(nz(p.getAmount()));
        for (Expense e : expenseRepo.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to))
            row.apply(e.getExpenseDate())[4] = row.apply(e.getExpenseDate())[4].add(nz(e.getAmount()));
        for (Payment p : paymentRepo.findByKindAndPayDateBetweenOrderByPayDateAscIdAsc(Payment.SUPPLIER, from, to))
            row.apply(p.getPayDate())[5] = row.apply(p.getPayDate())[5].add(nz(p.getAmount()));
        for (SalesReturn r : returnRepo.findByReturnDateBetweenOrderByReturnDateDescIdDesc(from, to))
            row.apply(r.getReturnDate())[5] = row.apply(r.getReturnDate())[5].add(nz(r.getRefund()));

        List<PeriodRow> rows = new ArrayList<>();
        m.forEach((d, r) -> rows.add(new PeriodRow(d.format(fmt), d, r[0].intValue(), r[1], r[2], r[3], r[4], r[5])));
        return rows;
    }

    // ---------------- receivables & payables ----------------

    public BigDecimal receivables() {
        return billService.outstandingByCustomer().stream().map(Map.Entry::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public record SupplierBalance(Supplier supplier, BigDecimal balance) {}

    public List<SupplierBalance> supplierBalances() {
        return supplierRepo.findAllByOrderByNameAsc().stream()
                .map(s -> new SupplierBalance(s, purchaseService.balance(s.getId()))).toList();
    }

    public BigDecimal payables() {
        return supplierBalances().stream().map(SupplierBalance::balance).filter(b -> b.signum() > 0)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
