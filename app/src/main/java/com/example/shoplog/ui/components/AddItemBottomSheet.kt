package com.example.shoplog.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.shoplog.core.util.BarcodeScannerManager
import com.example.shoplog.core.util.CameraBarcodeScannerDialog
import com.example.shoplog.core.util.Money
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.data.model.ProductLookupResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemBottomSheet(
    currencySymbol: String,
    editingItem: ShoppingItemEntity? = null,
    onDismiss: () -> Unit,
    onSaveItem: (name: String, quantity: Int, unitPriceCents: Long, barcode: String?) -> Unit,
    onDeleteItem: ((itemId: String) -> Unit)? = null,
    onLookupBarcode: (suspend (String) -> ProductLookupResult)? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf(editingItem?.name ?: "") }
    var quantity by remember { mutableIntStateOf(editingItem?.quantity ?: 1) }
    var priceInput by remember {
        mutableStateOf(
            if (editingItem != null) Money.centsToInputValue(editingItem.unitPriceCents) else ""
        )
    }

    var scannedBarcode by remember { mutableStateOf(editingItem?.barcode ?: "") }
    var showCameraScanner by remember { mutableStateOf(false) }
    var showManualBarcodeDialog by remember { mutableStateOf(false) }
    var manualBarcodeEntry by remember { mutableStateOf("") }

    var isLookingUp by remember { mutableStateOf(false) }
    var lookupStatusMessage by remember { mutableStateOf<String?>(null) }
    var lookupStatusType by remember { mutableStateOf<StatusType?>(null) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val sampleBarcodeProducts = remember {
        listOf(
            Triple("5449000000996", "Coca-Cola 500ml", "1.99"),
            Triple("5000159461122", "KitKat Chocolate", "1.49"),
            Triple("737628064502", "Thai Rice Noodles", "2.99"),
            Triple("600123456001", "Full Cream Milk 2L", "2.50"),
            Triple("600123456002", "Whole Wheat Bread", "1.80")
        )
    }

    fun handleBarcodeScanned(barcode: String) {
        val cleanCode = barcode.trim()
        if (cleanCode.isBlank()) return

        scannedBarcode = cleanCode
        isLookingUp = true
        lookupStatusMessage = "Looking up product $cleanCode..."
        lookupStatusType = StatusType.LOADING

        scope.launch {
            val result = onLookupBarcode?.invoke(cleanCode) ?: ProductLookupResult.NotFound(cleanCode)

            isLookingUp = false
            when (result) {
                is ProductLookupResult.Found -> {
                    name = result.product.name
                    lookupStatusMessage = if (result.isFromLocalCache) {
                        "Barcode $cleanCode detected • Found in local product cache ✓"
                    } else {
                        "Barcode $cleanCode detected • Found online ✓"
                    }
                    lookupStatusType = StatusType.SUCCESS
                }
                is ProductLookupResult.Offline -> {
                    lookupStatusMessage = "Barcode $cleanCode detected • Device is offline and product not cached locally. Enter product name manually."
                    lookupStatusType = StatusType.OFFLINE
                }
                is ProductLookupResult.NotFound -> {
                    lookupStatusMessage = "Barcode $cleanCode detected • Product information could not be found. Enter product name manually."
                    lookupStatusType = StatusType.INFO
                }
                is ProductLookupResult.Error -> {
                    lookupStatusMessage = "Barcode $cleanCode detected • Lookup failed. Enter product name manually."
                    lookupStatusType = StatusType.INFO
                }
            }
        }
    }

    val commonItemDatabase = listOf(
        "Milk", "Bread", "Eggs", "Sugar", "Rice", "Flour", "Cooking Oil", "Coffee",
        "Tea", "Butter", "Cheese", "Soap", "Apples", "Bananas", "Salt", "Tomatoes",
        "Onions", "Potatoes", "Chicken", "Beef", "Water", "Juice", "Yogurt"
    )

    val matchingSuggestions = if (name.isNotBlank()) {
        commonItemDatabase.filter { it.contains(name.trim(), ignoreCase = true) && !it.equals(name.trim(), ignoreCase = true) }.take(5)
    } else {
        commonItemDatabase.shuffled().take(5)
    }

    val currentUnitPriceCents = Money.parseToCents(priceInput)
    val calculatedSubtotalCents = Money.calculateSubtotal(quantity, currentUnitPriceCents)

    fun handleDismiss() {
        keyboardController?.hide()
        focusManager.clearFocus()
        onDismiss()
    }

    // Camera Barcode Scanner Dialog
    if (showCameraScanner) {
        CameraBarcodeScannerDialog(
            onBarcodeScanned = { rawBarcode ->
                showCameraScanner = false
                handleBarcodeScanned(rawBarcode)
            },
            onDismiss = {
                showCameraScanner = false
            }
        )
    }

    // Manual Barcode Input Dialog Fallback
    if (showManualBarcodeDialog) {
        AlertDialog(
            onDismissRequest = { showManualBarcodeDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Manual Barcode Lookup", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enter a product barcode (EAN-13, EAN-8, UPC-A, UPC-E) or pick a sample preset:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = manualBarcodeEntry,
                        onValueChange = { manualBarcodeEntry = it },
                        label = { Text("Barcode Number") },
                        placeholder = { Text("e.g. 5449000000996") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Sample Test Barcodes:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        sampleBarcodeProducts.forEach { (code, pName, _) ->
                            SuggestionChip(
                                onClick = {
                                    manualBarcodeEntry = code
                                },
                                label = { Text("$pName ($code)", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val entered = manualBarcodeEntry.trim()
                        showManualBarcodeDialog = false
                        if (entered.isNotBlank()) {
                            handleBarcodeScanned(entered)
                        }
                    },
                    enabled = manualBarcodeEntry.isNotBlank()
                ) {
                    Text("Lookup Barcode")
                }
            },
            dismissButton = {
                TextButton(onClick = { showManualBarcodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = ::handleDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = if (editingItem == null) "Add Shopping Item" else "Edit Shopping Item",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Item Name Input with Barcode Scanner Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name") },
                    placeholder = { Text("Type name OR scan barcode 📷") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isLookingUp) {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                            IconButton(
                                onClick = {
                                    // Try native camera scanner first
                                    BarcodeScannerManager.startCameraScan(
                                        context = context,
                                        onBarcodeScanned = { barcode ->
                                            handleBarcodeScanned(barcode)
                                        },
                                        onErrorOrFallback = { _ ->
                                            showCameraScanner = true
                                        }
                                    )
                                }
                            ) {
                                Icon(
                                    Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan Product Barcode with Camera",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )
            }

            // Status Banner for Barcode Lookup Results
            if (lookupStatusMessage != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = when (lookupStatusType) {
                        StatusType.SUCCESS -> MaterialTheme.colorScheme.primaryContainer
                        StatusType.OFFLINE -> MaterialTheme.colorScheme.tertiaryContainer
                        StatusType.INFO -> MaterialTheme.colorScheme.surfaceContainerHigh
                        StatusType.LOADING -> MaterialTheme.colorScheme.secondaryContainer
                        null -> MaterialTheme.colorScheme.surfaceContainer
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (lookupStatusType) {
                            StatusType.SUCCESS -> Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            StatusType.OFFLINE -> Icon(
                                Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            StatusType.LOADING -> CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            else -> Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = lookupStatusMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Quick Manual Barcode Trigger Link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        manualBarcodeEntry = scannedBarcode
                        showManualBarcodeDialog = true
                    }
                ) {
                    Text(
                        text = if (scannedBarcode.isBlank()) "Type Barcode Number" else "Barcode: $scannedBarcode (edit)",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Quick Item Search / Auto-Complete Suggestions
            if (matchingSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    matchingSuggestions.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { name = suggestion },
                            label = { Text(suggestion, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Quantity Stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Quantity",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { if (quantity > 1) quantity-- },
                        enabled = quantity > 1
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease Quantity")
                    }

                    OutlinedTextField(
                        value = quantity.toString(),
                        onValueChange = { input ->
                            val parsed = input.filter { it.isDigit() }.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                quantity = parsed
                            } else if (input.isEmpty()) {
                                quantity = 1
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.width(70.dp),
                        shape = RoundedCornerShape(8.dp),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Next
                        )
                    )

                    IconButton(
                        onClick = { quantity++ }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase Quantity")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Unit Price Input
            OutlinedTextField(
                value = priceInput,
                onValueChange = { priceInput = it },
                label = { Text("Price per item ($currencySymbol)") },
                placeholder = { Text("0.00") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Live Subtotal Calculation Preview
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subtotal:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = Money.format(calculatedSubtotalCents, currencySymbol),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (editingItem != null && onDeleteItem != null) {
                    OutlinedButton(
                        onClick = {
                            onDeleteItem(editingItem.id)
                            handleDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }

                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            onSaveItem(
                                name.trim(),
                                quantity,
                                currentUnitPriceCents,
                                scannedBarcode.ifBlank { null }
                            )
                            onDismiss()
                        }
                    },
                    enabled = name.isNotBlank(),
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(if (editingItem == null) "Add to List" else "Save Changes")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private enum class StatusType {
    SUCCESS,
    OFFLINE,
    INFO,
    LOADING
}
