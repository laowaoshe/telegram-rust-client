from pathlib import Path
from datetime import datetime
import shutil
import re

root = Path.cwd()
main = root / 'app/src/main/java/com/example/telegramrust/MainActivity.kt'
bridge = root / 'app/src/main/java/com/example/telegramrust/RustBridge.kt'
rust = root / 'rust/src/lib.rs'
for p in (main, bridge, rust):
    if not p.exists():
        raise SystemExit('Запусти скрипт из корня telegram-rust-client-androidide-compatible')

m = main.read_text(encoding='utf-8')
b = bridge.read_text(encoding='utf-8')
r = rust.read_text(encoding='utf-8')
if 'private lateinit var dialogsScreen' not in m:
    raise SystemExit('Нужна версия проекта после Stage 6')
if 'nativeSearchUsername' in b:
    raise SystemExit('Stage 8 уже применён')

backup_dir = root / ('backup-before-stage8-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
for target in (main, bridge, rust):
    dst = backup_dir / target.relative_to(root)
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(target, dst)

# Local chat search field.
m = m.replace('import android.text.InputType\n', 'import android.text.InputType\nimport android.text.TextWatcher\nimport android.text.Editable\n', 1)
m = m.replace('    private lateinit var dialogs: LinearLayout\n', '    private lateinit var dialogs: LinearLayout\n    private var cachedDialogsResult = ""\n', 1)
old_search = '        dialogsScreen.addView(field("Поиск по чатам"))'
new_search = '''        val dialogSearch = field("⌕  Поиск по чатам")
        dialogsScreen.addView(dialogSearch)
        dialogSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderDialogs(cachedDialogsResult, s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })'''
if old_search not in m:
    raise SystemExit('Не найдено поле поиска диалогов')
m = m.replace(old_search, new_search, 1)

# Functional Contacts screen: public username lookup.
old_contacts = '''        contactsScreen.addView(text("Контакты", 22f))
        contactsScreen.addView(text("Раздел контактов подготовлен для следующего этапа.", 15f, muted))'''
new_contacts = '''        contactsScreen.addView(text("Пользователи", 22f))
        contactsScreen.addView(text("Найди пользователя по @username", 15f, muted))
        val usernameField = field("@username")
        val usernameButton = glassButton("Найти пользователя", purple)
        val usernameResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        contactsScreen.addView(usernameField)
        addGap(contactsScreen)
        contactsScreen.addView(usernameButton)
        addGap(contactsScreen)
        contactsScreen.addView(usernameResults)
        usernameButton.setOnClickListener {
            val query = usernameField.text.toString().trim()
            if (query.isEmpty()) {
                status.text = "Введи username пользователя"
                return@setOnClickListener
            }
            status.text = "Ищу пользователя…"
            runRust({ RustBridge.nativeSearchUsername(query) }) { result ->
                usernameResults.removeAllViews()
                val resultCard = glassButton("◉  $result", surfaceSoft)
                usernameResults.addView(resultCard)
                status.text = if (result.startsWith("Ошибка:")) result else "Пользователь найден"
            }
        }'''
if old_contacts not in m:
    raise SystemExit('Не найден экран контактов Stage 6')
m = m.replace(old_contacts, new_contacts, 1)

# Replace dialog renderer with filtered version.
pattern = re.compile(r'    private fun showDialogs\(result: String\) \{.*?\n    \}\n\n    private fun loadMessages', re.S)
replacement = '''    private fun showDialogs(result: String) {
        cachedDialogsResult = result
        renderDialogs(result, "")
    }

    private fun renderDialogs(result: String, query: String) {
        dialogs.removeAllViews()
        if (result.startsWith("Ошибка:")) { status.text = result; return }
        val rows = result.lines().filter { it.contains("|") }
            .filter { query.isBlank() || it.substringAfter("|").contains(query.trim(), ignoreCase = true) }
        if (rows.isEmpty()) {
            val empty = TextView(this).apply {
                text = if (query.isBlank()) "Диалогов пока нет" else "Ничего не найдено"
                textSize = 15f
                setTextColor(muted)
                setPadding(dp(8), dp(16), dp(8), dp(16))
            }
            dialogs.addView(empty)
            return
        }
        rows.forEach { row ->
            val parts = row.split("|", limit = 2)
            val index = parts[0].toIntOrNull() ?: return@forEach
            val title = parts.getOrNull(1)?.ifBlank { "Без названия" } ?: "Без названия"
            val item = glassButton("◉  $title", surfaceSoft)
            item.gravity = Gravity.START
            item.setOnClickListener {
                selectedDialog = index
                chatTitle.text = title
                chatScreen.visibility = View.VISIBLE
                dialogsScreen.visibility = View.GONE
                contactsScreen.visibility = View.GONE
                settingsScreen.visibility = View.GONE
                status.text = "Загружаю историю…"
                loadMessages(index)
            }
            val params = LinearLayout.LayoutParams(-1, -2)
            params.setMargins(0, dp(5), 0, dp(5))
            dialogs.addView(item, params)
        }
    }

    private fun loadMessages'''
m, count = pattern.subn(replacement, m, count=1)
if count != 1:
    raise SystemExit('Не удалось заменить список диалогов')
main.write_text(m, encoding='utf-8')

# Kotlin bridge.
b = b.replace('    external fun nativeDialogs(): String\n', '    external fun nativeDialogs(): String\n    external fun nativeSearchUsername(query: String): String\n', 1)
bridge.write_text(b, encoding='utf-8')

# Rust username resolver.
rust_fn = '''
fn search_username(query: String) -> String {
    let username = query.trim().trim_start_matches('@').to_string();
    if username.is_empty() {
        return err("введи username пользователя");
    }
    let mut slot = match engine_slot().lock() {
        Ok(value) => value,
        Err(_) => return err("внутреннее состояние Rust повреждено"),
    };
    let engine = match slot.as_mut() {
        Some(value) => value,
        None => return err("сначала подключись к Telegram"),
    };
    match engine.runtime.block_on(engine.client.resolve_username(&username)) {
        Ok(Some(peer)) => ok(peer.name().unwrap_or("Без имени").to_string()),
        Ok(None) => err("пользователь не найден"),
        Err(e) => err(e.to_string()),
    }
}

'''
if 'fn search_username(query: String)' not in r:
    pos = r.index('fn dialogs() -> String {')
    r = r[:pos] + rust_fn + r[pos:]

jni_fn = '''
#[no_mangle]
pub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeSearchUsername(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
) -> jstring {
    let value = match take_string(&mut env, query) {
        Ok(query) => search_username(query),
        Err(e) => err(e),
    };
    jstring(&mut env, value)
}

'''
if 'Java_com_example_telegramrust_RustBridge_nativeSearchUsername' not in r:
    pos = r.index('#[no_mangle]\npub extern "system" fn Java_com_example_telegramrust_RustBridge_nativeDialogs')
    r = r[:pos] + jni_fn + r[pos:]
rust.write_text(r, encoding='utf-8')

print('Stage 8 применён: поиск чатов и поиск пользователей по username')
print('Резервная копия:', backup_dir)
