package com.expensebot.util;


import com.expensebot.model.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import java.util.*;
import java.util.List;

public final class KeyboardFactory {

    private KeyboardFactory() {}

    // ── Главное меню ───────────────────────────────────────────
    public static ReplyKeyboardMarkup mainMenu() {
        return ReplyKeyboardMarkup.builder()
                .keyboard(List.of(
                        row("➕ Расход", "➕ Доход"),
                        row("💼 Счета", "📂 Категории"),
                        row("📊 Аналитика", "📅 Бюджет"),
                        row("🕐 История")
                ))
                .resizeKeyboard(true)
                .build();
    }

    // ── Список категорий ───────────────────────────────────────
    public static InlineKeyboardMarkup categories(List<Category> cats, String prefix) {
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        var row  = new ArrayList<InlineKeyboardButton>();
        for (int i = 0; i < cats.size(); i++) {
            var c = cats.get(i);
            row.add(btn(c.getDisplayName(), prefix + c.getId()));
            if (row.size() == 2 || i == cats.size() - 1) {
                rows.add(new ArrayList<>(row));
                row.clear();
            }
        }
        rows.add(List.of(
                btn("➕ Новая категория", prefix + "new"),
                btn("🔙 Назад", "main_menu")
        ));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ── Список счетов ──────────────────────────────────────────
    public static InlineKeyboardMarkup accounts(List<Account> accounts, String prefix) {
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        for (var a : accounts) {
            String star = a.isDefault() ? " ⭐" : "";
            rows.add(List.of(btn(a.getEmoji() + " " + a.getName() + star, prefix + a.getId())));
        }
        rows.add(List.of(btn("🔙 Назад", "main_menu")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ── Управление счётом ──────────────────────────────────────
    public static InlineKeyboardMarkup accountActions(Long accountId) {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                List.of(btn("⭐ Сделать основным", "acc_default_" + accountId)),
                List.of(btn("🗑 Удалить счёт",     "acc_delete_" + accountId)),
                List.of(btn("🔙 Назад",            "acc_list"))
        )).build();
    }

    // ── Подтверждение транзакции ───────────────────────────────
    public static InlineKeyboardMarkup confirmTx() {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                List.of(btn("✅ Добавить", "tx_confirm"), btn("❌ Отмена", "main_menu"))
        )).build();
    }

    // ── Аналитика ──────────────────────────────────────────────
    public static InlineKeyboardMarkup analyticsMenu() {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                List.of(btn("📅 Сегодня",         "stats_today"),
                        btn("📆 7 дней",          "stats_7d")),
                List.of(btn("🗓 30 дней",         "stats_30d"),
                        btn("📊 Этот месяц",      "stats_month")),
                List.of(btn("📈 По неделям",      "stats_weekly"),
                        btn("📉 По месяцам",      "stats_yearly")),
                List.of(btn("🏷 По категориям ↓", "stats_cat_exp"),
                        btn("🏷 По категориям ↑", "stats_cat_inc")),
                List.of(btn("🔙 Назад", "main_menu"))
        )).build();
    }

    // ── Бюджет ─────────────────────────────────────────────────
    public static InlineKeyboardMarkup budgetMenu(boolean hasBudget) {
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        rows.add(List.of(btn("➕ Создать бюджет", "budget_create")));
        if (hasBudget) {
            rows.add(List.of(btn("📊 Статус бюджета",  "budget_status")));
            rows.add(List.of(btn("🗑 Удалить бюджет",  "budget_delete")));
        }
        rows.add(List.of(btn("🔙 Назад", "main_menu")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ── Выбор типа категории ───────────────────────────────────
    public static InlineKeyboardMarkup categoryType() {
        return InlineKeyboardMarkup.builder().keyboard(List.of(
                List.of(btn("➕ Доход", "cat_type_INCOME"), btn("➖ Расход", "cat_type_EXPENSE"))
        )).build();
    }

    // ── Выбор охвата бюджета ───────────────────────────────────
    public static InlineKeyboardMarkup budgetScope(List<Category> expCats) {
        var rows = new ArrayList<List<InlineKeyboardButton>>();
        rows.add(List.of(btn("📋 Все расходы", "budget_scope_all")));
        var row = new ArrayList<InlineKeyboardButton>();
        for (int i = 0; i < expCats.size(); i++) {
            var c = expCats.get(i);
            row.add(btn(c.getDisplayName(), "budget_scope_cat_" + c.getId()));
            if (row.size() == 2 || i == expCats.size() - 1) {
                rows.add(new ArrayList<>(row));
                row.clear();
            }
        }
        rows.add(List.of(btn("✅ Готово (все выбранные)", "budget_scope_done")));
        return InlineKeyboardMarkup.builder().keyboard(rows).build();
    }

    // ── Вспомогательные ───────────────────────────────────────
    private static KeyboardRow row(String... labels) {
        var r = new KeyboardRow();
        Arrays.stream(labels).forEach(r::add);
        return r;
    }

    private static InlineKeyboardButton btn(String text, String data) {
        return InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
