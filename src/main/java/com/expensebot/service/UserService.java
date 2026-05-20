package com.expensebot.service;

import com.expensebot.model.*;
import com.expensebot.repository.AccountRepository;
import com.expensebot.repository.BotUserRepository;
import com.expensebot.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.User;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final BotUserRepository userRepo;
    private final AccountRepository accountRepo;
    private final CategoryRepository categoryRepo;

    @Transactional
    public BotUser getOrCreate(User tgUser) {
        return userRepo.findByTelegramId(tgUser.getId()).orElseGet(() -> {
            var user = BotUser.builder()
                    .telegramId(tgUser.getId())
                    .username(tgUser.getUserName())
                    .firstName(tgUser.getFirstName())
                    .state(UserState.IDLE)
                    .build();
            userRepo.save(user);
            createDefaults(user);
            return user;
        });
    }

    private void createDefaults(BotUser user) {
        // Явно задаём balance=ZERO — Lombok Builder не подставляет дефолты из поля
        var account = Account.builder()
                .user(user).name("Основной").emoji("💰")
                .currency("RUB").balance(BigDecimal.ZERO).isDefault(true).build();
        accountRepo.save(account);

        // Системные категории расходов
        List.of(
                new String[]{"🛒", "Продукты"},
                new String[]{"🚗", "Транспорт"},
                new String[]{"🏠", "Жильё"},
                new String[]{"💊", "Здоровье"},
                new String[]{"👗", "Одежда"},
                new String[]{"🎮", "Развлечения"},
                new String[]{"📱", "Связь"},
                new String[]{"🍕", "Кафе и рестораны"},
                new String[]{"📚", "Образование"},
                new String[]{"✈️", "Путешествия"}
        ).forEach(c -> categoryRepo.save(Category.builder()
                .user(user).emoji(c[0]).name(c[1])
                .type(TransactionType.EXPENSE).isSystem(true).build()));

        // Системные категории доходов
        List.of(
                new String[]{"💼", "Зарплата"},
                new String[]{"💸", "Фриланс"},
                new String[]{"🎁", "Подарок"},
                new String[]{"📈", "Инвестиции"},
                new String[]{"🏦", "Прочий доход"}
        ).forEach(c -> categoryRepo.save(Category.builder()
                .user(user).emoji(c[0]).name(c[1])
                .type(TransactionType.INCOME).isSystem(true).build()));
    }

    @Transactional
    public void setState(Long telegramId, UserState state, String data) {
        userRepo.findByTelegramId(telegramId).ifPresent(u -> {
            u.setState(state);
            u.setStateData(data);
        });
    }

    @Transactional
    public void resetState(Long telegramId) {
        setState(telegramId, UserState.IDLE, null);
    }

    public BotUser getByTelegramId(Long telegramId) {
        return userRepo.findByTelegramId(telegramId).orElseThrow();
    }
}
