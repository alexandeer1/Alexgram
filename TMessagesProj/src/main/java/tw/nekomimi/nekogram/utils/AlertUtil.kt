package tw.nekomimi.nekogram.utils

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.ApplicationLoader
import org.telegram.messenger.LocaleController.getString
import org.telegram.messenger.R
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.Components.BulletinFactory
import tw.nekomimi.nekogram.NekoConfig
import tw.nekomimi.nekogram.translate.Translator
import tw.nekomimi.nekogram.ui.BottomBuilder
import tw.nekomimi.nekogram.ui.PopupBuilder
import java.util.concurrent.atomic.AtomicReference

object AlertUtil {

    @JvmStatic
    fun copyAndAlert(text: String, fragment: BaseFragment? = null) {
        if (AndroidUtilities.addToClipboard(text)) {
            BulletinFactory.of(fragment).createCopyBulletin(getString(R.string.TextCopied)).show()
        }
    }

    @JvmStatic
    fun copyLinkAndAlert(text: String, fragment: BaseFragment? = null) {
        AndroidUtilities.addToClipboard(text)
        BulletinFactory.of(fragment).createCopyBulletin(getString(R.string.LinkCopied)).show()
    }

    @JvmStatic
    fun call(number: String) {
        runCatching {
            val intent = Intent(Intent.ACTION_DIAL, ("tel:+$number").toUri())
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ApplicationLoader.applicationContext.startActivity(intent)
        }.onFailure {
            showToast(it)
        }
    }

    @JvmStatic
    fun showToast(e: Throwable) = showToast(e.message ?: e.javaClass.simpleName)

    @JvmStatic
    fun showToast(e: TLRPC.TL_error?) {
        if (e == null) return
        showToast("${e.code}: ${e.text}")
    }

    @JvmStatic
    fun showToast(text: String) = AndroidUtilities.runOnUIThread {
        Toast.makeText(
            ApplicationLoader.applicationContext,
            text.takeIf { it.isNotBlank() }
                ?: "喵 !",
            Toast.LENGTH_LONG
        ).show()
    }

    @JvmStatic
    fun showSimpleAlert(ctx: Context?, error: Throwable) {
        showSimpleAlert(ctx, null, error.message ?: error.javaClass.simpleName)
    }

    @JvmStatic
    @JvmOverloads
    fun showSimpleAlert(ctx: Context?, text: String, listener: ((AlertDialog.Builder) -> Unit)? = null) {
        showSimpleAlert(ctx, null, text, listener)
    }

    @JvmStatic
    @JvmOverloads
    fun showSimpleAlert(ctx: Context?, title: String?, text: String, listener: ((AlertDialog.Builder) -> Unit)? = null) = AndroidUtilities.runOnUIThread(Runnable {
        if (ctx == null) return@Runnable

        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(title ?: getString(R.string.NagramX))
        builder.setMessage(text)

        builder.setPositiveButton(getString(R.string.OK)) { _, _ ->
            builder.dismissRunnable?.run()
            listener?.invoke(builder)
        }
        builder.show()
    })

    @JvmStatic
    fun showCopyAlert(ctx: Context, text: String) = AndroidUtilities.runOnUIThread {
        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(getString(R.string.Translate))
        builder.setMessage(text)

        builder.setNegativeButton(getString(R.string.Copy)) { _, _ ->
            AndroidUtilities.addToClipboard(text)
            if (AndroidUtilities.shouldShowClipboardToast()) {
                showToast(getString(R.string.TextCopied))
            }
            builder.dismissRunnable.run()
        }
        builder.setPositiveButton(getString(R.string.OK)) { _, _ ->
            builder.dismissRunnable.run()
        }
        builder.show()

    }

    @JvmOverloads
    @JvmStatic
    fun showProgress(ctx: Context, text: String = getString(R.string.Loading)): AlertDialog {
        return AlertDialog.Builder(ctx, AlertDialog.ALERT_TYPE_MESSAGE).apply {
            setMessage(text)
        }.create()
    }

