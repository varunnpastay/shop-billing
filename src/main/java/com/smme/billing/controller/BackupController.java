package com.smme.billing.controller;

import com.smme.billing.service.BackupService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;

/** "Backup now" and backup downloads on the Settings page (under /settings so it needs a logged-in user). */
@Controller
@RequestMapping("/settings/backup")
public class BackupController {

    private final BackupService backups;

    public BackupController(BackupService backups) {
        this.backups = backups;
    }

    @PostMapping
    public String backupNow(RedirectAttributes ra) {
        try {
            Path file = backups.backupNow();
            ra.addFlashAttribute("msg", "Backup saved: " + file.getFileName());
        } catch (Exception e) {
            ra.addFlashAttribute("err", "Backup failed: " + e.getMessage());
        }
        return "redirect:/settings#backup";
    }

    @GetMapping("/{name:.+}")
    public ResponseEntity<FileSystemResource> download(@PathVariable String name) {
        Path file = backups.resolve(name);
        if (file == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .body(new FileSystemResource(file));
    }
}
