package com.smme.billing.controller;

import com.smme.billing.model.Purchase;
import com.smme.billing.model.PurchaseItem;
import com.smme.billing.repository.ProductRepository;
import com.smme.billing.repository.PurchaseRepository;
import com.smme.billing.repository.SupplierRepository;
import com.smme.billing.service.BillService;
import com.smme.billing.service.PurchaseService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/purchases")
public class PurchaseController {

    private final PurchaseService service;
    private final PurchaseRepository repo;
    private final SupplierRepository supplierRepo;
    private final ProductRepository productRepo;

    public PurchaseController(PurchaseService service, PurchaseRepository repo, SupplierRepository supplierRepo,
                              ProductRepository productRepo) {
        this.service = service;
        this.repo = repo;
        this.supplierRepo = supplierRepo;
        this.productRepo = productRepo;
    }

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        List<Purchase> list = repo.findByPurchaseDateBetweenOrderByPurchaseDateDescIdDesc(from, to);
        model.addAttribute("purchases", list);
        model.addAttribute("total", list.stream().map(Purchase::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        return "purchases/list";
    }

    @GetMapping("/new")
    public String create(@RequestParam(required = false) Long supplierId, Model model) {
        Purchase p = new Purchase();
        p.setSupplierId(supplierId);
        p.setPurchaseDate(LocalDate.now());
        p.getItems().add(new PurchaseItem());
        return form(p, model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        return form(service.get(id), model);
    }

    private String form(Purchase p, Model model) {
        if (p.getItems().isEmpty()) p.getItems().add(new PurchaseItem());
        model.addAttribute("purchase", p);
        model.addAttribute("suppliers", supplierRepo.findAllByOrderByNameAsc());
        model.addAttribute("products", productRepo.findAllByOrderByNameAsc());
        return "purchases/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Purchase purchase, Model model, RedirectAttributes ra) {
        try {
            Purchase saved = service.save(purchase);
            ra.addFlashAttribute("msg", "Purchase saved – stock updated");
            return "redirect:/purchases/" + saved.getId();
        } catch (BillService.InvalidBillException e) {
            model.addAttribute("err", e.getMessage());
            return form(purchase, model);
        }
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("purchase", service.get(id));
        return "purchases/view";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Long supplierId = service.get(id).getSupplierId();
        service.delete(id);
        ra.addFlashAttribute("msg", "Purchase deleted – stock reduced back");
        return "redirect:/suppliers/" + supplierId;
    }
}
