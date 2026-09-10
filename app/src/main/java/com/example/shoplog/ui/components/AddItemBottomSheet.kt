package com.example.shoplog.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.shoplog.core.util.Money
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import kotlin.random.Random

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddItemBottomSheet(
    currencySymbol: String,
    editingItem: ShoppingItemEntity? = null,
    onDismiss: () -> Unit,
    onSaveItem: (name: String, quantity: Int, unitPriceCents: Long) -> Unit,
    onDeleteItem: ((itemId: String) -> Unit)? = null
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
    var showBarcodeDialog by remember { mutableStateOf(false) }

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

    if (showBarcodeDialog) {
        AlertDialog(
            onDismissRequest = { showBarcodeDialog = false },
            title = { Text("Scan Product Barcode", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Point camera at item barcode or simulate barcode scan.")
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Scanned Barcode: 6001234567${Random.nextInt(10, 99)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showBarcodeDialog = false
                        if (name.isBlank()) {
                            name = "Scanned Item ${Random.nextInt(100, 999)}"
                        }
                    }
                ) {
                    Text("Use Scanned Product")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBarcodeDialog = false }) {
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
                    placeholder = { Text("e.g. Milk, Bread, Sugar") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showBarcodeDialog = true }) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan Barcode")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Next
                    )
                )
            }

            // Quick Item Search / Auto-Complete Suggestions
            if (matchingSuggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
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
                            onSaveItem(name.trim(), quantity, currentUnitPriceCents)
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
