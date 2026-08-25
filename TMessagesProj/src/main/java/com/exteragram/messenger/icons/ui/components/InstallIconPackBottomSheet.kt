package com.exteragram.messenger.icons.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.icons.IconPack
import com.exteragram.messenger.icons.IconPackStorage
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.BottomSheet
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView
import java.io.File

class InstallIconPackBottomSheet(
    context: Context,
    private val iconPack: IconPack,
    private val installDelegate: InstallDelegate?
) : BottomSheet(context, true) {

    interface InstallDelegate {
        fun onInstall(enable: Boolean, update: Boolean)
    }

    private val iconEntries = iconPack.icons.entries.toList()

    init {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AndroidUtilities.dp(16f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f), AndroidUtilities.dp(16f))
        }

        // Header: Title & Author
        val titleView = TextView(context).apply {
            text = iconPack.name
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20f)
            setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"))
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
            setSingleLine()
            ellipsize = TextUtils.TruncateAt.END
        }
        container.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        val authorView = TextView(context).apply {
            text = "${LocaleController.getString(R.string.Author)}: ${iconPack.author} • v${iconPack.version}"
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14f)
            setTextColor(Theme.getColor(Theme.key_dialogTextGray2))
            setPadding(0, AndroidUtilities.dp(4f), 0, AndroidUtilities.dp(12f))
        }
        container.addView(authorView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT))

        // Grid Preview of Icons
        if (iconEntries.isNotEmpty()) {
            val gridView = RecyclerListView(context).apply {
                layoutManager = GridLayoutManager(context, 5)
                adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                        val frame = FrameLayout(context).apply {
                            layoutParams = RecyclerView.LayoutParams(AndroidUtilities.dp(56f), AndroidUtilities.dp(56f))
                        }
                        val imageView = ImageView(context).apply {
                            scaleType = ImageView.ScaleType.CENTER_INSIDE
                        }
                        frame.addView(imageView, LayoutHelper.createFrame(40, 40, Gravity.CENTER))
                        return object : RecyclerView.ViewHolder(frame) {}
                    }

                    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                        val entry = iconEntries[position]
                        val frame = holder.itemView as FrameLayout
                        val imageView = frame.getChildAt(0) as ImageView
                        
                        var drawable = IconManager.INSTANCE.getPackIconDrawable(iconPack, entry.key.hashCode())
                        if (drawable == null && iconPack.location != null) {
                            val f = File(iconPack.location, entry.value)
                            if (f.exists()) {
                                val bm = BitmapFactory.decodeFile(f.absolutePath)
                                if (bm != null) {
                                    drawable = BitmapDrawable(context.resources, bm)
                                }
                            }
                        }

                        if (drawable != null) {
                            imageView.setImageDrawable(drawable)
                            imageView.colorFilter = null
                        } else {
                            imageView.setImageResource(R.drawable.msg_bot)
                            imageView.colorFilter = PorterDuffColorFilter(Theme.getColor(Theme.key_dialogIcon), PorterDuff.Mode.SRC_IN)
                        }
                    }

                    override fun getItemCount(): Int = minOf(iconEntries.size, 25)
                }
            }
            container.addView(gridView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 200))
        }

        // Install Button
        val installButton = TextView(context).apply {
            text = LocaleController.getString(R.string.InstallPack)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15f)
            setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"))
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = Theme.createSimpleSelectorRoundRectDrawable(
                AndroidUtilities.dp(8f),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)
            )
            setOnClickListener {
                installDelegate?.onInstall(enable = true, update = false)
                dismiss()
            }
        }
        container.addView(installButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48, 0f, 16f, 0f, 0f))

        setCustomView(container)
    }
}
