from pathlib import Path
from datetime import datetime
import shutil

root = Path.cwd()
app = root / 'app'
main = app / 'src/main/java/com/example/telegramrust/MainActivity.kt'
styles = app / 'src/main/res/values/styles.xml'
manifest = app / 'src/main/AndroidManifest.xml'
icon = app / 'src/main/res/drawable/rustgram_logo.xml'
if not main.exists() or not styles.exists() or not manifest.exists():
    raise SystemExit('Запусти этот скрипт из корня telegram-rust-client-androidide-compatible')

backup = root / ('backup-before-stage3-' + datetime.now().strftime('%Y%m%d-%H%M%S'))
for target in (main, styles, manifest):
    dst = backup / target.relative_to(root)
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(target, dst)

styles.write_text('''<resources>
    <style name="Theme.TelegramRustClient" parent="android:style/Theme.Material.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowBackground">#000000</item>
        <item name="android:colorAccent">#4F46E5</item>
        <item name="android:windowLightStatusBar">false</item>
        <item name="android:statusBarColor">#000000</item>
        <item name="android:navigationBarColor">#000000</item>
        <item name="android:textColorPrimary">#F5F3FF</item>
        <item name="android:textColorSecondary">#8B8B98</item>
    </style>
</resources>
''', encoding='utf-8')

icon.parent.mkdir(parents=True, exist_ok=True)
icon.write_text('''<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#07050D" android:pathData="M0,0h108v108h-108z"/>
    <path android:fillColor="#17112D" android:alpha="0.55" android:pathData="M0,0h108v108h-108z"/>
    <path android:fillColor="#7064F2" android:strokeColor="#B9B5FF" android:strokeWidth="1.3" android:strokeLineJoin="round" android:pathData="M17,48 L89,22 L55,84 L44,65 L24,57 Q20,55 24,52 L39,45 Z"/>
    <path android:fillColor="#978FFF" android:alpha="0.55" android:pathData="M17,48 L89,22 L44,57 L39,45 Z"/>
    <path android:fillColor="#30258C" android:alpha="0.55" android:pathData="M44,57 L89,22 L55,84 L44,65 Z"/>
    <path android:fillColor="@android:color/transparent" android:strokeColor="#F4F5FB" android:strokeWidth="1.1" android:strokeLineCap="round" android:pathData="M44,57 L89,22"/>
    <path android:fillColor="@android:color/transparent" android:strokeColor="#FFFFFF" android:strokeWidth="1.1" android:strokeLineCap="round" android:pathData="M33,49 l5,2 3,-2 4,2 M46,59 l5,2 2,-2 4,3 M58,48 l4,-2 3,2 4,-1 M66,37 l3,2 3,-1"/>
    <path android:fillColor="#FFFFFF" android:pathData="M39,50h1v1h-1z M53,61h1v1h-1z M71,37h1v1h-1z"/>
</vector>
''', encoding='utf-8')

m = manifest.read_text(encoding='utf-8')
m = m.replace('<application android:theme="@style/Theme.TelegramRustClient" android:label="Telegram Rust Client"', '<application android:theme="@style/Theme.TelegramRustClient" android:icon="@drawable/rustgram_logo" android:label="RustGram"')
manifest.write_text(m, encoding='utf-8')

s = main.read_text(encoding='utf-8')
if 'import android.graphics.drawable.GradientDrawable' not in s:
    s = s.replace('import android.graphics.Bitmap\n', 'import android.graphics.Bitmap\nimport android.graphics.drawable.GradientDrawable\n')
s = s.replace('setTextColor(Color.DKGRAY)', 'setTextColor(Color.rgb(245, 243, 255))')
if 'setHintTextColor(Color.rgb(139, 139, 152))' not in s and 'hint = hintText' in s:
    s = s.replace('            hint = hintText\n            inputType = type', '            hint = hintText\n            inputType = type\n            setTextColor(Color.rgb(245, 243, 255))\n            setHintTextColor(Color.rgb(139, 139, 152))\n            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(79, 70, 229))')
if 'setImageResource(R.drawable.rustgram_logo)' not in s and 'root.addView(text("Telegram Rust Client", 24f))' in s:
    s = s.replace('        root.addView(text("Telegram Rust Client", 24f))', '        val logo = ImageView(this).apply {\n            setImageResource(R.drawable.rustgram_logo)\n            adjustViewBounds = true\n            setPadding(0, 0, 0, 12)\n        }\n        root.addView(logo, LinearLayout.LayoutParams(-1, (92 * resources.displayMetrics.density).toInt()))\n        root.addView(text("RustGram", 24f))')
if 'window.statusBarColor = Color.BLACK' not in s:
    s = s.replace('        super.onCreate(savedInstanceState)\n', '        super.onCreate(savedInstanceState)\n        window.statusBarColor = Color.BLACK\n        window.navigationBarColor = Color.BLACK\n')
if 'setBackgroundColor(Color.BLACK)' not in s:
    s = s.replace('            orientation = LinearLayout.VERTICAL\n', '            orientation = LinearLayout.VERTICAL\n            setBackgroundColor(Color.BLACK)\n', 1)
main.write_text(s, encoding='utf-8')
print('Stage 3 применён: AMOLED-тема, фиолетовый акцент и иконка RustGram')
print('Резервная копия:', backup)
