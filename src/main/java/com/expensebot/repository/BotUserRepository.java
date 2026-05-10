package com.expensebot.repository;

import com.expensebot.model.BotUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface BotUserRepository extends JpaRepository<BotUser, Long> {
    Optional<BotUser> findByTelegramId(Long telegramId);
}
