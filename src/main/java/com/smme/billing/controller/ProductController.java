package com.smme.billing.controller;

import com.smme.billing.NotFoundException;
import com.smme.billing.model.Product;
import com.smme.billing.model.ProductImage;
import com.smme.billing.model.ShopLogo;
import com.smme.billing.repository.ProductImageRepository;
import com.smme.billing.repository.ProductRepository;
import com.smme.billing.service.ActivityService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository repo;
    private final ProductImageRepository imageRepo;
    private final ActivityService activity;

    public ProductController(ProductRepository repo, ProductImageRepository imageRepo, ActivityService activity) {
        this.repo = repo;
        this.imageRepo = imageRepo;
        this.activity = activity;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       @RequestParam(defaultValue = "false") boolean low,
                       Model model) {
        List<Product> list = q == null || q.isBlank() ? repo.findAllByOrderByNameAsc() : repo.search(q.trim());
        if (category != null && !category.isBlank())
            list = list.stream().filter(p -> category.equalsIgnoreCase(p.getCategory())).toList();
        if (low) list = list.stream().filter(Product::isLowStock).toList();
        model.addAttribute("products", list);
        model.addAttribute("categories", repo.categories());
        model.addAttribute("q", q);
        model.addAttribute("category", category);
        model.addAttribute("low", low);
        model.addAttribute("stockValue", list.stream().map(Product::stockValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("lowCount", repo.findAll().stream().filter(Product::isLowStock).count());
        return "products/list";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", repo.categories());
        return "products/form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("product", find(id));
        model.addAttribute("categories", repo.categories());
        return "products/form";
    }

    private Product find(Long id) {
        return repo.findById(id).orElseThrow(() -> new NotFoundException("Item not found"));
    }

    @PostMapping("/save")
    public String save(@ModelAttribute Product product,
                       @RequestParam(value = "image", required = false) MultipartFile image,
                       @RequestParam(defaultValue = "false") boolean removeImage,
                       Model model, RedirectAttributes ra) throws IOException {
        product.setName(trim(product.getName()));
        product.setCode(trim(product.getCode()));
        product.setCategory(trim(product.getCategory()));
        product.setSareeType(trim(product.getSareeType()));
        product.setFabric(trim(product.getFabric()));
        product.setColor(trim(product.getColor()));
        product.setDesign(trim(product.getDesign()));
        product.setBarcode(trim(product.getBarcode()));
        product.setHsn(trim(product.getHsn()));
        product.setUnit(trim(product.getUnit()));

        String error = null;
        if (product.getName() == null) error = "Item name is required";
        else if (negative(product.getRate()) || negative(product.getMrp()) || negative(product.getPurchasePrice())
                || negative(product.getWholesalePrice())) error = "Prices cannot be negative";
        else if (negative(product.getMinStock())) error = "Minimum stock cannot be negative";
        else if (product.getPiecesPerBundle() != null && product.getPiecesPerBundle() < 0) error = "Pieces per bundle cannot be negative";
        else if (repo.findFirstByNameIgnoreCase(product.getName())
                .filter(p -> !p.getId().equals(product.getId())).isPresent())
            error = "Another item already has this name";
        else if (image != null && !image.isEmpty() && image.getSize() > ShopLogo.MAX_BYTES) error = "Photo must be smaller than 1 MB";
        else if (image != null && !image.isEmpty() && SettingsController.detectImageType(image.getBytes()) == null)
            error = "Photo must be a PNG, JPG, WEBP or GIF image";
        if (error != null) {
            model.addAttribute("err", error);
            model.addAttribute("categories", repo.categories());
            return "products/form";
        }

        Product saved;
        if (product.getId() != null) {
            Product old = find(product.getId());
            List<String> changes = new ArrayList<>();
            priceChange(changes, "Selling price", old.getRate(), product.getRate());
            priceChange(changes, "Wholesale price", old.getWholesalePrice(), product.getWholesalePrice());
            priceChange(changes, "Purchase price", old.getPurchasePrice(), product.getPurchasePrice());
            priceChange(changes, "MRP", old.getMrp(), product.getMrp());
            if (!old.getName().equals(product.getName())) changes.add("name " + old.getName() + " → " + product.getName());
            product.setStockQty(old.getStockQty());   // stock changes only through bills, purchases or "Adjust stock"
            product.setHasImage(old.getHasImage());
            saved = repo.save(product);
            if (!changes.isEmpty()) activity.log("Item price changed", saved.getName() + ": " + String.join(", ", changes));
        } else {
            saved = repo.save(product);
            activity.log("Item added", saved.getName() + (saved.stock().signum() != 0 ? " – opening stock " + saved.stock().stripTrailingZeros().toPlainString() : ""));
        }

        if (removeImage && imageRepo.existsById(saved.getId())) {
            imageRepo.deleteById(saved.getId());
            saved.setHasImage(false);
            repo.save(saved);
        }
        if (image != null && !image.isEmpty()) {
            ProductImage img = imageRepo.findById(saved.getId()).orElseGet(ProductImage::new);
            img.setId(saved.getId());
            img.setData(image.getBytes());
            img.setContentType(SettingsController.detectImageType(image.getBytes()));
            imageRepo.save(img);
            saved.setHasImage(true);
            repo.save(saved);
        }
        ra.addFlashAttribute("msg", "Item saved");
        return "redirect:/products";
    }

    @GetMapping("/{id}/image")
    public ResponseEntity<byte[]> image(@PathVariable Long id) {
        ProductImage img = imageRepo.findById(id).orElseThrow(() -> new NotFoundException("No photo"));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(img.getContentType()))
                .cacheControl(CacheControl.noCache()).body(img.getData());
    }

    /** Manual stock correction (damaged, counted, opening stock...) – always logged with the reason. */
    @PostMapping("/{id}/stock")
    public String adjustStock(@PathVariable Long id, @RequestParam BigDecimal change,
                              @RequestParam(defaultValue = "") String reason, RedirectAttributes ra) {
        Product p = find(id);
        if (change.signum() == 0) {
            ra.addFlashAttribute("err", "Enter how much to add (e.g. 10) or remove (e.g. -2)");
        } else if (reason.isBlank()) {
            ra.addFlashAttribute("err", "Write a reason for the stock change");
        } else {
            BigDecimal before = p.stock();
            p.setStockQty(before.add(change));
            repo.save(p);
            activity.log("Stock adjusted", p.getName() + ": " + plain(before) + " → " + plain(p.stock())
                    + " (" + (change.signum() > 0 ? "+" : "") + plain(change) + ") – " + reason.trim());
            ra.addFlashAttribute("msg", "Stock of " + p.getName() + " is now " + plain(p.stock()));
        }
        return "redirect:/products/" + id + "/edit";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        repo.findById(id).ifPresent(p -> {
            if (imageRepo.existsById(id)) imageRepo.deleteById(id);
            repo.delete(p);
            activity.log("Item deleted", p.getName() + " (stock was " + plain(p.stock()) + ")");
        });
        ra.addFlashAttribute("msg", "Item deleted");
        return "redirect:/products";
    }

    private static void priceChange(List<String> out, String label, BigDecimal before, BigDecimal after) {
        boolean same = before == null ? after == null : after != null && before.compareTo(after) == 0;
        if (!same) out.add(label + " " + (before == null ? "-" : plain(before)) + " → " + (after == null ? "-" : plain(after)));
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }

    private static boolean negative(BigDecimal v) {
        return v != null && v.signum() < 0;
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
