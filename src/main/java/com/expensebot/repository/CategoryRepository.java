package com.expensebot.repository;

import com.expensebot.model.Category;
import com.expensebot.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByUserIdOrderByNameAsc(Long userId);
    List<Category> findByUserIdAndTypeOrderByNameAsc(Long userId, TransactionType type);
}
