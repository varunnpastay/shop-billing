package com.smme.billing.controller;

import com.smme.billing.model.Supplier;
import com.smme.billing.repository.SupplierRepository;
import com.smme.billing.service.ActivityService;
import com.smme.billing.service.BillService;
import com.smme.billing.service.InsightService;
import com.smme.billing.service.PurchaseService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/suppliers")
public class SupplierController {

    private final SupplierRepository repo;
    private final PurchaseService purchaseService;
    private final InsightService insights;
    private final ActivityService activity;

    public SupplierController(SupplierRepository repo, PurchaseService purchaseService, InsightService insights,
                              ActivityService activity) {
        this.repo = repo;
        this.purchaseService = purchaseService;
        this.insights = insights;
        this.activity = activity;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("rows", insights.supplierBalances());
        model.addAttribute("payables", insights.payables());
        return "suppliers/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("supplier", new Supplier());
        return "suppliers/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("supplier", purchaseService.supplier(id));
        return "suppliers/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Supplier supplier, Model model, RedirectAttributes ra) {
        supplier.setName(trim(supplier.getName()));
        supplier.setPhone(trim(supplier.getPhone()));
        supplier.setAddress(trim(supplier.getAddress()));
        supplier.setGstin(supplier.getGstin() == null || supplier.getGstin().isBlank() ? null : supplier.getGstin().trim().toUpperCase());
        supplier.setNotes(trim(supplier.getNotes()));
        String error = null;
        if (supplier.getName() == null) error = "Supplier name is required";
        else if (supplier.getGstin() != null && supplier.getGstin().length() != 15) error = "GSTIN must be exactly 15 characters";
        else if (supplier.getId() != null) purchaseService.supplier(supplier.getId());   // 404 if missing
        if (error != null) {
            model.addAttribute("err", error);
            return "suppliers/form";
        }
        Supplier s = repo.save(supplier);
        ra.addFlashAttribute("msg", "Supplier saved");
        return "redirect:/suppliers/" + s.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("supplier", purchaseService.supplier(id));
        model.addAttribute("purchases", purchaseService.ofSupplier(id));
        model.addAttribute("payments", purchaseService.paymentsTo(id));
        model.addAttribute("balance", purchaseService.balance(id));
        model.addAttribute("today", LocalDate.now());
        return "suppliers/view";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id, @RequestParam(required = false) BigDecimal amount,
                      @RequestParam(defaultValue = "CASH") String mode,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam(defaultValue = "") String note, RedirectAttributes ra) {
        try {
            purchaseService.pay(id, amount, mode, date, note);
            ra.addFlashAttribute("msg", "Payment saved");
        } catch (BillService.InvalidBillException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/suppliers/" + id;
    }

    @PostMapping("/{id}/payments/{paymentId}/delete")
    public String deletePayment(@PathVariable Long id, @PathVariable Long paymentId, RedirectAttributes ra) {
        purchaseService.deletePayment(paymentId);
        ra.addFlashAttribute("msg", "Payment deleted");
        return "redirect:/suppliers/" + id;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Supplier s = purchaseService.supplier(id);
        if (!purchaseService.ofSupplier(id).isEmpty() || !purchaseService.paymentsTo(id).isEmpty()) {
            ra.addFlashAttribute("err", "This supplier has purchases or payments, so it cannot be deleted");
            return "redirect:/suppliers/" + id;
        }
        repo.delete(s);
        activity.log("Supplier deleted", s.getName());
        ra.addFlashAttribute("msg", "Supplier deleted");
        return "redirect:/suppliers";
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
