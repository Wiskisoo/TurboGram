package it.octogram.android;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

public class TurboAboutActivity extends BaseFragment {

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("О моде TurboGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(30), AndroidUtilities.dp(20), AndroidUtilities.dp(30));
        scrollView.addView(container);

        // Заголовок
        TextView title = new TextView(context);
        title.setText("⚡ TurboGram");
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 28);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, AndroidUtilities.dp(20));
        container.addView(title);

        // Основной текст
        TextView description = new TextView(context);
        description.setText("Этот мод был написан чисто по фану!\nИдея геймификации и стриков была вдохновлена TikTok.\n\nОгромное спасибо разработчикам OctoGram за крутую базу, на которой всё это построено.");
        description.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        description.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        description.setGravity(Gravity.CENTER);
        description.setLineSpacing(AndroidUtilities.dp(4), 1.2f);
        description.setPadding(0, 0, 0, AndroidUtilities.dp(40));
        container.addView(description);

        // Блок доната
        TextView donateTitle = new TextView(context);
        donateTitle.setText("Поддержать автора (USDT в сети TON)\nот 1 USDT\nкриптобот от 0.3$\nhttps://t.me/send?start=IVHGeOvuBGD2:");
        donateTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        donateTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        donateTitle.setTypeface(AndroidUtilities.bold());
        donateTitle.setGravity(Gravity.CENTER);
        donateTitle.setPadding(0, 0, 0, AndroidUtilities.dp(10));
        container.addView(donateTitle);

        // Кошелек (Кликабельный для копирования)
        String walletAddress = "UQB8ytuuLLBbGaygyi2ZwW5mDfecefdfo9UvgI5JNH9HvPSX"; // <-- ВПИШИ СВОЙ КОШЕЛЕК

        TextView wallet = new TextView(context);
        wallet.setText(walletAddress);
        wallet.setTextColor(0xFF2196F3); // Синий цвет ссылки
        wallet.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        wallet.setGravity(Gravity.CENTER);
        wallet.setBackground(Theme.getSelectorDrawable(false));
        wallet.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10), AndroidUtilities.dp(10));
        wallet.setClickable(true);
        wallet.setOnClickListener(v -> {
            android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            android.content.ClipData clip = android.content.ClipData.newPlainText("wallet", walletAddress);
            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                android.widget.Toast.makeText(context, "Кошелек скопирован!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        container.addView(wallet);

        fragmentView = scrollView;
        return fragmentView;
    }
}