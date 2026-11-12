package com.smme.billing.controller;

import com.smme.billing.model.Bill;
import com.smme.billing.model.LocalBill;
import com.smme.billing.service.BillService;
import com.smme.billing.service.LocalBillService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/reports")
public class ReportController {

    private final BillService billService;
    private final LocalBillService localBillService;
    private final com.smme.billing.service.InsightService insights;
    private final com.smme.billing.repository.PurchaseRepository purchaseRepo;
    private final com.smme.billing.repository.ProductRepository productRepo;

    public ReportController(BillService billService, LocalBillService localBillService,
                            com.smme.billing.service.InsightService insights,
                            com.smme.billing.repository.PurchaseRepository purchaseRepo,
                            com.smme.billing.repository.ProductRepository productRepo) {
        this.billService = billService;
        this.localBillService = localBillService;
        this.insights = insights;
        this.purchaseRepo = purchaseRepo;
        this.productRepo = productRepo;
    }

    /** Excel (CSV) download of the other report tabs. */
    @GetMapping("/export")
    public void export(@RequestParam String type,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "DAY") String group,
                       HttpServletResponse res) throws IOException {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        if (!java.util.Set.of("DAY", "MONTH", "YEAR").contains(group)) group = "DAY";
        String name = switch (type) {
            case "summary", "items", "purchases", "stock", "profit", "outstanding" -> type;
            default -> null;
        };
        if (name == null) { res.sendError(404); return; }
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType("text/csv; charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=" + name + "_" + from + "_to_" + to + ".csv");
        PrintWriter w = res.getWriter();
        w.print('﻿');
        switch (name) {
            case "summary" -> {
                w.print("Period,Bills,Wholesale,Local,Total Sales,Money Received,Expenses,Paid Out,Net Cash\r\n");
                for (var r : insights.byPeriod(from, to, group))
                    w.print(String.join(",", csv(r.label()), String.valueOf(r.bills()), num(r.wholesale()), num(r.local()),
                            num(r.total()), num(r.received()), num(r.expenses()), num(r.paidOut()), num(r.net())) + "\r\n");
            }
            case "items" -> {
                w.print("Item,Qty Sold,Amount\r\n");
                for (var r : insights.bestSellers(from, to, 1000))
                    w.print(String.join(",", csv(r.name()), num(r.qty()), num(r.amount())) + "\r\n");
            }
            case "purchases" -> {
                w.print("Date,Supplier,Invoice No,Items,Other Charges,Total\r\n");
                for (var p : purchaseRepo.findByPurchaseDateBetweenOrderByPurchaseDateDescIdDesc(from, to))
                    w.print(String.join(",", String.valueOf(p.getPurchaseDate()), csv(p.getSupplierName()), csv(p.getInvoiceNo()),
                            String.valueOf(p.getItems().size()), num(p.getOtherCharges()), num(p.getTotal())) + "\r\n");
            }
            case "stock" -> {
                w.print("Item,Code,Category,Fabric,Color,Stock,Min Stock,Purchase Price,Selling Price,Wholesale Price,Stock Value\r\n");
                for (var p : productRepo.findAllByOrderByNameAsc())
                    w.print(String.join(",", csv(p.getName()), csv(p.getCode()), csv(p.getCategory()), csv(p.getFabric()),
                            csv(p.getColor()), num(p.getStockQty()), num(p.getMinStock()), num(p.getPurchasePrice()),
                            num(p.getRate()), num(p.getWholesalePrice()), num(p.stockValue())) + "\r\n");
            }
            case "profit" -> {
                var s = insights.summary(from, to);
                w.print("Line,Amount\r\n");
                for (Object[] l : new Object[][]{{"Wholesale sales (without GST)", s.wholesaleSales()}, {"Local sales", s.localSales()},
                        {"Less returns", s.returns()}, {"Net sales", s.netSales()}, {"Cost of goods sold", s.cogs()},
                        {"Gross profit", s.grossProfit()}, {"Expenses", s.expenses()}, {"Net profit (estimated)", s.netProfit()},
                        {"GST collected", s.wholesaleGst()}, {"Purchases", s.purchases()}})
                    w.print(csv((String) l[0]) + "," + num((BigDecimal) l[1]) + "\r\n");
            }
            case "outstanding" -> {
                w.print("Type,Name,Amount\r\n");
                for (var e : billService.outstandingByCustomer())
                    w.print("Customer owes us," + csv(e.getKey()) + "," + num(e.getValue()) + "\r\n");
                for (var r : insights.supplierBalances())
                    if (r.balance().signum() != 0)
                        w.print("We owe supplier," + csv(r.supplier().getName()) + "," + num(r.balance()) + "\r\n");
            }
            default -> { }
        }
        w.flush();
    }

    private static final java.util.Set<String> TABS =
            java.util.Set.of("sales", "summary", "items", "purchases", "stock", "profit", "outstanding");

    @GetMapping
    public String report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         @RequestParam(defaultValue = "sales") String tab,
                         @RequestParam(defaultValue = "DAY") String group,
                         Model model) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        if (!TABS.contains(tab)) tab = "sales";
        if (!java.util.Set.of("DAY", "MONTH", "YEAR").contains(group)) group = "DAY";
        model.addAttribute("tab", tab);
        model.addAttribute("group", group);
        switch (tab) {
            case "summary" -> model.addAttribute("rows", insights.byPeriod(from, to, group));
            case "items" -> {
                model.addAttribute("items", insights.bestSellers(from, to, 200));
                model.addAttribute("byCategory", insights.salesBy(from, to, com.smme.billing.model.Product::getCategory));
                model.addAttribute("byFabric", insights.salesBy(from, to, com.smme.billing.model.Product::getFabric));
            }
            case "purchases" -> {
                var list = purchaseRepo.findByPurchaseDateBetweenOrderByPurchaseDateDescIdDesc(from, to);
                model.addAttribute("purchases", list);
                model.addAttribute("purchaseTotal", list.stream().map(com.smme.billing.model.Purchase::getTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
            }
            case "stock" -> {
                model.addAttribute("stock", insights.stock());
                model.addAttribute("products", productRepo.findAllByOrderByNameAsc());
            }
            case "profit" -> model.addAttribute("s", insights.summary(from, to));
            case "outstanding" -> {
                model.addAttribute("customers", billService.outstandingByCustomer());
                model.addAttribute("receivables", insights.receivables());
                model.addAttribute("suppliers", insights.supplierBalances());
                model.addAttribute("payables", insights.payables());
            }
            default -> { }
        }
        List<Bill> bills = billService.forReport(from, to);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("bills", bills);
        model.addAttribute("taxable", BillService.sum(bills, Bill::getTaxableAmount));
        model.addAttribute("cgst", BillService.sum(bills, Bill::getCgst));
        model.addAttribute("sgst", BillService.sum(bills, Bill::getSgst));
        model.addAttribute("igst", BillService.sum(bills, Bill::getIgst));
        model.addAttribute("grand", BillService.sum(bills, Bill::getGrandTotal));
        model.addAttribute("pending", BillService.sum(bills, Bill::getBalance));

        List<LocalBill> localBills = localBillService.forReport(from, to);
        model.addAttribute("localBills", localBills);
        model.addAttribute("localSale", LocalBillService.sum(localBills, LocalBill::getTotalSale));
        model.addAttribute("localSavings", LocalBillService.sum(localBills, LocalBill::getTotalSavings));
        model.addAttribute("localNet", LocalBillService.sum(localBills, LocalBill::getNetPayable));
        return "reports";
    }

    @GetMapping("/csv")
    public void csv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                    HttpServletResponse res) throws IOException {
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType("text/csv; charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=sales_" + from + "_to_" + to + ".csv");
        PrintWriter w = res.getWriter();
        w.print('﻿'); // BOM so Excel opens the file as UTF-8
        w.print("Bill No,Date,Customer,Customer GSTIN,Type,Taxable,CGST,SGST,IGST,Round Off,Grand Total,Status\r\n");
        for (Bill b : billService.forReport(from, to)) {
            w.print(String.join(",",
                    String.valueOf(b.getBillNo()), String.valueOf(b.getBillDate()),
                    csv(b.getCustomerName()), csv(b.getCustomerGstin()), csv(b.getBillType()),
                    num(b.getTaxableAmount()), num(b.getCgst()), num(b.getSgst()),
                    num(b.getIgst()), num(b.getRoundOff()), num(b.getGrandTotal()),
                    b.isPaid() ? "Paid" : "Unpaid"));
            w.print("\r\n");
        }
        w.flush();
    }

    @GetMapping("/local-csv")
    public void localCsv(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         HttpServletResponse res) throws IOException {
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType("text/csv; charset=UTF-8");
        res.setHeader("Content-Disposition", "attachment; filename=local_sales_" + from + "_to_" + to + ".csv");
        PrintWriter w = res.getWriter();
        w.print('\uFEFF'); // BOM so Excel opens the file as UTF-8
        w.print("Bill No,Date,Customer,Mobile,Total Qty,Total Sale,Total Savings,Net Payable\r\n");
        for (LocalBill b : localBillService.forReport(from, to)) {
            w.print(String.join(",",
                    csv(b.getBillNo()), String.valueOf(b.getBillDate()),
                    csv(b.getCustomerName()), csv(b.getCustMobile()),
                    num(b.getTotalQty()), num(b.getTotalSale()), num(b.getTotalSavings()), num(b.getNetPayable())));
            w.print("\r\n");
        }
        w.flush();
    }

    private static String num(BigDecimal v) {
        return v == null ? "0" : v.toPlainString();
    }

    /** Quotes a text cell and neutralises values Excel would run as a formula. */
    private static String csv(String s) {
        if (s == null) return "";
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
