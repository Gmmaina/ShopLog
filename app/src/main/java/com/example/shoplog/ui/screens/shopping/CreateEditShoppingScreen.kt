package com.example.shoplog.ui.screens.shopping

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.ui.components.AddItemBottomSheet
import com.example.shoplog.ui.components.ReceiptCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditShoppingScreen(
    listIdParam: String,
    viewModel: CreateEditShoppingViewModel,
    onNavigateBack: () -> Unit,
    onSaved: (savedListId: String) -> Unit
) {
    val listWithItems by viewModel.shoppingListWithItems.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var titleInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }

    var showAddItemSheet by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    LaunchedEffect(listIdParam) {
        viewModel.initList(listIdParam)
    }

    LaunchedEffect(listWithItems) {
        listWithItems?.let { data ->
            if (!isInitialized) {
                titleInput = data.list.title
                locationInput = data.list.location ?: ""
                isInitialized = true

                // Auto prompt to add item if new/empty list
                if (data.items.isEmpty() && (listIdParam == "new" || data.list.isDraft)) {
                    showAddItemSheet = true
                }
            }
        }
    }

    if (showAddItemSheet) {
        AddItemBottomSheet(
            currencySymbol = currencySymbol,
            editingItem = itemToEdit,
            onDismiss = {
                showAddItemSheet = false
                itemToEdit = null
            },
            onSaveItem = { name, quantity, unitPriceCents ->
                viewModel.addOrUpdateItem(
                    itemId = itemToEdit?.id,
                    name = name,
                    quantity = quantity,
                    unitPriceCents = unitPriceCents
                )
            },
            onDeleteItem = { itemId ->
                viewModel.deleteItem(itemId)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (listWithItems?.list?.isDraft == false) "Edit Shopping" else "New Shopping",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            // Title Input
            OutlinedTextField(
                value = titleInput,
                onValueChange = {
                    titleInput = it
                    viewModel.updateListInfo(titleInput, locationInput)
                },
                label = { Text("Shopping Title") },
                placeholder = { Text("e.g. Monthly Groceries, Xmas Shopping") },
                leadingIcon = { Icon(Icons.Default.Title, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Location Input (Optional)
            OutlinedTextField(
                value = locationInput,
                onValueChange = {
                    locationInput = it
                    viewModel.updateListInfo(titleInput, locationInput)
                },
                label = { Text("Shopping Location (Optional)") },
                placeholder = { Text("e.g. Carrefour, Naivas, Quickmart") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Digital Receipt Card Component
            ReceiptCard(
                items = listWithItems?.items ?: emptyList(),
                totalCents = listWithItems?.list?.totalCents ?: 0L,
                currencySymbol = currencySymbol,
                isEditable = true,
                onItemClick = { item ->
                    itemToEdit = item
                    showAddItemSheet = true
                },
                onAddItemClick = {
                    itemToEdit = null
                    showAddItemSheet = true
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Save Button
            Button(
                onClick = {
                    val currentItems = listWithItems?.items ?: emptyList()
                    if (currentItems.isEmpty()) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Please add at least one item before saving your list.")
                        }
                        showAddItemSheet = true
                    } else {
                        val finalTitle = titleInput.ifBlank { "New Shopping" }
                        viewModel.saveShopping(finalTitle, locationInput.ifBlank { null }) { savedId ->
                            onSaved(savedId)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Text(
                    text = "Save Shopping List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
