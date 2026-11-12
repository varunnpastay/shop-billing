package com.smme.billing.service;

import com.smme.billing.controller.AuthController;
import com.smme.billing.model.ActivityLog;
import com.smme.billing.repository.ActivityLogRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;

/** Records important actions (price changes, stock adjustments, cancellations, deletions...) for the Activity page. */
@Service
public class ActivityService {

    private final ActivityLogRepository repo;

    public ActivityService(ActivityLogRepository repo) {
        this.repo = repo;
    }

    public void log(String action, String details) {
        ActivityLog a = new ActivityLog();
        a.setAt(LocalDateTime.now());
        a.setUserName(currentUser());
        a.setAction(action);
        a.setDetails(details != null && details.length() > 1000 ? details.substring(0, 1000) : details);
        repo.save(a);
    }

    public List<ActivityLog> recent() {
        return repo.findTop500ByOrderByIdDesc();
    }

    private static String currentUser() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return "system";
        HttpSession s = attrs.getRequest().getSession(false);
        Object u = s == null ? null : s.getAttribute(AuthController.SESSION_USER);
        return u == null ? "system" : u.toString();
    }
}
