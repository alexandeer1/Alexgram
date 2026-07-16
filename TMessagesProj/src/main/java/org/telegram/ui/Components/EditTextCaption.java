/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Components;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.CharacterStyle;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import org.jetbrains.annotations.NotNull;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.utils.CopyUtilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.AlertDialogDecor;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.LaunchActivity;

import java.util.List;
import java.util.Locale;

import tw.nekomimi.nekogram.helpers.ChatsHelper;
import tw.nekomimi.nekogram.llm.LlmConfig;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.utils.AlertUtil;

public class EditTextCaption extends EditTextBoldCursor implements FloatingToolbar.StyleDelegate {

    private static final int ACCESSIBILITY_ACTION_SHARE = 0x10000000;
    private static final int FONT_STYLE_BOLD_SERIF = 0;
    private static final int FONT_STYLE_FANCY_SCRIPT = 1;
    private static final int FONT_STYLE_DOUBLE_STRUCK = 2;
    private static final int FONT_STYLE_SMALL_CAPS = 3;
    private static final int FONT_STYLE_MONOSPACE = 4;
    private static final int FONT_STYLE_BUBBLE = 5;
    private static final int FONT_STYLE_SQUARE = 6;
    private static final int FONT_STYLE_UPSIDE_DOWN = 7;

    private static final String[] SCRIPT_UPPER = {
            "\uD835\uDC9C", "\u212C", "\uD835\uDC9E", "\uD835\uDC9F", "\u2130", "\u2131", "\uD835\uDCA2", "\u210B", "\u2110", "\uD835\uDCA5", "\uD835\uDCA6", "\u2112", "\u2133", "\uD835\uDCA9", "\uD835\uDCAA", "\uD835\uDCAB", "\uD835\uDCAC", "\u211B", "\uD835\uDCAE", "\uD835\uDCAF", "\uD835\uDCB0", "\uD835\uDCB1", "\uD835\uDCB2", "\uD835\uDCB3", "\uD835\uDCB4", "\uD835\uDCB5"
    };
    private static final String[] SCRIPT_LOWER = {
            "\uD835\uDCB6", "\uD835\uDCB7", "\uD835\uDCB8", "\uD835\uDCB9", "\u212F", "\uD835\uDCBB", "\u210A", "\uD835\uDCBD", "\uD835\uDCBE", "\uD835\uDCBF", "\uD835\uDCC0", "\uD835\uDCC1", "\uD835\uDCC2", "\uD835\uDCC3", "\u2134", "\uD835\uDCC5", "\uD835\uDCC6", "\uD835\uDCC7", "\uD835\uDCC8", "\uD835\uDCC9", "\uD835\uDCCA", "\uD835\uDCCB", "\uD835\uDCCC", "\uD835\uDCCD", "\uD835\uDCCE", "\uD835\uDCCF"
    };
    private static final String[] DOUBLE_STRUCK_UPPER = {
            "\uD835\uDD38", "\uD835\uDD39", "\u2102", "\uD835\uDD3B", "\uD835\uDD3C", "\uD835\uDD3D", "\uD835\uDD3E", "\u210D", "\uD835\uDD40", "\uD835\uDD41", "\uD835\uDD42", "\uD835\uDD43", "\uD835\uDD44", "\u2115", "\uD835\uDD46", "\u2119", "\u211A", "\u211D", "\uD835\uDD4A", "\uD835\uDD4B", "\uD835\uDD4C", "\uD835\uDD4D", "\uD835\uDD4E", "\uD835\uDD4F", "\uD835\uDD50", "\u2124"
    };
    private static final String[] SMALL_CAPS = {
            "\u1D00", "\u0299", "\u1D04", "\u1D05", "\u1D07", "\uA730", "\u0262", "\u029C", "\u026A", "\u1D0A", "\u1D0B", "\u029F", "\u1D0D", "\u0274", "\u1D0F", "\u1D18", "\u01EB", "\u0280", "\uA731", "\u1D1B", "\u1D1C", "\u1D20", "\u1D21", "x", "\u028F", "\u1D22"
    };
    private static final java.util.Map<Integer, String> UPSIDE_DOWN_MAP = createUpsideDownMap();

    private String caption;
    private StaticLayout captionLayout;
    private Text rightText;
    private int userNameLength;
    private int xOffset;
    private int yOffset;
    private boolean copyPasteShowed;
    private int hintColor;
    private EditTextCaptionDelegate delegate;
    private int selectionStart = -1;
    private int selectionEnd = -1;
    private boolean allowTextEntitiesIntersection;
    private int lineCount;
    private boolean isInitLineCount;
    private final Theme.ResourcesProvider resourcesProvider;
    private AlertDialog creationLinkDialog;
    public boolean adaptiveCreateLinkDialog;
    private int currentFontStyle = -1;
    private boolean isTransforming = false;
    private int fontTransformStart = -1;
    private int fontTransformCount = 0;

    public interface EditTextCaptionDelegate {
        void onSpansChanged();

        default long getCurrentChat() { return 0; };
    }

