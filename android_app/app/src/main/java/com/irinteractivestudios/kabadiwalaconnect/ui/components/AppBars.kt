package com.irinteractivestudios.kabadiwalaconnect.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.BOTTOM_TABS
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.KABADIWALA_BOTTOM_TABS
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.HOUSEHOLD_BOTTOM_TABS
import com.irinteractivestudios.kabadiwalaconnect.ui.navigation.RECYCLER_BOTTOM_TABS
import com.irinteractivestudios.kabadiwalaconnect.domain.model.AccountRole

/**
 * App top bar: screen title (large) + optional back arrow.
 */
@Composable
fun KcTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxWidth().statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        }
    }
}

/** Bottom navigation: the 5 primary tabs with icon + label. */
@Composable
fun KcBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    role: AccountRole = AccountRole.COLLECTOR,
    unreadNotifications: Int = 0,
    demoMode: Boolean = false
) {
    val tabs = when (role) {
        AccountRole.RECYCLER -> RECYCLER_BOTTOM_TABS
        AccountRole.HOUSEHOLD -> HOUSEHOLD_BOTTOM_TABS
        AccountRole.COLLECTOR -> if (demoMode) BOTTOM_TABS else KABADIWALA_BOTTOM_TABS
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
        tabs.forEach { tab ->
            val isSelected = currentRoute == tab.route
            val selectedColor = MaterialTheme.colorScheme.primary
            val itemColor = animateColorAsState(
                if (isSelected) selectedColor else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "bottomNavColor"
            ).value
            val indicatorWidth = animateDpAsState(
                if (isSelected) 28.dp else 6.dp,
                label = "bottomNavIndicator"
            ).value
            val iconScale = animateFloatAsState(
                if (isSelected) 1.08f else 1f,
                label = "bottomNavIconScale"
            ).value
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .testTag(tab.testTag)
                    .heightIn(min = 64.dp)
                    .clickable { onNavigate(tab.route) }
                    .semantics(mergeDescendants = true) { this.role = Role.Tab; selected = isSelected }
                    .padding(vertical = 5.dp)
            )
            {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .width(indicatorWidth)
                        .heightIn(min = 3.dp)
                        .background(
                            if (isSelected) selectedColor else MaterialTheme.colorScheme.outline.copy(alpha = .22f),
                            RoundedCornerShape(99.dp)
                        )
                )
                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 6.dp))
                if (tab.route == com.irinteractivestudios.kabadiwalaconnect.ui.navigation.Destinations.SETTINGS && unreadNotifications > 0) {
                    BadgedBox(badge = { Badge { Text(unreadNotifications.coerceAtMost(99).toString()) } }) {
                        Icon(tab.icon, contentDescription = null, tint = itemColor, modifier = Modifier.size(23.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                    }
                } else {
                    Icon(tab.icon, contentDescription = null, tint = itemColor, modifier = Modifier.size(23.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
                }
                Text(
                    stringResource(tab.labelRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = itemColor,
                    maxLines = 1
                )
            }
        }
        }
    }
}
