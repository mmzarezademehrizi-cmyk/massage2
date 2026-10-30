package app.msg

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val Ink = Color(0xFF111111)
val Soft = Color(0xFFF2F2F2)

suspend fun <T> io(f: () -> T): T = withContext(Dispatchers.IO) { f() }
fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
fun Msg.preview() = text ?: if (type?.startsWith("image") == true) "عکس" else "فایل: $name"

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val api = Api(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Ink)) {
                Surface(Modifier.fillMaxSize(), color = Color.White) { App(api) }
            }
        }
    }
}

@Composable
fun App(api: Api) {
    var user by remember { mutableStateOf(api.me) }
    var peer by remember { mutableStateOf<String?>(null) }
    BackHandler(peer != null) { peer = null }
    val p = peer
    when {
        user == null || api.token == null -> Login(api) { user = it }
        p == null -> Chats(api, { peer = it }) { api.token = null; api.me = null; user = null }
        else -> Chat(api, p) { peer = null }
    }
}

@Composable
fun Field(v: String, on: (String) -> Unit, hint: String, pass: Boolean = false, mod: Modifier = Modifier.fillMaxWidth()) =
    TextField(
        v, on, mod,
        placeholder = { Text(hint, color = Color.Gray) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = if (pass) PasswordVisualTransformation() else VisualTransformation.None,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Soft, unfocusedContainerColor = Soft,
            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
        )
    )

@Composable
fun Login(api: Api, done: (String) -> Unit) {
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center) {
        Text("Msg", fontSize = 36.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(32.dp))
        Field(u, { u = it }, "نام کاربری (انگلیسی)")
        Spacer(Modifier.height(12.dp))
        Field(p, { p = it }, "رمز عبور", true)
        if (err.isNotEmpty()) Text(err, color = Color(0xFFC62828), modifier = Modifier.padding(top = 12.dp), fontSize = 13.sp)
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                scope.launch {
                    try { io { api.auth(reg, u.trim(), p) }; done(api.me!!) }
                    catch (e: Exception) { err = "ناموفق بود. نام کاربری ۳ تا ۲۰ حرف انگلیسی/عدد و رمز حداقل ۶ کاراکتر باشد." }
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ink)
        ) { Text(if (reg) "ساخت حساب" else "ورود") }
        TextButton(onClick = { reg = !reg }) { Text(if (reg) "حساب دارم" else "حساب ندارم؟ بساز", color = Color.Gray) }
    }
}

@Composable
fun Chats(api: Api, open: (String) -> Unit, out: () -> Unit) {
    var list by remember { mutableStateOf(listOf<Msg>()) }
    var dlg by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            try { list = io { api.chats() } } catch (_: Exception) {}
            delay(3000)
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(24.dp, 20.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("پیام‌ها", fontSize = 26.sp, fontWeight = FontWeight.Light)
            Row {
                TextButton(onClick = out) { Text("خروج", color = Color.Gray) }
                TextButton(onClick = { dlg = true }) { Text("+ جدید") }
            }
        }
        LazyColumn {
            items(list, key = { it.id }) { m ->
                val peer = if (m.from == api.me) m.to else m.from
                Column(Modifier.fillMaxWidth().clickable { open(peer) }.padding(24.dp, 14.dp)) {
                    Text(peer, fontWeight = FontWeight.Medium)
                    Text(m.preview(), color = Color.Gray, maxLines = 1, fontSize = 14.sp)
                }
            }
        }
    }
    if (dlg) {
        var n by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { dlg = false },
            confirmButton = { TextButton(onClick = { dlg = false; if (n.isNotBlank()) open(n.trim().lowercase()) }) { Text("شروع") } },
            title = { Text("گفتگوی جدید") },
            text = { Field(n, { n = it }, "نام کاربری") }
        )
    }
}

@Composable
fun Chat(api: Api, peer: String, back: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var msgs by remember { mutableStateOf(listOf<Msg>()) }
    var txt by remember { mutableStateOf("") }
    val st = rememberLazyListState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u ->
            scope.launch {
                try {
                    val (k, n, t) = io { api.upload(ctx, u) }
                    io { api.send(peer, null, k, n, t) }
                } catch (_: Exception) { toast(ctx, "ارسال نشد") }
            }
        }
    }
    LaunchedEffect(peer) {
        while (true) {
            try {
                val last = msgs.lastOrNull()?.id ?: 0
                val n = io { api.messages(peer, last) }
                if (n.isNotEmpty()) msgs = msgs + n
            } catch (_: Exception) {}
            delay(1500)
        }
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) st.animateScrollToItem(msgs.lastIndex) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back) { Text("‹", fontSize = 30.sp, color = Ink) }
            Text(peer, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 16.dp),
            state = st,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) { items(msgs, key = { it.id }) { Bubble(api, it, it.from == api.me) } }
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { pick.launch("*/*") }) { Text("+", fontSize = 26.sp, color = Ink) }
            Field(txt, { txt = it }, "پیام", mod = Modifier.weight(1f))
            TextButton(onClick = {
                val t = txt.trim()
                if (t.isNotEmpty()) {
                    txt = ""
                    scope.launch { try { io { api.send(peer, t) } } catch (_: Exception) { toast(ctx, "ارسال نشد") } }
                }
            }) { Text("ارسال", color = Ink) }
        }
    }
}

@Composable
fun Bubble(api: Api, m: Msg, mine: Boolean) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val fg = if (mine) Color.White else Ink
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 280.dp).background(if (mine) Ink else Soft, RoundedCornerShape(18.dp)).padding(12.dp, 8.dp)) {
            val k = m.key
            if (k != null) {
                if (m.type?.startsWith("image") == true) {
                    val bmp by produceState<ImageBitmap?>(null, k) {
                        value = try {
                            io {
                                val b = api.fileBytes(k)
                                val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                                BitmapFactory.decodeByteArray(b, 0, b.size, o).asImageBitmap()
                            }
                        } catch (_: Exception) { null }
                    }
                    bmp?.let { Image(it, null, Modifier.widthIn(max = 240.dp).clip(RoundedCornerShape(12.dp))) }
                } else {
                    Text("↓ ${m.name}", color = fg, modifier = Modifier.clickable {
                        scope.launch {
                            try {
                                io { saveDownload(ctx, m.name ?: "file", m.type ?: "*/*", api.fileBytes(k)) }
                                toast(ctx, "در Downloads ذخیره شد")
                            } catch (_: Exception) { toast(ctx, "دانلود نشد") }
                        }
                    })
                }
            }
            m.text?.let { Text(it, color = fg) }
        }
    }
}
