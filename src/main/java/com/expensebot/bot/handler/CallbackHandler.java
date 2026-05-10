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
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CallbackHandler {

    private final UserService userService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransactionService txService;
    private final BudgetService budgetService;
    private final ObjectMapper objectMapper;

    // Возвращает либо EditMessageText, либо SendMessage
    public Object handle(CallbackQuery cb) {
        long chatId   = cb.getMessage().getChatId();
        int  msgId    = cb.getMessage().getMessageId();
        String data   = cb.getData();
        var user      = userService.getByTelegramId(cb.getFrom().getId());

        log.debug("Callback: {} from {}", data, user.getTelegramId());

        // ── Главное меню ───────────────────────────────────────
        if ("main_menu".equals(data)) {
            userService.resetState(user.getTelegramId());
            return send(chatId, "Главное меню 👇", KeyboardFactory.mainMenu());
        }

        // ── Счета ──────────────────────────────────────────────
        if ("acc_create".equals(data)) {
            saveState(user, UserState.AWAIT_ACCOUNT_NAME, Map.of());
            return edit(chatId, msgId, "🏦 Введите название нового счёта:", null);
        }
        if ("acc_list".equals(data)) {
            var accounts = accountService.getAccounts(user.getId());
            return edit(chatId, msgId, "<b>💼 Ваши счета</b>\nВыберите счёт:",
                    KeyboardFactory.accounts(accounts, "acc_view_"));
        }
        if (data.startsWith("acc_view_")) {
            Long accId = Long.parseLong(data.substring(9));
            var acc = accountService.findById(accId).orElse(null);
            if (acc == null) return edit(chatId, msgId, "❌ Счёт не найден.", null);
            String info = """
                <b>%s %s</b>
                💰 Баланс: <b>%s %s</b>
                %s
                """.formatted(acc.getEmoji(), acc.getName(),
                    BotUtil.fmt(acc.getBalance()), acc.getCurrency(),
                    acc.isDefault() ? "⭐ Основной счёт" : "");
            return edit(chatId, msgId, info, KeyboardFactory.accountActions(accId));
        }
        if (data.startsWith("acc_default_")) {
            Long accId = Long.parseLong(data.substring(12));
            accountService.setDefault(accId, user.getId());
            return edit(chatId, msgId, "✅ Счёт установлен как основной.",
                    KeyboardFactory.accounts(accountService.getAccounts(user.getId()), "acc_view_"));
        }
        if (data.startsWith("acc_delete_")) {
            Long accId = Long.parseLong(data.substring(11));
            accountService.delete(accId);
            var accounts = accountService.getAccounts(user.getId());
            return edit(chatId, msgId, "🗑 Счёт удалён.\n\n<b>Ваши счета:</b>",
                    KeyboardFactory.accounts(accounts, "acc_view_"));
        }

        // ── Категории ──────────────────────────────────────────
        if ("cat_create".equals(data)) {
            saveState(user, UserState.AWAIT_CATEGORY_NAME, Map.of());
            return edit(chatId, msgId, "📂 Введите название новой категории:", null);
        }
        if (data.startsWith("cat_type_")) {
            var type = TransactionType.valueOf(data.substring(9));
            var stateData = readState(user);
            stateData.put("catType", type.name());
            // Создаём категорию
            String name  = (String) stateData.get("name");
            String emoji = (String) stateData.get("emoji");
            categoryService.create(user.getId(), name, emoji, type);
            userService.resetState(user.getTelegramId());
            return send(chatId,
                    "✅ Категория <b>" + emoji + " " + name + "</b> создана!",
                    KeyboardFactory.mainMenu());
        }
        if ("cat_delete_list".equals(data)) {
            var cats = categoryService.getAll(user.getId());
            var nonSystem = cats.stream().filter(c -> !c.isSystem()).collect(Collectors.toList());
            if (nonSystem.isEmpty())
                return edit(chatId, msgId, "Нет пользовательских категорий для удаления.", backBtn());
            return edit(chatId, msgId, "Выберите категорию для удаления:",
                    KeyboardFactory.categories(nonSystem, "cat_del_"));
        }
        if (data.startsWith("cat_del_") && !data.equals("cat_del_new")) {
            Long catId = Long.parseLong(data.substring(8));
            categoryService.delete(catId);
            return edit(chatId, msgId, "🗑 Категория удалена.", backBtn());
        }

        // ── Выбор категории при добавлении транзакции ──────────
        if (data.startsWith("tx_cat_")) {
            String catPart = data.substring(7);
            if ("new".equals(catPart)) {
                // Предложим создать категорию
                return edit(chatId, msgId,
                        "Для создания категории перейдите в 📂 Категории.", backBtn());
            }
            Long catId = Long.parseLong(catPart);
            var cat    = categoryService.findById(catId).orElse(null);
            if (cat == null) return edit(chatId, msgId, "❌ Категория не найдена.", null);

            var stateData = readState(user);
            stateData.put("categoryId", catId.toString());
            stateData.put("step", "account");

            var accounts = accountService.getAccounts(user.getId());
            saveState(user, UserState.AWAIT_TX_ACCOUNT, stateData);
            return edit(chatId, msgId, "🏦 Выберите счёт:",
                    KeyboardFactory.accounts(accounts, "tx_acc_"));
        }

        // ── Выбор счёта при добавлении транзакции ─────────────
        if (data.startsWith("tx_acc_")) {
            Long accId    = Long.parseLong(data.substring(7));
            var stateData = readState(user);
            stateData.put("accountId", accId.toString());
            saveState(user, UserState.AWAIT_TX_NOTE, stateData);
            return edit(chatId, msgId,
                    "📝 Добавьте заметку (или напишите <b>-</b> чтобы пропустить):", null);
        }

        // ── Подтверждение транзакции ───────────────────────────
        if ("tx_confirm".equals(data)) {
            var stateData = readState(user);
            try {
                var type     = TransactionType.valueOf((String) stateData.get("type"));
                var amount   = new BigDecimal((String) stateData.get("amount"));
                Long catId   = Long.parseLong((String) stateData.get("categoryId"));
                Long accId   = Long.parseLong((String) stateData.get("accountId"));
                String note  = (String) stateData.getOrDefault("note", "");
                if ("-".equals(note)) note = null;

                var category = categoryService.findById(catId).orElseThrow();
                txService.addWithCategory(user.getId(), accId, category, amount, type, note);
                userService.resetState(user.getTelegramId());

                String sign  = type == TransactionType.EXPENSE ? "➖" : "➕";
                String emoji = type == TransactionType.EXPENSE ? "🔴" : "🟢";
                var acc      = accountService.findById(accId).orElseThrow();

                // Проверка бюджета
                String budgetWarn = checkBudgetAlert(user, type);

                return send(chatId,
                        emoji + " <b>" + sign + BotUtil.fmt(amount) + " ₽</b> добавлено!\n" +
                                "📂 " + category.getDisplayName() + "\n" +
                                "🏦 " + acc.getEmoji() + " " + acc.getName() +
                                " → <b>" + BotUtil.fmt(acc.getBalance()) + " ₽</b>" +
                                budgetWarn,
                        KeyboardFactory.mainMenu());
            } catch (Exception e) {
                log.error("tx_confirm error", e);
                userService.resetState(user.getTelegramId());
                return send(chatId, "❌ Ошибка при сохранении операции.", KeyboardFactory.mainMenu());
            }
        }

        // ── Аналитика ──────────────────────────────────────────
        if (data.startsWith("stats_")) {
            return edit(chatId, msgId, buildStats(user, data), KeyboardFactory.analyticsMenu());
        }

        // ── Бюджет ─────────────────────────────────────────────
        if ("budget_create".equals(data)) {
            saveState(user, UserState.AWAIT_BUDGET_NAME,
                    Map.of("selectedCats", new ArrayList<String>()));
            return edit(chatId, msgId, "📝 Введите название бюджета (например: <b>Май 2025</b>):", null);
        }
        if ("budget_status".equals(data)) {
            return edit(chatId, msgId, buildBudgetStatus(user), KeyboardFactory.budgetMenu(true));
        }
        if ("budget_scope_all".equals(data)) {
            return finalizeBudget(user, chatId, msgId, true, Collections.emptyList());
        }
        if (data.startsWith("budget_scope_cat_")) {
            Long catId    = Long.parseLong(data.substring(17));
            var stateData = readState(user);
            @SuppressWarnings("unchecked")
            var selected  = (List<String>) stateData.getOrDefault("selectedCats", new ArrayList<>());
            if (!selected.contains(catId.toString())) selected.add(catId.toString());
            stateData.put("selectedCats", selected);
            saveState(user, UserState.AWAIT_BUDGET_CATEGORIES, stateData);
            var expCats   = categoryService.getByType(user.getId(), TransactionType.EXPENSE);
            int selCount  = selected.size();
            return edit(chatId, msgId,
                    "✅ Выбрано категорий: <b>" + selCount + "</b>\n" +
                            "Продолжайте выбирать или нажмите «Готово»:",
                    KeyboardFactory.budgetScope(expCats));
        }
        if ("budget_scope_done".equals(data)) {
            var stateData = readState(user);
            @SuppressWarnings("unchecked")
            var selected  = (List<String>) stateData.getOrDefault("selectedCats", new ArrayList<>());
            return finalizeBudget(user, chatId, msgId, false, selected);
        }
        if ("budget_delete".equals(data)) {
            var budgets = budgetService.getCurrentBudgets(user.getId());
            budgets.forEach(b -> budgetService.delete(b.getId()));
            return edit(chatId, msgId, "🗑 Бюджет удалён.", KeyboardFactory.budgetMenu(false));
        }

        log.warn("Unhandled callback: {}", data);
        return edit(chatId, msgId, "Неизвестная команда.", backBtn());
    }

    // ── Статистика ─────────────────────────────────────────────
    private String buildStats(BotUser user, String key) {
        var now   = LocalDateTime.now();
        var today = LocalDate.now();
        return switch (key) {
            case "stats_today" -> {
                var from = today.atStartOfDay();
                var to   = today.atTime(23, 59, 59);
                yield periodStats(user, "Сегодня", from, to);
            }
            case "stats_7d" -> {
                var from = now.minusDays(7);
                yield periodStats(user, "Последние 7 дней", from, now);
            }
            case "stats_30d" -> {
                var from = now.minusDays(30);
                yield periodStats(user, "Последние 30 дней", from, now);
            }
            case "stats_month" -> {
                var from = today.withDayOfMonth(1).atStartOfDay();
                var to   = today.withDayOfMonth(today.lengthOfMonth()).atTime(23, 59, 59);
                yield periodStats(user, "Этот месяц (" +
                        today.format(DateTimeFormatter.ofPattern("MMMM yyyy",
                                new java.util.Locale("ru"))), from, to);
            }
            case "stats_weekly" -> buildWeeklyStats(user);
            case "stats_yearly" -> buildYearlyStats(user);
            case "stats_cat_exp" -> {
                var from = today.withDayOfMonth(1).atStartOfDay();
                var to   = today.withDayOfMonth(today.lengthOfMonth()).atTime(23, 59, 59);
                yield buildCategoryStats(user, TransactionType.EXPENSE, from, to,
                        "Расходы по категориям (месяц)");
            }
            case "stats_cat_inc" -> {
                var from = today.withDayOfMonth(1).atStartOfDay();
                var to   = today.withDayOfMonth(today.lengthOfMonth()).atTime(23, 59, 59);
                yield buildCategoryStats(user, TransactionType.INCOME, from, to,
                        "Доходы по категориям (месяц)");
            }
            default -> "Выберите период:";
        };
    }

    private String periodStats(BotUser user, String title,
                               LocalDateTime from, LocalDateTime to) {
        var income  = txService.sumByType(user.getId(), TransactionType.INCOME, from, to);
        var expense = txService.sumByType(user.getId(), TransactionType.EXPENSE, from, to);
        var balance = income.subtract(expense);
        String balSign = balance.compareTo(BigDecimal.ZERO) >= 0 ? "+" : "";
        return """
            <b>📊 %s</b>
            
            🟢 Доходы:   <b>%s ₽</b>
            🔴 Расходы:  <b>%s ₽</b>
            ━━━━━━━━━━━━━━
            💼 Баланс:   <b>%s%s ₽</b>
            """.formatted(title,
                BotUtil.fmt(income), BotUtil.fmt(expense),
                balSign, BotUtil.fmt(balance));
    }

    private String buildWeeklyStats(BotUser user) {
        var from = LocalDateTime.now().minusWeeks(8);
        var rows = txService.weeklyStats(user.getId(), from, LocalDateTime.now());
        var sb   = new StringBuilder("<b>📈 Статистика по неделям</b>\n\n");
        var fmt  = DateTimeFormatter.ofPattern("dd.MM");
        for (var row : rows) {
            var weekStart = ((java.sql.Timestamp) row[0]).toLocalDateTime();
            var inc       = ((BigDecimal) row[1]);
            var exp       = ((BigDecimal) row[2]);
            sb.append("📅 <b>").append(weekStart.format(fmt)).append("</b>\n")
                    .append("  🟢 ").append(BotUtil.fmt(inc)).append(" ₽")
                    .append("  🔴 ").append(BotUtil.fmt(exp)).append(" ₽\n");
        }
        return sb.isEmpty() ? "Нет данных за последние 8 недель." : sb.toString();
    }

    private String buildYearlyStats(BotUser user) {
        int year = LocalDate.now().getYear();
        var rows = txService.monthlyStats(user.getId(), year);
        String[] months = {"Янв","Фев","Мар","Апр","Май","Июн",
                "Июл","Авг","Сен","Окт","Ноя","Дек"};
        var sb = new StringBuilder("<b>📉 Статистика по месяцам ").append(year).append("</b>\n\n");
        for (var row : rows) {
            int m   = ((Number) row[0]).intValue();
            var inc = ((BigDecimal) row[1]);
            var exp = ((BigDecimal) row[2]);
            sb.append("<b>").append(months[m - 1]).append("</b>  ")
                    .append("🟢 ").append(BotUtil.fmt(inc))
                    .append("  🔴 ").append(BotUtil.fmt(exp)).append("\n");
        }
        return sb.toString();
    }

    private String buildCategoryStats(BotUser user, TransactionType type,
                                      LocalDateTime from, LocalDateTime to, String title) {
        var rows  = txService.categoryStats(user.getId(), type, from, to);
        var total = rows.stream()
                .map(r -> (BigDecimal) r[3])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var sb = new StringBuilder("<b>🏷 ").append(title).append("</b>\n\n");
        for (var row : rows) {
            String emoji  = (String) row[2];
            String name   = (String) row[1];
            var    amount = (BigDecimal) row[3];
            double pct    = total.compareTo(BigDecimal.ZERO) > 0
                    ? amount.divide(total, 4, java.math.RoundingMode.HALF_UP)
                    .doubleValue() * 100 : 0;
            sb.append(emoji).append(" <b>").append(name).append("</b>\n")
                    .append("  ").append(BotUtil.fmt(amount)).append(" ₽  ")
                    .append(String.format("(%.1f%%)", pct)).append("\n");
        }
        sb.append("\n💰 Итого: <b>").append(BotUtil.fmt(total)).append(" ₽</b>");
        return rows.isEmpty() ? "Нет данных за период." : sb.toString();
    }

    // ── Статус бюджета ─────────────────────────────────────────
    private String buildBudgetStatus(BotUser user) {
        var budgets = budgetService.getCurrentBudgets(user.getId());
        if (budgets.isEmpty()) return "На этот месяц бюджет не задан.";

        var sb = new StringBuilder("<b>📊 Статус бюджета</b>\n\n");
        for (var b : budgets) {
            var s = budgetService.getStats(b);
            String catInfo = b.isAllCategories() ? "Все расходы"
                    : b.getCategories().stream()
                    .map(Category::getDisplayName)
                    .collect(Collectors.joining(", "));
            sb.append("📌 <b>").append(b.getName() != null ? b.getName() : "Бюджет").append("</b>\n")
                    .append("📂 Категории: ").append(catInfo).append("\n\n")
                    .append("💰 Лимит:      <b>").append(BotUtil.fmt(s.total())).append(" ₽</b>\n")
                    .append("🔴 Потрачено:  <b>").append(BotUtil.fmt(s.spent())).append(" ₽</b>\n")
                    .append("🟢 Осталось:   <b>").append(BotUtil.fmt(s.left())).append(" ₽</b>\n");
            if (s.over().compareTo(BigDecimal.ZERO) > 0)
                sb.append("⚠️ Превышение: <b>").append(BotUtil.fmt(s.over())).append(" ₽</b>\n");
            sb.append("\n").append(BotUtil.progressBar(s.spentPercent())).append("\n\n")
                    .append("📅 Осталось дней: <b>").append(s.daysLeft()).append("</b>\n")
                    .append("💡 Рекомендуемо в день: <b>").append(BotUtil.fmt(s.dailyRecommended()))
                    .append(" ₽</b>\n\n");
        }
        return sb.toString();
    }

    // ── Финализация бюджета ────────────────────────────────────
    private Object finalizeBudget(BotUser user, long chatId, int msgId,
                                  boolean allCats, List<String> catIds) {
        var stateData = readState(user);
        String name   = (String) stateData.getOrDefault("budgetName", "Бюджет");
        var amount    = new BigDecimal((String) stateData.get("budgetAmount"));
        var now       = LocalDate.now();
        Set<Category> cats = allCats ? Collections.emptySet() :
                catIds.stream()
                        .map(id -> categoryService.findById(Long.parseLong(id)).orElse(null))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());

        budgetService.create(user.getId(), name, amount, now.getYear(),
                now.getMonthValue(), allCats, cats);
        userService.resetState(user.getTelegramId());

        String catLabel = allCats ? "все расходы"
                : cats.stream().map(Category::getDisplayName).collect(Collectors.joining(", "));
        return edit(chatId, msgId,
                "✅ Бюджет <b>" + name + "</b> создан!\n" +
                        "💰 Лимит: <b>" + BotUtil.fmt(amount) + " ₽</b>\n" +
                        "📂 Категории: " + catLabel,
                KeyboardFactory.budgetMenu(true));
    }

    // ── Алерт при превышении бюджета ──────────────────────────
    private String checkBudgetAlert(BotUser user, TransactionType type) {
        if (type != TransactionType.EXPENSE) return "";
        var budgets = budgetService.getCurrentBudgets(user.getId());
        var sb = new StringBuilder();
        for (var b : budgets) {
            var s = budgetService.getStats(b);
            if (s.spentPercent() >= 100) {
                sb.append("\n\n⚠️ <b>Внимание!</b> Бюджет <b>")
                        .append(b.getName() != null ? b.getName() : "").append("</b> превышен на <b>")
                        .append(BotUtil.fmt(s.over())).append(" ₽</b>!");
            } else if (s.spentPercent() >= 80) {
                sb.append("\n\n⚠️ Бюджет использован на <b>")
                        .append(String.format("%.0f%%", s.spentPercent())).append("</b>. Остаток: <b>")
                        .append(BotUtil.fmt(s.left())).append(" ₽</b>");
            }
        }
        return sb.toString();
    }

    // ── Вспомогательные ───────────────────────────────────────
    @SuppressWarnings("unchecked")
    private Map<String, Object> readState(BotUser user) {
        try {
            if (user.getStateData() == null) return new HashMap<>();
            return objectMapper.readValue(user.getStateData(), HashMap.class);
        } catch (Exception e) { return new HashMap<>(); }
    }

    private void saveState(BotUser user, UserState state, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            userService.setState(user.getTelegramId(), state, json);
            user.setState(state);
            user.setStateData(json);
        } catch (Exception e) { log.error("saveState error", e); }
    }

    private EditMessageText edit(long chatId, int msgId, String text,
                                 org.telegram.telegrambots.meta.api.objects.replykeyboard
                                         .InlineKeyboardMarkup kb) {
        return BotUtil.edit(chatId, msgId, text, kb);
    }

    private SendMessage send(long chatId, String text,
                             org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard kb) {
        return BotUtil.msg(chatId, text, kb);
    }

    private org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup backBtn() {
        return org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons
                                .InlineKeyboardButton.builder()
                                .text("🔙 Назад").callbackData("main_menu").build()
                ))).build();
    }
}
