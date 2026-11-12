package com.smme.billing.config;

import com.smme.billing.controller.AuthController;
import com.smme.billing.model.ShopSetting;
import com.smme.billing.repository.ShopLogoRepository;
import com.smme.billing.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModel {

    private final SettingService settingService;
    private final ShopLogoRepository logoRepo;
    private final com.smme.billing.repository.ProductRepository productRepo;

    public GlobalModel(SettingService settingService, ShopLogoRepository logoRepo,
                       com.smme.billing.repository.ProductRepository productRepo) {
        this.settingService = settingService;
        this.logoRepo = logoRepo;
        this.productRepo = productRepo;
    }

    @ModelAttribute("shop")
    public ShopSetting shop() {
        return settingService.get();
    }

    @ModelAttribute("hasLogo")
    public boolean hasLogo() {
        return logoRepo.existsById(1L);
    }

    /** Path of the current page (without context path), used to highlight the active menu item. */
    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** Number of items at or below their minimum stock (badge in the menu). */
    @ModelAttribute("lowStockCount")
    public long lowStockCount() {
        return productRepo.countLowStock();
    }

    @ModelAttribute("mustChangePassword")
    public boolean mustChangePassword(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute(AuthController.SESSION_MUST_CHANGE_PASSWORD) != null;
    }
}
