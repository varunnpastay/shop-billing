package com.smme.billing.config;

import com.smme.billing.model.*;
import com.smme.billing.repository.*;
import com.smme.billing.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Fills the shop with example data so every screen has something to show.
 * Runs only when the app is started with  --sample-data=true  and only once (it stops if the sample suppliers exist).
 * Everything goes through the same services the screens use, so stock, balances and the activity log stay consistent.
 */
@Component
@ConditionalOnProperty(name = "sample-data", havingValue = "true")
public class SampleDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDataLoader.class);

    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final ExpenseRepository expenses;
    private final SalesReturnRepository returns;
    private final BillService bills;
    private final LocalBillService localBills;
    private final PurchaseService purchases;
    private final StockService stock;
    private final ActivityService activity;

    public SampleDataLoader(ProductRepository products, CustomerRepository customers, SupplierRepository suppliers,
                            ExpenseRepository expenses, SalesReturnRepository returns, BillService bills,
                            LocalBillService localBills, PurchaseService purchases, StockService stock,
                            ActivityService activity) {
        this.products = products;
        this.customers = customers;
        this.suppliers = suppliers;
        this.expenses = expenses;
        this.returns = returns;
        this.bills = bills;
        this.localBills = localBills;
        this.purchases = purchases;
        this.stock = stock;
        this.activity = activity;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (suppliers.findAllByOrderByNameAsc().stream().anyMatch(s -> s.getName().equals("Surat Silk Mills"))) {
            log.info("Sample data already present - nothing added");
            return;
        }
        LocalDate t = LocalDate.now();

        // ---------------- items (opening stock) ----------------
        item("Mysore Silk Saree", "SVT-101", "Saree", "Silk Saree", "Pure Silk", "Maroon", "Zari border", "5007", "Pcs", 1850, 2600, 2300, 2999, 24, 5, 10);
        item("Kanchipuram Pattu Saree", "SVT-102", "Saree", "Pattu", "Silk", "Gold / Green", "Temple border", "5007", "Pcs", 4200, 5800, 5200, 6500, 12, 3, 6);
        item("Banarasi Saree", "SVT-103", "Saree", "Banarasi", "Silk Blend", "Royal Blue", "Butta", "5007", "Pcs", 2400, 3400, 3000, 3900, 4, 5, 10);
        item("Cotton Saree", "SVT-104", "Saree", "Cotton", "Cotton", "Yellow", "Checks", "5208", "Pcs", 380, 650, 520, 750, 60, 15, 20);
        item("Ilkal Saree", "SVT-105", "Saree", "Ilkal", "Cotton Silk", "Red / Black", "Tope teni pallu", "5208", "Pcs", 900, 1450, 1250, 1650, 18, 6, 10);
        item("Chiffon Printed Saree", "SVT-106", "Saree", "Chiffon", "Georgette", "Pink", "Floral print", "5407", "Pcs", 520, 899, 760, 1099, 3, 8, 20);
        item("Dress Material (3 pc)", "SVT-201", "Dress Material", null, "Cotton", "Assorted", "Printed", "5208", "Set", 450, 799, 650, 950, 40, 10, 10);
        item("Shirting Cloth", "SVT-301", "Shirting", null, "Cotton", "White", "Plain", "5208", "Mtr", 110, 180, 150, 220, 250, 50, null);
        item("Dhoti (Cotton)", "SVT-401", "Dhoti", null, "Cotton", "White", "Gold border", "5208", "Pcs", 220, 380, 320, 450, 35, 10, 10);
        item("Blouse Piece", "SVT-501", "Blouse", null, "Cotton Silk", "Assorted", "Plain", "5208", "Pcs", 60, 120, 95, 150, 2, 20, 50);

        // ---------------- customers ----------------
        customer("Ravi Textiles", "WHOLESALE", "9845012345", "Main Road, Haveri", "29ABCPR1234K1Z5");
        customer("Sri Ganesh Cloth Stores", "WHOLESALE", "9448123456", "Station Road, Davangere", "29AAGFS5678L1Z2");
        customer("Lakshmi Sarees", "WHOLESALE", "9902234567", "MG Road, Hubballi", null);
        customer("Kavya Patil", "RETAIL", "9741345678", "Ranebennur", null);
        customer("Suresh Kulkarni", "RETAIL", "9611456789", "Ranebennur", null);

        // ---------------- suppliers, purchases & payments ----------------
        Supplier surat = supplier("Surat Silk Mills", "9825011122", "Ring Road, Surat, Gujarat", "24AAKCS1234M1Z9", "Bank: HDFC, A/c 501002345678");
        Supplier kanchi = supplier("Kanchi Weavers Co-op", "9443022233", "Gandhi Road, Kanchipuram, Tamil Nadu", "33AABAK4567N1Z3", "Agent: Murugan");
        Supplier ilkal = supplier("Ilkal Handloom Society", "9480033344", "Ilkal, Bagalkot", null, "Cash on delivery");

        purchase(surat, "SSM-2041", t.minusDays(38), 650, line("Mysore Silk Saree", 20, 1850), line("Chiffon Printed Saree", 6, 520));
        purchase(kanchi, "KWC-118", t.minusDays(25), 400, line("Kanchipuram Pattu Saree", 8, 4200));
        purchase(ilkal, "IHS-77", t.minusDays(9), 0, line("Ilkal Saree", 15, 900), line("Cotton Saree", 40, 380));
        purchase(surat, "SSM-2107", t.minusDays(3), 300, line("Banarasi Saree", 10, 2400), line("Blouse Piece", 50, 60));

        purchases.pay(surat.getId(), new BigDecimal("40000"), "BANK", t.minusDays(30), "NEFT");
        purchases.pay(kanchi.getId(), new BigDecimal("34000"), "BANK", t.minusDays(20), "Full payment");
        purchases.pay(ilkal.getId(), new BigDecimal("15000"), "CASH", t.minusDays(8), "Part payment");

        // ---------------- wholesale (GST) bills ----------------
        bill("Ravi Textiles", t.minusDays(34), "CREDIT", "BANK", "CGST_SGST", wl("Mysore Silk Saree", 8, 2300), wl("Cotton Saree", 20, 520));
        bill("Sri Ganesh Cloth Stores", t.minusDays(27), "CASH", "UPI", "CGST_SGST", wl("Dress Material (3 pc)", 15, 650), wl("Dhoti (Cotton)", 10, 320));
        bill("Lakshmi Sarees", t.minusDays(15), "CREDIT", "BANK", "CGST_SGST", wl("Kanchipuram Pattu Saree", 3, 5200), wl("Ilkal Saree", 6, 1250));
        bill("Ravi Textiles", t.minusDays(8), "CASH", "CASH", "CGST_SGST", wl("Shirting Cloth", 60, 150), wl("Cotton Saree", 10, 520));
        bill("Sri Ganesh Cloth Stores", t.minusDays(4), "CREDIT", "UPI", "CGST_SGST", wl("Mysore Silk Saree", 5, 2300), wl("Banarasi Saree", 2, 3000));
        bill("Lakshmi Sarees", t.minusDays(1), "CASH", "UPI", "CGST_SGST", wl("Ilkal Saree", 4, 1250), wl("Blouse Piece", 40, 95));
        bill("Ravi Textiles", t, "CASH", "BANK", "CGST_SGST", wl("Dhoti (Cotton)", 12, 320), wl("Cotton Saree", 8, 520));

        // customer receipts against credit bills (oldest bill first)
        bills.receive("Ravi Textiles", new BigDecimal("10000"), "BANK", t.minusDays(20), "Part payment");
        bills.receive("Lakshmi Sarees", new BigDecimal("8000"), "UPI", t.minusDays(6), "Part payment");

        // ---------------- local (retail) bills ----------------
        local(t.minusDays(31), "Kavya Patil", "9741345678", "UPI", ll("Mysore Silk Saree", 1, 2999, 2600), ll("Blouse Piece", 1, 150, 120));
        local(t.minusDays(26), null, null, "CASH", ll("Cotton Saree", 2, 750, 650));
        local(t.minusDays(19), "Suresh Kulkarni", "9611456789", "CASH", ll("Dhoti (Cotton)", 2, 450, 380), ll("Shirting Cloth", 2.5, 220, 180));
        local(t.minusDays(11), null, null, "UPI", ll("Ilkal Saree", 1, 1650, 1450));
        local(t.minusDays(7), "Kavya Patil", "9741345678", "UPI", ll("Kanchipuram Pattu Saree", 1, 6500, 5800));
        local(t.minusDays(5), null, null, "CASH", ll("Chiffon Printed Saree", 2, 1099, 899), ll("Blouse Piece", 2, 150, 120));
        local(t.minusDays(2), null, null, "BANK", ll("Banarasi Saree", 1, 3900, 3400));
        local(t.minusDays(1), null, null, "CASH", ll("Dress Material (3 pc)", 2, 950, 799));
        local(t, "Suresh Kulkarni", "9611456789", "UPI", ll("Mysore Silk Saree", 1, 2999, 2600), ll("Blouse Piece", 1, 150, 120));
        local(t, null, null, "CASH", ll("Cotton Saree", 3, 750, 650));
        LocalBill wrong = local(t, null, null, "CASH", ll("Ilkal Saree", 1, 1650, 1450));
        localBills.setCancelled(wrong.getId(), true);   // a cancelled bill (stock goes back)

        // ---------------- expenses ----------------
        expense(t.minusDays(40), "Rent", 15000, "BANK", "October shop rent");
        expense(t.minusDays(36), "Electricity", 2350, "UPI", "HESCOM bill");
        expense(t.minusDays(30), "Salary", 12000, "CASH", "Helper salary - October");
        expense(t.minusDays(11), "Rent", 15000, "BANK", "November shop rent");
        expense(t.minusDays(9), "Transport", 1800, "CASH", "Lorry charges - Ilkal goods");
        expense(t.minusDays(6), "Electricity", 2180, "UPI", "HESCOM bill");
        expense(t.minusDays(4), "Packing", 650, "CASH", "Carry bags and boxes");
        expense(t.minusDays(2), "Tea / Snacks", 420, "CASH", null);
        expense(t, "Repairs", 900, "CASH", "Tube lights and fan");

        // ---------------- returns & a stock adjustment ----------------
        returned(t.minusDays(17), "Suresh Kulkarni", "Shirting Cloth", 1, 180, "CASH", "Wrong length");
        returned(t.minusDays(3), "Kavya Patil", "Kanchipuram Pattu Saree", 1, 5800, "UPI", "Colour not liked");
        stock.change("Chiffon Printed Saree", new BigDecimal("-1"));
        activity.log("Stock adjusted", "Chiffon Printed Saree -1 (damaged in display)");

        activity.log("Sample data added", "Example items, customers, suppliers, bills, purchases, payments, expenses and returns");
        log.info("Sample data added");
    }

    // ---------------- helpers ----------------

    private void item(String name, String code, String category, String sareeType, String fabric, String color, String design,
                      String hsn, String unit, double cost, double rate, double wholesale, double mrp,
                      double openingStock, double minStock, Integer perBundle) {
        if (products.findFirstByNameIgnoreCase(name).isPresent()) return;
        Product p = new Product();
        p.setName(name);
        p.setCode(code);
        p.setBarcode("890" + code.replaceAll("\\D", "") + "0001");
        p.setCategory(category);
        p.setSareeType(sareeType);
        p.setFabric(fabric);
        p.setColor(color);
        p.setDesign(design);
        p.setHsn(hsn);
        p.setUnit(unit);
        p.setPurchasePrice(bd(cost));
        p.setRate(bd(rate));
        p.setWholesalePrice(bd(wholesale));
        p.setMrp(bd(mrp));
        p.setStockQty(bd(openingStock));
        p.setMinStock(bd(minStock));
        p.setPiecesPerBundle(perBundle);
        p.setHasImage(false);
        products.save(p);
    }

    private void customer(String name, String type, String phone, String address, String gstin) {
        if (customers.findFirstByNameIgnoreCase(name).isPresent()) return;
        Customer c = new Customer();
        c.setName(name);
        c.setCustomerType(type);
        c.setPhone(phone);
        c.setAddress(address);
        c.setGstin(gstin);
        c.setStateName("Karnataka");
        customers.save(c);
    }

    private Supplier supplier(String name, String phone, String address, String gstin, String notes) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setPhone(phone);
        s.setAddress(address);
        s.setGstin(gstin);
        s.setNotes(notes);
        return suppliers.save(s);
    }

    private static PurchaseItem line(String name, double qty, double rate) {
        PurchaseItem i = new PurchaseItem();
        i.setProductName(name);
        i.setQty(bd(qty));
        i.setRate(bd(rate));
        return i;
    }

    private void purchase(Supplier s, String invoice, LocalDate date, double other, PurchaseItem... lines) {
        Purchase p = new Purchase();
        p.setSupplierId(s.getId());
        p.setInvoiceNo(invoice);
        p.setPurchaseDate(date);
        p.setOtherCharges(bd(other));
        p.getItems().addAll(List.of(lines));
        purchases.save(p);
    }

    private BillItem wl(String name, double qty, double rate) {
        Product p = products.findFirstByNameIgnoreCase(name).orElseThrow();
        BillItem i = new BillItem();
        i.setParticulars(name);
        i.setHsn(p.getHsn());
        i.setUnit(p.getUnit());
        i.setQty(bd(qty));
        i.setRate(bd(rate));
        return i;
    }

    private void bill(String customerName, LocalDate date, String type, String mode, String tax, BillItem... items) {
        Customer c = customers.findFirstByNameIgnoreCase(customerName).orElseThrow();
        Bill b = new Bill();
        b.setCustomerName(c.getName());
        b.setCustomerAddress(c.getAddress());
        b.setCustomerGstin(c.getGstin());
        b.setCustomerPhone(c.getPhone());
        b.setBillDate(date);
        b.setBillType(type);
        b.setPaymentMode(mode);
        b.setTaxType(tax);
        b.setGstPercent(new BigDecimal("5"));
        b.getItems().addAll(List.of(items));
        bills.save(b);
    }

    private static LocalBillService.ItemForm ll(String name, double qty, double mrp, double rate) {
        return new LocalBillService.ItemForm(name, bd(qty), bd(mrp), bd(rate));
    }

    private LocalBill local(LocalDate date, String name, String mobile, String mode, LocalBillService.ItemForm... items) {
        return localBills.save(new LocalBillService.BillForm(null, date, name, mobile, mode, List.of(items)));
    }

    private void expense(LocalDate date, String category, double amount, String mode, String note) {
        Expense e = new Expense();
        e.setExpenseDate(date);
        e.setCategory(category);
        e.setAmount(bd(amount));
        e.setMode(mode);
        e.setNote(note);
        expenses.save(e);
    }

    private void returned(LocalDate date, String customer, String product, double qty, double refund, String mode, String reason) {
        SalesReturn r = new SalesReturn();
        r.setReturnDate(date);
        r.setCustomerName(customer);
        r.setProductName(product);
        r.setQty(bd(qty));
        r.setRefund(bd(refund));
        r.setMode(mode);
        r.setReason(reason);
        r.setCreatedAt(LocalDateTime.now());
        returns.save(r);
        stock.change(product, bd(qty));
    }

    private static BigDecimal bd(double v) {
        return BigDecimal.valueOf(v);
    }
}
