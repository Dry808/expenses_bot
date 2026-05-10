package com.expensebot.service;

import com.expensebot.model.*;
import com.expensebot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository txRepo;
    private final AccountService accountService;
    private final BotUserRepository userRepo;

    @Transactional
    public Transaction add(Long userId, Long accountId, Long categoryId,
                           BigDecimal amount, TransactionType type, String note) {
        var user = userRepo.findById(userId).orElseThrow();
        var account = accountService.findById(accountId).orElseThrow();
        var categoryRepo = (CategoryRepository) null; // injected via field below
        // adjustBalance
        accountService.adjustBalance(accountId,
                type == TransactionType.INCOME ? amount : amount.negate());

        var tx = Transaction.builder()
                .user(user).account(account).amount(amount)
                .type(type).note(note)
                .transactionDate(LocalDateTime.now()).build();
        return txRepo.save(tx);
    }

    @Transactional
    public Transaction addWithCategory(Long userId, Long accountId, Category category,
                                       BigDecimal amount, TransactionType type, String note) {
        var user = userRepo.findById(userId).orElseThrow();
        var account = accountService.findById(accountId).orElseThrow();
        accountService.adjustBalance(accountId,
                type == TransactionType.INCOME ? amount : amount.negate());
        var tx = Transaction.builder()
                .user(user).account(account).category(category)
                .amount(amount).type(type).note(note)
                .transactionDate(LocalDateTime.now()).build();
        return txRepo.save(tx);
    }

    public List<Transaction> getRecent(Long userId, int limit) {
        return txRepo.findTopByUserId(userId, limit);
    }

    public List<Transaction> getBetween(Long userId, LocalDateTime from, LocalDateTime to) {
        return txRepo.findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(userId, from, to);
    }

    public BigDecimal sumByType(Long userId, TransactionType type,
                                LocalDateTime from, LocalDateTime to) {
        return txRepo.sumByUserIdAndTypeAndDateBetween(userId, type, from, to);
    }

    public BigDecimal sumByTypeAndCategories(Long userId, TransactionType type,
                                             List<Long> categoryIds,
                                             LocalDateTime from, LocalDateTime to) {
        return txRepo.sumByUserIdAndTypeAndCategoryIdsAndDateBetween(userId, type, categoryIds, from, to);
    }

    public List<Object[]> categoryStats(Long userId, TransactionType type,
                                        LocalDateTime from, LocalDateTime to) {
        return txRepo.sumByCategoryAndDateBetween(userId, type, from, to);
    }

    public List<Object[]> weeklyStats(Long userId, LocalDateTime from, LocalDateTime to) {
        return txRepo.weeklyStatsBetween(userId, from, to);
    }

    public List<Object[]> monthlyStats(Long userId, int year) {
        return txRepo.monthlyStatsByYear(userId, year);
    }
}
