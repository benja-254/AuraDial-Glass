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
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.view.WindowManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RecentCallEntry(
    val number: String,
    val nameOrNumber: String,
    val callType: Int,
    val timestamp: Long
)

data class SimAccountOption(
    val label: String,
    val handle: PhoneAccountHandle
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

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

private fun getAvailableSimAccounts(context: Context): List<SimAccountOption> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager ?: return emptyList()
    val handles = runCatching { telecom.callCapablePhoneAccounts }.getOrNull().orEmpty()
    return handles.mapIndexed { idx, handle ->
        val account = runCatching { telecom.getPhoneAccount(handle) }.getOrNull()
        val label = account?.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${idx + 1}"
        SimAccountOption(label = label, handle = handle)
    }
}

private fun placeCall(
    context: Context,
    number: String,
    selectedSim: SimAccountOption? = null
) {
    val cleanNumber = number.trim()
    if (cleanNumber.isEmpty()) return

    val hasCallPermission = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED

    if (selectedSim != null && hasCallPermission) {
        val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        val extras = Bundle().apply {
            putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, selectedSim.handle)
        }
        val launched = runCatching {
            telecom?.placeCall(Uri.parse("tel:${Uri.encode(cleanNumber)}"), extras)
            true
        }.getOrDefault(false)
        if (launched) return
    }

    val callIntent = if (hasCallPermission) {
        Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(cleanNumber)}"))
    } else {
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(cleanNumber)}"))
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    runCatching { context.startActivity(callIntent) }
}

private fun openAddContactIntent(context: Context, initialPhone: String = "") {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        if (initialPhone.isNotBlank()) {
            putExtra(ContactsContract.Intents.Insert.PHONE, initialPhone)
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun loadRecentCalls(context: Context, filter: String = ""): List<RecentCallEntry> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val list = mutableListOf<RecentCallEntry>()
    val projection = arrayOf(
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE
    )
    runCatching {
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)

            while (cursor.moveToNext() && list.size < 30) {
                val number = if (numIdx >= 0) cursor.getString(numIdx).orEmpty() else ""
                val cachedName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                val callType = if (typeIdx >= 0) cursor.getInt(typeIdx) else CallLog.Calls.INCOMING_TYPE
                val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L

                val display = cachedName.ifBlank { number.ifBlank { "Unknown" } }
                if (filter.isBlank() ||
                    display.contains(filter, ignoreCase = true) ||
                    number.contains(filter, ignoreCase = true)
                ) {
                    list.add(
                        RecentCallEntry(
                            number = number,
                            nameOrNumber = display,
                            callType = callType,
                            timestamp = date
                        )
                    )
                }
            }
        }
    }
    return list
}

