package com.expensebot.controller;

import com.expensebot.model.*;
import com.expensebot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REST API для Telegram Mini App.
 * Все эндпоинты защищены валидацией Telegram initData.
 *
 * Базовый URL: /api/miniapp
 */
@Slf4j
@RestController
@RequestMapping("/api/miniapp")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MiniAppController {

    private final UserService userService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransactionService txService;
    private final BudgetService budgetService;
    private final TelegramAuthService telegramAuthService;

    // ── Dashboard ──────────────────────────────────────────────
    /**
     * GET /api/miniapp/dashboard
     * Возвращает всю сводную информацию для главного экрана.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard(
            @RequestHeader("X-Telegram-Init-Data") String initData) {

        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();

        var now   = LocalDateTime.now();
        var today = LocalDate.now();
        var from  = today.withDayOfMonth(1).atStartOfDay();
        var to    = today.withDayOfMonth(today.lengthOfMonth()).atTime(23, 59, 59);

        var accounts  = accountService.getAccounts(user.getId());
        var income    = txService.sumByType(user.getId(), TransactionType.INCOME, from, to);
        var expense   = txService.sumByType(user.getId(), TransactionType.EXPENSE, from, to);
        var recent    = txService.getRecent(user.getId(), 10);
        var catStats  = txService.categoryStats(user.getId(), TransactionType.EXPENSE, from, to);
        var budgets   = budgetService.getCurrentBudgets(user.getId());

        // Общий баланс по всем счетам
        var totalBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Статус бюджета
        List<BudgetStatusDto> budgetStatuses = budgets.stream().map(b -> {
            var s = budgetService.getStats(b);
            return new BudgetStatusDto(
                    b.getId(), b.getName(),
                    s.total(), s.spent(), s.left(),
                    s.spentPercent(), s.daysLeft(), s.dailyRecommended()
            );
        }).collect(Collectors.toList());

        // Категориальная статистика
        List<CategoryStatDto> catDtos = catStats.stream().map(row -> new CategoryStatDto(
                (String) row[1], (String) row[2], (BigDecimal) row[3]
        )).collect(Collectors.toList());

        var resp = new DashboardResponse(
                totalBalance, income, expense,
                accounts.stream().map(AccountDto::from).collect(Collectors.toList()),
                recent.stream().map(TransactionDto::from).collect(Collectors.toList()),
                catDtos,
                budgetStatuses
        );

        return ResponseEntity.ok(resp);
    }

    // ── Transactions ───────────────────────────────────────────
    /**
     * GET /api/miniapp/transactions?from=2026-05-01&to=2026-05-31
     */
    @GetMapping("/transactions")
    public ResponseEntity<List<TransactionDto>> getTransactions(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @RequestParam(defaultValue = "") String from,
            @RequestParam(defaultValue = "") String to) {

        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();

        LocalDateTime dtFrom = from.isBlank()
                ? LocalDateTime.now().minusMonths(1)
                : LocalDate.parse(from).atStartOfDay();
        LocalDateTime dtTo = to.isBlank()
                ? LocalDateTime.now()
                : LocalDate.parse(to).atTime(23, 59, 59);

        var txs = txService.getBetween(user.getId(), dtFrom, dtTo);
        return ResponseEntity.ok(txs.stream().map(TransactionDto::from).collect(Collectors.toList()));
    }

    /**
     * POST /api/miniapp/transactions
     * Body: { type, amount, categoryId, accountId, note }
     */
    @PostMapping("/transactions")
    public ResponseEntity<TransactionDto> addTransaction(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @RequestBody AddTransactionRequest req) {

        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();

        try {
            if (req.categoryId() == null) {
                log.error("categoryId is null in request: {}", req);
                return ResponseEntity.badRequest().build();
            }
            if (req.accountId() == null) {
                log.error("accountId is null in request: {}", req);
                return ResponseEntity.badRequest().build();
            }

            var category = categoryService.findById(req.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + req.categoryId()));

            var tx = txService.addWithCategory(
                    user.getId(), req.accountId(), category,
                    req.amount(), req.type(), req.note()
            );
            return ResponseEntity.ok(TransactionDto.from(tx));
        } catch (Exception e) {
            log.error("Add transaction error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ── Accounts ───────────────────────────────────────────────
    @GetMapping("/accounts")
    public ResponseEntity<List<AccountDto>> getAccounts(
            @RequestHeader("X-Telegram-Init-Data") String initData) {
        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(
                accountService.getAccounts(user.getId())
                        .stream().map(AccountDto::from).collect(Collectors.toList())
        );
    }

    // ── Create account ─────────────────────────────────────────
    @PostMapping("/accounts")
    public ResponseEntity<AccountDto> createAccount(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @RequestBody AccountRequest req) {
        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();
        try {
            var acc = accountService.create(user.getId(),
                    req.name(), req.emoji() != null ? req.emoji() : "💳",
                    req.currency() != null ? req.currency() : "RUB");
            return ResponseEntity.ok(AccountDto.from(acc));
        } catch (Exception e) {
            log.error("Create account error", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ── Update account ─────────────────────────────────────────
    @Transactional
    @PostMapping("/accounts/{id}/update")
    public ResponseEntity<Void> updateAccount(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @PathVariable Long id,
            @RequestBody AccountRequest req) {
        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();
        try {
            accountService.findById(id).ifPresent(acc -> {
                if (!acc.getUser().getId().equals(user.getId())) return;
                if (req.name() != null) acc.setName(req.name());
                if (req.emoji() != null) acc.setEmoji(req.emoji());
                if (req.currency() != null) acc.setCurrency(req.currency());
            });
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Update account error", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ── Set default account ────────────────────────────────────
    @PostMapping("/accounts/{id}/default")
    public ResponseEntity<Void> setDefaultAccount(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @PathVariable Long id) {
        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();
        try {
            accountService.setDefault(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Set default account error", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ── Delete account ─────────────────────────────────────────
    @PostMapping("/accounts/{id}/delete")
    public ResponseEntity<Void> deleteAccount(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @PathVariable Long id) {
        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();
        try {
            var acc = accountService.findById(id).orElse(null);
            if (acc == null || !acc.getUser().getId().equals(user.getId()))
                return ResponseEntity.status(403).build();
            if (acc.isDefault())
                return ResponseEntity.badRequest().build(); // нельзя удалить основной
            accountService.delete(id);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Delete account error", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ── Categories ─────────────────────────────────────────────
    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @RequestParam(required = false) String type) {
        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();

        List<Category> cats = type != null
                ? categoryService.getByType(user.getId(), TransactionType.valueOf(type.toUpperCase()))
                : categoryService.getAll(user.getId());

        return ResponseEntity.ok(cats.stream().map(CategoryDto::from).collect(Collectors.toList()));
    }

    // ── Analytics ──────────────────────────────────────────────
    /**
     * GET /api/miniapp/analytics?period=month|7d|30d|year
     */
    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(
            @RequestHeader("X-Telegram-Init-Data") String initData,
            @RequestParam(defaultValue = "month") String period) {

        var user  = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();

        var today = LocalDate.now();
        LocalDateTime from, to;

        switch (period) {
            case "7d"   -> { from = LocalDateTime.now().minusDays(7); to = LocalDateTime.now(); }
            case "30d"  -> { from = LocalDateTime.now().minusDays(30); to = LocalDateTime.now(); }
            case "year" -> { from = today.withDayOfYear(1).atStartOfDay(); to = today.withDayOfYear(today.lengthOfYear()).atTime(23,59,59); }
            default     -> { from = today.withDayOfMonth(1).atStartOfDay(); to = today.withDayOfMonth(today.lengthOfMonth()).atTime(23,59,59); }
        }

        var income    = txService.sumByType(user.getId(), TransactionType.INCOME, from, to);
        var expense   = txService.sumByType(user.getId(), TransactionType.EXPENSE, from, to);
        var catExp    = txService.categoryStats(user.getId(), TransactionType.EXPENSE, from, to);
        var catInc    = txService.categoryStats(user.getId(), TransactionType.INCOME, from, to);
        var weekly    = txService.weeklyStats(user.getId(), LocalDateTime.now().minusWeeks(8), LocalDateTime.now());
        var monthly   = txService.monthlyStats(user.getId(), today.getYear());

        List<CategoryStatDto> expCats = catExp.stream().map(r ->
                new CategoryStatDto((String)r[1], (String)r[2], (BigDecimal)r[3])).toList();
        List<CategoryStatDto> incCats = catInc.stream().map(r ->
                new CategoryStatDto((String)r[1], (String)r[2], (BigDecimal)r[3])).toList();

        List<WeeklyStatDto> weeklyStat = weekly.stream().map(r -> new WeeklyStatDto(
                ((java.sql.Timestamp) r[0]).toLocalDateTime().toString().substring(0, 10),
                (BigDecimal) r[1], (BigDecimal) r[2]
        )).toList();

        List<MonthlyStatDto> monthlyStat = monthly.stream().map(r -> new MonthlyStatDto(
                ((Number) r[0]).intValue(), (BigDecimal) r[1], (BigDecimal) r[2]
        )).toList();

        return ResponseEntity.ok(new AnalyticsResponse(
                income, expense, income.subtract(expense),
                expCats, incCats, weeklyStat, monthlyStat
        ));
    }

    // ── Budget ─────────────────────────────────────────────────
    @GetMapping("/budgets")
    public ResponseEntity<List<BudgetStatusDto>> getBudgets(
            @RequestHeader("X-Telegram-Init-Data") String initData) {
        var user = authenticate(initData);
        if (user == null) return ResponseEntity.status(401).build();

        var budgets = budgetService.getCurrentBudgets(user.getId());
        return ResponseEntity.ok(budgets.stream().map(b -> {
            var s = budgetService.getStats(b);
            return new BudgetStatusDto(b.getId(), b.getName(),
                    s.total(), s.spent(), s.left(),
                    s.spentPercent(), s.daysLeft(), s.dailyRecommended());
        }).collect(Collectors.toList()));
    }

    // ── Auth helper ────────────────────────────────────────────
    private BotUser authenticate(String initData) {
        Long telegramId = telegramAuthService.validateAndGetUserId(initData);
        if (telegramId == null) return null;
        return userService.getByTelegramId(telegramId);
    }

    // ── DTOs ───────────────────────────────────────────────────
    public record DashboardResponse(
            BigDecimal totalBalance, BigDecimal monthIncome, BigDecimal monthExpense,
            List<AccountDto> accounts, List<TransactionDto> recentTransactions,
            List<CategoryStatDto> expensesByCategory, List<BudgetStatusDto> budgets) {}

    public record AccountDto(Long id, String name, String emoji, String currency, BigDecimal balance, boolean isDefault) {
        static AccountDto from(Account a) {
            return new AccountDto(a.getId(), a.getName(), a.getEmoji(), a.getCurrency(), a.getBalance(), a.isDefault());
        }
    }

    public record TransactionDto(Long id, String type, BigDecimal amount, String categoryName,
                                 String categoryEmoji, Long categoryId, Long accountId,
                                 String accountName, String note, String date) {
        static TransactionDto from(Transaction t) {
            String dateStr = t.getTransactionDate() != null
                    ? t.getTransactionDate().toString().substring(0, 10)
                    : "";
            return new TransactionDto(
                    t.getId(), t.getType().name().toLowerCase(),
                    t.getAmount(),
                    t.getCategory() != null ? t.getCategory().getName() : null,
                    t.getCategory() != null ? t.getCategory().getEmoji() : null,
                    t.getCategory() != null ? t.getCategory().getId() : null,
                    t.getAccount().getId(), t.getAccount().getName(),
                    t.getNote(), dateStr
            );
        }
    }

    public record CategoryDto(Long id, String name, String emoji, String type) {
        static CategoryDto from(Category c) {
            return new CategoryDto(c.getId(), c.getName(), c.getEmoji(), c.getType().name().toLowerCase());
        }
    }

    public record CategoryStatDto(String name, String emoji, BigDecimal total) {}
    public record WeeklyStatDto(String week, BigDecimal income, BigDecimal expense) {}
    public record MonthlyStatDto(int month, BigDecimal income, BigDecimal expense) {}
    public record BudgetStatusDto(Long id, String name, BigDecimal total, BigDecimal spent,
                                  BigDecimal left, double spentPercent, int daysLeft, BigDecimal dailyRecommended) {}

    public record AddTransactionRequest(TransactionType type, BigDecimal amount,
                                        Long categoryId, Long accountId, String note) {}

    public record AnalyticsResponse(BigDecimal income, BigDecimal expense, BigDecimal balance,
                                    List<CategoryStatDto> expenseCategories, List<CategoryStatDto> incomeCategories,
                                    List<WeeklyStatDto> weekly, List<MonthlyStatDto> monthly) {}

    public record AccountRequest(String name, String emoji, String currency) {}
}
