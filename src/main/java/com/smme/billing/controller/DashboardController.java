package com.smme.billing.controller;

import com.smme.billing.model.Bill;
import com.smme.billing.model.LocalBill;
import com.smme.billing.service.BillService;
import com.smme.billing.service.LocalBillService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

@Controller
public class DashboardController {

    private final BillService billService;
    private final LocalBillService localBillService;

    public DashboardController(BillService billService, LocalBillService localBillService) {
        this.billService = billService;
        this.localBillService = localBillService;
    }

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
        model.addAttribute("unpaidTotal", BillService.sum(unpaid, Bill::getGrandTotal));
        model.addAttribute("unpaidCount", unpaid.size());
        model.addAttribute("recent", billService.recent());

        List<LocalBill> localToday = localBillService.forReport(today, today);
        List<LocalBill> localMonth = localBillService.forReport(today.withDayOfMonth(1), today);
        model.addAttribute("localTodayTotal", LocalBillService.sum(localToday, LocalBill::getNetPayable));
        model.addAttribute("localTodayCount", localToday.size());
        model.addAttribute("localMonthTotal", LocalBillService.sum(localMonth, LocalBill::getNetPayable));
        model.addAttribute("localMonthCount", localMonth.size());
        model.addAttribute("localRecent", localBillService.recent());
        return "dashboard";
    }
}
