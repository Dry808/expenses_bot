package com.expensebot.bot.handler;


import com.expensebot.model.*;
import com.expensebot.service.*;
import com.expensebot.util.BotUtil;
import com.expensebot.util.KeyboardFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class MessageHandler {

    private final UserService userService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransactionService txService;
    private final BudgetService budgetService;
    private final ObjectMapper objectMapper;

    public SendMessage handle(Message msg) {
        long chatId = msg.getChatId();
        String text = msg.getText().trim();
        var tgUser  = msg.getFrom();

        var user = userService.getOrCreate(tgUser);

        // ── Команды ────────────────────────────────────────────
        if (text.startsWith("/start")) return handleStart(user, chatId);
        if (text.startsWith("/help"))  return handleHelp(chatId);

        // ── Кнопки главного меню ───────────────────────────────
        return switch (text) {
            case "➕ Расход"     -> startAddTx(user, chatId, TransactionType.EXPENSE);
            case "➕ Доход"      -> startAddTx(user, chatId, TransactionType.INCOME);
            case "💼 Счета"      -> showAccounts(user, chatId);
            case "📂 Категории"  -> showCategories(user, chatId);
            case "📊 Аналитика"  -> showAnalytics(chatId);
            case "📅 Бюджет"     -> showBudget(user, chatId);
            case "🕐 История"    -> showHistory(user, chatId);
            default              -> handleState(user, chatId, text);
        };
    }

    // ── /start ────────────────────────────────────────────────
    private SendMessage handleStart(BotUser user, long chatId) {
        userService.resetState(user.getTelegramId());
        String welcome = """
            👋 Привет, <b>%s</b>!
            
            Я твой персональный финансовый помощник.
            Веду учёт доходов и расходов, помогаю планировать бюджет.
            
            Используй кнопки ниже 👇
            """.formatted(user.getFirstName());
        return BotUtil.msg(chatId, welcome, KeyboardFactory.mainMenu());
    }

    private SendMessage handleHelp(long chatId) {
        String help = """
            <b>📖 Справка</b>
            
            <b>➕ Расход / Доход</b> — добавить операцию
            <b>💼 Счета</b> — управление счетами
            <b>📂 Категории</b> — управление категориями
            <b>📊 Аналитика</b> — статистика по периодам
            <b>📅 Бюджет</b> — планирование бюджета
            <b>🕐 История</b> — последние операции
            """;
        return BotUtil.msg(chatId, help, KeyboardFactory.mainMenu());
    }

    // ── Добавить транзакцию ───────────────────────────────────
    private SendMessage startAddTx(BotUser user, long chatId, TransactionType type) {
        var cats = categoryService.getByType(user.getId(), type);
        if (cats.isEmpty()) {
            return BotUtil.msg(chatId, "У вас нет категорий типа " + type.name()
                    + ". Сначала создайте категорию через 📂 Категории.");
        }
        String typeStr = type == TransactionType.EXPENSE ? "расход" : "доход";
        saveState(user, UserState.AWAIT_TX_AMOUNT,
                Map.of("type", type.name(), "step", "amount"));
        return BotUtil.msg(chatId,
                "💰 Введите сумму " + typeStr + "а:");
    }

    // ── Счета ─────────────────────────────────────────────────
    private SendMessage showAccounts(BotUser user, long chatId) {
        var accounts = accountService.getAccounts(user.getId());
        var sb = new StringBuilder("<b>💼 Ваши счета</b>\n\n");
        for (var a : accounts) {
            sb.append(a.getEmoji()).append(" <b>").append(a.getName()).append("</b>");
            if (a.isDefault()) sb.append(" ⭐");
            sb.append(" — ").append(BotUtil.fmt(a.getBalance()))
                    .append(" ").append(a.getCurrency()).append("\n");
        }
        sb.append("\nВыберите счёт для управления или создайте новый:");
        var kb = KeyboardFactory.accounts(accounts, "acc_view_");
        // добавляем кнопку создания счёта
        var rows = new ArrayList<>(kb.getKeyboard());
        rows.add(0, List.of(
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons
                        .InlineKeyboardButton.builder()
                        .text("➕ Новый счёт").callbackData("acc_create").build()
        ));
        var newKb = org.telegram.telegrambots.meta.api.objects.replykeyboard
                .InlineKeyboardMarkup.builder().keyboard(rows).build();
        return BotUtil.msg(chatId, sb.toString(), newKb);
    }

    // ── Категории ─────────────────────────────────────────────
    private SendMessage showCategories(BotUser user, long chatId) {
        var cats = categoryService.getAll(user.getId());
        var sb = new StringBuilder("<b>📂 Категории</b>\n\n");
        sb.append("<b>Расходы:</b>\n");
        cats.stream().filter(c -> c.getType() == TransactionType.EXPENSE)
                .forEach(c -> sb.append("  ").append(c.getDisplayName()).append("\n"));
        sb.append("\n<b>Доходы:</b>\n");
        cats.stream().filter(c -> c.getType() == TransactionType.INCOME)
                .forEach(c -> sb.append("  ").append(c.getDisplayName()).append("\n"));
        sb.append("\nВыберите действие:");
        return BotUtil.msg(chatId, sb.toString(),
                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder()
                        .keyboard(List.of(
                                List.of(btn("➕ Создать категорию", "cat_create")),
                                List.of(btn("🗑 Удалить категорию", "cat_delete_list")),
                                List.of(btn("🔙 Главное меню", "main_menu"))
                        )).build()
        );
    }

    // ── Аналитика ─────────────────────────────────────────────
    private SendMessage showAnalytics(long chatId) {
        return BotUtil.msg(chatId, "<b>📊 Аналитика</b>\nВыберите период:",
                KeyboardFactory.analyticsMenu());
    }

    // ── Бюджет ────────────────────────────────────────────────
    private SendMessage showBudget(BotUser user, long chatId) {
        var budgets = budgetService.getCurrentBudgets(user.getId());
        var sb = new StringBuilder("<b>📅 Бюджет</b>\n\n");
        if (budgets.isEmpty()) {
            sb.append("На этот месяц бюджет не задан.\n");
        } else {
            for (var b : budgets) {
                var s = budgetService.getStats(b);
                sb.append("📌 <b>").append(b.getName() != null ? b.getName() : "Бюджет").append("</b>\n")
                        .append("Лимит: ").append(BotUtil.fmt(s.total())).append(" ₽\n")
                        .append("Потрачено: ").append(BotUtil.fmt(s.spent())).append(" ₽\n")
                        .append(BotUtil.progressBar(s.spentPercent())).append("\n\n");
            }
        }
        return BotUtil.msg(chatId, sb.toString(),
                KeyboardFactory.budgetMenu(!budgets.isEmpty()));
    }

    // ── История ───────────────────────────────────────────────
    private SendMessage showHistory(BotUser user, long chatId) {
        var txs = txService.getRecent(user.getId(), 15);
        if (txs.isEmpty()) {
            return BotUtil.msg(chatId, "История пуста. Добавьте первую операцию!");
        }
        var sb = new StringBuilder("<b>🕐 Последние операции</b>\n\n");
        for (var tx : txs) {
            String sign  = tx.getType() == TransactionType.INCOME ? "+" : "-";
            String emoji = tx.getType() == TransactionType.INCOME ? "🟢" : "🔴";
            String cat   = tx.getCategory() != null ? tx.getCategory().getDisplayName() : "—";
            sb.append(emoji).append(" <b>").append(sign)
                    .append(BotUtil.fmt(tx.getAmount())).append(" ₽</b>")
                    .append("  ").append(cat);
            if (tx.getNote() != null && !tx.getNote().isBlank())
                sb.append("\n    📝 ").append(tx.getNote());
            sb.append("\n    📅 ").append(tx.getTransactionDate()
                            .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm")))
                    .append("\n\n");
        }
        return BotUtil.msg(chatId, sb.toString(), KeyboardFactory.mainMenu());
    }

    // ── Обработка состояний (пошаговый ввод) ──────────────────
    @SuppressWarnings("unchecked")
    private SendMessage handleState(BotUser user, long chatId, String text) {
        return switch (user.getState()) {

            // ── Сумма транзакции ───────────────────────────────
            case AWAIT_TX_AMOUNT -> {
                BigDecimal amount;
                try { amount = new BigDecimal(text.replace(",", ".")); }
                catch (NumberFormatException e) {
                    yield BotUtil.msg(chatId, "❌ Введите корректную сумму, например: <b>1500</b> или <b>249.90</b>");
                }
                if (amount.compareTo(BigDecimal.ZERO) <= 0)
                    yield BotUtil.msg(chatId, "❌ Сумма должна быть больше нуля.");

                var data = readState(user);
                data.put("amount", amount.toPlainString());
                data.put("step", "category");

                var type = TransactionType.valueOf((String) data.get("type"));
                var cats = categoryService.getByType(user.getId(), type);
                saveState(user, UserState.AWAIT_TX_CATEGORY, data);
                yield BotUtil.msg(chatId, "📂 Выберите категорию:",
                        KeyboardFactory.categories(cats, "tx_cat_"));
            }

            // ── Заметка транзакции ────────────────────────────
            case AWAIT_TX_NOTE -> {
                var data = readState(user);
                data.put("note", text);
                var preview = buildTxPreview(user, data);
                saveState(user, UserState.AWAIT_TX_NOTE, data); // держим данные до подтверждения
                userService.setState(user.getTelegramId(), UserState.IDLE, toJson(data));
                yield BotUtil.msg(chatId, preview + "\n\nПодтвердить?",
                        KeyboardFactory.confirmTx());
            }

            // ── Имя нового счёта ──────────────────────────────
            case AWAIT_ACCOUNT_NAME -> {
                var data = readState(user);
                data.put("name", text);
                saveState(user, UserState.AWAIT_ACCOUNT_EMOJI, data);
                yield BotUtil.msg(chatId, "Введите эмодзи для счёта (например: 💳 🏦 💵):");
            }

            case AWAIT_ACCOUNT_EMOJI -> {
                var data = readState(user);
                data.put("emoji", text);
                saveState(user, UserState.AWAIT_ACCOUNT_CURRENCY, data);
                yield BotUtil.msg(chatId, "Введите валюту (например: <b>RUB</b>, <b>USD</b>, <b>EUR</b>):");
            }

            case AWAIT_ACCOUNT_CURRENCY -> {
                var data  = readState(user);
                String name = (String) data.get("name");
                String emoji = (String) data.get("emoji");
                accountService.create(user.getId(), name, emoji, text.toUpperCase());
                userService.resetState(user.getTelegramId());
                yield BotUtil.msg(chatId,
                        "✅ Счёт <b>" + emoji + " " + name + "</b> создан!",
                        KeyboardFactory.mainMenu());
            }

            // ── Создание категории ────────────────────────────
            case AWAIT_CATEGORY_NAME -> {
                var data = readState(user);
                data.put("name", text);
                saveState(user, UserState.AWAIT_CATEGORY_EMOJI, data);
                yield BotUtil.msg(chatId, "Введите эмодзи для категории:");
            }

            case AWAIT_CATEGORY_EMOJI -> {
                var data = readState(user);
                data.put("emoji", text);
                saveState(user, UserState.AWAIT_CATEGORY_TYPE, data);
                yield BotUtil.msg(chatId, "Выберите тип категории:",
                        KeyboardFactory.categoryType());
            }

            // ── Создание бюджета ──────────────────────────────
            case AWAIT_BUDGET_NAME -> {
                var data = readState(user);
                data.put("budgetName", text);
                saveState(user, UserState.AWAIT_BUDGET_AMOUNT, data);
                yield BotUtil.msg(chatId, "💰 Введите лимит бюджета на месяц (в рублях):");
            }

            case AWAIT_BUDGET_AMOUNT -> {
                BigDecimal amount;
                try { amount = new BigDecimal(text.replace(",", ".")); }
                catch (NumberFormatException e) {
                    yield BotUtil.msg(chatId, "❌ Введите корректную сумму.");
                }
                var data = readState(user);
                data.put("budgetAmount", amount.toPlainString());
                saveState(user, UserState.AWAIT_BUDGET_CATEGORIES, data);
                var expCats = categoryService.getByType(user.getId(), TransactionType.EXPENSE);
                yield BotUtil.msg(chatId,
                        "📂 Выберите категории для бюджета или нажмите <b>«Все расходы»</b>:",
                        KeyboardFactory.budgetScope(expCats));
            }

            default -> BotUtil.msg(chatId,
                    "Используйте кнопки меню 👇", KeyboardFactory.mainMenu());
        };
    }

    // ── Вспомогательные ───────────────────────────────────────
    private String buildTxPreview(BotUser user, Map<String, Object> data) {
        var type   = TransactionType.valueOf((String) data.get("type"));
        var amount = new BigDecimal((String) data.get("amount"));
        String catId  = (String) data.get("categoryId");
        String note   = (String) data.getOrDefault("note", "");
        String catName = "—";
        if (catId != null) {
            catName = categoryService.findById(Long.parseLong(catId))
                    .map(Category::getDisplayName).orElse("—");
        }
        String sign = type == TransactionType.EXPENSE ? "➖" : "➕";
        return "<b>%s %s ₽</b>\n📂 %s\n📝 %s"
                .formatted(sign, BotUtil.fmt(amount), catName, note.isBlank() ? "—" : note);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readState(BotUser user) {
        try {
            if (user.getStateData() == null) return new HashMap<>();
            return objectMapper.readValue(user.getStateData(), HashMap.class);
        } catch (Exception e) { return new HashMap<>(); }
    }

    private void saveState(BotUser user, UserState state, Map<String, Object> data) {
        userService.setState(user.getTelegramId(), state, toJson(data));
        user.setState(state);
        user.setStateData(toJson(data));
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); }
        catch (Exception e) { return "{}"; }
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton
    btn(String text, String data) {
        return org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons
                .InlineKeyboardButton.builder().text(text).callbackData(data).build();
    }
}
