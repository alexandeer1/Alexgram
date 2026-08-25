package com.exteragram.messenger.icons

import tw.nekomimi.nekogram.ui.icons.RemixIcons
import tw.nekomimi.nekogram.ui.icons.SolarIcons

object BaseIconPacks {
    @JvmField
    val DEFAULT = IconPack(
        id = "base.default",
        name = "Default",
        author = "Telegram",
        version = "1.0",
        icons = emptyMap(),
        preinstalledMap = null,
        location = null
    )

    @JvmField
    val SOLAR = IconPack(
        id = "base.solar",
        name = "Solar Icons",
        author = "@Design480",
        version = "1.0",
        icons = emptyMap(),
        preinstalledMap = SolarIcons.map,
        location = null
    )

    @JvmField
    val REMIX = IconPack(
        id = "base.remix",
        name = "Remix Icons",
        author = "Remix-Design",
        version = "1.0",
        icons = emptyMap(),
        preinstalledMap = RemixIcons.map,
        location = null
    )
}
