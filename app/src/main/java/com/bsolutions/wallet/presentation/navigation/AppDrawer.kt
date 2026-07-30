package com.bsolutions.wallet.presentation.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.SsidChart
import androidx.compose.material.icons.outlined.TableRows
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bsolutions.wallet.R

// Colores calcados del menú de Wallet (BudgetBakers)
private val HeaderGradientStart = Color(0xFF8BD08D)
private val HeaderGradientEnd = Color(0xFF3FA45B)
private val IconOrange = Color(0xFFF59E0B)
private val IconBlue = Color(0xFF4A90D2)
private val IconRed = Color(0xFFE5484D)
private val IconGreen = Color(0xFF43A047)
private val IconTeal = Color(0xFF00897B)
private val IconGray = Color(0xFF9E9E9E)
private val BadgeBlue = Color(0xFF2979FF)
private val PillBlueLight = Color(0xFFBBDEFB)
private val PillBlueDark = Color(0xFF1E3A5F)
private val DrawerBgLight = Color(0xFFF2F2F2)
private val DrawerBgDark = Color(0xFF1C1C1E)

data class DrawerItem(
    val route: String,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val iconTint: Color,
    @StringRes val badgeRes: Int? = null,
    val subItems: List<DrawerItem> = emptyList(),
    val showDividerBefore: Boolean = false
)

val drawerItems = listOf(
    DrawerItem("dashboard", R.string.drawer_home, Icons.Outlined.Home, IconRed),
    DrawerItem("transactions", R.string.drawer_records, Icons.Outlined.FormatListBulleted, IconOrange),
    DrawerItem("accounts", R.string.nav_accounts, Icons.Outlined.AccountBalance, IconBlue),
    DrawerItem("budgets", R.string.drawer_budgets, Icons.Outlined.TableRows, IconRed),
    DrawerItem(
        "reports", R.string.drawer_statistics, Icons.Outlined.SsidChart, IconTeal,
        subItems = listOf(
            DrawerItem("reports", R.string.nav_reports, Icons.Outlined.PieChartOutline, IconTeal)
        )
    ),
    DrawerItem("planned_payments", R.string.drawer_planned_payments, Icons.Outlined.Update, IconOrange),
    DrawerItem("debts", R.string.drawer_debts, Icons.Outlined.MonetizationOn, IconRed),
    DrawerItem("goals", R.string.drawer_goals, Icons.Outlined.TrackChanges, IconTeal),
    DrawerItem("email_connections", R.string.drawer_email_sync, Icons.Outlined.Email, IconBlue),
    // La importacion de CSV queda fuera del menu: no esta en uso y su via de guardado
    // es la unica que no encola para el servidor, asi que lo importado en un telefono no
    // llegaria a los demas. Se reactiva cuando esa via encole.
    DrawerItem("settings", R.string.common_settings, Icons.Outlined.Settings, IconGray, showDividerBefore = true)
)

@Composable
fun AppDrawerContent(
    userName: String,
    walletName: String,
    email: String = "",
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val darkTheme = isSystemInDarkTheme()
    val drawerBg = if (darkTheme) DrawerBgDark else DrawerBgLight
    val pillColor = if (darkTheme) PillBlueDark else PillBlueLight

    ModalDrawerSheet(
        drawerShape = RectangleShape,
        drawerContainerColor = drawerBg,
        modifier = Modifier.width(310.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
        ) {
            DrawerHeader(userName = userName, walletName = walletName, email = email)

            Spacer(modifier = Modifier.height(12.dp))

            drawerItems.forEach { item ->
                if (item.showDividerBefore) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                if (item.subItems.isEmpty()) {
                    DrawerRow(
                        item = item,
                        selected = currentRoute == item.route,
                        pillColor = pillColor,
                        onClick = { onNavigate(item.route) }
                    )
                } else {
                    ExpandableDrawerRow(
                        item = item,
                        currentRoute = currentRoute,
                        pillColor = pillColor,
                        onNavigate = onNavigate
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DrawerHeader(userName: String, walletName: String, email: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    colors = listOf(HeaderGradientStart, HeaderGradientEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD8D8D8))
                    .border(3.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = Color(0xFF9E9E9E),
                    modifier = Modifier.size(40.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = userName,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Con sesión iniciada se muestra el correo; sin ella, el nombre del wallet.
            Text(
                text = email.ifBlank { walletName },
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DrawerRow(
    item: DrawerItem,
    selected: Boolean,
    pillColor: Color,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) pillColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = item.iconTint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.width(24.dp))
        Text(
            text = stringResource(item.labelRes),
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.badgeRes != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(BadgeBlue)
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = stringResource(item.badgeRes),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun ExpandableDrawerRow(
    item: DrawerItem,
    currentRoute: String?,
    pillColor: Color,
    onNavigate: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    DrawerRow(
        item = item,
        selected = false,
        pillColor = pillColor,
        onClick = { expanded = !expanded },
        trailing = {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(if (expanded) R.string.drawer_collapse else R.string.drawer_expand),
                tint = item.iconTint,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(chevronRotation)
            )
        }
    )

    AnimatedVisibility(visible = expanded) {
        Column(modifier = Modifier.padding(start = 24.dp)) {
            item.subItems.forEach { sub ->
                DrawerRow(
                    item = sub,
                    selected = currentRoute == sub.route,
                    pillColor = pillColor,
                    onClick = { onNavigate(sub.route) }
                )
            }
        }
    }
}
