package com.smme.billing.controller;

import com.smme.billing.model.LocalBill;
import com.smme.billing.model.LocalBillItem;
import com.smme.billing.repository.CustomerRepository;
import com.smme.billing.repository.ProductRepository;
import com.smme.billing.service.BillService;
import com.smme.billing.service.LocalBillService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local (retail counter) billing – the Smart Billing receipt, now saved in the database. */
@Controller
@RequestMapping("/local")
public class LocalBillController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH);

    private final LocalBillService service;
    private final CustomerRepository customerRepo;
    private final ProductRepository productRepo;

    public LocalBillController(LocalBillService service, CustomerRepository customerRepo, ProductRepository productRepo) {
        this.service = service;
        this.customerRepo = customerRepo;
        this.productRepo = productRepo;
    }

    // ---------------- pages ----------------

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(required = false, defaultValue = "ALL") String status,
                       Model model) {
        List<LocalBill> bills = service.search(q, from, to, status);
        model.addAttribute("bills", bills);
        model.addAttribute("total", LocalBillService.sum(bills.stream().filter(b -> !b.isCancelled()).toList(),
                LocalBill::getNetPayable));
        model.addAttribute("q", q);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("status", status);
        return "local/list";
    }

    @GetMapping("/new")
    public String newBill(Model model) {
        return form(null, false, model);
    }

    @GetMapping("/{id}")
    public String open(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean print, Model model) {
        service.get(id); // 404 if missing
        return form(id, print, model);
    }

    private String form(Long id, boolean print, Model model) {
        model.addAttribute("billId", id);
        model.addAttribute("autoPrint", print);
        model.addAttribute("nextBillNo", service.peekNextBillNo(LocalDate.now().getYear()));
        model.addAttribute("customers", customerRepo.findAllByOrderByNameAsc());
        model.addAttribute("products", productRepo.findAllByOrderByNameAsc());
        return "local/form";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam boolean value, RedirectAttributes ra) {
        service.setCancelled(id, value);
        ra.addFlashAttribute("msg", value ? "Local bill cancelled" : "Local bill restored");
        return "redirect:/local";
    }

    // ---------------- JSON used by the billing page ----------------

    public record ItemView(String name, BigDecimal qty, BigDecimal mrp, BigDecimal rate, BigDecimal amt) {}

    public record BillView(Long id, String billNo, String billDate, String time, String customerName, String custMobile,
                           boolean cancelled, List<ItemView> items,
                           BigDecimal totalSale, BigDecimal totalSavings, BigDecimal netPayable) {}

    public record SavedView(Long id, String billNo) {}

    static BillView view(LocalBill b) {
        return new BillView(b.getId(), b.getBillNo(), String.valueOf(b.getBillDate()),
                b.getCreatedAt() == null ? "" : b.getCreatedAt().format(TIME),
                b.getCustomerName(), b.getCustMobile(), b.isCancelled(),
                b.getItems().stream().map(LocalBillController::itemView).toList(),
                b.getTotalSale(), b.getTotalSavings(), b.getNetPayable());
    }

    private static ItemView itemView(LocalBillItem i) {
        return new ItemView(i.getName(), i.getQty(), i.getMrp(), i.getRate(), i.getAmount());
    }

    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<?> save(@RequestBody LocalBillService.BillForm form) {
        try {
            return ResponseEntity.ok(view(service.save(form)));
        } catch (BillService.InvalidBillException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/bills/{id}")
    @ResponseBody
    public BillView bill(@PathVariable Long id) {
        return view(service.get(id));
    }

    @GetMapping("/api/saved")
    @ResponseBody
    public List<SavedView> saved() {
        return service.savedList().stream().map(b -> new SavedView(b.getId(), b.getBillNo())).toList();
    }

    @GetMapping("/api/next")
    @ResponseBody
    public Map<String, String> next() {
        return Map.of("billNo", service.peekNextBillNo(LocalDate.now().getYear()));
    }

    /** Daily sales report on the billing page (cancelled bills excluded). */
    @GetMapping("/api/report")
    @ResponseBody
    public List<BillView> report(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.forReport(date, date).stream().map(LocalBillController::view).toList();
    }
}
