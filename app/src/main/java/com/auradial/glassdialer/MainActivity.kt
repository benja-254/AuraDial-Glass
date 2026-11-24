package com.auradial.glassdialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialNumber = if (intent?.action == Intent.ACTION_DIAL) {
            intent.data?.schemeSpecificPart.orEmpty()
        } else {
            ""
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AuraDialApp(initialNumber = initialNumber)
            }
        }
    }
}

private fun isDefaultDialer(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    } else {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        telecom?.defaultDialerPackage == context.packageName
    }
}

private fun requestDefaultDialer(
    context: Context,
    roleLauncher: ActivityResultLauncher<Intent>
) {
    if (isDefaultDialer(context)) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(RoleManager::class.java)
        if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            runCatching {
                roleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            }
        }
    } else {
        @Suppress("DEPRECATION")
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(
                TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                context.packageName
            )
        }
        runCatching {
            roleLauncher.launch(intent)
        }
    }
}

private fun placeCall(context: Context, number: String) {
    val cleanNumber = number.trim()
    if (cleanNumber.isEmpty()) return

    val hasCallPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED

    val callIntent = if (hasCallPermission) {
        Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(cleanNumber)}"))
    } else {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(cleanNumber)}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(callIntent) }
}

@Composable
fun AuraDialApp(initialNumber: String = "") {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    var dialedNumber by remember { mutableStateOf(initialNumber) }
    var searchQuery by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(isDefaultDialer(context)) }
    var hasContactsPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }

    val roleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isDefault = isDefaultDialer(context)
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasContactsPerm = grants[Manifest.permission.READ_CONTACTS] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        requestDefaultDialer(context, roleLauncher)
    }

    val activeFilter = if (selectedTab == 0) dialedNumber else searchQuery

    LaunchedEffect(hasContactsPerm, activeFilter, selectedTab) {
        if (hasContactsPerm) {
            contacts = withContext(Dispatchers.IO) {
                ContactsHelper.searchContacts(context, activeFilter)
            }
        } else {
            contacts = emptyList()
        }
    }

    val safeWallpaper = remember {
        runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeResource(context.resources, R.drawable.bg_luxury, options)?.asImageBitmap()
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
    ) {
        if (safeWallpaper != null) {
            Image(
                bitmap = safeWallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.52f),
                            Color(0xFF0C0D10).copy(alpha = 0.78f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AuraDial",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (!isDefault || !hasContactsPerm) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.ANSWER_PHONE_CALLS,
                                        Manifest.permission.READ_CONTACTS
                                    )
                                )
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = if (!isDefault) "Set Default" else "Contacts",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mode switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(3.dp)
            ) {
                TabPill(
                    text = "Keypad",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                TabPill(
                    text = "Contacts",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (selectedTab == 0) {
                GlassLockPadView(
                    dialedNumber = dialedNumber,
                    matchingContacts = contacts.take(3),
                    onDigitPress = { dialedNumber += it },
                    onBackspace = {
                        if (dialedNumber.isNotEmpty()) {
                            dialedNumber = dialedNumber.dropLast(1)
                        }
                    },
                    onClear = { dialedNumber = "" },
                    onCall = { numberToCall ->
                        placeCall(context, numberToCall)
                    },
                    modifier = Modifier.weight(1f)
                )
            } else {
                ContactsListView(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    contacts = contacts,
                    hasContactsPerm = hasContactsPerm,
                    onRequestContacts = {
                        permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                    },
                    onCallContact = { placeCall(context, it.phoneNumber) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun TabPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun GlassLockPadView(
    dialedNumber: String,
    matchingContacts: List<ContactItem>,
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCall: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top display + live contact match suggestion
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
        ) {
            Text(
                text = if (dialedNumber.isEmpty()) "Enter number" else dialedNumber,
                color = if (dialedNumber.isEmpty()) Color.White.copy(alpha = 0.85f) else Color.White,
                fontSize = if (dialedNumber.length > 12) 24.sp else 28.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (dialedNumber.isNotEmpty() && matchingContacts.isNotEmpty()) {
                val topMatch = matchingContacts.first()
                Text(
                    text = "${topMatch.name} • ${topMatch.phoneNumber}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onCall(topMatch.phoneNumber) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    text = "Tap digits to dial or search contacts",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp
                )
            }
        }

        // Frosted circular 1-9 grid
        val rows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    row.forEach { (digit, letters) ->
                        FrostedDigitCircle(
                            digit = digit,
                            letters = letters,
                            onClick = { onDigitPress(digit) }
                        )
                    }
                }
            }

            // Bottom row: Backspace icon | 0 (+) | CALL action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable(
                            enabled = dialedNumber.isNotEmpty(),
                            onClick = onBackspace
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (dialedNumber.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Rounded.Backspace,
                            contentDescription = "Backspace",
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                FrostedDigitCircle(
                    digit = "0",
                    letters = "+",
                    onClick = { onDigitPress("0") }
                )

                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .clickable {
                            val target = dialedNumber.ifBlank {
                                matchingContacts.firstOrNull()?.phoneNumber.orEmpty()
                            }
                            if (target.isNotBlank()) {
                                onCall(target)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CALL",
                        color = if (dialedNumber.isNotEmpty()) Color.White else Color.White.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Bottom pill action (Clear / Emergency)
        Box(
            modifier = Modifier
                .padding(bottom = 6.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.14f))
                .clickable {
                    if (dialedNumber.isNotEmpty()) {
                        onClear()
                    } else {
                        onCall("112")
                    }
                }
                .padding(horizontal = 24.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (dialedNumber.isNotEmpty()) "Clear number" else "Emergency call",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FrostedDigitCircle(
    digit: String,
    letters: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.24f),
                        Color.White.copy(alpha = 0.12f)
                    )
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = digit,
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Normal
            )
            if (letters.isNotEmpty()) {
                Text(
                    text = letters,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}

@Composable
private fun ContactsListView(
    query: String,
    onQueryChange: (String) -> Unit,
    contacts: List<ContactItem>,
    hasContactsPerm: Boolean,
    onRequestContacts: () -> Unit,
    onCallContact: (ContactItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search contacts or number", color = Color.White.copy(alpha = 0.5f)) },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (!hasContactsPerm) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = onRequestContacts) {
                    Text("Allow Contacts Access")
                }
            }
        } else if (contacts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (query.isBlank()) "No phone contacts found." else "No matching contacts.",
                    color = Color.White.copy(alpha = 0.65f)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(contacts, key = { "${it.id}_${it.phoneNumber}" }) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                            .clickable { onCallContact(contact) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contact.name.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = contact.phoneNumber,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Call ${contact.name}",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}
