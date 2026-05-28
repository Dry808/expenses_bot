package com.expensebot.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Валидация Telegram WebApp initData согласно официальной документации:
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app
 *
 * Алгоритм:
 * 1. Из initData извлекаем поле hash
 * 2. Остальные поля сортируем по алфавиту и соединяем через \n
 * 3. Вычисляем HMAC-SHA256 с ключом = HMAC-SHA256("WebAppData", BOT_TOKEN)
 * 4. Сравниваем с полученным hash
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    @Value("${telegram.bot.token}")
    private String botToken;

    /**
     * Проверяет initData и возвращает telegram_id пользователя,
     * либо null если данные невалидны или устарели (> 24ч).
     */
    public Long validateAndGetUserId(String initData) {
        if (initData == null || initData.isBlank()) return null;

        try {
            // Разбираем query-string
            Map<String, String> params = parseQueryString(initData);

            String receivedHash = params.remove("hash");
            if (receivedHash == null) return null;

            // Проверяем свежесть (не старше 24 часов)
            String authDateStr = params.get("auth_date");
            if (authDateStr != null) {
                long authDate = Long.parseLong(authDateStr);
                long nowSeconds = System.currentTimeMillis() / 1000;
                if (nowSeconds - authDate > 86400) {
                    log.warn("Telegram initData expired: auth_date={}", authDate);
                    return null;
                }
            }

            // Строим data_check_string
            String dataCheckString = params.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("\n"));

            // Вычисляем секретный ключ: HMAC-SHA256(key="WebAppData", data=botToken)
            byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8),
                    botToken.getBytes(StandardCharsets.UTF_8));

            // Вычисляем ожидаемый hash
            byte[] expectedHashBytes = hmacSha256(secretKey,
                    dataCheckString.getBytes(StandardCharsets.UTF_8));
            String expectedHash = bytesToHex(expectedHashBytes);

            if (!expectedHash.equalsIgnoreCase(receivedHash)) {
                log.warn("Telegram initData hash mismatch");
                return null;
            }

            // Извлекаем user из JSON
            String userJson = params.get("user");
            if (userJson == null) return null;

            return extractUserId(userJson);

        } catch (Exception e) {
            log.error("TelegramAuthService validation error", e);
            return null;
        }
    }

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : query.split("&")) {
            int idx = part.indexOf('=');
            if (idx > 0) {
                String key = URLDecoder.decode(part.substring(0, idx), StandardCharsets.UTF_8);
                String val = URLDecoder.decode(part.substring(idx + 1), StandardCharsets.UTF_8);
                result.put(key, val);
            }
        }
        return result;
    }

    /**
     * Простой парсинг id из JSON строки вида {"id":123456789,"first_name":"..."}
     * Не требует подключения Jackson для этого метода.
     */
    private Long extractUserId(String userJson) {
        // Ищем "id":NUMBER
        int idIdx = userJson.indexOf("\"id\":");
        if (idIdx < 0) return null;
        int start = idIdx + 5;
        while (start < userJson.length() && userJson.charAt(start) == ' ') start++;
        int end = start;
        while (end < userJson.length() && Character.isDigit(userJson.charAt(end))) end++;
        if (end == start) return null;
        return Long.parseLong(userJson.substring(start, end));
    }

    private byte[] hmacSha256(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
