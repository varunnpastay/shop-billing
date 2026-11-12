package com.smme.billing.controller;

import com.smme.billing.service.ActivityService;
import com.smme.billing.service.InsightService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

/** Accounts (money in / out, profit) and the activity history. */
@Controller
public class AccountsController {

    private final InsightService insights;
    private final ActivityService activity;

    public AccountsController(InsightService insights, ActivityService activity) {
        this.insights = insights;
        this.activity = activity;
    }

    @GetMapping("/accounts")
    public String accounts(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           Model model) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("s", insights.summary(from, to));
        model.addAttribute("days", insights.byPeriod(from, to, "DAY"));
        model.addAttribute("receivables", insights.receivables());
        model.addAttribute("payables", insights.payables());
        return "accounts";
    }

    @GetMapping("/activity")
    public String activity(Model model) {
        model.addAttribute("logs", activity.recent());
        return "activity";
    }
}
