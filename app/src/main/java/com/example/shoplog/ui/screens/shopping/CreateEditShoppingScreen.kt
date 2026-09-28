package com.example.shoplog.ui.screens.shopping

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.shoplog.core.util.Money
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.data.local.entity.ShoppingListEntity
import com.example.shoplog.ui.components.AddItemBottomSheet
import com.example.shoplog.ui.components.ReceiptCard
import com.example.shoplog.ui.screens.share.ShareShoppingDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEditShoppingScreen(
    listIdParam: String,
    viewModel: CreateEditShoppingViewModel,
    onNavigateBack: () -> Unit,
    onSaved: (savedListId: String) -> Unit
) {
    val context = LocalContext.current
    val listWithItems by viewModel.shoppingListWithItems.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var titleInput by remember { mutableStateOf("") }
    var locationInput by remember { mutableStateOf("") }
    var isInitialized by remember { mutableStateOf(false) }

    var showAddItemSheet by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<ShoppingItemEntity?>(null) }

    var showEndShoppingDialog by remember { mutableStateOf(false) }
    var showShareDialog by remember { mutableStateOf(false) }
    var generatedShareCode by remember { mutableStateOf("") }

    val currentUid = viewModel.currentUserId
    val list = listWithItems?.list
    val isCompleted = list?.isCompleted == true
    val isSharedWithMe = list?.isSharedWithMe == true
    val isOwner = list == null || list.ownerId.isBlank() || list.ownerId == "offline_user" || list.ownerId == currentUid || !isSharedWithMe

    // Read-only condition for invited users on completed sessions
    val isReadOnlyForMember = isCompleted && !isOwner

    val csvImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.bufferedReader()?.useLines { lines ->
                    lines.drop(1).forEach { line ->
                        val tokens = line.split(",")
                        if (tokens.isNotEmpty()) {
                            val name = tokens[0].trim().removeSurrounding("\"")
                            val qty = tokens.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
                            val priceStr = tokens.getOrNull(2)?.trim() ?: "0"
                            val priceCents = Money.parseToCents(priceStr)
                            if (name.isNotBlank()) {
                                viewModel.addOrUpdateItem(null, name, qty, priceCents)
                            }
                        }
                    }
                }
                scope.launch {
                    snackbarHostState.showSnackbar("Items imported successfully from CSV!")
                }
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar("Failed to import CSV: ${e.message}")
                }
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.attachReceiptPhoto(it.toString())
            scope.launch {
                snackbarHostState.showSnackbar("Receipt photo attached!")
            }
        }
    }

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
                if (data.items.isEmpty() && (listIdParam == "new" || data.list.isDraft) && !isReadOnlyForMember) {
                    showAddItemSheet = true
                }
            }
        }
    }

    // End Shopping Confirmation Dialog
    if (showEndShoppingDialog) {
        AlertDialog(
            onDismissRequest = { showEndShoppingDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.StopCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Shopping Session?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Once shopping ends, invited participants will no longer be able to edit this shopping list. Are you sure you want to complete this session?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEndShoppingDialog = false
                        viewModel.endShopping(
                            onEnded = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Shopping session ended.")
                                }
                            },
                            onError = { err ->
                                scope.launch {
                                    snackbarHostState.showSnackbar(err)
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("End Shopping")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndShoppingDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Share / Invite Code Dialog
    if (showShareDialog && list != null) {
        ShareShoppingDialog(
            title = list.title,
            shareCode = generatedShareCode,
            onDismiss = { showShareDialog = false }
        )
    }

    if (showAddItemSheet && !isReadOnlyForMember) {
        AddItemBottomSheet(
            currencySymbol = currencySymbol,
            editingItem = itemToEdit,
            onDismiss = {
                showAddItemSheet = false
                itemToEdit = null
            },
            onSaveItem = { name, quantity, unitPriceCents, barcode ->
                viewModel.addOrUpdateItem(
                    itemId = itemToEdit?.id,
                    name = name,
                    quantity = quantity,
                    unitPriceCents = unitPriceCents,
                    barcode = barcode
                )
            },
            onDeleteItem = { itemId ->
                viewModel.deleteItem(itemId)
            },
            onLookupBarcode = { barcode ->
                viewModel.lookupBarcode(barcode)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            isReadOnlyForMember -> "Completed Shopping (Read-Only)"
                            list?.isCompleted == true -> "Completed Shopping"
                            list?.isDraft == false -> "Edit Shopping"
                            else -> "New Shopping"
                        },
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isOwner && !isCompleted && list != null) {
                        IconButton(onClick = {
                            viewModel.generateShareCode { code ->
                                generatedShareCode = code
                                showShareDialog = true
                            }
                        }) {
                            Icon(Icons.Default.Group, contentDescription = "Invite Participants")
                        }
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
            // Live Collaboration & Session Status Banner
            if (isCompleted) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isReadOnlyForMember) {
                                "Shopping session ended by owner • Read-only mode for invited participants"
                            } else {
                                "Shopping session COMPLETED"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            } else if (list?.shareCode != null || isSharedWithMe) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Group,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Live Collaborative Session ACTIVE",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }

                        if (isOwner) {
                            TextButton(
                                onClick = {
                                    viewModel.generateShareCode { code ->
                                        generatedShareCode = code
                                        showShareDialog = true
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Invite")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Title Input
            OutlinedTextField(
                value = titleInput,
                onValueChange = {
                    if (!isReadOnlyForMember) {
                        titleInput = it
                        viewModel.updateListInfo(titleInput, locationInput)
                    }
                },
                enabled = !isReadOnlyForMember,
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
                    if (!isReadOnlyForMember) {
                        locationInput = it
                        viewModel.updateListInfo(titleInput, locationInput)
                    }
                },
                enabled = !isReadOnlyForMember,
                label = { Text("Shopping Location (Optional)") },
                placeholder = { Text("e.g. Carrefour, Naivas, Quickmart") },
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            if (!isReadOnlyForMember) {
                Spacer(modifier = Modifier.height(16.dp))

                // Quick Tools Row: Import CSV, Attach Photo & Export Text
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { csvImportLauncher.launch("*/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("CSV", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddAPhoto, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Photo", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = {
                            val currentItems = listWithItems?.items ?: emptyList()
                            val total = listWithItems?.list?.totalCents ?: 0L
                            val title = titleInput.ifBlank { "Shopping List" }
                            val sb = StringBuilder("🛒 ShopLog Receipt: $title\n")
                            if (locationInput.isNotBlank()) sb.append("📍 Location: $locationInput\n")
                            sb.append("----------------------------\n")
                            currentItems.forEach { item ->
                                val status = if (item.isPurchased) "[✓]" else "[ ]"
                                val price = Money.format(item.subtotalCents, currencySymbol)
                                sb.append("$status ${item.name} (x${item.quantity}) - $price\n")
                            }
                            sb.append("----------------------------\n")
                            sb.append("Total: ${Money.format(total, currencySymbol)}\n")
                            sb.append("\nShared via ShopLog App")

                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, sb.toString())
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Export Receipt Summary"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Digital Receipt Card Component
            ReceiptCard(
                items = listWithItems?.items ?: emptyList(),
                totalCents = listWithItems?.list?.totalCents ?: 0L,
                currencySymbol = currencySymbol,
                isEditable = !isReadOnlyForMember,
                onItemClick = { item ->
                    if (!isReadOnlyForMember) {
                        itemToEdit = item
                        showAddItemSheet = true
                    }
                },
                onAddItemClick = {
                    if (!isReadOnlyForMember) {
                        itemToEdit = null
                        showAddItemSheet = true
                    }
                },
                onItemTogglePurchased = { item, isPurchased ->
                    viewModel.toggleItemPurchased(item.id, isPurchased)
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // End Shopping Button for Owner (if active)
            if (isOwner && !isCompleted) {
                OutlinedButton(
                    onClick = { showEndShoppingDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.StopCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("End Shopping Session", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Save Button
            if (!isReadOnlyForMember) {
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
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
