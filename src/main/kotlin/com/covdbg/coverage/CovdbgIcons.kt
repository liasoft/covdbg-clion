package com.covdbg.coverage

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object CovdbgIcons {

    /** The covdbg logo at 16x16, for menus and tabs. */
    @JvmField
    val Logo: Icon = IconLoader.getIcon("/icons/covdbg.svg", CovdbgIcons::class.java)

    /**
     * The detailed logo at 32x32, for the settings page. The same drawing as the plugin icon
     * (META-INF/pluginIcon.svg), which has no size of its own and would otherwise load at 880 px.
     */
    @JvmField
    val LogoLarge: Icon = IconLoader.getIcon("/icons/covdbgLogo32.svg", CovdbgIcons::class.java)
}
