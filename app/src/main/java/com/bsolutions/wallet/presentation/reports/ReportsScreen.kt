package com.bsolutions.wallet.presentation.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.bsolutions.wallet.R
import com.bsolutions.wallet.core.common.MoneyFormat
import com.bsolutions.wallet.presentation.common.DonutChart
import com.bsolutions.wallet.presentation.common.DonutSegment
import com.bsolutions.wallet.presentation.common.parseHexColor
import com.bsolutions.wallet.presentation.dashboard.CategoryLegendItem
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onOpenDrawer: () -> Unit = {},
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isMonthlySelected by remember { mutableStateOf(true) }
    var expandedCategoryId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_reports), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.common_open_menu))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                // Selector de período (el título ya está en la barra superior)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.reports_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    // Period Toggle Buttons
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(4.dp)
                    ) {
                        Button(
                            onClick = { isMonthlySelected = true },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMonthlySelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isMonthlySelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(stringResource(R.string.freq_monthly), fontSize = 12.sp)
                        }

                        Button(
                            onClick = { isMonthlySelected = false },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isMonthlySelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (!isMonthlySelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(stringResource(R.string.freq_yearly), fontSize = 12.sp)
                        }
                    }
                }
            }

            // Categories Donut Chart Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(R.string.dashboard_expenses_by_category),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Donut real proporcional a los gastos por categoría
                            val fallback = MaterialTheme.colorScheme.primary
                            DonutChart(
                                segments = uiState.categoryItems.map { item ->
                                    DonutSegment(
                                        value = item.amount,
                                        color = parseHexColor(item.category.colorHex, fallback)
                                    )
                                },
                                size = 160.dp
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.reports_total),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = MoneyFormat.formatCompact(uiState.totalExpense),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // Legends List
                            Column(
                                modifier = Modifier.weight(1f).padding(paddingValues = PaddingValues(start = 24.dp)),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (uiState.categoryItems.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.reports_no_expenses),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    uiState.categoryItems.take(4).forEach { item ->
                                        Row(
                                             modifier = Modifier.fillMaxWidth().clickable { expandedCategoryId = item.category.id },
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CategoryLegendItem(
                                                text = item.category.name,
                                                color = parseHexColor(item.category.colorHex, MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = "${item.percentage}%",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Income vs Expenses Bar Chart Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.reports_income_vs_expenses),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                LegendDot(stringResource(R.string.dashboard_income), MaterialTheme.colorScheme.secondaryContainer)
                                LegendDot(stringResource(R.string.dashboard_expenses), MaterialTheme.colorScheme.errorContainer)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Bar Chart Layout
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Escala dinámica: el mes con mayor movimiento define el 100%
                            val maxVal = uiState.monthlyDataList
                                .maxOfOrNull { maxOf(it.incomeAmount, it.expenseAmount) }
                                ?.coerceAtLeast(1L) ?: 1L
                            uiState.monthlyDataList.forEach { month ->
                                val incScale = (month.incomeAmount.toFloat() / maxVal).coerceIn(0.02f, 1f)
                                val expScale = (month.expenseAmount.toFloat() / maxVal).coerceIn(0.02f, 1f)

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        // Income Bar
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(incScale)
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                        )
                                        // Expense Bar
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxHeight(expScale)
                                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                .background(MaterialTheme.colorScheme.errorContainer)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = month.monthName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                CategoryExpenseDetailsCard(
                    items = uiState.categoryItems,
                    expandedCategoryId = expandedCategoryId,
                    onCategoryClick = { id -> expandedCategoryId = if (expandedCategoryId == id) null else id }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun CategoryExpenseDetailsCard(
    items: List<CategoryReportItem>,
    expandedCategoryId: String?,
    onCategoryClick: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.reports_category_details_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (items.isEmpty()) {
                Text(stringResource(R.string.reports_no_expenses), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                items.forEach { item ->
                    val expanded = item.category.id == expandedCategoryId
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onCategoryClick(item.category.id) }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier.size(12.dp).clip(CircleShape).background(parseHexColor(item.category.colorHex, MaterialTheme.colorScheme.primary))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(item.category.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            Text(MoneyFormat.formatCompact(item.amount), fontWeight = FontWeight.Bold)
                            Icon(
                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                        if (expanded) {
                            if (item.transactions.isEmpty()) {
                                Text(stringResource(R.string.reports_category_details_empty), style = MaterialTheme.typography.bodySmall)
                            } else {
                                item.transactions.forEach { transaction ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(start = 22.dp, bottom = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(transaction.note.ifBlank { stringResource(R.string.dashboard_other_category) }, style = MaterialTheme.typography.bodyMedium)
                                            Text(formatReportDate(transaction.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(MoneyFormat.formatSigned(transaction.amount, transaction.type == "INCOME", transaction.currency), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatReportDate(value: Long): String = DateFormat.getDateInstance(
    DateFormat.MEDIUM,
    Locale.forLanguageTag("es-DO")
).format(Date(value))

@Composable
fun LegendDot(text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
