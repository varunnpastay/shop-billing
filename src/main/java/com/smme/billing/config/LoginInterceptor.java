package com.smme.billing.config;

import com.smme.billing.controller.AuthController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute(AuthController.SESSION_USER) == null) {
            res.sendRedirect(req.getContextPath() + "/login");
            return false;
        }
        // still on the default password: only the settings page (to change it) and logout are allowed
        if (session.getAttribute(AuthController.SESSION_MUST_CHANGE_PASSWORD) != null) {
            String path = req.getRequestURI().substring(req.getContextPath().length());
            if (!path.startsWith("/settings") && !path.equals("/logout")) {
                res.sendRedirect(req.getContextPath() + "/settings#password");
                return false;
            }
        }
        return true;
    }
}
