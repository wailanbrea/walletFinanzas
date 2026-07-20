package com.bsolutions.wallet.presentation.sync_settings

import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bsolutions.wallet.R
import com.bsolutions.wallet.core.network.ProviderDto
import com.bsolutions.wallet.data.repository.BankSyncRepository
import com.bsolutions.wallet.presentation.common.walletTopBarColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Países ofrecidos para buscar banco. XF = "Fake Country" (bancos de prueba de Salt Edge). */
data class BankCountry(val code: String, val nameRes: Int, val flag: String)

val bankCountries = listOf(
    BankCountry("DO", R.string.find_bank_country_do, "🇩🇴"),
    BankCountry("XF", R.string.find_bank_country_sandbox, "🧪")
)

data class FindBankUiState(
    val country: BankCountry = bankCountries.first(),
    val providers: List<ProviderDto> = emptyList(),
    val isLoadingProviders: Boolean = false,
    val selectedProvider: ProviderDto? = null,
    val isConnecting: Boolean = false,
    val connectUrl: String? = null,
    val error: String? = null
)

@HiltViewModel
class FindBankViewModel @Inject constructor(
    private val bankSyncRepository: BankSyncRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FindBankUiState())
    val uiState: StateFlow<FindBankUiState> = _uiState.asStateFlow()

    init {
        loadProviders(bankCountries.first())
    }

    fun selectCountry(country: BankCountry) {
        if (country.code == _uiState.value.country.code) return
        _uiState.value = _uiState.value.copy(country = country, selectedProvider = null, providers = emptyList())
        loadProviders(country)
    }

    fun selectProvider(provider: ProviderDto) {
        _uiState.value = _uiState.value.copy(selectedProvider = provider)
    }

    private fun loadProviders(country: BankCountry) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingProviders = true, error = null)
            try {
                val providers = bankSyncRepository.getProviders(country.code)
                _uiState.value = _uiState.value.copy(isLoadingProviders = false, providers = providers)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingProviders = false, error = e.message ?: "Error")
            }
        }
    }

    fun connect() {
        val provider = _uiState.value.selectedProvider ?: return
        if (_uiState.value.isConnecting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isConnecting = true, error = null)
            try {
                val url = bankSyncRepository.createConnectUrl(provider.code)
                _uiState.value = _uiState.value.copy(isConnecting = false, connectUrl = url)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isConnecting = false, error = e.message ?: "Error")
            }
        }
    }

    fun consumeConnectUrl() {
        _uiState.value = _uiState.value.copy(connectUrl = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindBankScreen(
    onNavigateBack: () -> Unit,
    viewModel: FindBankViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var countryExpanded by remember { mutableStateOf(false) }
    var showBankPicker by remember { mutableStateOf(false) }
    var bankQuery by remember { mutableStateOf("") }

    // Al obtener la URL del widget, abrir Chrome Custom Tab directo en el login del banco
    LaunchedEffect(uiState.connectUrl) {
        uiState.connectUrl?.let { url ->
            viewModel.consumeConnectUrl()
            CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(url))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.find_bank_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                colors = walletTopBarColors()
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Icono de banco en círculo azul, como en Wallet BudgetBakers
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF64B5F6), Color(0xFF1E70D8)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalance,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // País
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.find_bank_country),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { countryExpanded = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = uiState.country.flag, style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(uiState.country.nameRes),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = countryExpanded, onDismissRequest = { countryExpanded = false }) {
                    bankCountries.forEach { country ->
                        DropdownMenuItem(
                            text = { Text("${country.flag}  ${stringResource(country.nameRes)}") },
                            onClick = {
                                countryExpanded = false
                                viewModel.selectCountry(country)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Nombre del banco
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.find_bank_name_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.selectedProvider?.name ?: stringResource(R.string.find_bank_select),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (uiState.selectedProvider != null) MaterialTheme.colorScheme.onSurface
                    else Color(0xFF1E70D8),
                    fontWeight = if (uiState.selectedProvider != null) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showBankPicker = true }
                        .padding(vertical = 8.dp)
                        .fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = { viewModel.connect() },
                enabled = uiState.selectedProvider != null && !uiState.isConnecting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp)
            ) {
                if (uiState.isConnecting) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.find_bank_connect), fontWeight = FontWeight.SemiBold)
                }
            }

            uiState.error?.let { error ->
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    // Selector de banco (hoja inferior con buscador)
    if (showBankPicker) {
        ModalBottomSheet(onDismissRequest = { showBankPicker = false; bankQuery = "" }) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.find_bank_select),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = bankQuery,
                    onValueChange = { bankQuery = it },
                    placeholder = { Text(stringResource(R.string.find_bank_search_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    uiState.isLoadingProviders -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }

                    uiState.providers.isEmpty() -> Text(
                        text = stringResource(R.string.find_bank_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 32.dp)
                    )

                    else -> {
                        val filtered = uiState.providers.filter {
                            bankQuery.isBlank() || it.name.contains(bankQuery, ignoreCase = true)
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                            items(filtered, key = { it.code }) { provider ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.selectProvider(provider)
                                            showBankPicker = false
                                            bankQuery = ""
                                        }
                                        .padding(vertical = 12.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalance,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Text(text = provider.name, style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
