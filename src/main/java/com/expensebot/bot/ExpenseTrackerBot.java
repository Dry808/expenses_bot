package com.expensebot.bot;

import com.expensebot.bot.handler.CallbackHandler;
import com.expensebot.bot.handler.MessageHandler;
import com.expensebot.config.BotConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExpenseTrackerBot extends TelegramLongPollingBot {

    private final BotConfig config;
    private final MessageHandler msgHandler;
    private final CallbackHandler cbHandler;
    private final ObjectMapper objectMapper;

    @Override
    public String getBotUsername() {
        return config.getUsername();
    }

    @Override
    public String getBotToken() {
        return config.getToken();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            // ── Обычные текстовые сообщения ────────────────────
            if (update.hasMessage() && update.getMessage().hasText()) {
                var resp = msgHandler.handle(update.getMessage());
                if (resp != null) execute((BotApiMethod<?>) resp);

            // ── Данные из Mini App (WebApp) ────────────────────
            } else if (update.hasMessage() && update.getMessage().hasWebAppData()) {
                handleWebAppData(
                        update.getMessage().getChatId(),
                        update.getMessage().getWebAppData().getData()
                );

            // ── Callback кнопки ────────────────────────────────
            } else if (update.hasCallbackQuery()) {
                var resp = cbHandler.handle(update.getCallbackQuery());
                if (resp != null) {
                    if (resp instanceof EditMessageText emt) execute(emt);
                    else if (resp instanceof SendMessage sm) execute(sm);
                }
            }
        } catch (TelegramApiException e) {
            log.error("Telegram API error", e);
        } catch (Exception e) {
            log.error("Bot error", e);
        }
    }

    /**
     * Обрабатывает данные отправленные из Mini App через Telegram.WebApp.sendData()
     * Формат JSON: { "type": "expense"|"income", "amount": 500.0,
     *                "category": "🛒 Продукты", "note": "..." }
     */
    private void handleWebAppData(Long chatId, String json) {
        try {
            JsonNode node = objectMapper.readTree(json);

            String type     = node.path("type").asText("expense");
            double amount   = node.path("amount").asDouble(0);
            String category = node.path("category").asText("—");
            String note     = node.path("note").asText("").trim();

            if (amount <= 0) {
                sendMessage(chatId, "❌ Некорректная сумма из приложения.");
                return;
            }

            String emoji = "expense".equals(type) ? "🔴" : "🟢";
            String sign  = "expense".equals(type) ? "➖" : "➕";
            String typeRu = "expense".equals(type) ? "Расход" : "Доход";

            String text = emoji + " <b>" + typeRu + ": " + sign +
                    String.format("%,.2f", amount) + " ₽</b>\n" +
                    "📂 " + category +
                    (note.isBlank() ? "" : "\n📝 " + note) +
                    "\n\n✅ Сохранено через приложение!";

            sendMessage(chatId, text);

        } catch (Exception e) {
            log.error("WebApp data parse error: {}", json, e);
            sendMessage(chatId, "❌ Ошибка при обработке данных из приложения.");
        }
    }

    public void sendMessage(Long chatId, String text) {
        try {
            execute(SendMessage.builder()
                    .chatId(chatId.toString())
                    .text(text)
                    .parseMode("HTML")
                    .build());
        } catch (TelegramApiException e) {
            log.error("Send message failed", e);
        }
    }
}
