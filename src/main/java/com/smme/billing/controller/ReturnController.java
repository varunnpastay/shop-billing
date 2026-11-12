package com.smme.billing.controller;

import com.smme.billing.model.SalesReturn;
import com.smme.billing.repository.ProductRepository;
import com.smme.billing.repository.SalesReturnRepository;
import com.smme.billing.service.ActivityService;
import com.smme.billing.service.BillService;
import com.smme.billing.service.StockService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Goods returned by customers: stock goes back up, the refund counts against sales. */
@Controller
@RequestMapping("/returns")
public class ReturnController {

    private final SalesReturnRepository repo;
    private final ProductRepository productRepo;
    private final StockService stock;
    private final ActivityService activity;

    public ReturnController(SalesReturnRepository repo, ProductRepository productRepo, StockService stock, ActivityService activity) {
        this.repo = repo;
        this.productRepo = productRepo;
        this.stock = stock;
        this.activity = activity;
    }

    @GetMapping
    public String list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       Model model) {
        if (from == null) from = LocalDate.now().withDayOfMonth(1);
        if (to == null) to = LocalDate.now();
        if (to.isBefore(from)) { LocalDate t = from; from = to; to = t; }
        List<SalesReturn> list = repo.findByReturnDateBetweenOrderByReturnDateDescIdDesc(from, to);
        model.addAttribute("returns", list);
        model.addAttribute("total", list.stream().map(SalesReturn::getRefund).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("products", productRepo.findAllByOrderByNameAsc());
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("today", LocalDate.now());
        return "returns/list";
    }

    @PostMapping
    @Transactional
    public String add(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                      @RequestParam(defaultValue = "") String billRef,
                      @RequestParam(defaultValue = "") String customerName,
                      @RequestParam(defaultValue = "") String productName,
                      @RequestParam(required = false) BigDecimal qty,
                      @RequestParam(required = false) BigDecimal refund,
                      @RequestParam(defaultValue = "CASH") String mode,
                      @RequestParam(defaultValue = "") String reason, RedirectAttributes ra) {
        if (productName.isBlank()) {
            ra.addFlashAttribute("err", "Enter the item returned");
        } else if (qty == null || qty.signum() <= 0) {
            ra.addFlashAttribute("err", "Quantity must be more than 0");
        } else if (refund == null || refund.signum() < 0) {
            ra.addFlashAttribute("err", "Enter the refund amount (0 if exchanged)");
        } else {
            SalesReturn r = new SalesReturn();
            r.setReturnDate(date != null ? date : LocalDate.now());
            r.setBillRef(blank(billRef));
            r.setCustomerName(blank(customerName));
            r.setProductName(productName.trim());
            r.setQty(qty);
            r.setRefund(refund);
            r.setMode(BillService.mode(mode));
            r.setReason(blank(reason));
            r.setCreatedAt(LocalDateTime.now());
            repo.save(r);
            stock.change(r.getProductName(), qty);
            activity.log("Goods returned", r.getProductName() + " × " + qty.stripTrailingZeros().toPlainString()
                    + (r.getBillRef() == null ? "" : " (bill " + r.getBillRef() + ")") + " – refund ₹" + refund);
            ra.addFlashAttribute("msg", "Return saved – stock added back");
        }
        return "redirect:/returns";
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        repo.findById(id).ifPresent(r -> {
            stock.change(r.getProductName(), r.getQty().negate());
            repo.delete(r);
            activity.log("Return deleted", r.getProductName() + " × " + r.getQty().stripTrailingZeros().toPlainString()
                    + " – refund ₹" + r.getRefund());
        });
        ra.addFlashAttribute("msg", "Return deleted – stock taken out again");
        return "redirect:/returns";
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
