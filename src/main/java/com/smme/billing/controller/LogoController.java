package com.smme.billing.controller;

import com.smme.billing.NotFoundException;
import com.smme.billing.model.ShopLogo;
import com.smme.billing.repository.ShopLogoRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LogoController {

    private final ShopLogoRepository logoRepo;

    public LogoController(ShopLogoRepository logoRepo) {
        this.logoRepo = logoRepo;
    }

    @GetMapping("/shop-logo")
    public ResponseEntity<byte[]> logo() {
        ShopLogo logo = logoRepo.findById(1L).orElseThrow(() -> new NotFoundException("No logo uploaded"));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(logo.getContentType()))
                .cacheControl(CacheControl.noCache())
                .body(logo.getData());
    }
}