    @JvmStatic
    @JvmOverloads
    fun showConfirm(ctx: Context, title: String, text: String? = null, icon: Int, button: String, red: Boolean, listener: Runnable) = AndroidUtilities.runOnUIThread {
        val builder = BottomBuilder(ctx)

        if (text != null) {
            builder.addTitle(title, text)
        } else {
            builder.addTitle(title)
        }

        builder.addItem(button, icon, red) {
            listener.run()
        }
        builder.addCancelItem()
        builder.show()

    }

    fun interface RetryCallback {
        fun onRetry(newProvider: Int)
    }

    @JvmStatic
    fun showTransFailedDialog(ctx: Context, noRetry: Boolean, message: String, retryRunnable: Runnable) =
        showTransFailedDialog(ctx, noRetry, message) { retryRunnable.run() }

    @JvmStatic
    fun showTransFailedDialog(ctx: Context, noRetry: Boolean, message: String, onRetry: RetryCallback) = AndroidUtilities.runOnUIThread {
        ctx.setTheme(R.style.Theme_TMessages)
        val reference = AtomicReference<AlertDialog>()

        val builder = AlertDialog.Builder(ctx)
        builder.setTitle(getString(R.string.TranslateFailed))
        builder.setMessage(message)

        builder.setNeutralButton(getString(R.string.ChangeTranslateProvider)) { _, _ ->
            val view = reference.get().getButton(AlertDialog.BUTTON_NEUTRAL)
            val popup = PopupBuilder(view, true)
            val providers = listOf(
                ProviderInfo(Translator.providerGoogle, R.string.ProviderGoogleTranslate),
                ProviderInfo(Translator.providerGoogleCN, R.string.ProviderGoogleTranslateCN),
                ProviderInfo(Translator.providerGoogle2, R.string.ProviderGoogleTranslate),
                ProviderInfo(Translator.providerLingo, R.string.ProviderLingocloud),
                ProviderInfo(Translator.providerMicrosoft, R.string.ProviderMicrosoftTranslator),
                ProviderInfo(Translator.providerVolcengine, R.string.ProviderVolcengineTranslate),
                ProviderInfo(Translator.providerDeepL, R.string.ProviderDeepLxTranslate),
                ProviderInfo(Translator.providerTelegram, R.string.ProviderTelegramAPI),
                ProviderInfo(Translator.providerTranSmart, R.string.ProviderTranSmartTranslate),
                ProviderInfo(Translator.providerLLM, R.string.ProviderLLMTranslate),
                ProviderInfo(Translator.providerDeepLOfficial, R.string.ProviderDeepLTranslate),
                ProviderInfo(Translator.providerDeepLFree, R.string.ProviderDeepLFreeTranslate)
            )
            val itemNames = providers.map {
                if (it.providerConstant == Translator.providerGoogle2) {
                    getString(it.nameResId) + " 2"
                } else {
                    getString(it.nameResId)
                }
            }
            popup.setItems(itemNames.toTypedArray()) { index, _ ->
                reference.get().dismiss()
                val selectedProvider = providers[index].providerConstant
                NekoConfig.translationProvider.setConfigInt(selectedProvider)
                onRetry.onRetry(selectedProvider)
            }
            popup.show()
        }

        if (noRetry) {
            builder.setPositiveButton(getString(R.string.Cancel)) { _, _ ->
                reference.get().dismiss()
            }
        } else {
            builder.setPositiveButton(getString(R.string.Retry)) { _, _ ->
                reference.get().dismiss()
                onRetry.onRetry(0)
            }
            builder.setNegativeButton(getString(R.string.Cancel)) { _, _ ->
                reference.get().dismiss()
            }
        }

        reference.set(builder.create().apply {
            setDismissDialogByButtons(false)
            show()
        })
    }

    private data class ProviderInfo(
        val providerConstant: Int,
        val nameResId: Int
    )

