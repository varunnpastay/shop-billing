package com.smme.billing.controller;

import com.smme.billing.NotFoundException;
import com.smme.billing.model.Product;
import com.smme.billing.repository.ProductRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository repo;

    public ProductController(ProductRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("products", q == null || q.isBlank()
                ? repo.findAllByOrderByNameAsc()
                : repo.findByNameContainingIgnoreCaseOrderByNameAsc(q.trim()));
        model.addAttribute("q", q);
        return "products/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("product", new Product());
        return "products/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("product", repo.findById(id).orElseThrow(() -> new NotFoundException("Item not found")));
        return "products/form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Product product, Model model, RedirectAttributes ra) {
        product.setName(trim(product.getName()));
        product.setHsn(trim(product.getHsn()));
        product.setUnit(trim(product.getUnit()));
        String error = null;
        if (product.getName() == null) error = "Item name is required";
        else if (product.getRate() != null && product.getRate().signum() < 0) error = "Rate cannot be negative";
        else if (product.getMrp() != null && product.getMrp().signum() < 0) error = "MRP cannot be negative";
        else if (product.getId() != null && !repo.existsById(product.getId())) throw new NotFoundException("Item not found");
        if (error != null) {
            model.addAttribute("err", error);
            return "products/form";
        }
        repo.save(product);
        ra.addFlashAttribute("msg", "Item saved");
        return "redirect:/products";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        if (repo.existsById(id)) repo.deleteById(id);
        ra.addFlashAttribute("msg", "Item deleted");
        return "redirect:/products";
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
