package ng.asuu.thrift.web;

import ng.asuu.thrift.service.AdminAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
public class AdminAnalyticsController {
    private final AdminAnalyticsService analyticsService;

    public AdminAnalyticsController(AdminAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/summary")
    public AdminAnalyticsService.Summary summary() {
        return analyticsService.summary();
    }

    @GetMapping("/monthly-savings")
    public AdminAnalyticsService.FiscalYearTrend monthlySavings() {
        return analyticsService.monthlySavingsTrendForCurrentFiscalYear();
    }

    @GetMapping("/loans-granted-by-type")
    public AdminAnalyticsService.FiscalYearBreakdown loansGrantedByType() {
        return analyticsService.loansGrantedByTypeForCurrentFiscalYear();
    }

    @GetMapping("/loan-repayments")
    public AdminAnalyticsService.FiscalYearTrend loanRepayments() {
        return analyticsService.loanRepaymentsTrendForCurrentFiscalYear();
    }

    @GetMapping("/loan-lifecycle")
    public AdminAnalyticsService.FiscalYearBreakdown loanLifecycle() {
        return analyticsService.loanLifecycleBreakdown();
    }

    @GetMapping("/member-status")
    public List<AdminAnalyticsService.StatusCount> memberStatus() {
        return analyticsService.memberStatusBreakdown();
    }
}