    @JvmStatic
    fun showMicPermissionDialog(ctx: Context) = AndroidUtilities.runOnUIThread {
        val builder = org.telegram.ui.ActionBar.BottomSheet.Builder(ctx)
        builder.setApplyTopPadding(false)
        builder.setApplyBottomPadding(false)

        val container = android.widget.LinearLayout(ctx)
        container.orientation = android.widget.LinearLayout.VERTICAL
        container.gravity = android.view.Gravity.CENTER_HORIZONTAL
        container.setPadding(AndroidUtilities.dp(24f), AndroidUtilities.dp(32f), AndroidUtilities.dp(24f), AndroidUtilities.dp(24f))

        val iconView = android.widget.ImageView(ctx)
        iconView.setImageResource(R.drawable.input_mic)
        iconView.setColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        val bg = android.graphics.drawable.GradientDrawable()
        bg.shape = android.graphics.drawable.GradientDrawable.OVAL
        bg.setColor(0xFFE91E63.toInt())
        iconView.background = bg
        iconView.scaleType = android.widget.ImageView.ScaleType.CENTER
        container.addView(iconView, org.telegram.ui.Components.LayoutHelper.createLinear(72, 72, android.view.Gravity.CENTER_HORIZONTAL, 0, 0, 0, 16))

        val titleView = android.widget.TextView(ctx)
        titleView.text = "Microphone Access"
        titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20f)
        titleView.typeface = AndroidUtilities.getTypeface("fonts/rmedium.ttf")
        titleView.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextBlack))
        titleView.gravity = android.view.Gravity.CENTER
        container.addView(titleView, org.telegram.ui.Components.LayoutHelper.createLinear(org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8))

        val descView = android.widget.TextView(ctx)
        descView.text = "To create beautiful visualizers that react to your music, Alexgram needs access to your microphone.\n\nPlease grant the permission in Settings."
        descView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f)
        descView.setTextColor(org.telegram.ui.ActionBar.Theme.getColor(org.telegram.ui.ActionBar.Theme.key_dialogTextGray2))
        descView.gravity = android.view.Gravity.CENTER
        descView.setLineSpacing(AndroidUtilities.dp(2f).toFloat(), 1.0f)
        container.addView(descView, org.telegram.ui.Components.LayoutHelper.createLinear(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT, android.view.Gravity.CENTER_HORIZONTAL, 0, 0, 0, 24))

        val allowBtn = android.widget.TextView(ctx)
        allowBtn.text = "Allow Microphone"
        allowBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15f)
        allowBtn.typeface = AndroidUtilities.getTypeface("fonts/rmedium.ttf")
        allowBtn.setTextColor(0xFFFFFFFF.toInt())
        allowBtn.gravity = android.view.Gravity.CENTER
        val btnBg = android.graphics.drawable.GradientDrawable()
        btnBg.setColor(0xFF2196F3.toInt())
        btnBg.cornerRadius = AndroidUtilities.dp(8f).toFloat()
        allowBtn.background = btnBg
        allowBtn.setOnClickListener {
            builder.dismissRunnable.run()
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:" + ApplicationLoader.applicationContext.packageName)
            ctx.startActivity(intent)
        }
        container.addView(allowBtn, org.telegram.ui.Components.LayoutHelper.createLinear(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, 48, android.view.Gravity.CENTER_HORIZONTAL, 0, 0, 0, 12))

        val closeBtn = android.widget.TextView(ctx)
        closeBtn.text = "Close"
        closeBtn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 15f)
        closeBtn.typeface = AndroidUtilities.getTypeface("fonts/rmedium.ttf")
        closeBtn.setTextColor(0xFF2196F3.toInt())
        closeBtn.gravity = android.view.Gravity.CENTER
        val closeBg = android.graphics.drawable.GradientDrawable()
        closeBg.setColor(0x1A2196F3)
        closeBg.cornerRadius = AndroidUtilities.dp(8f).toFloat()
        closeBtn.background = closeBg
        closeBtn.setOnClickListener { builder.dismissRunnable.run() }
        container.addView(closeBtn, org.telegram.ui.Components.LayoutHelper.createLinear(org.telegram.ui.Components.LayoutHelper.MATCH_PARENT, 48, android.view.Gravity.CENTER_HORIZONTAL, 0, 0, 0, 0))

        builder.setCustomView(container)
        builder.show()
    }
}
