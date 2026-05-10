package com.expensebot.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name = "bot_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class BotUser {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_id", unique = true, nullable = false)
    private Long telegramId;

    private String username;

    @Column(name = "first_name")
    private String firstName;

    @Enumerated(EnumType.STRING)
    @Column(length = 100)
    private UserState state = UserState.IDLE;

    @Column(name = "state_data", columnDefinition = "TEXT")
    private String stateData;          // JSON для хранения временных данных

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
