package com.expensebot.service;

import com.expensebot.model.*;
import com.expensebot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepo;
    private final BotUserRepository userRepo;

    public List<Account> getAccounts(Long userId) {
        return accountRepo.findByUserIdOrderByIsDefaultDescCreatedAtAsc(userId);
    }

    @Transactional
    public Account create(Long userId, String name, String emoji, String currency) {
        var user = userRepo.findById(userId).orElseThrow();
        boolean isFirst = accountRepo.countByUserId(userId) == 0;
        return accountRepo.save(Account.builder()
                .user(user).name(name).emoji(emoji)
                .currency(currency).isDefault(isFirst).build());
    }

    @Transactional
    public void adjustBalance(Long accountId, BigDecimal delta) {
        accountRepo.findById(accountId).ifPresent(a -> {
            a.setBalance(a.getBalance().add(delta));
        });
    }

    public Optional<Account> getDefault(Long userId) {
        return accountRepo.findByUserIdAndIsDefaultTrue(userId);
    }

    public Optional<Account> findById(Long id) {
        return accountRepo.findById(id);
    }

    @Transactional
    public void setDefault(Long accountId, Long userId) {
        accountRepo.findByUserIdAndIsDefaultTrue(userId)
                .ifPresent(a -> a.setDefault(false));
        accountRepo.findById(accountId).ifPresent(a -> a.setDefault(true));
    }

    @Transactional
    public void delete(Long accountId) {
        accountRepo.deleteById(accountId);
    }
}