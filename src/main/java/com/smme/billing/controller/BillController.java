package com.smme.billing.controller;

import com.smme.billing.model.Bill;
import com.smme.billing.repository.CustomerRepository;
import com.smme.billing.repository.ProductRepository;
import com.smme.billing.service.BillService;
import com.smme.billing.service.NumberToWords;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/bills")
public class BillController {

    private final BillService billService;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;

    public BillController(BillService billService, CustomerRepository customerRepo, ProductRepository productRepo) {
        this.billService = billService;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false, defaultValue = "ALL") String status,
                       Model model) {
        List<Bill> bills = billService.search(q, from, to, status);
        model.addAttribute("bills", bills);
        model.addAttribute("total", BillService.sum(bills.stream().filter(b -> !b.isCancelled()).toList(), Bill::getGrandTotal));
        model.addAttribute("q", q);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("status", status);
        return "bills/list";
    }

    @GetMapping("/new")
    public String newBill(Model model) {
        return form(billService.newBill(), model);
    }

    @GetMapping("/{id}/copy")
    public String copy(@PathVariable Long id, Model model) {
        return form(billService.copyOf(id), model);
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Bill bill = billService.get(id);
        if (bill.isCancelled()) {
            ra.addFlashAttribute("err", "A cancelled bill cannot be edited. Restore it first.");
            return "redirect:/bills/" + id;
        }
        return form(bill, model);
    }

    private String form(Bill bill, Model model) {
        if (bill.getItems().isEmpty()) bill.getItems().add(new com.smme.billing.model.BillItem());
        model.addAttribute("bill", bill);
        model.addAttribute("customers", customerRepo.findAllByOrderByNameAsc());
        model.addAttribute("products", productRepo.findAllByOrderByNameAsc());
        model.addAttribute("byNames", billService.byNames());
        return "bills/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Bill bill, @RequestParam(defaultValue = "false") boolean print,
                       Model model, RedirectAttributes ra) {
        Bill saved;
        try {
            saved = billService.save(bill);
        } catch (BillService.InvalidBillException e) {
            model.addAttribute("err", e.getMessage());
            if (bill.getId() != null) bill.setBillNo(billService.get(bill.getId()).getBillNo());
            else bill.setBillNo(billService.newBill().getBillNo());
            return form(bill, model);
        }
        if (print) return "redirect:/bills/" + saved.getId() + "/print";
        ra.addFlashAttribute("msg", "Bill #" + saved.getBillNo() + " saved");
        return "redirect:/bills/" + saved.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        model.addAttribute("bill", billService.get(id));
        return "bills/view";
    }

    @GetMapping("/{id}/print")
    public String print(@PathVariable Long id,
                        @RequestParam(defaultValue = "A4") String size,
                        @RequestParam(defaultValue = "ORIGINAL") String copy,
                        Model model) {
        Bill bill = billService.get(id);
        model.addAttribute("bill", bill);
        String words = NumberToWords.convert(bill.getGrandTotal());
        model.addAttribute("words", words.startsWith("Rupees ") ? words.substring(7) : words);   // label says "Rupee's in Words"
        // whitelisted: these values end up inside the page's CSS
        model.addAttribute("size", "A5".equalsIgnoreCase(size) ? "A5" : "A4");
        model.addAttribute("copy", "DUPLICATE".equalsIgnoreCase(copy) ? "DUPLICATE" : "ORIGINAL");
        return "bills/print";
    }

    @PostMapping("/{id}/paid")
    public String paid(@PathVariable Long id, @RequestParam boolean value, RedirectAttributes ra) {
        try {
            billService.markPaid(id, value);
            ra.addFlashAttribute("msg", value ? "Bill marked as paid" : "Bill marked as unpaid");
        } catch (BillService.InvalidBillException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/bills/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam boolean value, RedirectAttributes ra) {
        billService.setCancelled(id, value);
        ra.addFlashAttribute("msg", value ? "Bill cancelled" : "Bill restored");
        return "redirect:/bills/" + id;
    }
}
