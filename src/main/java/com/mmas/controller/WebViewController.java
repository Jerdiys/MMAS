package com.mmas.controller;

import com.mmas.model.AuthLog;
import com.mmas.repository.AuthLogRepository;
import com.mmas.service.AuthLogService;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class WebViewController {

    private final AuthLogRepository authLogRepository;
    private final AuthLogService authLogService;

    @GetMapping("/")
    public String landingPage() {
        return "index";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/success")
    public String successPage(@RequestParam(required = false) String similarity, Model model) {
        model.addAttribute("similarity", similarity);
        return "success";
    }

    @GetMapping("/failure")
    public String failurePage(@RequestParam(required = false) String reason, Model model) {
        model.addAttribute("reason", reason);
        return "failure";
    }

    @GetMapping("/metrics")
    public String metricsPage(Model model, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Double avgSpeed = authLogRepository.getAverageSuccessDuration();
        long successCount = authLogRepository.countSuccessfulAttempts();
        long totalCount = authLogRepository.countTotalAttempts();

        double accuracy = totalCount > 0 ? ((double) successCount / totalCount) * 100.0 : 0.0;
        Page<AuthLog> logs = authLogService.getAuthLogs(page, size);

        model.addAttribute("avgSpeed", avgSpeed != null ? avgSpeed : 0.0);
        model.addAttribute("accuracy", accuracy);
        model.addAttribute("totalLogs", totalCount);
        model.addAttribute("logs", logs.getContent());
        model.addAttribute("totalPages", logs.getTotalPages());
        model.addAttribute("currentPage", logs.getNumber());
        model.addAttribute("hasPrevious", logs.hasPrevious());
        model.addAttribute("hasNext", logs.hasNext());

        return "metrics";
    }
}
