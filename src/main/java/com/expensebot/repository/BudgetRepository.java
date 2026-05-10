package com.expensebot.repository;

import com.expensebot.model.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {

    @Query("""
        SELECT b FROM Budget b
        LEFT JOIN FETCH b.categories
        WHERE b.user.id = :userId
          AND b.periodYear = :year
          AND b.periodMonth = :month
    """)
    List<Budget> findByUserAndPeriod(
            @Param("userId") Long userId,
            @Param("year") int year,
            @Param("month") int month);

    @Query("""
        SELECT b FROM Budget b
        LEFT JOIN FETCH b.categories
        WHERE b.user.id = :userId
        ORDER BY b.periodYear DESC, b.periodMonth DESC
    """)
    List<Budget> findByUserIdOrderByPeriodDesc(@Param("userId") Long userId);

    Optional<Budget> findByIdAndUserId(Long id, Long userId);
}
