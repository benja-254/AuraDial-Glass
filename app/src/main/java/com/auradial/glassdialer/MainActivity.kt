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
            .background(Color(0xFF0B0B0E))
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
                            Color.Black.copy(alpha = 0.55f),
                            Color(0xFF0B0B0E).copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "AuraDial",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!isDefault || !hasContactsPerm) {
                    Surface(
                        color = Color.White.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
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
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                tint = Color(0xFF90CAF9),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (!isDefault) "Enable Dialer" else "Sync Contacts",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
                    .padding(4.dp)
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

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == 0) {
                KeypadView(
                    dialedNumber = dialedNumber,
                    matchingContacts = contacts.take(4),
                    hasContactsPerm = hasContactsPerm,
                    onDigitPress = { dialedNumber += it },
                    onBackspace = {
                        if (dialedNumber.isNotEmpty()) {
                            dialedNumber = dialedNumber.dropLast(1)
                        }
                    },
                    onClear = { dialedNumber = "" },
                    onCallNumber = { placeCall(context, it) },
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
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun KeypadView(
    dialedNumber: String,
    matchingContacts: List<ContactItem>,
    hasContactsPerm: Boolean,
    onDigitPress: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onCallNumber: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        if (dialedNumber.isNotEmpty() && matchingContacts.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            ) {
                matchingContacts.forEach { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onCallNumber(contact.phoneNumber) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = contact.phoneNumber,
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 12.sp
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Call ${contact.name}",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        } else if (!hasContactsPerm && dialedNumber.isEmpty()) {
            Text(
                text = "Tap \"Sync Contacts\" above to match names as you dial.",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = dialedNumber.ifEmpty { "Enter number" },
                color = if (dialedNumber.isEmpty()) Color.White.copy(alpha = 0.35f) else Color.White,
                fontSize = if (dialedNumber.length > 12) 26.sp else 34.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (dialedNumber.isNotEmpty()) {
                IconButton(
                    onClick = onBackspace,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Backspace,
                        contentDescription = "Delete digit",
                        tint = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }

        val rows = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            rows.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { (digit, letters) ->
                        Box(
                            modifier = Modifier
                                .size(74.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                .clickable { onDigitPress(digit) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = digit,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                if (letters.isNotEmpty()) {
                                    Text(
                                        text = letters,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 9.sp,
                                        letterSpacing = 1.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.size(56.dp))

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32))
                    .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape)
                    .clickable {
                        if (dialedNumber.isNotBlank()) {
                            onCallNumber(dialedNumber)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "Call",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            if (dialedNumber.isNotEmpty()) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Clear,
                        contentDescription = "Clear number",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(56.dp))
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
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
                            .clickable { onCallContact(contact) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.14f)),
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
