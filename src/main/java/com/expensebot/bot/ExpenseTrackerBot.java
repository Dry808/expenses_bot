package com.expensebot.bot;

import com.expensebot.bot.handler.CallbackHandler;
import com.expensebot.bot.handler.MessageHandler;
import com.expensebot.config.BotConfig;
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
            if (update.hasMessage() && update.getMessage().hasText()) {
                var resp = msgHandler.handle(update.getMessage());
                if (resp != null) execute((BotApiMethod<?>) resp);
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