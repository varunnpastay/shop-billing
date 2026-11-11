package com.smme.billing.controller;

import com.smme.billing.service.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    public static final String SESSION_USER = "user";
    public static final String SESSION_MUST_CHANGE_PASSWORD = "mustChangePassword";

    private final SettingService settingService;
    public AuthController(SettingService settingService) {
        this.settingService = settingService;
    }

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        if (session.getAttribute(SESSION_USER) != null) return "redirect:/";
        return "login";
    }

    @PostMapping("/login")
    public String login(@RequestParam(defaultValue = "") String username,
                        @RequestParam(defaultValue = "") String password,
                        HttpServletRequest request, Model model) {
        if (!settingService.checkLogin(username, password)) {
            model.addAttribute("error", "Invalid username or password");
            model.addAttribute("username", username);
            return "login";
        }

        // new session id after login (prevents session fixation)
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_USER, username.trim());
        if (SettingService.DEFAULT_PASSWORD.equals(password)) {
            session.setAttribute(SESSION_MUST_CHANGE_PASSWORD, Boolean.TRUE);
            return "redirect:/settings#password";
        }
        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session, RedirectAttributes ra) {
        session.invalidate();
        ra.addFlashAttribute("msg", "You have been logged out.");
        return "redirect:/login";
    }
}
