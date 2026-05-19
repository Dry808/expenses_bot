package com.expensebot.repository;

import com.expensebot.model.Transaction;
import com.expensebot.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
        SELECT t FROM Transaction t
        LEFT JOIN FETCH t.category
        LEFT JOIN FETCH t.account
        WHERE t.user.id = :userId
          AND t.transactionDate BETWEEN :from AND :to
        ORDER BY t.transactionDate DESC
    """)
    List<Transaction> findByUserIdAndTransactionDateBetweenOrderByTransactionDateDesc(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = :type
          AND t.transactionDate >= :from
          AND t.transactionDate <= :to
    """)
    BigDecimal sumByUserIdAndTypeAndDateBetween(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = :type
          AND t.category.id IN :categoryIds
          AND t.transactionDate >= :from
          AND t.transactionDate <= :to
    """)
    BigDecimal sumByUserIdAndTypeAndCategoryIdsAndDateBetween(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("categoryIds") List<Long> categoryIds,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
        SELECT t.category.id, t.category.name, t.category.emoji,
               COALESCE(SUM(t.amount), 0) as total
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.type = :type
          AND t.transactionDate >= :from
          AND t.transactionDate <= :to
        GROUP BY t.category.id, t.category.name, t.category.emoji
        ORDER BY total DESC
    """)
    List<Object[]> sumByCategoryAndDateBetween(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
        SELECT DATE_TRUNC('week', t.transactionDate) as week,
               COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND t.transactionDate >= :from
          AND t.transactionDate <= :to
        GROUP BY DATE_TRUNC('week', t.transactionDate)
        ORDER BY week ASC
    """)
    List<Object[]> weeklyStatsBetween(
            @Param("userId") Long userId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    @Query("""
        SELECT EXTRACT(MONTH FROM t.transactionDate) as month,
               COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0),
               COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0)
        FROM Transaction t
        WHERE t.user.id = :userId
          AND EXTRACT(YEAR FROM t.transactionDate) = :year
        GROUP BY EXTRACT(MONTH FROM t.transactionDate)
        ORDER BY month ASC
    """)
    List<Object[]> monthlyStatsByYear(
            @Param("userId") Long userId,
            @Param("year") int year);

    List<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId);

    @Query("""
        SELECT t FROM Transaction t
        LEFT JOIN FETCH t.category
        LEFT JOIN FETCH t.account
        WHERE t.user.id = :userId
        ORDER BY t.transactionDate DESC
        LIMIT :limit
    """)
    List<Transaction> findTopByUserId(
            @Param("userId") Long userId,
            @Param("limit") int limit);
}