@Composable
fun AuraDialApp(initialNumber: String = "") {
    val context = LocalContext.current
    var showContactsPage by remember { mutableStateOf(false) }
    var keypadVisible by remember { mutableStateOf(true) }
    var showSearchBar by remember { mutableStateOf(false) }
    var dialedNumber by remember { mutableStateOf(initialNumber) }
    var searchQuery by remember { mutableStateOf("") }
    var isDefault by remember { mutableStateOf(isDefaultDialer(context)) }
    var promptedDefaultOnLaunch by remember { mutableStateOf(false) }
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }

    var hasContactsPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCallLogPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
        )
    }

    var contacts by remember { mutableStateOf<List<ContactItem>>(emptyList()) }
    var recents by remember { mutableStateOf<List<RecentCallEntry>>(emptyList()) }

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
        hasCallLogPerm = grants[Manifest.permission.READ_CALL_LOG] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == PackageManager.PERMISSION_GRANTED
    }

    // Prompt immediately on launch to set AuraDial as default dialer
    LaunchedEffect(Unit) {
        if (!promptedDefaultOnLaunch) {
            promptedDefaultOnLaunch = true
            if (!isDefaultDialer(context)) {
                requestDefaultDialer(context, roleLauncher)
            }
        }
    }

    val initiateCallWithSimCheck: (String) -> Unit = { number ->
        val sims = getAvailableSimAccounts(context)
        if (sims.size > 1) {
            pendingCallNumber = number
        } else {
            placeCall(context, number, sims.firstOrNull())
        }
    }

    val activeFilter = if (showSearchBar || showContactsPage) searchQuery.ifBlank { dialedNumber } else dialedNumber

    LaunchedEffect(hasContactsPerm, hasCallLogPerm, activeFilter, showContactsPage) {
        if (hasContactsPerm) {
            contacts = withContext(Dispatchers.IO) {
                ContactsHelper.searchContacts(context, activeFilter)
            }
        } else {
            contacts = emptyList()
        }

        if (hasCallLogPerm) {
            recents = withContext(Dispatchers.IO) {
                loadRecentCalls(context, activeFilter)
            }
        } else {
            recents = emptyList()
        }
    }

    val safeWallpaper = remember {
        runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeResource(context.resources, R.drawable.bg_luxury, options)?.asImageBitmap()
        }.getOrNull()
    }

    // Dual-SIM picker dialog (only shown when > 1 SIM account is available)
    val callTarget = pendingCallNumber
    if (callTarget != null) {
        val simOptions = remember(callTarget) { getAvailableSimAccounts(context) }
        AlertDialog(
            onDismissRequest = { pendingCallNumber = null },
            containerColor = Color(0xFF1A1D24),
            title = { Text("Choose SIM for call", color = Color.White, fontSize = 18.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(callTarget, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    simOptions.forEach { sim ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    pendingCallNumber = null
                                    placeCall(context, callTarget, sim)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.SimCard, contentDescription = null, tint = Color(0xFF4CAF50))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(sim.label, color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { pendingCallNumber = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.75f))
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0C10))
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
                            Color.Black.copy(alpha = 0.45f),
                            Color(0xFF0B0C10).copy(alpha = 0.76f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showContactsPage) "Contacts" else "Recents",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Normal
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PersonAdd,
                        contentDescription = "Add contact",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(21.dp)
                            .clickable { openAddContactIntent(context, dialedNumber) }
                    )
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = "Search",
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .size(21.dp)
                            .clickable { showSearchBar = !showSearchBar }
                    )
                    Box(
                        modifier = Modifier.clickable {
                            if (!isDefault) {
                                requestDefaultDialer(context, roleLauncher)
                            } else {
                                permissionsLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_PHONE_STATE,
                                        Manifest.permission.CALL_PHONE,
                                        Manifest.permission.ANSWER_PHONE_CALLS,
                                        Manifest.permission.READ_CONTACTS,
                                        Manifest.permission.READ_CALL_LOG
                                    )
                                )
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Default dialer & permissions",
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(21.dp)
                        )
                        if (!isDefault || !hasContactsPerm || !hasCallLogPerm) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF6B57))
                            )
                        }
                    }
                }
            }

            if (showSearchBar || showContactsPage) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search name or number",
                            color = Color.White.copy(alpha = 0.55f),
                            fontSize = 14.sp
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Upper/middle content: Typed number + Recents (on Dialer) or Contacts (on Contacts page)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (dialedNumber.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dialedNumber,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { dialedNumber = dialedNumber.dropLast(1) }) {
                            Icon(
                                imageVector = Icons.Rounded.Backspace,
                                contentDescription = "Delete digit",
                                tint = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (showContactsPage) {
                    if (!hasContactsPerm) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Button(onClick = {
                                permissionsLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                            }) {
                                Text("Allow Contacts Access")
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .clickable { openAddContactIntent(context, searchQuery) }
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.PersonAdd, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text("Add new contact", color = Color.White, fontWeight = FontWeight.Medium)
                                }
                            }
                            items(contacts, key = { "${it.id}_${it.phoneNumber}" }) { contact ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable { initiateCallWithSimCheck(contact.phoneNumber) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.16f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = contact.name.take(1).uppercase(),
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(contact.name, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(contact.phoneNumber, color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
                                    }
                                    Icon(Icons.Rounded.Call, contentDescription = null, tint = Color(0xFF4CAF50))
                                }
                            }
                        }
                    }
                } else {
                    // Dialer view: live contact match if typing, otherwise recents
                    if (dialedNumber.isNotEmpty() && contacts.isNotEmpty()) {
                        val topMatch = contacts.first()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.14f))
                                .clickable { initiateCallWithSimCheck(topMatch.phoneNumber) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PersonSearch, contentDescription = null, tint = Color(0xFF4FC3F7))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(topMatch.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(topMatch.phoneNumber, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                            Text("Match", color = Color(0xFF4FC3F7), fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (!hasCallLogPerm) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    permissionsLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.READ_CALL_LOG,
                                            Manifest.permission.READ_CONTACTS,
                                            Manifest.permission.READ_PHONE_STATE,
                                            Manifest.permission.CALL_PHONE
                                        )
                                    )
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Tap to allow Recents & Contacts permissions",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 12.sp
                            )
                        }
                    } else if (recents.isNotEmpty()) {
                        val visibleRecents = if (keypadVisible) recents.take(3) else recents
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(visibleRecents) { item ->
                                val timeText = remember(item.timestamp) {
                                    if (item.timestamp > 0L) {
                                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.timestamp))
                                    } else {
                                        ""
                                    }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .clickable { initiateCallWithSimCheck(item.number) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when (item.callType) {
                                            CallLog.Calls.OUTGOING_TYPE -> Icons.Rounded.CallMade
                                            CallLog.Calls.MISSED_TYPE -> Icons.Rounded.CallMissed
                                            else -> Icons.Rounded.CallReceived
                                        },
                                        contentDescription = null,
                                        tint = if (item.callType == CallLog.Calls.MISSED_TYPE) Color(0xFFFF5252) else Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.nameOrNumber,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (item.nameOrNumber != item.number && item.number.isNotBlank()) {
                                            Text(item.number, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                        }
                                    }
                                    if (timeText.isNotEmpty()) {
                                        Text(timeText, color = Color.White.copy(alpha = 0.55f), fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Keypad sheet + green call button
            if (keypadVisible && !showContactsPage) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(28.dp))
                        .padding(top = 12.dp, bottom = 12.dp, start = 12.dp, end = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keyRows = listOf(
                        listOf("1" to "", "2" to "ABC", "3" to "DEF"),
                        listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
                        listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
                        listOf("*" to "", "0" to "+", "#" to "")
                    )

                    keyRows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { (digit, letters) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { dialedNumber += digit },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(digit, color = Color.White, fontSize = 25.sp)
                                        if (letters.isNotEmpty()) {
                                            Text(
                                                letters,
                                                color = Color.White.copy(alpha = 0.65f),
                                                fontSize = 9.sp,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF43A047))
                            .clickable {
                                val target = dialedNumber.ifBlank {
                                    recents.firstOrNull()?.number.orEmpty()
                                }
                                if (target.isNotBlank()) {
                                    initiateCallWithSimCheck(target)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Call,
                            contentDescription = "Call",
                            tint = Color.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // Bottom navigation bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomPillTab(
                        text = "Recents",
                        icon = Icons.Rounded.Schedule,
                        selected = !showContactsPage,
                        onClick = {
                            showContactsPage = false
                            keypadVisible = true
                        }
                    )
                    BottomPillTab(
                        text = "Contacts",
                        icon = Icons.Rounded.Person,
                        selected = showContactsPage,
                        onClick = { showContactsPage = true }
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable {
                            showContactsPage = false
                            keypadVisible = !keypadVisible
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Apps,
                        contentDescription = "Toggle keypad",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomPillTab(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.65f),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
        )
    }
}
