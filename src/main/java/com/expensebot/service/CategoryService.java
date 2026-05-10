package com.expensebot.service;

import com.expensebot.model.*;
import com.expensebot.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepo;
    private final BotUserRepository userRepo;

    public List<Category> getAll(Long userId) {
        return categoryRepo.findByUserIdOrderByNameAsc(userId);
    }

    public List<Category> getByType(Long userId, TransactionType type) {
        return categoryRepo.findByUserIdAndTypeOrderByNameAsc(userId, type);
    }

    @Transactional
    public Category create(Long userId, String name, String emoji, TransactionType type) {
        var user = userRepo.findById(userId).orElseThrow();
        return categoryRepo.save(Category.builder()
                .user(user).name(name).emoji(emoji).type(type).build());
    }

    public Optional<Category> findById(Long id) {
        return categoryRepo.findById(id);
    }

    @Transactional
    public void delete(Long id) {
        categoryRepo.deleteById(id);
    }
}
