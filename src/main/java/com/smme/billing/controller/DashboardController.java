package com.smme.billing.controller;

import com.smme.billing.model.Bill;
import com.smme.billing.model.LocalBill;
import com.smme.billing.service.BillService;
import com.smme.billing.service.InsightService;
import com.smme.billing.service.LocalBillService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.smme.billing.model.Product;
import com.smme.billing.service.PieChart;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    private final BillService billService;
    private final LocalBillService localBillService;
    private final InsightService insights;

    public DashboardController(BillService billService, LocalBillService localBillService, InsightService insights) {
        this.billService = billService;
        this.localBillService = localBillService;
        this.insights = insights;
    }

    /** One row of the dashboard's "Recent Bills" list. */
    public record RecentBill(boolean local, Long id, String billNo, LocalDate billDate, String customerName,
                             BigDecimal amount, boolean cancelled, boolean paid, java.time.LocalDateTime createdAt) {}

    @GetMapping("/")
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        List<Bill> todayBills = billService.forReport(today, today);
        List<Bill> monthBills = billService.forReport(today.withDayOfMonth(1), today);
        List<Bill> unpaid = billService.unpaid();

        model.addAttribute("todayTotal", BillService.sum(todayBills, Bill::getGrandTotal));
        model.addAttribute("todayCount", todayBills.size());
        model.addAttribute("monthTotal", BillService.sum(monthBills, Bill::getGrandTotal));
        model.addAttribute("monthCount", monthBills.size());
        model.addAttribute("monthGst", BillService.sum(monthBills, Bill::getCgst)
                .add(BillService.sum(monthBills, Bill::getSgst))
                .add(BillService.sum(monthBills, Bill::getIgst)));
        model.addAttribute("unpaidTotal", BillService.sum(unpaid, Bill::getBalance));
        model.addAttribute("unpaidCount", unpaid.size());

        List<LocalBill> localToday = localBillService.forReport(today, today);
        List<LocalBill> localMonth = localBillService.forReport(today.withDayOfMonth(1), today);
        model.addAttribute("localTodayTotal", LocalBillService.sum(localToday, LocalBill::getNetPayable));
        model.addAttribute("localTodayCount", localToday.size());
        model.addAttribute("localMonthTotal", LocalBillService.sum(localMonth, LocalBill::getNetPayable));
        model.addAttribute("localMonthCount", localMonth.size());

        // the 5 latest bills, local and wholesale together, newest first
        List<RecentBill> recent = new java.util.ArrayList<>();
        for (Bill b : billService.recent())
            recent.add(new RecentBill(false, b.getId(), String.valueOf(b.getBillNo()), b.getBillDate(), b.getCustomerName(),
                    b.getGrandTotal(), b.isCancelled(), b.isPaid(), b.getCreatedAt()));
        for (LocalBill b : localBillService.recent())
            recent.add(new RecentBill(true, b.getId(), b.getBillNo(), b.getBillDate(),
                    b.getCustomerName() == null ? "Cash" : b.getCustomerName(), b.getNetPayable(), b.isCancelled(), true, b.getCreatedAt()));
        recent.sort(java.util.Comparator.comparing(RecentBill::billDate)
                .thenComparing(r -> r.createdAt() == null ? java.time.LocalDateTime.MIN : r.createdAt()).reversed());
        model.addAttribute("recent", recent.stream().filter(r -> !r.cancelled()).limit(5).toList());

        // year, stock, purchases & expenses, outstanding, best sellers
        List<Bill> yearBills = billService.forReport(today.withDayOfYear(1), today);
        List<LocalBill> localYear = localBillService.forReport(today.withDayOfYear(1), today);
        model.addAttribute("yearTotal", BillService.sum(yearBills, Bill::getGrandTotal)
                .add(LocalBillService.sum(localYear, LocalBill::getNetPayable)));
        model.addAttribute("yearCount", yearBills.size() + localYear.size());
        model.addAttribute("stock", insights.stock());
        InsightService.Summary month = insights.summary(today.withDayOfMonth(1), today);
        model.addAttribute("month", month);
        model.addAttribute("receivables", insights.receivables());
        model.addAttribute("payables", insights.payables());
        model.addAttribute("bestSellers", insights.bestSellers(today.withDayOfMonth(1), today, 5));

        // pie charts for this month
        Map<String, BigDecimal> split = new LinkedHashMap<>();
        split.put("Local bills", LocalBillService.sum(localMonth, LocalBill::getNetPayable));
        split.put("Wholesale bills", BillService.sum(monthBills, Bill::getGrandTotal));
        Map<String, BigDecimal> modes = new LinkedHashMap<>();
        month.moneyIn().forEach((k, v) -> modes.put(switch (k) { case "UPI" -> "UPI"; case "BANK" -> "Bank"; default -> "Cash"; }, v));
        Map<String, BigDecimal> byCategory = new LinkedHashMap<>();
        for (InsightService.ItemSale s : insights.salesBy(today.withDayOfMonth(1), today, Product::getCategory))
            byCategory.put(s.name(), s.amount());
        model.addAttribute("pies", List.of(
                PieChart.of("Sales this month", "Local vs wholesale", split),
                PieChart.of("Money received", "By payment mode, this month", modes),
                PieChart.of("Sales by category", "Items sold this month", byCategory),
                PieChart.of("Expenses", "By type, this month", month.expenseByCategory())));
        return "dashboard";
    }
}
