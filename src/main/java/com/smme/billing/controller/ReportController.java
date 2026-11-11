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

    public ReportController(BillService billService, LocalBillService localBillService) {
        this.billService = billService;
        this.localBillService = localBillService;
    }

    @GetMapping
    public String report(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                         Model model) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        List<Bill> bills = billService.forReport(from, to);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("bills", bills);
        model.addAttribute("taxable", BillService.sum(bills, Bill::getTaxableAmount));
        model.addAttribute("cgst", BillService.sum(bills, Bill::getCgst));
        model.addAttribute("sgst", BillService.sum(bills, Bill::getSgst));
        model.addAttribute("igst", BillService.sum(bills, Bill::getIgst));
        model.addAttribute("grand", BillService.sum(bills, Bill::getGrandTotal));
        model.addAttribute("pending", BillService.sum(bills.stream().filter(b -> !b.isPaid()).toList(), Bill::getGrandTotal));

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
