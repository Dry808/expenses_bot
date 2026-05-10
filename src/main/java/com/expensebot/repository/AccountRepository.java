package com.expensebot.repository;


import com.expensebot.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {
    List<Account> findByUserIdOrderByIsDefaultDescCreatedAtAsc(Long userId);
    Optional<Account> findByUserIdAndIsDefaultTrue(Long userId);
    long countByUserId(Long userId);
}
