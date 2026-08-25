package com.exteragram.messenger.icons.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.exteragram.messenger.icons.BaseIconPacks
import com.exteragram.messenger.icons.IconManager
import com.exteragram.messenger.icons.IconPack
import com.exteragram.messenger.icons.IconPackStorage
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.ActionBar
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Cells.HeaderCell
import org.telegram.ui.Cells.TextCheckCell
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.LayoutHelper
import org.telegram.ui.Components.RecyclerListView

class IconPacksActivity : BaseFragment() {

    private lateinit var listView: RecyclerListView
    private lateinit var adapter: IconPacksAdapter

    override fun createView(context: Context): View {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back)
        actionBar.setTitle(LocaleController.getString(R.string.IconPacks))
        actionBar.setAllowOverlayTitle(true)

        actionBar.setActionBarMenuOnItemClick(object : ActionBar.ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    finishFragment()
                } else if (id == 1) {
                    openFilePicker()
                }
            }
        })

        val menu = actionBar.createMenu()
        menu.addItem(1, R.drawable.msg_add)

        fragmentView = FrameLayout(context).apply {
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray))
        }
        val frameLayout = fragmentView as FrameLayout

        listView = RecyclerListView(context).apply {
            layoutManager = LinearLayoutManager(context)
            isVerticalScrollBarEnabled = false
        }
        
        adapter = IconPacksAdapter(context)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, position ->
            val pack = adapter.getPackAt(position) ?: return@setOnItemClickListener
            IconManager.INSTANCE.setActiveCustomPack(pack.id)
            adapter.notifyDataSetChanged()
            BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, LocaleController.getString(R.string.InstallPack)).show()
        }

        listView.setOnItemLongClickListener { _, position ->
            val pack = adapter.getPackAt(position) ?: return@setOnItemLongClickListener false
            if (pack.id == BaseIconPacks.DEFAULT.id || pack.id == BaseIconPacks.SOLAR.id || pack.id == BaseIconPacks.REMIX.id) {
                return@setOnItemLongClickListener false
            }
            val builder = AlertDialog.Builder(context)
            builder.setTitle(pack.name)
            builder.setMessage("Delete custom icon pack?")
            builder.setPositiveButton(LocaleController.getString(R.string.Delete)) { _, _ ->
                IconManager.INSTANCE.deletePack(pack.id)
                AndroidUtilities.runOnUIThread({
                    adapter.updatePacks()
                }, 300)
            }
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null)
            showDialog(builder.create())
            true
        }

        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT.toFloat()))

        return fragmentView
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
        }
        parentActivity?.startActivityForResult(Intent.createChooser(intent, "Select .icons file"), 9989)
    }

    override fun onActivityResultFragment(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 9989 && resultCode == Activity.RESULT_OK && data?.data != null) {
            val uri = data.data!!
            val path = AndroidUtilities.getPath(uri)
            if (path != null && path.endsWith(".icons", ignoreCase = true)) {
                IconManager.INSTANCE.handleIconPack(this, path)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.updatePacks()
    }

    private inner class IconPacksAdapter(private val context: Context) : RecyclerListView.SelectionAdapter() {

        private val basePacks = listOf(BaseIconPacks.DEFAULT, BaseIconPacks.SOLAR, BaseIconPacks.REMIX)
        private val customPacks = mutableListOf<IconPack>()

        init {
            updatePacks()
        }

        fun updatePacks() {
            customPacks.clear()
            customPacks.addAll(IconPackStorage.INSTANCE.getCustomPacks())
            notifyDataSetChanged()
        }

        fun getPackAt(position: Int): IconPack? {
            if (position in 1..basePacks.size) {
                return basePacks[position - 1]
            }
            val customOffset = basePacks.size + 2
            if (position in customOffset until customOffset + customPacks.size) {
                return customPacks[position - customOffset]
            }
            return null
        }

        override fun isEnabled(holder: RecyclerView.ViewHolder): Boolean {
            return holder.itemViewType == 1
        }

        override fun getItemCount(): Int {
            var count = 1 + basePacks.size
            if (customPacks.isNotEmpty()) {
                count += 1 + customPacks.size
            }
            return count
        }

        override fun getItemViewType(position: Int): Int {
            if (position == 0 || position == basePacks.size + 1) {
                return 0 // Header
            }
            return 1 // Item
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val view: View = if (viewType == 0) {
                HeaderCell(context).apply {
                    setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                }
            } else {
                TextCheckCell(context).apply {
                    setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite))
                }
            }
            return object : RecyclerView.ViewHolder(view) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder.itemViewType == 0) {
                val headerCell = holder.itemView as HeaderCell
                if (position == 0) {
                    headerCell.setText(LocaleController.getString(R.string.BasePacks))
                } else {
                    headerCell.setText("Custom Icon Packs")
                }
            } else {
                val checkCell = holder.itemView as TextCheckCell
                val pack = getPackAt(position) ?: return
                val activePackId = IconManager.INSTANCE.getActivePackId()
                val isChecked = (pack.id == activePackId)

                checkCell.setTextAndCheck(pack.name, isChecked, position != itemCount - 1)
            }
        }
    }
}
