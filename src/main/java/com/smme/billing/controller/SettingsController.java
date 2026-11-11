package com.smme.billing.controller;

import com.smme.billing.model.ShopLogo;
import com.smme.billing.model.ShopSetting;
import com.smme.billing.repository.ShopLogoRepository;
import com.smme.billing.service.BackupService;
import com.smme.billing.service.LocalBillService;
import com.smme.billing.service.SettingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Controller
@RequestMapping("/settings")
public class SettingsController {

    private static final Set<String> LOGO_TYPES = Set.of("image/png", "image/jpeg", "image/webp", "image/gif");

    private final SettingService settingService;
    private final ShopLogoRepository logoRepo;
    private final LocalBillService localBillService;
    private final BackupService backupService;

    public SettingsController(SettingService settingService, ShopLogoRepository logoRepo,
                              LocalBillService localBillService, BackupService backupService) {
        this.settingService = settingService;
        this.logoRepo = logoRepo;
        this.localBillService = localBillService;
        this.backupService = backupService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("setting", settingService.get());
        model.addAttribute("nextLocalBillNo", localBillService.nextSeqForSettings());
        model.addAttribute("backupSupported", backupService.isSupported());
        model.addAttribute("backups", backupService.list());
        model.addAttribute("backupDir", backupService.getDir().toString());
        return "settings";
    }

    @PostMapping
    public String save(@ModelAttribute("setting") ShopSetting setting, RedirectAttributes ra) {
        if (setting.getShopName() == null || setting.getShopName().isBlank()) {
            ra.addFlashAttribute("err", "Shop name is required");
        } else if (setting.getNextBillNo() == null || setting.getNextBillNo() < 1) {
            ra.addFlashAttribute("err", "Next bill number must be 1 or more");
        } else if (setting.getNextLocalBillNo() != null && setting.getNextLocalBillNo() < 1) {
            ra.addFlashAttribute("err", "Next local bill number must be 1 or more");
        } else if (setting.getDefaultGst() != null
                && (setting.getDefaultGst().signum() < 0 || setting.getDefaultGst().compareTo(new BigDecimal("100")) > 0)) {
            ra.addFlashAttribute("err", "Default GST must be between 0 and 100");
        } else if (setting.getGstin() != null && !setting.getGstin().isBlank() && setting.getGstin().trim().length() != 15) {
            ra.addFlashAttribute("err", "GSTIN must be exactly 15 characters");
        } else {
            if (setting.getDefaultGst() == null) setting.setDefaultGst(BigDecimal.ZERO);
            settingService.save(setting);
            ra.addFlashAttribute("msg", "Settings saved");
        }
        return "redirect:/settings";
    }

    @PostMapping("/password")
    public String password(@RequestParam(defaultValue = "") String oldPassword,
                           @RequestParam(defaultValue = "") String newPassword,
                           @RequestParam(defaultValue = "") String confirmPassword,
                           HttpSession session, RedirectAttributes ra) {
        if (newPassword.length() < SettingService.MIN_PASSWORD_LENGTH) {
            ra.addFlashAttribute("err", "New password must be at least " + SettingService.MIN_PASSWORD_LENGTH + " characters");
        } else if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("err", "New password and confirmation do not match");
        } else if (SettingService.DEFAULT_PASSWORD.equals(newPassword)) {
            ra.addFlashAttribute("err", "Please choose a password other than the default one");
        } else if (settingService.changePassword(oldPassword, newPassword)) {
            session.removeAttribute(AuthController.SESSION_MUST_CHANGE_PASSWORD);
            ra.addFlashAttribute("msg", "Password changed");
        } else {
            ra.addFlashAttribute("err", "Old password is wrong");
        }
        return "redirect:/settings#password";
    }

    @PostMapping("/logo")
    public String uploadLogo(@RequestParam("logo") MultipartFile file, RedirectAttributes ra) throws IOException {
        if (file == null || file.isEmpty()) {
            ra.addFlashAttribute("err", "Choose an image file to upload");
        } else if (file.getSize() > ShopLogo.MAX_BYTES) {
            ra.addFlashAttribute("err", "Logo must be smaller than 1 MB");
        } else {
            String type = detectImageType(file.getBytes());
            if (type == null || !LOGO_TYPES.contains(type)) {
                ra.addFlashAttribute("err", "Logo must be a PNG, JPG, WEBP or GIF image");
            } else {
                ShopLogo logo = logoRepo.findById(1L).orElseGet(ShopLogo::new);
                logo.setData(file.getBytes());
                logo.setContentType(type);
                logo.setUpdatedAt(LocalDateTime.now());
                logoRepo.save(logo);
                ra.addFlashAttribute("msg", "Logo uploaded");
            }
        }
        return "redirect:/settings#logo";
    }

    @PostMapping("/logo/delete")
    public String deleteLogo(RedirectAttributes ra) {
        if (logoRepo.existsById(1L)) logoRepo.deleteById(1L);
        ra.addFlashAttribute("msg", "Logo removed");
        return "redirect:/settings#logo";
    }

    /** Identify the image by its first bytes instead of trusting the browser-supplied type. */
    static String detectImageType(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "image/gif";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }
}
