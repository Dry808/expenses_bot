package com.expensebot.service;

import com.expensebot.model.*;
import com.expensebot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private final BudgetRepository budgetRepo;
    private final BotUserRepository userRepo;
    private final TransactionService txService;

    @Transactional
    public Budget create(Long userId, String name, BigDecimal amount,
                         int year, int month, boolean allCategories, Set<Category> categories) {
        var user = userRepo.findById(userId).orElseThrow();
        var budget = Budget.builder()
                .user(user).name(name).amount(amount)
                .periodYear(year).periodMonth(month)
                .allCategories(allCategories).categories(categories).build();
        return budgetRepo.save(budget);
    }

    public List<Budget> getCurrentBudgets(Long userId) {
        var now = LocalDate.now();
        return budgetRepo.findByUserAndPeriod(userId, now.getYear(), now.getMonthValue());
    }

    public List<Budget> getAllBudgets(Long userId) {
        return budgetRepo.findByUserIdOrderByPeriodDesc(userId);
    }

    public BudgetStats getStats(Budget budget) {
        var ym = YearMonth.of(budget.getPeriodYear(), budget.getPeriodMonth());
        var from = ym.atDay(1).atStartOfDay();
        var to   = ym.atEndOfMonth().atTime(23, 59, 59);

        BigDecimal spent;
        if (budget.isAllCategories()) {
            spent = txService.sumByType(budget.getUser().getId(),
                    TransactionType.EXPENSE, from, to);
        } else {
            var ids = budget.getCategories().stream()
                    .map(Category::getId).collect(Collectors.toList());
            spent = ids.isEmpty() ? BigDecimal.ZERO :
                    txService.sumByTypeAndCategories(budget.getUser().getId(),
                            TransactionType.EXPENSE, ids, from, to);
        }

        BigDecimal total   = budget.getAmount();
        BigDecimal left    = total.subtract(spent).max(BigDecimal.ZERO);
        BigDecimal over    = spent.subtract(total).max(BigDecimal.ZERO);

        // Дней в месяце и оставшихся дней
        int daysInMonth    = ym.lengthOfMonth();
        LocalDate today    = LocalDate.now();
        int daysLeft       = (ym.getYear() == today.getYear()
                && ym.getMonthValue() == today.getMonthValue())
                ? Math.max(1, ym.atEndOfMonth().getDayOfMonth() - today.getDayOfMonth() + 1)
                : 1;

        BigDecimal dailyRecommended = left.compareTo(BigDecimal.ZERO) > 0
                ? left.divide(BigDecimal.valueOf(daysLeft), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        double pct = total.compareTo(BigDecimal.ZERO) > 0
                ? spent.divide(total, 4, RoundingMode.HALF_UP).doubleValue() * 100 : 0;

        return new BudgetStats(total, spent, left, over, dailyRecommended,
                daysInMonth, daysLeft, pct);
    }

    public record BudgetStats(
            BigDecimal total, BigDecimal spent, BigDecimal left, BigDecimal over,
            BigDecimal dailyRecommended, int daysInMonth, int daysLeft, double spentPercent) {}

    @Transactional
    public void delete(Long id) { budgetRepo.deleteById(id); }

    public Optional<Budget> findById(Long id, Long userId) {
        return budgetRepo.findByIdAndUserId(id, userId);
    }
}
