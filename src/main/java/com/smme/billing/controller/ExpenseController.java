package com.smme.billing.controller;

import com.smme.billing.model.Expense;
import com.smme.billing.repository.ExpenseRepository;
import com.smme.billing.service.ActivityService;
import com.smme.billing.service.BillService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Controller
@RequestMapping("/expenses")
public class ExpenseController {

    public static final List<String> CATEGORIES =
            List.of("Rent", "Electricity", "Transport", "Salary", "Tea / Snacks", "Packing", "Repairs", "Other");

    private final ExpenseRepository repo;
    private final ActivityService activity;

    public ExpenseController(ExpenseRepository repo, ActivityService activity) {
        this.repo = repo;
        this.activity = activity;
    }

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        List<Expense> list = repo.findByExpenseDateBetweenOrderByExpenseDateDescIdDesc(from, to);
        Map<String, BigDecimal> byCat = new TreeMap<>();
        for (Expense e : list) byCat.merge(e.getCategory(), e.getAmount(), BigDecimal::add);
        model.addAttribute("expenses", list);
        model.addAttribute("byCategory", byCat);
        model.addAttribute("total", list.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("categories", CATEGORIES);
        return "expenses/list";
    }

    @PostMapping
    public String add(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam(defaultValue = "Other") String category,
                      @RequestParam(required = false) BigDecimal amount,
                      @RequestParam(defaultValue = "CASH") String mode,
                      @RequestParam(defaultValue = "") String note, RedirectAttributes ra) {
        if (amount == null || amount.signum() <= 0) {
            ra.addFlashAttribute("err", "Amount must be more than 0");
            return "redirect:/expenses";
        }
        Expense e = new Expense();
        e.setExpenseDate(date != null ? date : LocalDate.now());
        e.setCategory(category.isBlank() ? "Other" : category.trim());
        e.setAmount(amount);
        e.setMode(BillService.mode(mode));
        e.setNote(note.isBlank() ? null : note.trim());
        repo.save(e);
        ra.addFlashAttribute("msg", "Expense saved");
        return "redirect:/expenses";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        repo.findById(id).ifPresent(e -> {
            repo.delete(e);
            activity.log("Expense deleted", e.getCategory() + " ₹" + e.getAmount() + " on " + e.getExpenseDate()
                    + (e.getNote() == null ? "" : " – " + e.getNote()));
        });
        ra.addFlashAttribute("msg", "Expense deleted");
        return "redirect:/expenses";
    }
}
