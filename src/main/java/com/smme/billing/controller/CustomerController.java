package com.smme.billing.controller;

import com.smme.billing.NotFoundException;
import com.smme.billing.model.Bill;
import com.smme.billing.model.Customer;
import com.smme.billing.model.ShopSetting;
import com.smme.billing.repository.CustomerRepository;
import com.smme.billing.repository.LocalBillRepository;
import com.smme.billing.service.ActivityService;
import com.smme.billing.service.BillService;
import com.smme.billing.service.SettingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerRepository repo;
    private final BillService billService;
    private final LocalBillRepository localBillRepo;
    private final ActivityService activity;
    private final SettingService settingService;

    public CustomerController(CustomerRepository repo, BillService billService, LocalBillRepository localBillRepo,
                              ActivityService activity, SettingService settingService) {
        this.repo = repo;
        this.billService = billService;
        this.localBillRepo = localBillRepo;
        this.activity = activity;
        this.settingService = settingService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, @RequestParam(required = false) String type, Model model) {
        List<Customer> list = q == null || q.isBlank()
                ? repo.findAllByOrderByNameAsc()
                : repo.findByNameContainingIgnoreCaseOrderByNameAsc(q.trim());
        if (type != null && !type.isBlank()) list = list.stream().filter(c -> type.equals(c.getCustomerType())).toList();
        Map<String, BigDecimal> due = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        billService.outstandingByCustomer().forEach(e -> due.put(e.getKey(), e.getValue()));
        model.addAttribute("customers", list);
        model.addAttribute("due", due);
        model.addAttribute("q", q);
        model.addAttribute("type", type);
        return "customers/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("customer", new Customer());
        return "customers/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("customer", find(id));
        return "customers/form";
    }

    private Customer find(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Customer customer, Model model, RedirectAttributes ra) {
        customer.setName(trim(customer.getName()));
        customer.setAddress(trim(customer.getAddress()));
        String gstin = trim(customer.getGstin());
        customer.setGstin(gstin == null ? null : gstin.toUpperCase());
        customer.setPhone(trim(customer.getPhone()));
        customer.setStateName(trim(customer.getStateName()));
        customer.setCustomerType("WHOLESALE".equals(customer.getCustomerType()) ? "WHOLESALE"
                : "RETAIL".equals(customer.getCustomerType()) ? "RETAIL" : null);
        String error = null;
        if (customer.getName() == null) error = "Customer name is required";
        else if (customer.getGstin() != null && customer.getGstin().length() != 15) error = "GSTIN must be exactly 15 characters";
        else if (customer.getId() != null && !repo.existsById(customer.getId())) throw new NotFoundException("Customer not found");
        if (error != null) {
            model.addAttribute("err", error);
            return "customers/form";
        }
        Customer c = repo.save(customer);
        ra.addFlashAttribute("msg", "Customer saved");
        return "redirect:/customers/" + c.getId();
    }

    /** Customer page: details, outstanding, payments received, all bills. */
    @GetMapping("/{id}")
    public String view(@PathVariable Long id, Model model) {
        Customer c = find(id);
        List<Bill> bills = billService.ofCustomer(c.getName());
        model.addAttribute("customer", c);
        model.addAttribute("bills", bills);
        model.addAttribute("localBills", localBillRepo.findTop100ByCustomerNameIgnoreCaseOrCustMobileOrderByIdDesc(
                c.getName(), c.getPhone() == null ? "-" : c.getPhone()));
        model.addAttribute("receipts", billService.receiptsOf(c.getName()));
        model.addAttribute("outstanding", billService.outstanding(c.getName()));
        model.addAttribute("totalBought", BillService.sum(bills.stream().filter(b -> !b.isCancelled()).toList(), Bill::getGrandTotal));
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("whatsapp", reminderLink(c.getPhone(), c.getName(), billService.outstanding(c.getName())));
        return "customers/view";
    }

    @PostMapping("/{id}/receive")
    public String receive(@PathVariable Long id, @RequestParam(required = false) BigDecimal amount,
                          @RequestParam(defaultValue = "CASH") String mode,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                          @RequestParam(defaultValue = "") String note, RedirectAttributes ra) {
        Customer c = find(id);
        try {
            billService.receive(c.getName(), amount, mode, date, note);
            ra.addFlashAttribute("msg", "Payment of ₹" + amount.toPlainString() + " saved");
        } catch (BillService.InvalidBillException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/customers/" + id;
    }

    public record Due(String name, Long customerId, String phone, BigDecimal amount, LocalDate since, long days, String whatsapp) {}

    /** Payment reminders: everyone with credit due, highest first, with a WhatsApp reminder link. */
    @GetMapping("/outstanding")
    public String outstanding(Model model) {
        Map<String, LocalDate> since = billService.oldestDue();
        List<Due> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var e : billService.outstandingByCustomer()) {
            Optional<Customer> c = repo.findFirstByNameIgnoreCase(e.getKey());
            String phone = c.map(Customer::getPhone).orElse(null);
            LocalDate d = since.get(e.getKey());
            rows.add(new Due(e.getKey(), c.map(Customer::getId).orElse(null), phone, e.getValue(), d,
                    d == null ? 0 : ChronoUnit.DAYS.between(d, LocalDate.now()), reminderLink(phone, e.getKey(), e.getValue())));
            total = total.add(e.getValue());
        }
        model.addAttribute("rows", rows);
        model.addAttribute("total", total);
        return "customers/outstanding";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        repo.findById(id).ifPresent(c -> {
            repo.delete(c);
            activity.log("Customer deleted", c.getName());
        });
        ra.addFlashAttribute("msg", "Customer deleted");
        return "redirect:/customers";
    }

    /** wa.me link with a polite reminder; null when there is no usable mobile number or nothing is due. */
    String reminderLink(String phone, String name, BigDecimal amount) {
        if (phone == null || amount == null || amount.signum() <= 0) return null;
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10) digits = "91" + digits;
        if (digits.length() < 11) return null;
        ShopSetting s = settingService.get();
        String text = "Namaskara " + name + ", this is a gentle reminder from " + s.getShopName()
                + ". Your pending amount is Rs " + amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString()
                + " as on " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ". Kindly arrange the payment."
                + (s.getUpiId() == null ? "" : " UPI: " + s.getUpiId()) + " Thank you.";
        return "https://wa.me/" + digits + "?text=" + URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
