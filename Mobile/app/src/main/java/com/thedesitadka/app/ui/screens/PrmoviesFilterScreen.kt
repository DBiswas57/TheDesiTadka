package com.thedesitadka.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thedesitadka.provider.ProviderEngine

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrmoviesFilterScreen(
    providerId: String,
    providerEngine: ProviderEngine,
    onApplyFilter: (title: String, url: String) -> Unit,
    onBackClick: () -> Unit
) {
    val adapter = remember { providerEngine.getAdapter(providerId) }
    val info = remember { adapter?.providerInfo }
    val providerName = info?.name ?: if (providerId == "prmovies_church") "PRMovies Church" else "PRMovies"
    val baseUrl = info?.baseUrl ?: if (providerId == "prmovies_church") "https://prmovies.church" else "https://prmovies.com"

    var selectedSort by remember { mutableStateOf("latest") }
    val selectedQualities = remember { mutableStateListOf<String>() }
    val selectedGenres = remember { mutableStateListOf<String>() }
    val selectedCountries = remember { mutableStateListOf<String>() }
    var selectedYear by remember { mutableStateOf<String?>(null) }

    var countrySearchQuery by remember { mutableStateOf("") }
    var showAllCountries by remember { mutableStateOf(false) }

    val totalSelectedFilters = (if (selectedSort != "latest") 1 else 0) +
            selectedQualities.size +
            selectedGenres.size +
            selectedCountries.size +
            (if (selectedYear != null) 1 else 0)

    val filteredCountries = remember(countrySearchQuery, showAllCountries) {
        val list = PrmoviesFilterData.COUNTRIES.filter {
            countrySearchQuery.isBlank() || it.label.contains(countrySearchQuery, ignoreCase = true)
        }
        if (showAllCountries || countrySearchQuery.isNotBlank()) list else list.take(16)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = TopAppBarDefaults.windowInsets,
                title = {
                    Column {
                        Text(
                            text = "Advanced Filter",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = providerName,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (totalSelectedFilters > 0) {
                        TextButton(
                            onClick = {
                                selectedSort = "latest"
                                selectedQualities.clear()
                                selectedGenres.clear()
                                selectedCountries.clear()
                                selectedYear = null
                            }
                        ) {
                            Text("Reset", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (totalSelectedFilters == 0) "No filters selected" else "$totalSelectedFilters filters active",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = "WordPress Advanced Search",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }

                    Button(
                        onClick = {
                            val base = baseUrl.trimEnd('/')
                            val queryParams = mutableListOf("ptype=post")

                            selectedQualities.forEach { q ->
                                queryParams.add("tax_quality[]=$q")
                            }
                            selectedGenres.forEach { g ->
                                queryParams.add("tax_category[]=$g")
                            }
                            selectedCountries.forEach { c ->
                                queryParams.add("tax_country[]=$c")
                            }
                            selectedYear?.let { y ->
                                queryParams.add("tax_release-year=$y")
                            }
                            queryParams.add("wpas=1")

                            val filterUrl = "$base/account/?" + queryParams.joinToString("&")
                            val titleSummary = buildString {
                                append("Filtered")
                                if (selectedGenres.isNotEmpty()) append(" • ${selectedGenres.first().replaceFirstChar { it.uppercase() }}")
                                if (selectedYear != null) append(" • $selectedYear")
                            }
                            onApplyFilter(titleSummary, filterUrl)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Filter Results",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Sort By
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SORT BY",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrmoviesFilterData.SORT_OPTIONS.forEach { sort ->
                        val isSelected = selectedSort == sort.value
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedSort = sort.value },
                            label = { Text(sort.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // 2. Quality
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QUALITY (${selectedQualities.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    if (selectedQualities.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { selectedQualities.clear() }
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrmoviesFilterData.QUALITIES.forEach { quality ->
                        val isSelected = quality.value in selectedQualities
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedQualities.remove(quality.value)
                                else selectedQualities.add(quality.value)
                            },
                            label = { Text(quality.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // 3. Genre
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GENRE (${selectedGenres.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    if (selectedGenres.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { selectedGenres.clear() }
                        )
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PrmoviesFilterData.GENRES.forEach { genre ->
                        val isSelected = genre.value in selectedGenres
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedGenres.remove(genre.value)
                                else selectedGenres.add(genre.value)
                            },
                            label = { Text(genre.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // 4. Country
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COUNTRY (${selectedCountries.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    if (selectedCountries.isNotEmpty()) {
                        Text(
                            text = "Clear",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { selectedCountries.clear() }
                        )
                    }
                }

                // Compact Search Bar for Countries
                BasicTextField(
                    value = countrySearchQuery,
                    onValueChange = { countrySearchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (countrySearchQuery.isEmpty()) {
                                    Text(
                                        text = "Search 188 countries...",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                innerTextField()
                            }
                            if (countrySearchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { countrySearchQuery = "" }
                                )
                            }
                        }
                    }
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredCountries.forEach { country ->
                        val isSelected = country.value in selectedCountries
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedCountries.remove(country.value)
                                else selectedCountries.add(country.value)
                            },
                            label = { Text(country.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }

                if (countrySearchQuery.isBlank() && PrmoviesFilterData.COUNTRIES.size > 16) {
                    TextButton(
                        onClick = { showAllCountries = !showAllCountries },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (showAllCountries) "Show Less" else "Show All 188 Countries",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // 5. Release Year
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RELEASE YEAR" + (selectedYear?.let { " ($it)" } ?: ""),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                    )
                    if (selectedYear != null) {
                        Text(
                            text = "Clear",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.clickable { selectedYear = null }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedYear == null,
                        onClick = { selectedYear = null },
                        label = { Text("All", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = Color.Black,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = Color.White
                        )
                    )
                    PrmoviesFilterData.YEARS.take(30).forEach { yr ->
                        val isSelected = selectedYear == yr.value
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedYear = if (isSelected) null else yr.value },
                            label = { Text(yr.label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = Color.Black,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = Color.White
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
