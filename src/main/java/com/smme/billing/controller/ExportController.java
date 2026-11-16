package com.smme.billing.controller;

import com.smme.billing.model.ShopSetting;
import com.smme.billing.service.ActivityService;
import com.smme.billing.service.DataExportService;
import com.smme.billing.service.SettingService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

/** Daily Excel export to GitHub: set the private repository link, export now, download the latest Excel file. */
@Controller
@RequestMapping("/settings/export")
public class ExportController {

    private final DataExportService export;
    private final SettingService settings;
    private final ActivityService activity;

    public ExportController(DataExportService export, SettingService settings, ActivityService activity) {
        this.export = export;
        this.settings = settings;
        this.activity = activity;
    }

    @PostMapping("/link")
    public String saveLink(@RequestParam(defaultValue = "") String url, RedirectAttributes ra) {
        String u = url.trim();
        if (!u.isEmpty() && !DataExportService.GITHUB_URL.matcher(u).matches()) {
            ra.addFlashAttribute("err", "The link must look like https://github.com/your-name/shop-billing-data");
            return "redirect:/settings#export";
        }
        ShopSetting s = settings.get();
        s.setExportGitUrl(u.isEmpty() ? null : u);
        settings.saveRaw(s);
        activity.log("Export link changed", u.isEmpty() ? "removed" : u);
        ra.addFlashAttribute("msg", u.isEmpty() ? "GitHub link removed – the Excel file is now only saved on this computer"
                : "GitHub link saved. Press \"Export & push now\" to check it works.");
        return "redirect:/settings#export";
    }

    @PostMapping("/run")
    public String runNow(RedirectAttributes ra) {
        DataExportService.Status s = export.run("manual");
        ra.addFlashAttribute(s.ok() ? "msg" : "err", (s.ok() ? "" : "Export failed: ") + s.message());
        return "redirect:/settings#export";
    }

    @GetMapping("/latest")
    public ResponseEntity<FileSystemResource> latest() {
        Path file = export.latestFile();
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shop-data-" + LocalDate.now() + ".xlsx\"")
                .body(new FileSystemResource(file));
    }
}
