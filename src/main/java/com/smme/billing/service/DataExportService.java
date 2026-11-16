package com.smme.billing.service;

import com.smme.billing.model.*;
import com.smme.billing.repository.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Daily Excel export of all shop data, pushed to a PRIVATE GitHub repository.
 *
 * Every day (at {@code export.cron}, and once shortly after the app starts if today's file is missing) the app writes
 * one workbook with a sheet per kind of record, saves it in a local copy of the data repository
 * ({@code export.dir}, by default ./data-export) as YYYY/MM/shop-data-YYYY-MM-DD.xlsx plus latest.xlsx, then runs
 * git add / commit / push. The repository link is set on the Settings page. git must be installed and signed in to
 * GitHub on this computer (it already is if `git push` works here). Without a link the file is still written locally.
 */
@Service
public class DataExportService {

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);
    /** Only https GitHub links are accepted (they are passed to git). */
    public static final Pattern GITHUB_URL = Pattern.compile("https://github\\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+?(\\.git)?/?");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy hh:mm a");

    private final SettingService settings;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final SupplierRepository suppliers;
    private final BillRepository bills;
    private final LocalBillRepository localBills;
    private final PurchaseRepository purchases;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final SalesReturnRepository returns;
    private final ActivityService activity;
    private final TransactionTemplate readTx;
    private final Path dir;

    /** What happened the last time (shown on the Settings page). */
    public record Status(LocalDateTime at, boolean ok, String message, String file) {
        public String when() { return at == null ? "" : at.format(WHEN); }
    }
    private volatile Status last = null;

    public DataExportService(SettingService settings, ProductRepository products, CustomerRepository customers,
                             SupplierRepository suppliers, BillRepository bills, LocalBillRepository localBills,
                             PurchaseRepository purchases, PaymentRepository payments, ExpenseRepository expenses,
                             SalesReturnRepository returns, ActivityService activity, PlatformTransactionManager txManager,
                             @Value("${export.dir:./data-export}") String dir) {
        this.settings = settings;
        this.products = products;
        this.customers = customers;
        this.suppliers = suppliers;
        this.bills = bills;
        this.localBills = localBills;
        this.purchases = purchases;
        this.payments = payments;
        this.expenses = expenses;
        this.returns = returns;
        this.activity = activity;
        this.readTx = new TransactionTemplate(txManager);
        this.readTx.setReadOnly(true);
        this.dir = Paths.get(dir).toAbsolutePath().normalize();
    }

    public Status getLast() { return last; }
    public Path getDir() { return dir; }
    public Path latestFile() { return dir.resolve("latest.xlsx"); }

    // ------------------------------------------------------------------ schedule --

    /** Every evening (default 8 PM). */
    @Scheduled(cron = "${export.cron:0 0 20 * * *}")
    public void daily() {
        run("daily");
    }

    /** Shortly after start-up, if today's export has not been made yet (the shop may close before the evening run). */
    @Scheduled(initialDelayString = "${export.initial-delay-ms:300000}", fixedDelay = Long.MAX_VALUE)
    public void onStartup() {
        if (!Files.exists(todayFile())) run("start-up");
    }

    // ---------------------------------------------------------------------- run --

    /** Writes today's workbook and pushes it (if a repository link is set). Never throws. */
    public synchronized Status run(String reason) {
        Status s;
        try {
            String url = repoUrl();
            if (url != null) prepareRepo(url);
            Files.createDirectories(dir);
            Path file = todayFile();
            Files.createDirectories(file.getParent());
            writeWorkbook(file);
            Files.copy(file, latestFile(), StandardCopyOption.REPLACE_EXISTING);
            String rel = dir.relativize(file).toString().replace('\\', '/');
            if (url == null) {
                s = new Status(LocalDateTime.now(), true, "Excel file saved on this computer. Add the GitHub link to also push it.", rel);
            } else {
                s = new Status(LocalDateTime.now(), true, push(rel), rel);
            }
        } catch (Exception e) {
            log.warn("Data export ({}) failed: {}", reason, e.getMessage());
            s = new Status(LocalDateTime.now(), false, e.getMessage() == null ? e.toString() : e.getMessage(), null);
        }
        last = s;
        log.info("Data export ({}): {} – {}", reason, s.ok() ? "ok" : "FAILED", s.message());
        if (!"start-up".equals(reason) || !s.ok()) activity.log(s.ok() ? "Data exported" : "Data export failed", s.message());
        return s;
    }

    private Path todayFile() {
        LocalDate d = LocalDate.now();
        return dir.resolve(String.format("%d/%02d/shop-data-%s.xlsx", d.getYear(), d.getMonthValue(), d));
    }

    private String repoUrl() {
        String u = settings.get().getExportGitUrl();
        return u == null || u.isBlank() ? null : u.trim();
    }

    // ---------------------------------------------------------------------- git --

    /** Makes sure the export folder is a copy of the data repository (clones it the first time). */
    private void prepareRepo(String url) throws IOException, InterruptedException {
        if (!GITHUB_URL.matcher(url).matches()) throw new IOException("The GitHub link must look like https://github.com/name/repository");
        if (Files.isDirectory(dir.resolve(".git"))) {
            git(dir, "remote", "set-url", "origin", url);
            git(dir, "pull", "--quiet", "--rebase", "origin", "main");   // fine to fail on a brand-new empty repository
            return;
        }
        if (Files.exists(dir) && Files.list(dir).findAny().isPresent()) {
            // a folder with earlier local-only exports: turn it into a copy of the repository
            check(git(dir, "init", "--quiet"), "git init");
            git(dir, "remote", "add", "origin", url);
            git(dir, "fetch", "--quiet", "origin");
            git(dir, "checkout", "-B", "main");
            git(dir, "reset", "--soft", "origin/main");
        } else {
            Files.createDirectories(dir.getParent());
            check(git(dir.getParent(), "clone", "--quiet", url, dir.getFileName().toString()), "git clone (check the link and that this computer is signed in to GitHub)");
            git(dir, "checkout", "-B", "main");
        }
        // commits need a name and email: use the ones this computer already uses for git
        if (git(dir, "config", "user.name").out.isBlank()) {
            String name = git(Paths.get("."), "config", "user.name").out;
            String email = git(Paths.get("."), "config", "user.email").out;
            git(dir, "config", "user.name", name.isBlank() ? "Shop Billing" : name);
            git(dir, "config", "user.email", email.isBlank() ? "shop-billing@users.noreply.github.com" : email);
        }
        Path readme = dir.resolve("README.md");
        if (!Files.exists(readme)) {
            Files.writeString(readme, "# Shop data\n\nDaily Excel export of the billing app (one workbook per day under YYYY/MM/, "
                    + "and latest.xlsx). Keep this repository PRIVATE: it contains customer details.\n", StandardCharsets.UTF_8);
        }
    }

    private String push(String rel) throws IOException, InterruptedException {
        check(git(dir, "add", "-A"), "git add");
        GitResult staged = git(dir, "diff", "--cached", "--quiet");
        if (staged.code == 0) return "Nothing changed since the last push.";
        check(git(dir, "commit", "--quiet", "-m", "Shop data " + LocalDate.now()), "git commit (set your name with: git config --global user.name / user.email)");
        check(git(dir, "push", "--quiet", "-u", "origin", "HEAD:main"), "git push (check that this computer is signed in to GitHub)");
        return "Pushed " + rel + " to GitHub.";
    }

    private record GitResult(int code, String out) {}

    private static GitResult git(Path where, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(where.toFile()).redirectErrorStream(true);
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");   // never hang waiting for a password
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        if (!p.waitFor(3, TimeUnit.MINUTES)) {
            p.destroyForcibly();
            return new GitResult(-1, "timed out");
        }
        return new GitResult(p.exitValue(), new String(out, StandardCharsets.UTF_8).trim());
    }

    private static void check(GitResult r, String what) throws IOException {
        if (r.code != 0) throw new IOException(what + " failed" + (r.out.isBlank() ? "" : ": " + r.out));
    }

    // ------------------------------------------------------------------- excel --

    /** One workbook, one sheet per kind of record. */
    public void writeWorkbook(Path file) throws IOException {
        byte[] bytes;
        try {
            bytes = readTx.execute(status -> {
                try { return buildWorkbook(); } catch (IOException e) { throw new UncheckedIOException(e); }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }

    private byte[] buildWorkbook() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            Sheets x = new Sheets(wb);

            List<Bill> allBills = bills.findAll(Sort.by("billDate", "billNo"));
            List<LocalBill> allLocal = localBills.findAll(Sort.by("billDate", "id"));
            List<Purchase> allPurchases = purchases.findAll(Sort.by("purchaseDate", "id"));

            Sheet s = x.sheet("Summary", "What", "Value");
            x.row(s, "Shop", settings.get().getShopName());
            x.row(s, "Exported on", LocalDateTime.now().format(WHEN));
            x.row(s, "Items", products.count());
            x.row(s, "Customers", customers.count());
            x.row(s, "Suppliers", suppliers.count());
            x.row(s, "Wholesale bills", allBills.size());
            x.row(s, "Local bills", allLocal.size());
            x.row(s, "Purchases", allPurchases.size());

            s = x.sheet("Items", "Name", "Code", "Category", "Saree type", "Fabric", "Colour", "Design", "Barcode", "HSN", "Unit",
                    "Purchase price", "Selling price", "Wholesale price", "MRP", "Stock", "Min stock", "Pieces per bundle", "Stock value");
            for (Product p : products.findAll(Sort.by("name")))
                x.row(s, p.getName(), p.getCode(), p.getCategory(), p.getSareeType(), p.getFabric(), p.getColor(), p.getDesign(),
                        p.getBarcode(), p.getHsn(), p.getUnit(), p.getPurchasePrice(), p.getRate(), p.getWholesalePrice(), p.getMrp(),
                        p.stock(), p.getMinStock(), p.getPiecesPerBundle(), p.stockValue());

            s = x.sheet("Customers", "Name", "Type", "Phone", "Address", "GSTIN", "State");
            for (Customer c : customers.findAllByOrderByNameAsc())
                x.row(s, c.getName(), c.getCustomerType(), c.getPhone(), c.getAddress(), c.getGstin(), c.getStateName());

            s = x.sheet("Suppliers", "Name", "Phone", "Address", "GSTIN", "Notes");
            for (Supplier p : suppliers.findAllByOrderByNameAsc())
                x.row(s, p.getName(), p.getPhone(), p.getAddress(), p.getGstin(), p.getNotes());

            s = x.sheet("Wholesale Bills", "Bill No", "Date", "Customer", "Address", "GSTIN", "Phone", "Type", "Payment", "Tax",
                    "GST %", "Sub total", "Discount", "Taxable", "CGST", "SGST", "IGST", "Round off", "Grand total",
                    "Received", "Balance", "Paid", "Cancelled", "By", "Notes");
            Sheet lines = x.sheet("Wholesale Bill Lines", "Bill No", "Date", "Customer", "Particulars", "Details", "HSN", "Qty", "Unit", "Rate", "Amount");
            for (Bill b : allBills) {
                x.row(s, b.getBillNo(), b.getBillDate(), b.getCustomerName(), b.getCustomerAddress(), b.getCustomerGstin(),
                        b.getCustomerPhone(), b.getBillType(), b.getPaymentMode(), b.getTaxType(), b.getGstPercent(), b.getSubTotal(),
                        b.getDiscount(), b.getTaxableAmount(), b.getCgst(), b.getSgst(), b.getIgst(), b.getRoundOff(), b.getGrandTotal(),
                        b.getAmountReceived(), b.getBalance(), yes(b.isPaid()), yes(b.isCancelled()), b.getByName(), b.getNotes());
                for (BillItem i : b.getItems())
                    x.row(lines, b.getBillNo(), b.getBillDate(), b.getCustomerName(), i.getParticulars(), i.getDetails(), i.getHsn(),
                            i.getQty(), i.getUnit(), i.getRate(), i.getAmount());
            }

            s = x.sheet("Local Bills", "Bill No", "Date", "Customer", "Mobile", "Payment", "Total qty", "Total sale", "Savings", "Net payable", "Cancelled");
            lines = x.sheet("Local Bill Lines", "Bill No", "Date", "Item", "Qty", "MRP", "Rate", "Amount");
            for (LocalBill b : allLocal) {
                x.row(s, b.getBillNo(), b.getBillDate(), b.getCustomerName(), b.getCustMobile(), b.getPaymentMode(), b.getTotalQty(),
                        b.getTotalSale(), b.getTotalSavings(), b.getNetPayable(), yes(b.isCancelled()));
                for (LocalBillItem i : b.getItems())
                    x.row(lines, b.getBillNo(), b.getBillDate(), i.getName(), i.getQty(), i.getMrp(), i.getRate(), i.getAmount());
            }

            s = x.sheet("Purchases", "Date", "Supplier", "Invoice No", "Other charges", "Total", "Notes");
            lines = x.sheet("Purchase Lines", "Date", "Supplier", "Invoice No", "Item", "Qty", "Rate", "Amount");
            for (Purchase p : allPurchases) {
                x.row(s, p.getPurchaseDate(), p.getSupplierName(), p.getInvoiceNo(), p.getOtherCharges(), p.getTotal(), p.getNotes());
                for (PurchaseItem i : p.getItems())
                    x.row(lines, p.getPurchaseDate(), p.getSupplierName(), p.getInvoiceNo(), i.getProductName(), i.getQty(), i.getRate(), i.getAmount());
            }

            s = x.sheet("Payments", "Date", "From / To", "Customer or supplier", "Amount", "Mode", "Note");
            for (Payment p : payments.findAll(Sort.by("payDate", "id")))
                x.row(s, p.getPayDate(), Payment.CUSTOMER.equals(p.getKind()) ? "Received from customer" : "Paid to supplier",
                        p.getPartyName(), p.getAmount(), p.getMode(), p.getNote());

            s = x.sheet("Expenses", "Date", "Type", "Amount", "Mode", "Note");
            for (Expense e : expenses.findAll(Sort.by("expenseDate", "id")))
                x.row(s, e.getExpenseDate(), e.getCategory(), e.getAmount(), e.getMode(), e.getNote());

            s = x.sheet("Returns", "Date", "Bill ref", "Customer", "Item", "Qty", "Refund", "Mode", "Reason");
            for (SalesReturn r : returns.findAll(Sort.by("returnDate", "id")))
                x.row(s, r.getReturnDate(), r.getBillRef(), r.getCustomerName(), r.getProductName(), r.getQty(), r.getRefund(),
                        r.getMode(), r.getReason());

            x.finish();
            wb.write(out);
        }
        return out.toByteArray();
    }

    private static String yes(boolean b) { return b ? "Yes" : "No"; }

    /** Small helper: bold frozen header row, numbers as numbers, dates as dates, sensible column widths. */
    private static final class Sheets {
        private final Workbook wb;
        private final CellStyle head, date, money;
        private final List<Sheet> all = new ArrayList<>();

        Sheets(Workbook wb) {
            this.wb = wb;
            Font bold = wb.createFont();
            bold.setBold(true);
            head = wb.createCellStyle();
            head.setFont(bold);
            head.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            head.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CreationHelper h = wb.getCreationHelper();
            date = wb.createCellStyle();
            date.setDataFormat(h.createDataFormat().getFormat("dd/mm/yyyy"));
            money = wb.createCellStyle();
            money.setDataFormat(h.createDataFormat().getFormat("#,##0.00"));
        }

        Sheet sheet(String name, String... headers) {
            Sheet s = wb.createSheet(name);
            Row r = s.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell c = r.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(head);
                s.setColumnWidth(i, Math.max(12, Math.min(40, headers[i].length() + 4)) * 256);
            }
            s.createFreezePane(0, 1);
            all.add(s);
            return s;
        }

        void row(Sheet s, Object... values) {
            Row r = s.createRow(s.getLastRowNum() + 1);
            for (int i = 0; i < values.length; i++) {
                Object v = values[i];
                if (v == null) continue;
                Cell c = r.createCell(i);
                if (v instanceof BigDecimal n) { c.setCellValue(n.doubleValue()); c.setCellStyle(money); }
                else if (v instanceof Number n) c.setCellValue(n.doubleValue());
                else if (v instanceof LocalDate d) { c.setCellValue(d); c.setCellStyle(date); }
                else {
                    String t = v.toString();
                    c.setCellValue(t);
                    int w = Math.min(50, t.length() + 2) * 256;
                    if (w > s.getColumnWidth(i)) s.setColumnWidth(i, w);
                }
            }
        }

        void finish() {
            for (Sheet s : all) if (s.getLastRowNum() > 0) s.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, s.getLastRowNum(), 0, s.getRow(0).getLastCellNum() - 1));
        }
    }
}
