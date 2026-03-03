package it.octogram.android;

/*
 * This is the source code of turbogram for Android
 * It is licensed under GNU GPL v2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright TurboGram, 2026.
 */

import java.util.HashMap;

public class TurboAnalytics {
    private static final HashMap<Long, String> analyticsCache = new HashMap<>();
    private static final HashMap<Long, Boolean> isCalculating = new HashMap<>();
    // Кэш для твоей крутой фишки с профилем
    private static final HashMap<Long, String> karmaCache = new HashMap<>();

    // Метод для шапки чата (Стрик + Скорость)
    public static String getAnalytics(long dialogId, int currentAccount) {
        if (analyticsCache.containsKey(dialogId)) {
            return analyticsCache.get(dialogId);
        }
        if (isCalculating.containsKey(dialogId)) {
            return " | \u23F3 грузим...";
        }
        isCalculating.put(dialogId, true);

        // Отправляем в фон, чтобы интерфейс не тормозил
        org.telegram.messenger.MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            calculateBackground(dialogId, currentAccount);
        });

        return " | \u23F3 грузим...";
    }

    // Метод для профиля (Цифровая Карма)
    public static String getKarma(long dialogId) {
        return karmaCache.containsKey(dialogId) ? karmaCache.get(dialogId) : "";
    }

    private static void calculateBackground(long dialogId, int currentAccount) {
        String result = "";
        try {
            org.telegram.SQLite.SQLiteDatabase db = org.telegram.messenger.MessagesStorage.getInstance(currentAccount).getDatabase();
            if (db != null) {
                org.telegram.SQLite.SQLiteCursor cursor = null;
                try {
                    // Идеальный запрос, собранный по твоим логам!
                    cursor = db.queryFinalized("SELECT date, out FROM messages_v2 WHERE uid = " + dialogId + " ORDER BY mid DESC LIMIT 5000");
                } catch (Exception e1) {
                    try {
                        // Резервный вариант, если это старый чат
                        cursor = db.queryFinalized("SELECT date, out FROM messages WHERE uid = " + dialogId + " ORDER BY mid DESC LIMIT 5000");
                    } catch (Exception e2) {
                        result = " | \uD83D\uDEAB Ошибка БД";
                    }
                }

                if (cursor != null) {
                    int currentStreak = 0;
                    long lastDay = -1;
                    boolean hasMyMsg = false;
                    boolean hasTheirMsg = false;
                    int today = (int) (System.currentTimeMillis() / 1000) / 86400;

                    long totalResponseTime = 0;
                    int responseCount = 0;
                    int lastTheirDate = -1;

                    while (cursor.next()) {
                        int date = cursor.intValue(0);
                        int out = cursor.intValue(1); // 1 - мое сообщение, 0 - чужое

                        // ---- 1. СТРИК ----
                        int msgDay = date / 86400;
                        if (lastDay == -1) {
                            if (today - msgDay <= 1) {
                                currentStreak = 1;
                                lastDay = msgDay;
                            }
                        } else if (currentStreak > 0) {
                            if (lastDay - msgDay == 1) {
                                currentStreak++;
                                lastDay = msgDay;
                            } else if (lastDay - msgDay > 1) {
                                lastDay = -2; // Разрыв серии
                            }
                        }

                        // Засчитываем обоюдность только если серия жива
                        if (currentStreak > 0) {
                            if (out == 1) hasMyMsg = true;
                            else hasTheirMsg = true;
                        }

                        // ---- 2. СКОРОСТЬ ОТВЕТА ----
                        if (out == 0) {
                            lastTheirDate = date;
                        } else if (out == 1 && lastTheirDate != -1) {
                            int diff = lastTheirDate - date;
                            if (diff > 0 && diff < 43200) { // Игнорим ответы дольше 12 часов
                                totalResponseTime += diff;
                                responseCount++;
                            }
                            lastTheirDate = -1;
                        }
                    }
                    cursor.dispose();

                    // Серия сгорает, если кто-то из двоих проигнорил
                    if (!hasMyMsg || !hasTheirMsg) {
                        currentStreak = 0;
                    }

                    // ---- 3. ЦИФРОВАЯ КАРМА (ТВОЯ ЛОГИКА) ----
                    String karmaEmoji = "";
                    long avgMin = responseCount >= 3 ? (totalResponseTime / responseCount) / 60 : 0;

                    if (currentStreak == 0 && (avgMin > 60 || responseCount == 0)) {
                        karmaEmoji = "\u26B0\uFE0F"; // Гроб
                    } else if (currentStreak >= 30) {
                        karmaEmoji = "\uD83D\uDC98"; // Пробитое сердечко
                    } else if (currentStreak >= 10) {
                        if (avgMin >= 1 && avgMin <= 15) {
                            karmaEmoji = "\u2764\uFE0F"; // Сердечко
                        } else if (avgMin > 15 && avgMin <= 30) {
                            karmaEmoji = "\uD83E\uDD1D"; // Рукопожатие
                        } else {
                            karmaEmoji = "\uD83D\uDC4D"; // Лайк
                        }
                    } else if (currentStreak > 0 && currentStreak < 10) {
                        if (avgMin >= 15 && avgMin <= 60) {
                            karmaEmoji = "\uD83D\uDC4D"; // Лайк
                        } else {
                            karmaEmoji = "\uD83D\uDD25"; // Огонек
                        }
                    }

                    // Сохраняем карму для ProfileActivity
                    karmaCache.put(dialogId, karmaEmoji);

                    // ---- 4. СБОРКА ТЕКСТА ДЛЯ ШАПКИ ЧАТА ----
                    StringBuilder sb = new StringBuilder();
                    if (currentStreak > 0) {
                        sb.append(" | \uD83D\uDD25 ").append(currentStreak).append(" дн.");
                    }

                    if (responseCount >= 3) {
                        long avgSeconds = totalResponseTime / responseCount;
                        if (avgSeconds < 60) {
                            sb.append(" | \u23F1 ~").append(avgSeconds).append(" сек");
                        } else if (avgSeconds < 3600) {
                            sb.append(" | \u23F1 ~").append(avgSeconds / 60).append(" мин");
                        } else {
                            sb.append(String.format(" | \u23F1 ~%.1f ч", avgSeconds / 3600.0f));
                        }
                    }
                    result = sb.toString();
                }
            }
        } catch (Exception e) {
            result = " | \uD83D\uDEAB Ошибка";
        }

        analyticsCache.put(dialogId, result);
        isCalculating.remove(dialogId);

        // Обновляем UI
        org.telegram.messenger.AndroidUtilities.runOnUIThread(() -> {
            org.telegram.messenger.NotificationCenter.getInstance(currentAccount).postNotificationName(
                    org.telegram.messenger.NotificationCenter.updateInterfaces,
                    org.telegram.messenger.MessagesController.UPDATE_MASK_STATUS
            );
        });
    }
}