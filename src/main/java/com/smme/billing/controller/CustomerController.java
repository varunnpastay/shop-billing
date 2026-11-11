package com.smme.billing.controller;

import com.smme.billing.NotFoundException;
import com.smme.billing.model.Customer;
import com.smme.billing.repository.CustomerRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerRepository repo;

    public CustomerController(CustomerRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("customers", q == null || q.isBlank()
                ? repo.findAllByOrderByNameAsc()
                : repo.findByNameContainingIgnoreCaseOrderByNameAsc(q.trim()));
        model.addAttribute("q", q);
        return "customers/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("customer", new Customer());
        return "customers/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("customer", repo.findById(id).orElseThrow(() -> new NotFoundException("Customer not found")));
        return "customers/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Customer customer, Model model, RedirectAttributes ra) {
        customer.setName(trim(customer.getName()));
        customer.setAddress(trim(customer.getAddress()));
        String gstin = trim(customer.getGstin());
        customer.setGstin(gstin == null ? null : gstin.toUpperCase());
        customer.setPhone(trim(customer.getPhone()));
        customer.setStateName(trim(customer.getStateName()));
        String error = null;
        if (customer.getName() == null) error = "Customer name is required";
        else if (customer.getGstin() != null && customer.getGstin().length() != 15) error = "GSTIN must be exactly 15 characters";
        else if (customer.getId() != null && !repo.existsById(customer.getId())) throw new NotFoundException("Customer not found");
        if (error != null) {
            model.addAttribute("err", error);
            return "customers/form";
        }
        repo.save(customer);
        ra.addFlashAttribute("msg", "Customer saved");
        return "redirect:/customers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        if (repo.existsById(id)) repo.deleteById(id);
        ra.addFlashAttribute("msg", "Customer deleted");
        return "redirect:/customers";
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
