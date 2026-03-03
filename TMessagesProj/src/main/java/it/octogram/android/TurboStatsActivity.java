package it.octogram.android;
/*
 * This is the source code of turbogram for Android
 * It is licensed under GNU GPL v2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright TurboGram, 2026.
 */


import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;

public class TurboStatsActivity extends BaseFragment {
    private LinearLayout listContainer;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Статистика TurboGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listContainer = new LinearLayout(context);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        TextView loadingText = new TextView(context);
        loadingText.setText("⏳ Собираем статистику переписок...\nВысчитываем Карму");
        loadingText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        loadingText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        loadingText.setGravity(Gravity.CENTER);
        loadingText.setPadding(50, 100, 50, 100);
        listContainer.addView(loadingText);

        fragmentView = scrollView;
        loadStatistics(context);

        return fragmentView;
    }

    private void loadStatistics(Context context) {
        int currentAccount = UserConfig.selectedAccount;
        SharedPreferences karmaPrefs = context.getSharedPreferences("turbokarma_db", Context.MODE_PRIVATE);

        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
            ArrayList<StatItem> stats = new ArrayList<>();
            try {
                SQLiteDatabase db = MessagesStorage.getInstance(currentAccount).getDatabase();
                if (db != null) {
                    SQLiteCursor dialogsCursor = db.queryFinalized("SELECT did FROM dialogs WHERE did > 0 ORDER BY date DESC LIMIT 500");
                    int timeOffset = java.util.TimeZone.getDefault().getRawOffset() / 1000;
                    int today = (int) ((System.currentTimeMillis() / 1000) + timeOffset) / 86400;

                    while (dialogsCursor.next()) {
                        long uid = dialogsCursor.longValue(0);
                        SQLiteCursor cursor = null;
                        try {
                            cursor = db.queryFinalized("SELECT date, out FROM messages_v2 WHERE uid = " + uid + " ORDER BY mid DESC LIMIT 5000");
                        } catch (Exception e) {
                            cursor = db.queryFinalized("SELECT date, out FROM messages WHERE uid = " + uid + " ORDER BY mid DESC LIMIT 5000");
                        }

                        if (cursor != null) {
                            int currentStreak = 0;
                            long lastDay = -1;
                            boolean hasMyMsg = false;
                            boolean hasTheirMsg = false;

                            long theirResponseTime = 0;
                            int theirResponseCount = 0;
                            int lastMyDate = -1;

                            long myResponseTime = 0;
                            int myResponseCount = 0;
                            int lastTheirDate = -1;

                            long myTotalMsgs = 0;
                            long theirTotalMsgs = 0;

                            while (cursor.next()) {
                                int date = cursor.intValue(0);
                                int out = cursor.intValue(1);

                                if (out == 1) myTotalMsgs++; else theirTotalMsgs++;

                                int msgDay = (date + timeOffset) / 86400;

                                if (lastDay == -1) {
                                    if (today - msgDay <= 1) { currentStreak = 1; lastDay = msgDay; }
                                    else { lastDay = -2; }
                                } else if (currentStreak > 0) {
                                    if (lastDay - msgDay == 1) { currentStreak++; lastDay = msgDay; }
                                    else if (lastDay - msgDay > 1) { lastDay = -2; }
                                }

                                if (currentStreak > 0) {
                                    if (out == 1) hasMyMsg = true; else hasTheirMsg = true;
                                }

                                if (out == 1) {
                                    lastMyDate = date;
                                    if (lastTheirDate != -1) {
                                        int diff = lastTheirDate - date;
                                        if (diff > 0 && diff < 43200) { myResponseTime += diff; myResponseCount++; }
                                        lastTheirDate = -1;
                                    }
                                } else {
                                    lastTheirDate = date;
                                    if (lastMyDate != -1) {
                                        int diff = lastMyDate - date;
                                        if (diff > 0 && diff < 43200) { theirResponseTime += diff; theirResponseCount++; }
                                        lastMyDate = -1;
                                    }
                                }
                            }
                            cursor.dispose();

                            if (!hasMyMsg || !hasTheirMsg) currentStreak = 0;

                            long avgTheirMin = theirResponseCount >= 3 ? (theirResponseTime / theirResponseCount) / 60 : 0;
                            long avgMyMin = myResponseCount >= 3 ? (myResponseTime / myResponseCount) / 60 : 0;

                            int karma = karmaPrefs.getInt("karma_" + uid, 50);
                            int lastStreak = karmaPrefs.getInt("streak_" + uid, 0);
                            int lastToday = karmaPrefs.getInt("today_" + uid, 0);

                            if (lastToday != today) {
                                if (currentStreak == 0) {
                                    if (lastStreak >= 30) karma -= 50;
                                    else if (lastStreak >= 10) karma -= 25;
                                } else {
                                    if (currentStreak % 10 == 0 && currentStreak != lastStreak) karma += 10;
                                    else if (currentStreak % 5 == 0 && currentStreak != lastStreak) karma += 5;
                                }

                                if (avgTheirMin > 0 && avgTheirMin <= 30) karma += 3;
                                else if (avgTheirMin > 75) karma -= 5;
                                if (avgTheirMin > 0 && avgTheirMin <= 5) karma += 5;
                                else if (avgTheirMin > 0 && avgTheirMin <= 25) karma += 1;

                                if (karma > 100) karma = 100;
                                if (karma < 0) karma = 0;

                                karmaPrefs.edit()
                                        .putInt("karma_" + uid, karma)
                                        .putInt("streak_" + uid, currentStreak)
                                        .putInt("today_" + uid, today)
                                        .apply();
                            }

                            if (currentStreak > 0) {
                                long totalMsgs = myTotalMsgs + theirTotalMsgs;
                                int myPct = totalMsgs > 0 ? (int) ((myTotalMsgs * 100.0f) / totalMsgs) : 50;
                                int theirPct = 100 - myPct;

                                String karmaEmoji = "😶";
                                if (karma == 0) karmaEmoji = "\u26B0\uFE0F";
                                else if (karma <= 20) karmaEmoji = "\u270C\uFE0F";
                                else if (karma <= 35) karmaEmoji = "\uD83E\uDD1D";
                                else if (karma <= 75) karmaEmoji = "\u2764\uFE0F";
                                else karmaEmoji = "\uD83D\uDD25";

                                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(uid);
                                String name = user != null ? user.first_name + (user.last_name != null ? " " + user.last_name : "") : "ID: " + uid;
                                if (user != null && user.bot) name += " \uD83E\uDD16";

                                stats.add(new StatItem(uid, name, currentStreak, avgTheirMin, avgMyMin, karma, karmaEmoji, myPct, theirPct));
                            }
                        }
                    }
                    dialogsCursor.dispose();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            AndroidUtilities.runOnUIThread(() -> {
                listContainer.removeAllViews();
                if (stats.isEmpty()) {
                    TextView empty = new TextView(context);
                    empty.setText("Пока нет активных стриков \uD83E\uDD72");
                    empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                    empty.setGravity(Gravity.CENTER);
                    empty.setPadding(50, 100, 50, 100);
                    listContainer.addView(empty);
                } else {
                    Collections.sort(stats, (a, b) -> Integer.compare(b.karma, a.karma));

                    for (StatItem item : stats) {
                        LinearLayout cell = new LinearLayout(context);
                        cell.setOrientation(LinearLayout.VERTICAL);
                        cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                        cell.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(15), AndroidUtilities.dp(20), AndroidUtilities.dp(15));

                        // Делаем ячейку кликабельной!
                        cell.setClickable(true);
                        cell.setOnClickListener(v -> showUserDialog(item));

                        TextView nameView = new TextView(context);
                        nameView.setText(item.emoji + " " + item.name);
                        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        nameView.setTypeface(AndroidUtilities.bold());
                        cell.addView(nameView);

                        // ТЕПЕРЬ ВЫВОДИТСЯ ВООБЩЕ ВСЁ: Стрик, оба времени ответа, баланс и карма!
                        TextView statView = new TextView(context);
                        statView.setText(String.format("🔥 Стрик: %d дн.\n⏱ Твой ответ: ~%d мин | Их: ~%d мин\n💬 Баланс: %d%% ты / %d%% они\n⚡ Карма: %d%%",
                                item.streak, item.myAvg, item.theirAvg, item.myPct, item.theirPct, item.karma));
                        statView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                        statView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
                        statView.setPadding(0, AndroidUtilities.dp(5), 0, 0);
                        cell.addView(statView);

                        listContainer.addView(cell);

                        View divider = new View(context);
                        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
                        listContainer.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                    }
                }
            });
        });
    }

    // --- НАДЕЖНОЕ ВСПЛЫВАЮЩЕЕ ОКНО ---
    private void showUserDialog(StatItem item) {
        if (getParentActivity() == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(item.emoji + " " + item.name);

        // Красивый текст для диалога
        String message = String.format(
                "🔥 Винстрик общения: %d дней\n\n" +
                        "⏱ Мой средний ответ: ~%d мин\n" +
                        "⏱ Их средний ответ: ~%d мин\n\n" +
                        "💬 Инициатива: %d%% (Я) / %d%% (Они)\n\n" +
                        "🌟 Цифровая Карма: %d%%",
                item.streak, item.myAvg, item.theirAvg, item.myPct, item.theirPct, item.karma
        );
        builder.setMessage(message);

        // Кнопка 1: Открыть чат
        builder.setPositiveButton("В чат", (dialog, which) -> {
            android.os.Bundle args = new android.os.Bundle();
            args.putLong("user_id", item.uid);
            presentFragment(new ChatActivity(args));
        });

        // Кнопка 2: Скачать карточку в фоне
        builder.setNeutralButton("Скачать карточку", (dialog, which) -> {
            saveCardAsImage(item);
        });

        // Кнопка 3: Отмена
        builder.setNegativeButton("Закрыть", null);

        showDialog(builder.create());
    }

    // --- ФОНОВЫЙ РЕНДЕР КАРТОЧКИ В КАРТИНКУ ---
    // --- ФОНОВЫЙ РЕНДЕР КРАСИВОЙ КАРТОЧКИ В ГАЛЕРЕЮ ---
    private void saveCardAsImage(StatItem item) {
        try {
            Context context = getParentActivity();
            if (context == null) return;

            // Строим карточку
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);

            // СТАВИМ КРАСИВЫЙ ФОН ИЗ ИСХОДНИКОВ!
            card.setBackgroundResource(org.telegram.messenger.R.mipmap.ic_dev_icon_background);
            card.setPadding(AndroidUtilities.dp(30), AndroidUtilities.dp(40), AndroidUtilities.dp(30), AndroidUtilities.dp(40));

            // ДОБАВЛЯЕМ ЛОГОТИП (ic_launcher)
            android.widget.ImageView logoImage = new android.widget.ImageView(context);
            logoImage.setImageResource(org.telegram.messenger.R.mipmap.ic_launcher);
            LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(AndroidUtilities.dp(72), AndroidUtilities.dp(72));
            logoParams.gravity = Gravity.CENTER;
            logoParams.bottomMargin = AndroidUtilities.dp(16);
            logoImage.setLayoutParams(logoParams);
            card.addView(logoImage);

            // Текст логотипа
            TextView logoText = new TextView(context);
            logoText.setText("TURBOGRAM STATS");
            logoText.setTextColor(0xFFFFFFFF); // Белый цвет
            logoText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            logoText.setTypeface(AndroidUtilities.bold());
            logoText.setGravity(Gravity.CENTER);
            logoText.setPadding(0, 0, 0, AndroidUtilities.dp(24));
            logoText.setShadowLayer(5, 0, 2, 0xFF000000); // ЧЕРНАЯ ТЕНЬ ДЛЯ ЧИТАЕМОСТИ!
            card.addView(logoText);

            // Имя контакта
            TextView nameText = new TextView(context);
            nameText.setText(item.emoji + " " + item.name);
            nameText.setTextColor(0xFFFFFFFF);
            nameText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 22);
            nameText.setTypeface(AndroidUtilities.bold());
            nameText.setGravity(Gravity.CENTER);
            nameText.setPadding(0, 0, 0, AndroidUtilities.dp(20));
            nameText.setShadowLayer(5, 0, 2, 0xFF000000);
            card.addView(nameText);

            // Статистика (сделал по центру)
            TextView statsText = new TextView(context);
            statsText.setText(String.format(
                    "🔥 Винстрик: %d дней\n\n" +
                            "⏱ Мой ответ: ~%d мин\n" +
                            "⏱ Их ответ: ~%d мин\n\n" +
                            "💬 Инициатива: %d%% (Я) / %d%% (Они)\n\n" +
                            "🌟 Карма: %d%%",
                    item.streak, item.myAvg, item.theirAvg, item.myPct, item.theirPct, item.karma
            ));
            statsText.setTextColor(0xFFFFFFFF);
            statsText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            statsText.setLineSpacing(AndroidUtilities.dp(6), 1.0f);
            statsText.setGravity(Gravity.CENTER); // Теперь текст ровно по центру
            statsText.setTypeface(AndroidUtilities.bold());
            statsText.setShadowLayer(5, 0, 2, 0xFF000000);
            card.addView(statsText);

            // Просчитываем её размеры в памяти
            card.measure(
                    View.MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(350), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            card.layout(0, 0, card.getMeasuredWidth(), card.getMeasuredHeight());

            // Рисуем в Bitmap
            Bitmap bitmap = Bitmap.createBitmap(card.getMeasuredWidth(), card.getMeasuredHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            card.draw(canvas);

            // СОХРАНЯЕМ В ГАЛЕРЕЮ (Папка Pictures/TurboGram)
            File picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES);
            File turboDir = new File(picturesDir, "TurboGram");
            if (!turboDir.exists()) turboDir.mkdirs();

            String cleanName = item.name.replaceAll("[^a-zA-Zа-яА-Я0-9]", "").trim();
            if (cleanName.isEmpty()) cleanName = "User_" + item.uid;

            // Добавим время, чтобы можно было делать много скринов одного человека и они не перезаписывались
            File imageFile = new File(turboDir, "Stats_" + cleanName + "_" + System.currentTimeMillis() + ".png");

            FileOutputStream out = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();

            // МАГИЯ ДЛЯ ГАЛЕРЕИ: Заставляем систему моментально "увидеть" новую картинку
            android.media.MediaScannerConnection.scanFile(
                    context,
                    new String[]{imageFile.getAbsolutePath()},
                    new String[]{"image/png"},
                    null
            );

            AndroidUtilities.runOnUIThread(() -> {
                android.widget.Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                        "🖼 Карточка сохранена в Галерею!",
                        android.widget.Toast.LENGTH_LONG).show();
            });
        } catch (Exception e) {
            e.printStackTrace();
            AndroidUtilities.runOnUIThread(() -> {
                android.widget.Toast.makeText(org.telegram.messenger.ApplicationLoader.applicationContext,
                        "❌ Ошибка сохранения",
                        android.widget.Toast.LENGTH_SHORT).show();
            });
        }
    }

    private static class StatItem {
        long uid;
        String name;
        int streak;
        long theirAvg;
        long myAvg;
        int karma;
        String emoji;
        int myPct;
        int theirPct;

        StatItem(long uid, String name, int streak, long theirAvg, long myAvg, int karma, String emoji, int myPct, int theirPct) {
            this.uid = uid;
            this.name = name;
            this.streak = streak;
            this.theirAvg = theirAvg;
            this.myAvg = myAvg;
            this.karma = karma;
            this.emoji = emoji;
            this.myPct = myPct;
            this.theirPct = theirPct;
        }
    }
}