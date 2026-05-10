package com.expensebot.util;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;

public final class BotUtil {

    private BotUtil() {}

    public static SendMessage msg(Long chatId, String text) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML")
                .build();
    }

    public static SendMessage msg(Long chatId, String text, ReplyKeyboard keyboard) {
        return SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .parseMode("HTML")
                .replyMarkup(keyboard)
                .build();
    }

    public static EditMessageText edit(Long chatId, Integer msgId,
                                       String text, InlineKeyboardMarkup kb) {
        return EditMessageText.builder()
                .chatId(chatId.toString())
                .messageId(msgId)
                .text(text)
                .parseMode("HTML")
                .replyMarkup(kb)
                .build();
    }

    public static String fmt(java.math.BigDecimal bd) {
        return String.format("%,.2f", bd);
    }

    public static String progressBar(double pct) {
        int filled = (int) Math.min(pct / 10, 10);
        return "█".repeat(filled) + "░".repeat(10 - filled)
                + " " + String.format("%.1f%%", pct);
    }
}