    public EditTextCaption(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        quoteColor = Theme.getColor(Theme.key_chat_inQuote, resourcesProvider);
        addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!isTransforming && currentFontStyle != -1 && count > 0) {
                    fontTransformStart = start;
                    fontTransformCount = count;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (currentFontStyle != -1 && fontTransformStart != -1 && !isTransforming) {
                    isTransforming = true;
                    int start = fontTransformStart;
                    int count = fontTransformCount;
                    fontTransformStart = -1;
                    fontTransformCount = 0;
                    String source = s.subSequence(start, start + count).toString();
                    String styled = applyFontStyle(source, currentFontStyle);
                    if (!TextUtils.equals(source, styled)) {
                        s.replace(start, start + count, styled);
                    }
                    isTransforming = false;
                }
                if (lineCount != getLineCount()) {
                    if (!isInitLineCount && getMeasuredWidth() > 0) {
                        onLineCountChanged(lineCount, getLineCount());
                    }
                    lineCount = getLineCount();
                }
            }
        });
        setClipToPadding(true);
    }

    protected void onLineCountChanged(int oldLineCount, int newLineCount) {

    }

    public void setCaption(String value) {
        if ((caption == null || caption.length() == 0) && (value == null || value.length() == 0) || caption != null && caption.equals(value)) {
            return;
        }
        caption = value;
        if (caption != null) {
            caption = caption.replace('\n', ' ');
        }
        requestLayout();
    }

    public void setDelegate(EditTextCaptionDelegate editTextCaptionDelegate) {
        delegate = editTextCaptionDelegate;
    }

    protected void notifySpansChanged() {
        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    public void setAllowTextEntitiesIntersection(boolean value) {
        allowTextEntitiesIntersection = value;
    }

    public boolean getAllowTextEntitiesIntersection() {
        return allowTextEntitiesIntersection;
    }

    public void makeSelectedBold() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_BOLD;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedSpoiler() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
        applyTextStyleToSelection(new TextStyleSpan(run));
        invalidateSpoilers();
    }

    public void makeSelectedItalic() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_ITALIC;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedMono() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_MONO;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedCode() {
        AlertDialog.Builder builder;
        if (adaptiveCreateLinkDialog) {
            builder = new AlertDialogDecor.Builder(getContext(), resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        }
        builder.setTitle(LocaleController.getString(R.string.CreateCode));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintText(LocaleController.getString(R.string.CreateCodeLanguage));
        editText.setHeaderHintColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.requestFocus();
        editText.setPadding(0, 0, 0, 0);
        builder.setView(editText);

        final int start;
        final int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }

        var styleSpans = getText().getSpans(start, end, CodeHighlighting.Span.class);
        if (styleSpans != null) {
            for (var oldSpan : styleSpans) {
                if (!TextUtils.isEmpty(oldSpan.lng)) {
                    editText.setText(oldSpan.lng);
                    break;
                }
            }
        }

        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
            Editable editable = getText();
            CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
            if (spans != null && spans.length > 0) {
                for (CharacterStyle oldSpan : spans) {
                    int spanStart = editable.getSpanStart(oldSpan);
                    int spanEnd = editable.getSpanEnd(oldSpan);
                    editable.removeSpan(oldSpan);
                    if (spanStart < start) {
                        editable.setSpan(oldSpan, spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (spanEnd > end) {
                        editable.setSpan(oldSpan, end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            try {
                var language = editText.getText().toString();
                editable.setSpan(new CodeHighlighting.Span(true, 0, null, language, editable.subSequence(start, end).toString()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            if (delegate != null) {
                delegate.onSpansChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        if (adaptiveCreateLinkDialog) {
            creationLinkDialog = builder.create();
            creationLinkDialog.setOnDismissListener(dialog -> {
                creationLinkDialog = null;
                requestFocus();
            });
            creationLinkDialog.setOnShowListener(dialog -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            creationLinkDialog.showDelayed(250);
        } else {
            builder.show().setOnShowListener(dialog -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
        }
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
        if (layoutParams != null) {
            if (layoutParams instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
            }
            layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.height = AndroidUtilities.dp(36);
            editText.setLayoutParams(layoutParams);
        }
        editText.setSelection(0, editText.getText().length());
    }

    public void makeSelectedStrike() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_STRIKE;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    public void makeSelectedUnderline() {
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags |= TextStyleSpan.FLAG_STYLE_UNDERLINE;
        applyTextStyleToSelection(new TextStyleSpan(run));
    }

    private String replaceAt(String origin, int start, int end, String translation) {

        String trans = origin.substring(0, start);

        trans += translation;

        trans += origin.substring(end);

        return trans;

    }

    void replaceTextInternal(int start, int end, CharSequence replacement) {
        Editable editable = getText();
        if (editable == null) {
            return;
        }

        int safeStart = Math.max(0, Math.min(start, end));
        int safeEnd = Math.min(editable.length(), Math.max(start, end));

        try {
            clearComposingText();
        } catch (Exception ignore) {
        }

        try {
            editable.replace(safeStart, safeEnd, replacement);
            int cursor = Math.min(safeStart + (replacement == null ? 0 : replacement.length()), editable.length());
            setSelection(cursor, cursor);
        } catch (Exception e) {
            FileLog.e(e);
        }

        try {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.restartInput(this);
            }
        } catch (Exception ignore) {
        }
    }

    public void makeSelectedTranslate() {
        Editable editable = getText();
        if (editable == null || TextUtils.isEmpty(editable)) {
            return;
        }

        Locale toLocale = Translator.getInputTranslateLangLocaleForChat(ChatsHelper.getChatId());

        int selectionStart = getSelectionStart();
        int selectionEnd = getSelectionEnd();
        if (selectionStart < 0 || selectionEnd < 0) {
            selectionStart = selectionEnd = 0;
        }
        final int start = Math.min(selectionStart, selectionEnd);
        final int end = Math.min(editable.length(), Math.max(selectionStart, selectionEnd));
        final String query = (start == end ? editable : editable.subSequence(start, end)).toString();

        int provider = LlmConfig.isLLMTranslatorAvailable() ? Translator.providerLLMTranslator : 0;

        Translator.translate(toLocale, query, provider, new Translator.Companion.TranslateCallBack() {
            AlertDialog status = AlertUtil.showProgress(getContext());

            {
                status.show();
            }

            @Override
            public void onSuccess(@NotNull String translation) {
                status.dismiss();
                if (start == end) replaceTextInternal(0, getText().length(), translation);
                else replaceTextInternal(start, end, translation);
            }

            @Override
            public void onFailed(boolean unsupported, @NotNull String message) {
                status.dismiss();
                AlertUtil.showTransFailedDialog(getContext(), unsupported, message, () -> {
                    status = AlertUtil.showProgress(getContext());
                    status.show();
                    Translator.translate(toLocale, query, 0, this);
                });
            }
        });
    }

    public void makeSelectedMention() {
        AlertDialog.Builder builder;
        if (adaptiveCreateLinkDialog) {
            builder = new AlertDialogDecor.Builder(getContext(), resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        }
        builder.setTitle(LocaleController.getString(R.string.CreateMention));

        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
            }
        };
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintText("ID");
        editText.setHeaderHintColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.requestFocus();
        editText.setPadding(0, 0, 0, 0);
        builder.setView(editText);

        final int start;
        final int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }

        var urlSpans = getText().getSpans(start, end, URLSpanUserMention.class);
        if (urlSpans != null) {
            for (var oldSpan : urlSpans) {
                var url = oldSpan.getURL();
                if (!TextUtils.isEmpty(url)) {
                    editText.setText(url);
                    break;
                }
            }
        }

        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
            Editable editable = getText();
            CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
            if (spans != null && spans.length > 0) {
                for (CharacterStyle oldSpan : spans) {
                    int spanStart = editable.getSpanStart(oldSpan);
                    int spanEnd = editable.getSpanEnd(oldSpan);
                    editable.removeSpan(oldSpan);
                    if (spanStart < start) {
                        editable.setSpan(oldSpan, spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                    if (spanEnd > end) {
                        editable.setSpan(oldSpan, end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                }
            }
            try {
                editable.setSpan(new URLSpanUserMention(editText.getText().toString(), 3), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            if (delegate != null) {
                delegate.onSpansChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        if (adaptiveCreateLinkDialog) {
            creationLinkDialog = builder.create();
            creationLinkDialog.setOnDismissListener(dialog -> {
                creationLinkDialog = null;
                requestFocus();
            });
            creationLinkDialog.setOnShowListener(dialog -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            creationLinkDialog.showDelayed(250);
        } else {
            builder.show().setOnShowListener(dialog -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
        }
        ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
        if (layoutParams != null) {
            if (layoutParams instanceof FrameLayout.LayoutParams) {
                ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
            }
            layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(24);
            layoutParams.height = AndroidUtilities.dp(36);
            editText.setLayoutParams(layoutParams);
        }
        editText.setSelection(0, editText.getText().length());
    }

    // Toggle an inline style over the current selection, mirroring a formatting button: if the style
    // already fully covers the selection it's removed, otherwise it's added. Mono is kept mutually
    // exclusive with every other inline style, and sub/superscript exclude each other (same rules the
    // rich editor uses), so there's no conflict between mono and the rest.
    public void toggleStyleForSelection(int flag) {
        final Editable editable = getText();
        if (editable == null) {
            return;
        }
        int start = getSelectionStart();
        int end = getSelectionEnd();
        if (start < 0 || end < 0) {
            return;
        }
        if (start > end) {
            final int tmp = start;
            start = end;
            end = tmp;
        }
        if (start >= end) {
            return;
        }
        final boolean add = (getCurrentStyle(start, end) & flag) == 0;
        if (add) {
            int clearMask;
            if (flag == TextStyleSpan.FLAG_STYLE_MONO) {
                clearMask = TextStyleSpan.FLAG_STYLE_BOLD | TextStyleSpan.FLAG_STYLE_ITALIC | TextStyleSpan.FLAG_STYLE_UNDERLINE
                    | TextStyleSpan.FLAG_STYLE_STRIKE | TextStyleSpan.FLAG_STYLE_SPOILER
                    | TextStyleSpan.FLAG_STYLE_SUBSCRIPT | TextStyleSpan.FLAG_STYLE_SUPERSCRIPT;
            } else {
                clearMask = TextStyleSpan.FLAG_STYLE_MONO;
                if (flag == TextStyleSpan.FLAG_STYLE_SUBSCRIPT) clearMask |= TextStyleSpan.FLAG_STYLE_SUPERSCRIPT;
                else if (flag == TextStyleSpan.FLAG_STYLE_SUPERSCRIPT) clearMask |= TextStyleSpan.FLAG_STYLE_SUBSCRIPT;
            }
            removeStyle(clearMask, start, end);
            addStyle(flag, start, end);
        } else {
            removeStyle(flag, start, end);
        }
    }

    public void makeSelectedQuote() {
        makeSelectedQuote(false);
    }

    public void makeSelectedQuote(boolean collapse) {
        int start, end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }
        final int setSelection = QuoteSpan.putQuoteToEditable(getText(), start, end, collapse);
        if (setSelection >= 0) {
            setSelection(setSelection);
            resetFontMetricsCache();
        }
        invalidateQuotes(true);
        invalidateSpoilers();
    }

    public void makeSelectedDate() {
        int start, end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }

        AlertsCreator.createFormattedDatePickerDialog(getContext(), (scheduleDate, flags) -> {
            Editable editable = getText();

            TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
            run.flags |= TextStyleSpan.FLAG_STYLE_URL;
            run.start = start;
            run.end = end;

            TLRPC.TL_messageEntityFormattedDate entity = new TLRPC.TL_messageEntityFormattedDate();
            entity.date = scheduleDate;
            entity.flags = flags;
            entity.applyFlags();

            try {
                editable.setSpan(new FormattedDateSpan(editable.subSequence(start, end).toString(), run, entity), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {

            }
            if (delegate != null) {
                delegate.onSpansChanged();
            }
        }, () -> {}, resourcesProvider);
    }

    public void translateSelected() {
        int start, end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }

        final CharSequence text = getText().subSequence(start, end);

        final BaseFragment lastFragment = LaunchActivity.getSafeLastFragment();

        new TranslateAlert3(getContext(), lastFragment != null ? lastFragment.getResourceProvider() : null)
            .setText(text)
            .setOnUse(translatedText -> {
                getText().replace(start, end, translatedText);
                setSelection(start, start + translatedText.length());
            })
            .show();

        setSelection(start, end);
    }

    public void makeSelectedChangeFont() {
        Editable editable = getText();

        final int start;
        final int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = Math.min(selectionStart, selectionEnd);
            end = Math.max(selectionStart, selectionEnd);
            selectionStart = selectionEnd = -1;
        } else {
            start = Math.min(getSelectionStart(), getSelectionEnd());
            end = Math.max(getSelectionStart(), getSelectionEnd());
        }

        CharSequence[] options = new CharSequence[]{
                LocaleController.getString(R.string.Regular),
                "Bold Serif Font  (\uD835\uDC07\uD835\uDC1E\uD835\uDC25\uD835\uDC25\uD835\uDC28)",
                "Fancy Script Font  (\uD835\uDCE7\uD835\uDCEE\uD835\uDCF5\uD835\uDCF5\uD835\uDCF8)",
                "Double Struck Font  (\u210D\uD835\uDD56\uD835\uDD5D\uD835\uDD5D\uD835\uDD60)",
                "Small Caps Font  (\u029C\u1D07\u029F\u029F\u1D0F)",
                "Monospace Hacker Font  (\uD835\uDE77\uD835\uDE8E\uD835\uDE95\uD835\uDE95\uD835\uDE98)",
                "Bubble Circle Font  (\u24BD\u24D4\u24DB\u24DB\u24DE)",
                "Square Font  (\uD83C\uDD77\uD83C\uDD74\uD83C\uDD7B\uD83C\uDD7B\uD83C\uDD7E)",
                "Upside Down Font  (oll\u01DD\u0265)"
        };

        AlertDialog.Builder builder;
        if (adaptiveCreateLinkDialog) {
            builder = new AlertDialogDecor.Builder(getContext(), resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        }
        builder.setTitle(LocaleController.getString(R.string.ChangeFont));
        builder.setItems(options, (dialog, which) -> {
            int style = which - 1;
            if (start < 0 || end < 0 || start == end || editable == null || end > (editable != null ? editable.length() : 0)) {
                currentFontStyle = style;
                return;
            }
            if (style == -1) {
                currentFontStyle = -1;
                return;
            }
            String source = editable.subSequence(start, end).toString();
            String styled = applyFontStyle(source, style);
            if (!TextUtils.isEmpty(styled) && !TextUtils.equals(source, styled)) {
                replaceTextInternal(start, end, styled);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);

        if (adaptiveCreateLinkDialog) {
            creationLinkDialog = builder.create();
            creationLinkDialog.setOnDismissListener(dialog -> {
                creationLinkDialog = null;
                requestFocus();
            });
            creationLinkDialog.showDelayed(250);
        } else {
            builder.show();
        }
    }

    private interface CodePointMapper {
        String map(int codePoint);
    }

    private String applyFontStyle(String text, int style) {
        switch (style) {
            case FONT_STYLE_BOLD_SERIF:
                return transformText(text, false, this::mapBoldSerif);
            case FONT_STYLE_FANCY_SCRIPT:
                return transformText(text, false, this::mapFancyScript);
            case FONT_STYLE_DOUBLE_STRUCK:
                return transformText(text, false, this::mapDoubleStruck);
            case FONT_STYLE_SMALL_CAPS:
                return transformText(text, false, this::mapSmallCaps);
            case FONT_STYLE_MONOSPACE:
                return transformText(text, false, this::mapMonospace);
            case FONT_STYLE_BUBBLE:
                return transformText(text, false, this::mapBubbleCircle);
            case FONT_STYLE_SQUARE:
                return transformText(text, false, this::mapSquare);
            case FONT_STYLE_UPSIDE_DOWN:
                return transformText(text, true, this::mapUpsideDown);
            default:
                return text;
        }
    }

    private String transformText(String text, boolean reverse, CodePointMapper mapper) {
        if (TextUtils.isEmpty(text)) {
            return text;
        }
        int length = text.length();
        int[] codePoints = new int[text.codePointCount(0, length)];
        int index = 0;
        for (int i = 0; i < length; ) {
            int cp = text.codePointAt(i);
            codePoints[index++] = cp;
            i += Character.charCount(cp);
        }

        StringBuilder out = new StringBuilder();
        if (reverse) {
            for (int i = codePoints.length - 1; i >= 0; i--) {
                appendMapped(out, mapper, codePoints[i]);
            }
        } else {
            for (int codePoint : codePoints) {
                appendMapped(out, mapper, codePoint);
            }
        }
        return out.toString();
    }

    private void appendMapped(StringBuilder out, CodePointMapper mapper, int codePoint) {
        String mapped = mapper.map(codePoint);
        if (mapped != null) {
            out.append(mapped);
        } else {
            out.appendCodePoint(codePoint);
        }
    }

    private String mapBoldSerif(int codePoint) {
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return codePointToString(0x1D400 + (codePoint - 'A'));
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return codePointToString(0x1D41A + (codePoint - 'a'));
        }
        if (codePoint >= '0' && codePoint <= '9') {
            return codePointToString(0x1D7CE + (codePoint - '0'));
        }
        return null;
    }

    private String mapFancyScript(int codePoint) {
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return SCRIPT_UPPER[codePoint - 'A'];
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return SCRIPT_LOWER[codePoint - 'a'];
        }
        return null;
    }

    private String mapDoubleStruck(int codePoint) {
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return DOUBLE_STRUCK_UPPER[codePoint - 'A'];
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return codePointToString(0x1D552 + (codePoint - 'a'));
        }
        if (codePoint >= '0' && codePoint <= '9') {
            return codePointToString(0x1D7D8 + (codePoint - '0'));
        }
        return null;
    }

    private String mapSmallCaps(int codePoint) {
        int lower = Character.toLowerCase(codePoint);
        if (lower >= 'a' && lower <= 'z') {
            return SMALL_CAPS[lower - 'a'];
        }
        return null;
    }

    private String mapMonospace(int codePoint) {
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return codePointToString(0x1D670 + (codePoint - 'A'));
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return codePointToString(0x1D68A + (codePoint - 'a'));
        }
        if (codePoint >= '0' && codePoint <= '9') {
            return codePointToString(0x1D7F6 + (codePoint - '0'));
        }
        return null;
    }

    private String mapBubbleCircle(int codePoint) {
        if (codePoint >= 'A' && codePoint <= 'Z') {
            return codePointToString(0x24B6 + (codePoint - 'A'));
        }
        if (codePoint >= 'a' && codePoint <= 'z') {
            return codePointToString(0x24D0 + (codePoint - 'a'));
        }
        if (codePoint == '0') {
            return codePointToString(0x24EA);
        }
        if (codePoint >= '1' && codePoint <= '9') {
            return codePointToString(0x2460 + (codePoint - '1'));
        }
        return null;
    }

    private String mapSquare(int codePoint) {
        int upper = Character.toUpperCase(codePoint);
        if (upper >= 'A' && upper <= 'Z') {
            return codePointToString(0x1F130 + (upper - 'A'));
        }
        return null;
    }

    private String mapUpsideDown(int codePoint) {
        String mapped = UPSIDE_DOWN_MAP.get(codePoint);
        if (mapped != null) {
            return mapped;
        }
        int lower = Character.toLowerCase(codePoint);
        return UPSIDE_DOWN_MAP.get(lower);
    }

    private static String codePointToString(int codePoint) {
        return new String(Character.toChars(codePoint));
    }

    private static java.util.Map<Integer, String> createUpsideDownMap() {
        java.util.Map<Integer, String> map = new java.util.HashMap<>();
        map.put((int) 'a', "\u0250");
        map.put((int) 'b', "q");
        map.put((int) 'c', "\u0254");
        map.put((int) 'd', "p");
        map.put((int) 'e', "\u01DD");
        map.put((int) 'f', "\u025F");
        map.put((int) 'g', "\u0183");
        map.put((int) 'h', "\u0265");
        map.put((int) 'i', "\u1D09");
        map.put((int) 'j', "\u027E");
        map.put((int) 'k', "\u029E");
        map.put((int) 'l', "\u05DF");
        map.put((int) 'm', "\u026F");
        map.put((int) 'n', "u");
        map.put((int) 'o', "o");
        map.put((int) 'p', "d");
        map.put((int) 'q', "b");
        map.put((int) 'r', "\u0279");
        map.put((int) 's', "s");
        map.put((int) 't', "\u0287");
        map.put((int) 'u', "n");
        map.put((int) 'v', "\u028C");
        map.put((int) 'w', "\u028D");
        map.put((int) 'x', "x");
        map.put((int) 'y', "\u028E");
        map.put((int) 'z', "z");
        map.put((int) '?', "\u00BF");
        map.put((int) '!', "\u00A1");
        map.put((int) '.', "\u02D9");
        map.put((int) ',', "'");
        map.put((int) '\'', ",");
        map.put((int) '"', ",,");
        map.put((int) '(', ")");
        map.put((int) ')', "(");
        map.put((int) '[', "]");
        map.put((int) ']', "[");
        map.put((int) '{', "}");
        map.put((int) '}', "{");
        return map;
    }

    public void makeSelectedUrl() {
        AlertDialog.Builder builder;
        if (adaptiveCreateLinkDialog) {
            builder = new AlertDialogDecor.Builder(getContext(), resourcesProvider);
        } else {
            builder = new AlertDialog.Builder(getContext(), resourcesProvider);
        }
        builder.setTitle(LocaleController.getString(R.string.CreateLink));

        FrameLayout container = new FrameLayout(getContext());
        final EditTextBoldCursor editText = new EditTextBoldCursor(getContext()) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64), MeasureSpec.EXACTLY));
            }
        };
        final String def = "http://";
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        editText.setText(def);
        editText.setTextColor(getThemedColor(Theme.key_dialogTextBlack));
        editText.setHintText(LocaleController.getString(R.string.URL));
        editText.setHeaderHintColor(getThemedColor(Theme.key_windowBackgroundWhiteBlueHeader));
        editText.setSingleLine(true);
        editText.setFocusable(true);
        editText.setTransformHintToHeader(true);
        editText.setLineColors(getThemedColor(Theme.key_windowBackgroundWhiteInputField), getThemedColor(Theme.key_windowBackgroundWhiteInputFieldActivated), getThemedColor(Theme.key_text_RedRegular));
        editText.setImeOptions(EditorInfo.IME_ACTION_DONE);
        editText.setBackgroundDrawable(null);
        editText.requestFocus();
        editText.setPadding(0, 0, 0, 0);
        editText.setHighlightColor(getThemedColor(Theme.key_chat_inTextSelectionHighlight));
        editText.setHandlesColor(getThemedColor(Theme.key_chat_TextSelectionCursor));
        container.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));

        TextView pasteTextView = new TextView(getContext());
        pasteTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        pasteTextView.setTypeface(AndroidUtilities.bold());
        pasteTextView.setText(getString(R.string.Paste));
        pasteTextView.setPadding(dp(10), 0, dp(10), 0);
        pasteTextView.setGravity(Gravity.CENTER);
        int textColor = getThemedColor(Theme.key_windowBackgroundWhiteBlueText2);
        pasteTextView.setTextColor(textColor);
        pasteTextView.setBackground(Theme.createSimpleSelectorRoundRectDrawable(dp(6), Theme.multAlpha(textColor, .12f), Theme.multAlpha(textColor, .15f)));
        ScaleStateListAnimator.apply(pasteTextView, .1f, 1.5f);
        container.addView(pasteTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 26, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 24, 3));

        Runnable checkPaste = () -> {
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            final boolean show = (TextUtils.isEmpty(editText.getText()) || TextUtils.equals(editText.getText().toString(), def)) && clipboardManager != null && clipboardManager.hasPrimaryClip();
            pasteTextView.animate()
                .alpha(show ? 1f : 0f)
                .scaleX(show ? 1f : .7f)
                .scaleY(show ? 1f : .7f)
                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                .setDuration(300)
                .start();
        };
        pasteTextView.setOnClickListener(v -> {
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            CharSequence text = null;
            try {
                text = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(getContext());
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (text != null) {
                editText.setText(text);
                editText.setSelection(0, editText.getText().length());
            }
            checkPaste.run();
        });
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                checkPaste.run();
            }
        });

        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
            CharSequence text = null;
            try {
                text = clipboardManager.getPrimaryClip().getItemAt(0).coerceToText(getContext());
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (text != null) {
                editText.setText(text);
                editText.setSelection(0, editText.getText().length());
            }
        }

        checkPaste.run();

        builder.setView(container);

        final int start;
        final int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }

        var urlSpans = getText().getSpans(start, end, URLSpanReplacement.class);
        if (urlSpans != null) {
            for (var span : urlSpans) {
                var url = span.getURL();
                if (!TextUtils.isEmpty(url)) {
                    editText.setText(url);
                    break;
                }
            }
        }

        builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
            Editable editable = getText();
            CharacterStyle[] spans = editable.getSpans(start, end, CharacterStyle.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    CharacterStyle oldSpan = spans[a];
                    if (!(oldSpan instanceof AnimatedEmojiSpan) && !(oldSpan instanceof QuoteSpan.QuoteStyleSpan)) {
                        int spanStart = editable.getSpanStart(oldSpan);
                        int spanEnd = editable.getSpanEnd(oldSpan);
                        editable.removeSpan(oldSpan);
                        if (spanStart < start) {
                            editable.setSpan(oldSpan, spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                        if (spanEnd > end) {
                            editable.setSpan(oldSpan, end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        }
                    }
                }
            }
            try {
                final String url = editText.getText().toString().trim();
                editable.setSpan(new URLSpanReplacement(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } catch (Exception ignore) {}
            if (delegate != null) {
                delegate.onSpansChanged();
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        if (adaptiveCreateLinkDialog) {
            creationLinkDialog = builder.create();
            creationLinkDialog.setOnDismissListener(dialog -> {
                creationLinkDialog = null;
                requestFocus();
            });
            creationLinkDialog.setOnShowListener(dialog -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
            creationLinkDialog.showDelayed(250);
        } else {
            builder.show().setOnShowListener(dialog -> {
                editText.requestFocus();
                AndroidUtilities.showKeyboard(editText);
            });
        }
        if (editText != null) {
            ViewGroup.MarginLayoutParams layoutParams = (ViewGroup.MarginLayoutParams) editText.getLayoutParams();
            if (layoutParams != null) {
                if (layoutParams instanceof FrameLayout.LayoutParams) {
                    ((FrameLayout.LayoutParams) layoutParams).gravity = Gravity.CENTER_HORIZONTAL;
                }
                layoutParams.rightMargin = layoutParams.leftMargin = AndroidUtilities.dp(24);
                layoutParams.height = AndroidUtilities.dp(36);
                editText.setLayoutParams(layoutParams);
            }
            editText.setSelection(0, editText.getText().length());
        }
    }

    public boolean closeCreationLinkDialog(boolean invoked) {
        if (creationLinkDialog != null && creationLinkDialog.isShowing()) {
            if (invoked) creationLinkDialog.dismiss();
            return true;
        }
        return false;
    }

    public void makeSelectedRegular() {
        applyTextStyleToSelection(null);
    }

    public void setSelectionOverride(int start, int end) {
        selectionStart = start;
        selectionEnd = end;
    }

    private static final int[] STYLE_FLAGS = {
        TextStyleSpan.FLAG_STYLE_BOLD,
        TextStyleSpan.FLAG_STYLE_ITALIC,
        TextStyleSpan.FLAG_STYLE_MONO,
        TextStyleSpan.FLAG_STYLE_STRIKE,
        TextStyleSpan.FLAG_STYLE_UNDERLINE,
        TextStyleSpan.FLAG_STYLE_SPOILER,
        TextStyleSpan.FLAG_STYLE_SUBSCRIPT,
        TextStyleSpan.FLAG_STYLE_SUPERSCRIPT,
    };

    private static int spanStyleFlags(TextStyleSpan span) {
        int flags = span.getStyleFlags();
        if ((flags & TextStyleSpan.FLAG_STYLE_SPOILER_REVEALED) != 0) {
            flags |= TextStyleSpan.FLAG_STYLE_SPOILER;
        }
        return flags;
    }

    @Override
    public int getCurrentStyle(int start, int end) {
        Editable editable = getText();
        if (editable == null) {
            return 0;
        }
        start = Math.max(0, start);
        end = Math.min(end, editable.length());
        if (start < 0 || end < 0 || start >= end) {
            return 0;
        }
        TextStyleSpan[] spans = editable.getSpans(start, end, TextStyleSpan.class);
        int result = 0;
        for (int flag : STYLE_FLAGS) {
            // spans aren't returned in position order, so loop to a fixpoint extending
            // the covered range by any span of this flag that touches it
            int covered = start;
            boolean advanced = true;
            while (advanced && covered < end) {
                advanced = false;
                for (int a = 0; a < spans.length; ++a) {
                    if ((spanStyleFlags(spans[a]) & flag) == 0) {
                        continue;
                    }
                    int spanStart = editable.getSpanStart(spans[a]);
                    int spanEnd = editable.getSpanEnd(spans[a]);
                    if (spanStart <= covered && spanEnd > covered) {
                        covered = spanEnd;
                        advanced = true;
                    }
                }
            }
            if (covered >= end) {
                result |= flag;
            }
        }
        return result;
    }

    @Override
    public void addStyle(int flag, int start, int end) {
        Editable editable = getText();
        if (editable == null || start < 0 || end < 0 || start >= end) {
            return;
        }
        end = Math.min(end, editable.length());
        if (start >= end) {
            return;
        }
        TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun();
        run.flags = flag;
        MediaDataController.addStyleToText(new TextStyleSpan(run), start, end, editable, true);
        if ((flag & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
            invalidateSpoilers();
        }
        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    @Override
    public void removeStyle(int flag, int start, int end) {
        Editable editable = getText();
        if (editable == null || start < 0 || end < 0 || start >= end) {
            return;
        }
        end = Math.min(end, editable.length());
        int removeMask = flag;
        if ((flag & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
            removeMask |= TextStyleSpan.FLAG_STYLE_SPOILER_REVEALED;
        }
        TextStyleSpan[] spans = editable.getSpans(start, end, TextStyleSpan.class);
        for (int a = 0; a < spans.length; ++a) {
            TextStyleSpan span = spans[a];
            int spanFlags = span.getStyleFlags();
            if ((spanFlags & removeMask) == 0) {
                continue;
            }
            int spanStart = editable.getSpanStart(span);
            int spanEnd = editable.getSpanEnd(span);
            editable.removeSpan(span);
            if (spanStart < start) {
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(span.getTextStyleRun());
                editable.setSpan(new TextStyleSpan(run), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (spanEnd > end) {
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(span.getTextStyleRun());
                editable.setSpan(new TextStyleSpan(run), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            int interStart = Math.max(spanStart, start);
            int interEnd = Math.min(spanEnd, end);
            int newFlags = spanFlags & ~removeMask;
            if (newFlags != 0 && interStart < interEnd) {
                TextStyleSpan.TextStyleRun run = new TextStyleSpan.TextStyleRun(span.getTextStyleRun());
                run.flags = newFlags;
                editable.setSpan(new TextStyleSpan(run), interStart, interEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if ((flag & TextStyleSpan.FLAG_STYLE_SPOILER) != 0) {
            invalidateSpoilers();
        }
        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    private void applyTextStyleToSelection(TextStyleSpan span) {
        int start;
        int end;
        if (selectionStart >= 0 && selectionEnd >= 0) {
            start = selectionStart;
            end = selectionEnd;
            selectionStart = selectionEnd = -1;
        } else {
            start = getSelectionStart();
            end = getSelectionEnd();
        }
        MediaDataController.addStyleToText(span, start, end, getText(), allowTextEntitiesIntersection);

        if (span == null) {
            Editable editable = getText();
            CodeHighlighting.Span[] code = editable.getSpans(start, end, CodeHighlighting.Span.class);
            for (int i = 0; i < code.length; ++i)
                editable.removeSpan(code[i]);
            QuoteSpan[] quotes = editable.getSpans(start, end, QuoteSpan.class);
            for (int i = 0; i < quotes.length; ++i) {
                editable.removeSpan(quotes[i]);
                editable.removeSpan(quotes[i].styleSpan);
                if (quotes[i].collapsedSpan != null) {
                    editable.removeSpan(quotes[i].collapsedSpan);
                }
            }
            if (quotes.length > 0) {
                invalidateQuotes(true);
            }
        }

        if (delegate != null) {
            delegate.onSpansChanged();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (Build.VERSION.SDK_INT < 23 && !hasWindowFocus && copyPasteShowed) {
            return;
        }
        try {
            super.onWindowFocusChanged(hasWindowFocus);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    protected void onContextMenuOpen() {

    }

    protected void onContextMenuClose() {

    }

    private ActionMode.Callback overrideCallback(final ActionMode.Callback callback) {
        ActionMode.Callback wrap = new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                copyPasteShowed = true;
                onContextMenuOpen();
                return callback.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                boolean result = callback.onPrepareActionMode(mode, menu);
                if (hasSelection()) {
                    if (menu.findItem(R.id.menu_change_font) == null) {
                        MenuItem menuItem = menu.add(Menu.NONE, R.id.menu_change_font, 100, LocaleController.getString(R.string.ChangeFont));
                        menuItem.setIcon(R.drawable.msg_edit);
                        menuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
                    }
                }
                return result;
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (performMenuAction(item.getItemId())) {
                    mode.finish();
                    return true;
                }
                try {
                    return callback.onActionItemClicked(mode, item);
                } catch (Exception ignore) {
                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                copyPasteShowed = false;
                onContextMenuClose();
                callback.onDestroyActionMode(mode);
            }
        };
        if (Build.VERSION.SDK_INT >= 23) {
            return new ActionMode.Callback2() {
                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return wrap.onCreateActionMode(mode, menu);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return wrap.onPrepareActionMode(mode, menu);
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return wrap.onActionItemClicked(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    wrap.onDestroyActionMode(mode);
                }

                @Override
                public void onGetContentRect(ActionMode mode, View view, Rect outRect) {
                    if (callback instanceof ActionMode.Callback2) {
                        ((ActionMode.Callback2) callback).onGetContentRect(mode, view, outRect);
                    } else {
                        super.onGetContentRect(mode, view, outRect);
                    }
                }
            };
        } else {
            return wrap;
        }
    }

    public boolean performMenuAction(int itemId) {
        if (itemId == R.id.menu_regular) {
            makeSelectedRegular();
            return true;
        } else if (itemId == R.id.menu_bold) {
            makeSelectedBold();
            return true;
        } else if (itemId == R.id.menu_italic) {
            makeSelectedItalic();
            return true;
        } else if (itemId == R.id.menu_mono) {
            makeSelectedMono();
            return true;
        } else if (itemId == R.id.menu_code) {
            makeSelectedCode();
            return true;
        } else if (itemId == R.id.menu_link) {
            makeSelectedUrl();
            return true;
        } else if (itemId == R.id.menu_mention) {
            makeSelectedMention();
            return true;
        } else if (itemId == R.id.menu_strike) {
            makeSelectedStrike();
            return true;
        } else if (itemId == R.id.menu_underline) {
            makeSelectedUnderline();
            return true;
        } else if (itemId == R.id.menu_spoiler) {
            makeSelectedSpoiler();
            return true;
        } else if (itemId == R.id.menu_translate) {
            // NekoX
            makeSelectedTranslate();
            return true;
        } else if (itemId == R.id.menu_quote) {
            makeSelectedQuote();
            return true;
        } else if (itemId == R.id.menu_date) {
            makeSelectedDate();
            return true;
        } else if (itemId == R.id.menu_change_font) {
            makeSelectedChangeFont();
            return true;
        } else if (itemId == R.id.menu_translate) {
            translateSelected();
            return true;
        }
        return false;
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback, int type) {
        return super.startActionMode(overrideCallback(callback), type);
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback) {
        return super.startActionMode(overrideCallback(callback));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            isInitLineCount = getMeasuredWidth() == 0 && getMeasuredHeight() == 0;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            if (isInitLineCount) {
                lineCount = getLineCount();
            }
            isInitLineCount = false;
        } catch (Exception e) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(51));
            FileLog.e(e);
        }

        captionLayout = null;

        if (caption != null && caption.length() > 0) {
            CharSequence text = getText();
            if (text.length() > 1 && text.charAt(0) == '@') {
                int index = TextUtils.indexOf(text, ' ');
                if (index != -1) {
                    TextPaint paint = getPaint();
                    CharSequence str = text.subSequence(0, index + 1);
                    int size = (int) Math.ceil(paint.measureText(text, 0, index + 1));
                    int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                    userNameLength = str.length();
                    CharSequence captionFinal = TextUtils.ellipsize(caption, paint, width - size, TextUtils.TruncateAt.END);
                    xOffset = size;
                    try {
                        captionLayout = new StaticLayout(captionFinal, getPaint(), width - size, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (captionLayout.getLineCount() > 0) {
                            xOffset += -captionLayout.getLineLeft(0);
                        }
                        yOffset = (getMeasuredHeight() - captionLayout.getLineBottom(0)) / 2 + AndroidUtilities.dp(0.5f);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        }
    }

    public boolean isNearRightCaption(int rightMargin) {
        final Layout layout = getLayout();
        if (layout == null || layout.getLineCount() <= 0) return false;
        if (layout.getLineCount() > 1) return true;
        return layout.getLineRight(0) + rightMargin >= getWidth() - getPaddingLeft() - getPaddingRight();
    }

    public String getCaption() {
        return caption;
    }

    @Override
    public void setHintColor(int value) {
        super.setHintColor(value);
        hintColor = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(0, offsetY);
        super.onDraw(canvas);
        try {
            if (captionLayout != null && userNameLength == length()) {
                Paint paint = getPaint();
                int oldColor = getPaint().getColor();
                paint.setColor(hintColor);
                canvas.save();
                canvas.translate(xOffset, yOffset);
                captionLayout.draw(canvas);
                canvas.restore();
                paint.setColor(oldColor);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (rightText != null && length() != 0) {
            final Layout layout = getLayout();
            if (layout != null && layout.getLineCount() > 0) {
                final float right = layout.getLineRight(0);
                rightText.draw(canvas, right, getHeight() / 2f + dp(1), hintColor, 1.0f);
            }
        }
        canvas.restore();
    }

    public void setRightText(CharSequence text) {
        this.rightText = new Text(text, 16, getTypeface());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        final AccessibilityNodeInfoCompat infoCompat = AccessibilityNodeInfoCompat.wrap(info);
        if (!TextUtils.isEmpty(caption)) {
            infoCompat.setHintText(caption);
        }
        final List<AccessibilityNodeInfoCompat.AccessibilityActionCompat> actions = infoCompat.getActionList();
        for (int i = 0, size = actions.size(); i < size; i++) {
            final AccessibilityNodeInfoCompat.AccessibilityActionCompat action = actions.get(i);
            if (action.getId() == ACCESSIBILITY_ACTION_SHARE) {
                infoCompat.removeAction(action);
                break;
            }
        }
        if (hasSelection()) {
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_spoiler, LocaleController.getString(R.string.Spoiler)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_bold, LocaleController.getString(R.string.Bold)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_italic, LocaleController.getString(R.string.Italic)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_mono, LocaleController.getString(R.string.Mono)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_code, LocaleController.getString(R.string.MonoCode)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_strike, LocaleController.getString(R.string.Strike)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_underline, LocaleController.getString(R.string.Underline)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_mention, LocaleController.getString(R.string.CreateMention)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_link, LocaleController.getString(R.string.CreateLink)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_regular, LocaleController.getString(R.string.Regular)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_change_font, LocaleController.getString(R.string.ChangeFont)));
            infoCompat.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(R.id.menu_date, LocaleController.getString(R.string.FormattedDate)));
        }
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return performMenuAction(action) || super.performAccessibilityAction(action, arguments);
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste) {
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData != null && clipData.getItemCount() == 1 && clipData.getDescription().hasMimeType("text/html")) {
                try {
                    String html = clipData.getItemAt(0).getHtmlText();
                    SpannableStringBuilder pasted = new SpannableStringBuilder(CopyUtilities.fromHTML(html));
                    Emoji.replaceEmoji(pasted, getPaint().getFontMetricsInt(), false, null);
                    AnimatedEmojiSpan[] spans = pasted.getSpans(0, pasted.length(), AnimatedEmojiSpan.class);
                    if (spans != null) {
                        for (int k = 0; k < spans.length; ++k) {
                            spans[k].applyFontMetrics(getPaint().getFontMetricsInt(), AnimatedEmojiDrawable.getCacheTypeForEnterView());
                        }
                    }
                    int start = Math.max(0, getSelectionStart());
                    int end = Math.min(getText().length(), getSelectionEnd());
                    QuoteSpan.QuoteStyleSpan[] quotesInSelection = getText().getSpans(start, end, QuoteSpan.QuoteStyleSpan.class);
                    if (quotesInSelection != null && quotesInSelection.length > 0) {
                        QuoteSpan.QuoteStyleSpan[] quotesToDelete = pasted.getSpans(0, pasted.length(), QuoteSpan.QuoteStyleSpan.class);
                        for (int i = 0; i < quotesToDelete.length; ++i) {
                            pasted.removeSpan(quotesToDelete[i]);
                            pasted.removeSpan(quotesToDelete[i].span);
                        }
                    } else {
                        QuoteSpan.normalizeQuotes(pasted);
                    }
                    setText(getText().replace(start, end, pasted));
                    setSelection(start + pasted.length(), start + pasted.length());
                    return true;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        } else if (id == android.R.id.copy) {
            int start = Math.max(0, getSelectionStart());
            int end = Math.min(getText().length(), getSelectionEnd());
            try {
                AndroidUtilities.addToClipboard(getText().subSequence(start, end));
                Activity activity = AndroidUtilities.findActivity(getContext());
                activity.closeContextMenu();
                if (floatingActionMode != null) {
                    floatingActionMode.finish();
                }
                setSelection(start, end);
                return true;
            } catch (Exception e) {}
        } else if (id == android.R.id.cut) {
            int start = Math.max(0, getSelectionStart());
            int end = Math.min(getText().length(), getSelectionEnd());
            try {
                AndroidUtilities.addToClipboard(getText().subSequence(start, end));
                SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
                if (start != 0) {
                    stringBuilder.append(getText().subSequence(0, start));
                }
                if (end != getText().length()) {
                    stringBuilder.append(getText().subSequence(end, getText().length()));
                }
                setText(stringBuilder);
                setSelection(start, start);
                return true;
            } catch (Exception e) {}
        }
        return super.onTextContextMenuItem(id);
    }
}
