package com.opensstp.android

import android.content.Context
import android.view.Gravity
import android.view.View

/**
 * Tiny in-app translation layer.
 * Language is stored in the plain "ui_prefs" file and applied by recreating the activity.
 * Every entry below is ordered like [LANGS]: fa, en, ar, tr, de, fr, es, ru, ja, zh.
 */
object Lang {
    class Language(val code: String, val nativeName: String, val rtl: Boolean)

    val LANGS = listOf(
        Language("fa", "فارسی", true),
        Language("en", "English", false),
        Language("ar", "العربية", true),
        Language("tr", "Türkçe", false),
        Language("de", "Deutsch", false),
        Language("fr", "Français", false),
        Language("es", "Español", false),
        Language("ru", "Русский", false),
        Language("ja", "日本語", false),
        Language("zh", "中文", false)
    )

    private var index = 0

    fun init(ctx: Context) {
        val code = ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).getString("lang", "fa")
        index = LANGS.indexOfFirst { it.code == code }.let { if (it < 0) 0 else it }
    }

    fun set(ctx: Context, i: Int) {
        index = i
        ctx.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE).edit().putString("lang", LANGS[i].code).apply()
    }

    val current: Int get() = index
    val isRtl: Boolean get() = LANGS[index].rtl

    /** Layout direction for containers that used to be hard-coded RTL. */
    val dir: Int get() = if (isRtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

    /** Text-start gravity (right for Persian/Arabic, left otherwise). */
    val start: Int get() = if (isRtl) Gravity.RIGHT else Gravity.LEFT

    fun t(key: String, vararg args: Any?): String {
        val row = TABLE[key] ?: return key
        var s = row.getOrNull(index)
        if (s.isNullOrEmpty()) s = row.getOrNull(1) ?: key
        return if (args.isEmpty()) s else String.format(s, *args)
    }

    private fun e(vararg s: String): Array<out String> = s

    private val TABLE: Map<String, Array<out String>> = mapOf(
        // ── language menu ──
        "language" to e("انتخاب زبان", "Select language", "اختيار اللغة", "Dil seç", "Sprache wählen", "Choisir la langue", "Elegir idioma", "Выбрать язык", "言語を選択", "选择语言"),
        "language_sub" to e("فارسی، English، العربية و …", "English, فارسی, العربية and more", "العربية، English، فارسی وغيرها", "Türkçe, English, Deutsch ve daha fazlası", "Deutsch, English, Français und mehr", "Français, English, Deutsch et plus", "Español, English, Français y más", "Русский, English, 中文 и другие", "日本語、English、中文など", "中文、English、日本語等"),

        // ── status / main screen ──
        "disconnected" to e("قطع است", "Disconnected", "غير متصل", "Bağlı değil", "Getrennt", "Déconnecté", "Desconectado", "Отключено", "切断中", "未连接"),
        "connected" to e("متصل", "Connected", "متصل", "Bağlı", "Verbunden", "Connecté", "Conectado", "Подключено", "接続済み", "已连接"),
        "connecting" to e("در حال اتصال به سرور…", "Connecting to server…", "جارٍ الاتصال بالخادم…", "Sunucuya bağlanılıyor…", "Verbindung zum Server…", "Connexion au serveur…", "Conectando al servidor…", "Подключение к серверу…", "サーバーに接続中…", "正在连接服务器…"),
        "disconnecting" to e("در حال قطع اتصال…", "Disconnecting…", "جارٍ قطع الاتصال…", "Bağlantı kesiliyor…", "Trennen…", "Déconnexion…", "Desconectando…", "Отключение…", "切断しています…", "正在断开…"),
        "vpn_perm_denied" to e("اجازهٔ ساخت تونل VPN داده نشد.", "VPN tunnel permission was not granted.", "لم يتم منح إذن إنشاء نفق VPN.", "VPN tüneli izni verilmedi.", "Berechtigung für den VPN-Tunnel wurde nicht erteilt.", "L’autorisation de créer le tunnel VPN a été refusée.", "No se concedió el permiso para el túnel VPN.", "Разрешение на создание VPN-туннеля не выдано.", "VPNトンネルの許可が得られませんでした。", "未授予创建 VPN 隧道的权限。"),
        "tap_connect" to e("برای اتصال، دکمهٔ بالا را لمس کنید", "Tap the button above to connect", "اضغط الزر أعلاه للاتصال", "Bağlanmak için yukarıdaki düğmeye dokunun", "Zum Verbinden die Taste oben antippen", "Touchez le bouton ci-dessus pour vous connecter", "Toca el botón de arriba para conectar", "Нажмите кнопку выше, чтобы подключиться", "上のボタンをタップして接続", "点击上方按钮进行连接"),
        "tap_disconnect" to e("برای قطع اتصال، دکمهٔ بالا را لمس کنید", "Tap the button above to disconnect", "اضغط الزر أعلاه لقطع الاتصال", "Bağlantıyı kesmek için yukarıdaki düğmeye dokunun", "Zum Trennen die Taste oben antippen", "Touchez le bouton ci-dessus pour vous déconnecter", "Toca el botón de arriba para desconectar", "Нажмите кнопку выше, чтобы отключиться", "上のボタンをタップして切断", "点击上方按钮断开连接"),
        "menu" to e("منو", "Menu", "القائمة", "Menü", "Menü", "Menu", "Menú", "Меню", "メニュー", "菜单"),
        "close_menu" to e("بستن منو", "Close menu", "إغلاق القائمة", "Menüyü kapat", "Menü schließen", "Fermer le menu", "Cerrar menú", "Закрыть меню", "メニューを閉じる", "关闭菜单"),
        "to_light" to e("تغییر به حالت روشن", "Switch to light mode", "التبديل إلى الوضع الفاتح", "Açık moda geç", "Zum hellen Modus wechseln", "Passer au mode clair", "Cambiar a modo claro", "Светлая тема", "ライトモードに切り替え", "切换到浅色模式"),
        "to_dark" to e("تغییر به حالت تاریک", "Switch to dark mode", "التبديل إلى الوضع الداكن", "Koyu moda geç", "Zum dunklen Modus wechseln", "Passer au mode sombre", "Cambiar a modo oscuro", "Тёмная тема", "ダークモードに切り替え", "切换到深色模式"),
        "toggle_conn" to e("اتصال / قطع اتصال", "Connect / Disconnect", "اتصال / قطع الاتصال", "Bağlan / Bağlantıyı kes", "Verbinden / Trennen", "Connexion / Déconnexion", "Conectar / Desconectar", "Подключить / Отключить", "接続 / 切断", "连接 / 断开"),
        "gate_title" to e("سرورهای عمومی VPN Gate", "Public VPN Gate servers", "خوادم VPN Gate العامة", "Herkese açık VPN Gate sunucuları", "Öffentliche VPN-Gate-Server", "Serveurs publics VPN Gate", "Servidores públicos de VPN Gate", "Публичные серверы VPN Gate", "公開 VPN Gate サーバー", "VPN Gate 公共服务器"),
        "gate_sub" to e("انتخاب از فهرست همهٔ کشورها", "Choose from servers in every country", "اختر من قائمة جميع الدول", "Tüm ülkelerin listesinden seçin", "Aus der Liste aller Länder wählen", "Choisir dans la liste de tous les pays", "Elige de la lista de todos los países", "Выберите из списка всех стран", "全世界のリストから選択", "从所有国家的列表中选择"),
        "conn_info" to e("اطلاعات اتصال", "Connection details", "بيانات الاتصال", "Bağlantı bilgileri", "Verbindungsdaten", "Informations de connexion", "Datos de conexión", "Данные подключения", "接続情報", "连接信息"),
        "server" to e("سرور", "Server", "الخادم", "Sunucu", "Server", "Serveur", "Servidor", "Сервер", "サーバー", "服务器"),
        "username" to e("نام کاربری", "Username", "اسم المستخدم", "Kullanıcı adı", "Benutzername", "Nom d’utilisateur", "Usuario", "Имя пользователя", "ユーザー名", "用户名"),
        "password" to e("رمز عبور", "Password", "كلمة المرور", "Parola", "Passwort", "Mot de passe", "Contraseña", "Пароль", "パスワード", "密码"),
        "remember" to e("مرا به خاطر بسپار", "Remember me", "تذكّرني", "Beni hatırla", "Angemeldet bleiben", "Se souvenir de moi", "Recordarme", "Запомнить меня", "ログイン情報を保存", "记住我"),
        "enter_server" to e("آدرس سرور را وارد کنید", "Enter the server address", "أدخل عنوان الخادم", "Sunucu adresini girin", "Serveradresse eingeben", "Saisissez l’adresse du serveur", "Introduce la dirección del servidor", "Введите адрес сервера", "サーバーアドレスを入力してください", "请输入服务器地址"),
        "enter_server_first" to e("اول آدرس سرور را وارد کنید", "Enter the server address first", "أدخل عنوان الخادم أولاً", "Önce sunucu adresini girin", "Zuerst die Serveradresse eingeben", "Saisissez d’abord l’adresse du serveur", "Primero introduce la dirección del servidor", "Сначала введите адрес сервера", "先にサーバーアドレスを入力してください", "请先输入服务器地址"),
        "enter_server_save" to e("آدرس سرور را وارد کنید و دوباره «ذخیرهٔ کانفیگ» را بزنید.", "Enter a server address, then tap “Save config” again.", "أدخل عنوان الخادم ثم اضغط «حفظ الإعداد» مرة أخرى.", "Sunucu adresini girin ve tekrar “Yapılandırmayı kaydet”e dokunun.", "Serveradresse eingeben und erneut „Konfiguration speichern“ tippen.", "Saisissez l’adresse du serveur puis touchez de nouveau « Enregistrer la config ».", "Introduce una dirección de servidor y toca de nuevo «Guardar configuración».", "Введите адрес сервера и снова нажмите «Сохранить конфигурацию».", "サーバーアドレスを入力して、もう一度「設定を保存」をタップしてください。", "请输入服务器地址后再次点击“保存配置”。"),

        // ── save dialog ──
        "name_hint" to e("مثلاً سرور شخصی", "e.g. My server", "مثلاً خادمي", "ör. Kişisel sunucum", "z. B. Mein Server", "p. ex. Mon serveur", "p. ej. Mi servidor", "напр. Мой сервер", "例: マイサーバー", "例如：我的服务器"),
        "config_n" to e("کانفیگ %d", "Config %d", "إعداد %d", "Yapılandırma %d", "Konfiguration %d", "Config %d", "Configuración %d", "Конфигурация %d", "設定 %d", "配置 %d"),
        "save_cfg_title" to e("ذخیرهٔ کانفیگ", "Save config", "حفظ الإعداد", "Yapılandırmayı kaydet", "Konfiguration speichern", "Enregistrer la config", "Guardar configuración", "Сохранить конфигурацию", "設定を保存", "保存配置"),
        "save_cfg_msg" to e("اطلاعات فعلی سرور، نام کاربری و رمز عبور ذخیره می‌شود.", "The current server, username and password will be saved.", "سيتم حفظ الخادم الحالي واسم المستخدم وكلمة المرور.", "Geçerli sunucu, kullanıcı adı ve parola kaydedilecek.", "Aktueller Server, Benutzername und Passwort werden gespeichert.", "Le serveur, le nom d’utilisateur et le mot de passe actuels seront enregistrés.", "Se guardarán el servidor, el usuario y la contraseña actuales.", "Текущие сервер, имя пользователя и пароль будут сохранены.", "現在のサーバー、ユーザー名、パスワードを保存します。", "将保存当前的服务器、用户名和密码。"),
        "cancel" to e("انصراف", "Cancel", "إلغاء", "İptal", "Abbrechen", "Annuler", "Cancelar", "Отмена", "キャンセル", "取消"),
        "save" to e("ذخیره", "Save", "حفظ", "Kaydet", "Speichern", "Enregistrer", "Guardar", "Сохранить", "保存", "保存"),
        "need_name" to e("برای کانفیگ یک نام وارد کنید.", "Enter a name for the config.", "أدخل اسماً للإعداد.", "Yapılandırma için bir ad girin.", "Geben Sie einen Namen für die Konfiguration ein.", "Saisissez un nom pour la config.", "Introduce un nombre para la configuración.", "Введите название конфигурации.", "設定の名前を入力してください。", "请为配置输入名称。"),
        "cfg_saved" to e("کانفیگ «%s» ذخیره شد.", "Config “%s” saved.", "تم حفظ الإعداد «%s».", "“%s” yapılandırması kaydedildi.", "Konfiguration „%s“ gespeichert.", "Config « %s » enregistrée.", "Configuración «%s» guardada.", "Конфигурация «%s» сохранена.", "設定「%s」を保存しました。", "配置“%s”已保存。"),

        // ── drawer sections & items ──
        "sec_configs" to e("کانفیگ‌ها", "Configs", "الإعدادات", "Yapılandırmalar", "Konfigurationen", "Configurations", "Configuraciones", "Конфигурации", "設定", "配置"),
        "sec_system" to e("سیستم", "System", "النظام", "Sistem", "System", "Système", "Sistema", "Система", "システム", "系统"),
        "sec_app" to e("برنامه", "App", "التطبيق", "Uygulama", "App", "Application", "Aplicación", "Приложение", "アプリ", "应用"),
        "m_gate" to e("سرورهای VPN Gate", "VPN Gate servers", "خوادم VPN Gate", "VPN Gate sunucuları", "VPN-Gate-Server", "Serveurs VPN Gate", "Servidores VPN Gate", "Серверы VPN Gate", "VPN Gate サーバー", "VPN Gate 服务器"),
        "m_gate_sub" to e("فهرست عمومی همهٔ کشورها", "Public list for every country", "قائمة عامة لجميع الدول", "Tüm ülkeler için genel liste", "Öffentliche Liste aller Länder", "Liste publique de tous les pays", "Lista pública de todos los países", "Публичный список всех стран", "全ての国の公開リスト", "所有国家的公共列表"),
        "m_save" to e("ذخیرهٔ کانفیگ فعلی", "Save current config", "حفظ الإعداد الحالي", "Geçerli yapılandırmayı kaydet", "Aktuelle Konfiguration speichern", "Enregistrer la config actuelle", "Guardar configuración actual", "Сохранить текущую конфигурацию", "現在の設定を保存", "保存当前配置"),
        "m_save_sub" to e("سرور و حساب را ذخیره کن", "Store the server and account", "احفظ الخادم والحساب", "Sunucuyu ve hesabı sakla", "Server und Konto speichern", "Mémoriser le serveur et le compte", "Guarda el servidor y la cuenta", "Сохранить сервер и учётную запись", "サーバーとアカウントを保存", "保存服务器和账户"),
        "m_saved" to e("کانفیگ‌های ذخیره‌شده", "Saved configs", "الإعدادات المحفوظة", "Kayıtlı yapılandırmalar", "Gespeicherte Konfigurationen", "Configs enregistrées", "Configuraciones guardadas", "Сохранённые конфигурации", "保存済みの設定", "已保存的配置"),
        "m_saved_sub" to e("رفتن به فهرست کانفیگ‌ها", "Jump to the config list", "الانتقال إلى قائمة الإعدادات", "Yapılandırma listesine git", "Zur Konfigurationsliste springen", "Aller à la liste des configs", "Ir a la lista de configuraciones", "Перейти к списку конфигураций", "設定リストへ移動", "跳转到配置列表"),
        "m_toggle" to e("نمایش / پنهان‌کردن لیست سرورها", "Show / hide server list", "إظهار / إخفاء قائمة الخوادم", "Sunucu listesini göster / gizle", "Serverliste ein-/ausblenden", "Afficher / masquer la liste des serveurs", "Mostrar / ocultar lista de servidores", "Показать / скрыть список серверов", "サーバーリストの表示 / 非表示", "显示 / 隐藏服务器列表"),
        "m_toggle_sub" to e("بازوبسته کردن فهرست زیر فرم", "Collapse or expand the list under the form", "طيّ أو توسيع القائمة أسفل النموذج", "Formun altındaki listeyi daralt veya genişlet", "Liste unter dem Formular ein- oder ausklappen", "Réduire ou développer la liste sous le formulaire", "Contraer o expandir la lista bajo el formulario", "Свернуть или развернуть список под формой", "フォーム下のリストを開閉", "折叠或展开表单下方的列表"),
        "m_clip" to e("وارد کردن از کلیپ‌بورد", "Import from clipboard", "استيراد من الحافظة", "Panodan içe aktar", "Aus Zwischenablage importieren", "Importer depuis le presse-papiers", "Importar del portapapeles", "Импорт из буфера обмена", "クリップボードから取り込み", "从剪贴板导入"),
        "m_clip_sub" to e("چسباندن آدرس سرور", "Paste a server address", "لصق عنوان خادم", "Sunucu adresi yapıştır", "Serveradresse einfügen", "Coller une adresse de serveur", "Pegar una dirección de servidor", "Вставить адрес сервера", "サーバーアドレスを貼り付け", "粘贴服务器地址"),
        "m_share" to e("اشتراک‌گذاری سرور", "Share server", "مشاركة الخادم", "Sunucuyu paylaş", "Server teilen", "Partager le serveur", "Compartir servidor", "Поделиться сервером", "サーバーを共有", "分享服务器"),
        "m_share_sub" to e("سرور و نام کاربری، بدون رمز", "Server and username, no password", "الخادم واسم المستخدم دون كلمة المرور", "Sunucu ve kullanıcı adı, parolasız", "Server und Benutzername, ohne Passwort", "Serveur et nom d’utilisateur, sans mot de passe", "Servidor y usuario, sin contraseña", "Сервер и имя пользователя, без пароля", "サーバーとユーザー名(パスワードなし)", "服务器和用户名，不含密码"),
        "m_vpnset" to e("تنظیمات VPN اندروید", "Android VPN settings", "إعدادات VPN في أندرويد", "Android VPN ayarları", "Android-VPN-Einstellungen", "Paramètres VPN d’Android", "Ajustes de VPN de Android", "Настройки VPN Android", "Android の VPN 設定", "Android VPN 设置"),
        "m_vpnset_sub" to e("اتصال همیشه روشن و قفل شبکه", "Always-on VPN and network lock", "VPN دائم التشغيل وقفل الشبكة", "Her zaman açık VPN ve ağ kilidi", "Always-on-VPN und Netzwerksperre", "VPN permanent et blocage réseau", "VPN siempre activa y bloqueo de red", "Постоянный VPN и блокировка сети", "常時接続 VPN とネットワークロック", "始终开启的 VPN 和网络锁定"),
        "m_battery" to e("بهینه‌سازی باتری", "Battery optimization", "تحسين البطارية", "Pil optimizasyonu", "Akkuoptimierung", "Optimisation de la batterie", "Optimización de batería", "Оптимизация батареи", "バッテリーの最適化", "电池优化"),
        "m_battery_sub" to e("زنده ماندن تونل در پس‌زمینه", "Keep the tunnel alive in the background", "إبقاء النفق يعمل في الخلفية", "Tünelin arka planda canlı kalması", "Tunnel im Hintergrund am Leben halten", "Garder le tunnel actif en arrière-plan", "Mantener el túnel activo en segundo plano", "Сохранять туннель в фоне", "バックグラウンドでトンネルを維持", "让隧道在后台保持运行"),
        "m_help" to e("راهنما", "Help", "المساعدة", "Yardım", "Hilfe", "Aide", "Ayuda", "Справка", "ヘルプ", "帮助"),
        "m_help_sub" to e("آموزش اتصال", "How to connect", "كيفية الاتصال", "Nasıl bağlanılır", "So verbinden Sie sich", "Comment se connecter", "Cómo conectarse", "Как подключиться", "接続方法", "连接教程"),
        "m_about" to e("دربارهٔ برنامه", "About", "حول التطبيق", "Hakkında", "Über die App", "À propos", "Acerca de", "О приложении", "アプリについて", "关于"),
        "m_about_sub" to e("نسخه و مجوزها", "Version and licenses", "الإصدار والتراخيص", "Sürüm ve lisanslar", "Version und Lizenzen", "Version et licences", "Versión y licencias", "Версия и лицензии", "バージョンとライセンス", "版本与许可"),
        "m_clear" to e("پاک‌کردن اطلاعات ذخیره‌شده", "Clear saved data", "مسح البيانات المحفوظة", "Kayıtlı verileri temizle", "Gespeicherte Daten löschen", "Effacer les données enregistrées", "Borrar datos guardados", "Удалить сохранённые данные", "保存データを消去", "清除已保存数据"),
        "m_clear_sub" to e("حذف حساب‌ها و کانفیگ‌ها", "Delete accounts and configs", "حذف الحسابات والإعدادات", "Hesapları ve yapılandırmaları sil", "Konten und Konfigurationen löschen", "Supprimer comptes et configs", "Eliminar cuentas y configuraciones", "Удалить учётные записи и конфигурации", "アカウントと設定を削除", "删除账户和配置"),
        "m_exit" to e("بستن برنامه", "Close app", "إغلاق التطبيق", "Uygulamayı kapat", "App schließen", "Fermer l’application", "Cerrar la app", "Закрыть приложение", "アプリを閉じる", "关闭应用"),
        "m_exit_sub" to e("VPN همچنان فعال می‌ماند", "The VPN stays active", "يبقى الـ VPN نشطاً", "VPN etkin kalır", "Das VPN bleibt aktiv", "Le VPN reste actif", "La VPN sigue activa", "VPN остаётся активным", "VPN は有効のままです", "VPN 将保持连接"),

        // ── toasts, dialogs ──
        "no_saved" to e("هنوز کانفیگی ذخیره نشده است.", "No configs have been saved yet.", "لم يتم حفظ أي إعداد بعد.", "Henüz kayıtlı yapılandırma yok.", "Noch keine Konfiguration gespeichert.", "Aucune config enregistrée pour le moment.", "Aún no hay configuraciones guardadas.", "Сохранённых конфигураций пока нет.", "保存された設定はまだありません。", "尚未保存任何配置。"),
        "clip_empty" to e("کلیپ‌بورد خالی است.", "The clipboard is empty.", "الحافظة فارغة.", "Pano boş.", "Die Zwischenablage ist leer.", "Le presse-papiers est vide.", "El portapapeles está vacío.", "Буфер обмена пуст.", "クリップボードが空です。", "剪贴板为空。"),
        "clip_not_server" to e("متن کلیپ‌بورد شبیه آدرس سرور نیست.", "The clipboard text doesn’t look like a server address.", "نص الحافظة لا يشبه عنوان خادم.", "Pano metni sunucu adresine benzemiyor.", "Der Text sieht nicht wie eine Serveradresse aus.", "Le texte du presse-papiers ne ressemble pas à une adresse de serveur.", "El texto del portapapeles no parece una dirección de servidor.", "Текст в буфере не похож на адрес сервера.", "クリップボードの内容はサーバーアドレスではないようです。", "剪贴板内容看起来不像服务器地址。"),
        "clip_imported" to e("آدرس سرور از کلیپ‌بورد وارد شد.", "Server address imported from the clipboard.", "تم استيراد عنوان الخادم من الحافظة.", "Sunucu adresi panodan alındı.", "Serveradresse aus der Zwischenablage übernommen.", "Adresse du serveur importée depuis le presse-papiers.", "Dirección de servidor importada del portapapeles.", "Адрес сервера вставлен из буфера обмена.", "クリップボードからサーバーアドレスを取り込みました。", "已从剪贴板导入服务器地址。"),
        "share_title" to e("اشتراک‌گذاری سرور", "Share server", "مشاركة الخادم", "Sunucuyu paylaş", "Server teilen", "Partager le serveur", "Compartir servidor", "Поделиться сервером", "サーバーを共有", "分享服务器"),
        "no_share_app" to e("برنامه‌ای برای اشتراک‌گذاری پیدا نشد.", "No app found to share with.", "لم يتم العثور على تطبيق للمشاركة.", "Paylaşmak için uygulama bulunamadı.", "Keine App zum Teilen gefunden.", "Aucune application de partage trouvée.", "No se encontró ninguna app para compartir.", "Не найдено приложение для отправки.", "共有できるアプリが見つかりません。", "找不到可用于分享的应用。"),
        "page_unavailable" to e("این صفحه روی این گوشی در دسترس نیست.", "This screen isn’t available on this phone.", "هذه الشاشة غير متاحة على هذا الهاتف.", "Bu ekran bu telefonda kullanılamıyor.", "Dieser Bildschirm ist auf diesem Gerät nicht verfügbar.", "Cet écran n’est pas disponible sur ce téléphone.", "Esta pantalla no está disponible en este teléfono.", "Этот экран недоступен на этом телефоне.", "この画面はこの端末では利用できません。", "此手机上无法打开该页面。"),
        "help_title" to e("راهنما", "Help", "المساعدة", "Yardım", "Hilfe", "Aide", "Ayuda", "Справка", "ヘルプ", "帮助"),
        "help_body" to e(
            "۱. آدرس سرور (مثلاً public-vpn-100.opengw.net یا host:port) را وارد کنید.\n۲. برای سرورهای VPN Gate معمولاً نام کاربری vpn است و رمز عبور خالی می‌ماند.\n۳. دکمهٔ بزرگ بالا یا «Connect» را بزنید و اجازهٔ VPN را تأیید کنید.\n۴. برای استفادهٔ دوباره، از منو «ذخیرهٔ کانفیگ فعلی» را بزنید.\n۵. اگر اتصال در پس‌زمینه قطع می‌شود، «بهینه‌سازی باتری» را از منو برای برنامه غیرفعال کنید.",
            "1. Enter the server address (e.g. public-vpn-100.opengw.net or host:port).\n2. For VPN Gate servers the username is usually vpn and the password stays empty.\n3. Tap the big button at the top or “Connect” and approve the VPN permission.\n4. To reuse it later, choose “Save current config” from the menu.\n5. If the connection drops in the background, turn off “Battery optimization” for the app from the menu.",
            "١. أدخل عنوان الخادم (مثلاً public-vpn-100.opengw.net أو host:port).\n٢. في خوادم VPN Gate يكون اسم المستخدم عادةً vpn وتبقى كلمة المرور فارغة.\n٣. اضغط الزر الكبير في الأعلى أو «Connect» ووافق على إذن VPN.\n٤. لإعادة الاستخدام لاحقاً اختر «حفظ الإعداد الحالي» من القائمة.\n٥. إذا انقطع الاتصال في الخلفية فعطّل «تحسين البطارية» للتطبيق من القائمة.",
            "1. Sunucu adresini girin (ör. public-vpn-100.opengw.net veya host:port).\n2. VPN Gate sunucularında kullanıcı adı genellikle vpn'dir, parola boş kalır.\n3. Üstteki büyük düğmeye veya “Connect”e dokunun ve VPN iznini onaylayın.\n4. Tekrar kullanmak için menüden “Geçerli yapılandırmayı kaydet”i seçin.\n5. Bağlantı arka planda kopuyorsa menüden uygulama için “Pil optimizasyonu”nu kapatın.",
            "1. Serveradresse eingeben (z. B. public-vpn-100.opengw.net oder host:port).\n2. Bei VPN-Gate-Servern ist der Benutzername meist vpn, das Passwort bleibt leer.\n3. Große Taste oben oder „Connect“ antippen und die VPN-Berechtigung bestätigen.\n4. Zur Wiederverwendung im Menü „Aktuelle Konfiguration speichern“ wählen.\n5. Bricht die Verbindung im Hintergrund ab, die „Akkuoptimierung“ für die App im Menü deaktivieren.",
            "1. Saisissez l’adresse du serveur (p. ex. public-vpn-100.opengw.net ou host:port).\n2. Pour les serveurs VPN Gate, le nom d’utilisateur est généralement vpn et le mot de passe reste vide.\n3. Touchez le grand bouton en haut ou « Connect » et acceptez l’autorisation VPN.\n4. Pour la réutiliser, choisissez « Enregistrer la config actuelle » dans le menu.\n5. Si la connexion tombe en arrière-plan, désactivez « Optimisation de la batterie » pour l’application depuis le menu.",
            "1. Introduce la dirección del servidor (p. ej. public-vpn-100.opengw.net o host:port).\n2. En los servidores VPN Gate el usuario suele ser vpn y la contraseña queda vacía.\n3. Toca el botón grande de arriba o «Connect» y acepta el permiso de VPN.\n4. Para reutilizarla, elige «Guardar configuración actual» en el menú.\n5. Si la conexión se corta en segundo plano, desactiva la «Optimización de batería» para la app desde el menú.",
            "1. Введите адрес сервера (например, public-vpn-100.opengw.net или host:port).\n2. Для серверов VPN Gate имя пользователя обычно vpn, пароль пустой.\n3. Нажмите большую кнопку сверху или «Connect» и подтвердите разрешение VPN.\n4. Чтобы использовать позже, выберите в меню «Сохранить текущую конфигурацию».\n5. Если соединение обрывается в фоне, отключите для приложения «Оптимизацию батареи» через меню.",
            "1. サーバーアドレス(例: public-vpn-100.opengw.net または host:port)を入力します。\n2. VPN Gate のサーバーでは通常ユーザー名は vpn で、パスワードは空のままです。\n3. 上の大きなボタンまたは「Connect」をタップし、VPN の許可を承認します。\n4. 次回以降のために、メニューの「現在の設定を保存」をタップします。\n5. バックグラウンドで切断される場合は、メニューから「バッテリーの最適化」をこのアプリで無効にしてください。",
            "1. 输入服务器地址(例如 public-vpn-100.opengw.net 或 host:port)。\n2. VPN Gate 服务器的用户名通常是 vpn，密码留空。\n3. 点击顶部大按钮或“Connect”，并允许 VPN 权限。\n4. 如需再次使用，请在菜单中选择“保存当前配置”。\n5. 如果连接在后台中断，请在菜单中为本应用关闭“电池优化”。"
        ),
        "ok" to e("باشه", "OK", "حسناً", "Tamam", "OK", "OK", "Aceptar", "ОК", "OK", "好的"),
        "about_extra" to e(
            "اتصال با موتور بومی پروتکل SoftEther و سرویس VpnService اندروید انجام می‌شود. اطلاعات ورود فقط به‌صورت رمزگذاری‌شده روی همین دستگاه نگه‌داری می‌شود.\n\nمجوز اجزای متن‌باز در فایل THIRD_PARTY.md پروژه آمده است.",
            "The connection uses the built-in native SoftEther protocol engine and Android’s VpnService. Login details are kept only on this device, encrypted.\n\nOpen-source component licenses are listed in the project’s THIRD_PARTY.md.",
            "يتم الاتصال عبر محرك بروتوكول SoftEther المدمج وخدمة VpnService في أندرويد. تُحفظ بيانات الدخول مشفّرة على هذا الجهاز فقط.\n\nتراخيص المكونات مفتوحة المصدر مذكورة في ملف THIRD_PARTY.md.",
            "Bağlantı, yerleşik SoftEther protokol motoru ve Android VpnService ile kurulur. Giriş bilgileri yalnızca bu cihazda şifrelenmiş olarak saklanır.\n\nAçık kaynak bileşen lisansları projedeki THIRD_PARTY.md dosyasındadır.",
            "Die Verbindung nutzt die integrierte SoftEther-Protokoll-Engine und Androids VpnService. Anmeldedaten werden nur verschlüsselt auf diesem Gerät gespeichert.\n\nLizenzen der Open-Source-Komponenten stehen in THIRD_PARTY.md.",
            "La connexion utilise le moteur de protocole SoftEther intégré et le VpnService d’Android. Les identifiants sont stockés chiffrés sur cet appareil uniquement.\n\nLes licences des composants open source figurent dans THIRD_PARTY.md.",
            "La conexión usa el motor de protocolo SoftEther integrado y el VpnService de Android. Las credenciales se guardan cifradas solo en este dispositivo.\n\nLas licencias de los componentes de código abierto están en THIRD_PARTY.md.",
            "Соединение использует встроенный движок протокола SoftEther и Android VpnService. Данные для входа хранятся только на этом устройстве в зашифрованном виде.\n\nЛицензии открытых компонентов указаны в THIRD_PARTY.md.",
            "接続は内蔵の SoftEther プロトコルエンジンと Android の VpnService で行われます。ログイン情報は暗号化してこの端末内にのみ保存されます。\n\nオープンソース部品のライセンスは THIRD_PARTY.md に記載されています。",
            "连接使用内置的 SoftEther 协议引擎和 Android VpnService。登录信息仅加密保存在本设备上。\n\n开源组件的许可见项目中的 THIRD_PARTY.md。"
        ),
        "version" to e("نسخه", "Version", "الإصدار", "Sürüm", "Version", "Version", "Versión", "Версия", "バージョン", "版本"),
        "clear_title" to e("پاک‌کردن اطلاعات", "Clear data", "مسح البيانات", "Verileri temizle", "Daten löschen", "Effacer les données", "Borrar datos", "Удаление данных", "データを消去", "清除数据"),
        "clear_msg" to e("همهٔ کانفیگ‌ها و اطلاعات ورود ذخیره‌شده حذف شوند؟ این کار برگشت‌پذیر نیست.", "Delete all saved configs and login details? This can’t be undone.", "هل تريد حذف جميع الإعدادات وبيانات الدخول المحفوظة؟ لا يمكن التراجع عن ذلك.", "Tüm kayıtlı yapılandırmalar ve giriş bilgileri silinsin mi? Bu işlem geri alınamaz.", "Alle gespeicherten Konfigurationen und Anmeldedaten löschen? Das kann nicht rückgängig gemacht werden.", "Supprimer toutes les configs et identifiants enregistrés ? Cette action est irréversible.", "¿Eliminar todas las configuraciones y credenciales guardadas? No se puede deshacer.", "Удалить все сохранённые конфигурации и данные входа? Это действие необратимо.", "保存済みの設定とログイン情報をすべて削除しますか? 元に戻せません。", "要删除所有已保存的配置和登录信息吗？此操作无法撤销。"),
        "clear_btn" to e("پاک کن", "Clear", "مسح", "Temizle", "Löschen", "Effacer", "Borrar", "Удалить", "消去", "清除"),
        "cleared" to e("اطلاعات ذخیره‌شده پاک شد.", "Saved data cleared.", "تم مسح البيانات المحفوظة.", "Kayıtlı veriler temizlendi.", "Gespeicherte Daten gelöscht.", "Données enregistrées effacées.", "Datos guardados borrados.", "Сохранённые данные удалены.", "保存データを消去しました。", "已清除保存的数据。"),

        // ── config list ──
        "saved_configs" to e("کانفیگ‌های ذخیره‌شده", "Saved configs", "الإعدادات المحفوظة", "Kayıtlı yapılandırmalar", "Gespeicherte Konfigurationen", "Configs enregistrées", "Configuraciones guardadas", "Сохранённые конфигурации", "保存済みの設定", "已保存的配置"),
        "show_list" to e("نمایش لیست سرورها", "Show server list", "إظهار قائمة الخوادم", "Sunucu listesini göster", "Serverliste anzeigen", "Afficher la liste des serveurs", "Mostrar lista de servidores", "Показать список серверов", "サーバーリストを表示", "显示服务器列表"),
        "hide_list" to e("پنهان‌کردن لیست سرورها", "Hide server list", "إخفاء قائمة الخوادم", "Sunucu listesini gizle", "Serverliste ausblenden", "Masquer la liste des serveurs", "Ocultar lista de servidores", "Скрыть список серверов", "サーバーリストを隠す", "隐藏服务器列表"),
        "ping_again" to e("↻ سنجش دوبارهٔ پینگ (TCP واقعی)", "↻ Re-measure ping (real TCP)", "↻ إعادة قياس البينغ (TCP حقيقي)", "↻ Ping'i yeniden ölç (gerçek TCP)", "↻ Ping neu messen (echtes TCP)", "↻ Remesurer le ping (TCP réel)", "↻ Volver a medir el ping (TCP real)", "↻ Измерить пинг заново (реальный TCP)", "↻ Ping を再測定(実際の TCP)", "↻ 重新测量延迟(真实 TCP)"),
        "cfg_selected" to e("کانفیگ «%s» انتخاب شد.", "Config “%s” selected.", "تم اختيار الإعداد «%s».", "“%s” yapılandırması seçildi.", "Konfiguration „%s“ ausgewählt.", "Config « %s » sélectionnée.", "Configuración «%s» seleccionada.", "Выбрана конфигурация «%s».", "設定「%s」を選択しました。", "已选择配置“%s”。"),
        "delete_desc" to e("حذف %s", "Delete %s", "حذف %s", "%s öğesini sil", "%s löschen", "Supprimer %s", "Eliminar %s", "Удалить %s", "%s を削除", "删除 %s"),
        "delete_ask" to e("کانفیگ «%s» حذف شود؟", "Delete config “%s”?", "هل تريد حذف الإعداد «%s»؟", "“%s” yapılandırması silinsin mi?", "Konfiguration „%s“ löschen?", "Supprimer la config « %s » ?", "¿Eliminar la configuración «%s»?", "Удалить конфигурацию «%s»?", "設定「%s」を削除しますか?", "要删除配置“%s”吗？"),
        "delete" to e("حذف", "Delete", "حذف", "Sil", "Löschen", "Supprimer", "Eliminar", "Удалить", "削除", "删除"),
        "ping_desc" to e("کمینه %d میلی‌ثانیه، %d از %d بسته پاسخ داد", "Min %d ms, %d of %d packets answered", "الحد الأدنى %d مللي ثانية، رد %d من %d حزمة", "En düşük %d ms, %d/%d paket yanıtlandı", "Min. %d ms, %d von %d Paketen beantwortet", "Min %d ms, %d paquets sur %d reçus", "Mín. %d ms, %d de %d paquetes respondidos", "Мин. %d мс, получен ответ на %d из %d пакетов", "最小 %d ms、%d/%d パケットが応答", "最小 %d 毫秒，%d/%d 个数据包有响应"),
        "gate_picked" to e("سرور %s انتخاب شد. برای اتصال دکمه را بزنید.", "Server %s selected. Tap the button to connect.", "تم اختيار الخادم %s. اضغط الزر للاتصال.", "%s sunucusu seçildi. Bağlanmak için düğmeye dokunun.", "Server %s ausgewählt. Zum Verbinden Taste antippen.", "Serveur %s sélectionné. Touchez le bouton pour vous connecter.", "Servidor %s seleccionado. Toca el botón para conectar.", "Выбран сервер %s. Нажмите кнопку для подключения.", "サーバー %s を選択しました。ボタンをタップして接続します。", "已选择服务器 %s。点击按钮即可连接。"),
        "already_saved" to e("این سرور قبلاً ذخیره شده است.", "This server is already saved.", "هذا الخادم محفوظ بالفعل.", "Bu sunucu zaten kayıtlı.", "Dieser Server ist bereits gespeichert.", "Ce serveur est déjà enregistré.", "Este servidor ya está guardado.", "Этот сервер уже сохранён.", "このサーバーは既に保存されています。", "该服务器已保存。"),
        "name_saved" to e("«%s» ذخیره شد.", "“%s” saved.", "تم حفظ «%s».", "“%s” kaydedildi.", "„%s“ gespeichert.", "« %s » enregistré.", "«%s» guardado.", "«%s» сохранён.", "「%s」を保存しました。", "“%s”已保存。"),
        "error" to e("خطا", "Error", "خطأ", "Hata", "Fehler", "Erreur", "Error", "Ошибка", "エラー", "错误"),
        "show_password" to e("نمایش رمز عبور", "Show password", "إظهار كلمة المرور", "Parolayı göster", "Passwort anzeigen", "Afficher le mot de passe", "Mostrar contraseña", "Показать пароль", "パスワードを表示", "显示密码"),

        // ── VPN Gate screen ──
        "g_cached" to e("ذخیره‌شده · %s", "Cached · %s", "محفوظ · %s", "Önbellekte · %s", "Zwischengespeichert · %s", "En cache · %s", "En caché · %s", "Из кэша · %s", "キャッシュ · %s", "已缓存 · %s"),
        "g_loading" to e("در حال دریافت فهرست سرورها…", "Fetching the server list…", "جارٍ تنزيل قائمة الخوادم…", "Sunucu listesi alınıyor…", "Serverliste wird geladen…", "Récupération de la liste des serveurs…", "Descargando la lista de servidores…", "Загрузка списка серверов…", "サーバーリストを取得中…", "正在获取服务器列表…"),
        "g_updated" to e("%d سرور · همین حالا به‌روز شد", "%d servers · updated just now", "%d خادم · تم التحديث الآن", "%d sunucu · az önce güncellendi", "%d Server · gerade aktualisiert", "%d serveurs · mis à jour à l’instant", "%d servidores · actualizado ahora", "Серверов: %d · обновлено только что", "%d 台のサーバー · たった今更新", "%d 台服务器 · 刚刚更新"),
        "g_fail" to e(
            "دریافت فهرست ناموفق بود (%s). اگر vpngate.net در شبکهٔ شما بسته است، اول به یکی از سرورهای ذخیره‌شده وصل شوید و دوباره «↻» را بزنید، یا متن CSV را از سایت کپی و با «چسباندن CSV» وارد کنید.",
            "Couldn’t fetch the list (%s). If vpngate.net is blocked on your network, first connect to one of your saved servers and tap “↻” again, or copy the CSV text from the site and use “Paste CSV”.",
            "تعذّر تنزيل القائمة (%s). إذا كان vpngate.net محجوباً في شبكتك فاتصل أولاً بأحد الخوادم المحفوظة ثم اضغط «↻» مجدداً، أو انسخ نص CSV من الموقع والصقه عبر «لصق CSV».",
            "Liste alınamadı (%s). vpngate.net ağınızda engelliyse önce kayıtlı sunuculardan birine bağlanıp tekrar “↻”ye dokunun ya da siteden CSV metnini kopyalayıp “CSV yapıştır” ile ekleyin.",
            "Liste konnte nicht geladen werden (%s). Ist vpngate.net in Ihrem Netzwerk blockiert, verbinden Sie sich zuerst mit einem gespeicherten Server und tippen erneut auf „↻“, oder kopieren Sie den CSV-Text von der Seite und nutzen „CSV einfügen“.",
            "Échec de la récupération de la liste (%s). Si vpngate.net est bloqué sur votre réseau, connectez-vous d’abord à un serveur enregistré puis touchez de nouveau « ↻ », ou copiez le texte CSV du site et utilisez « Coller le CSV ».",
            "No se pudo obtener la lista (%s). Si vpngate.net está bloqueado en tu red, conéctate primero a uno de tus servidores guardados y toca «↻» de nuevo, o copia el texto CSV del sitio y usa «Pegar CSV».",
            "Не удалось загрузить список (%s). Если vpngate.net заблокирован в вашей сети, сначала подключитесь к одному из сохранённых серверов и снова нажмите «↻» либо скопируйте CSV с сайта и вставьте через «Вставить CSV».",
            "リストを取得できませんでした(%s)。ネットワークで vpngate.net がブロックされている場合は、まず保存済みサーバーのいずれかに接続して「↻」をもう一度タップするか、サイトの CSV テキストをコピーして「CSV を貼り付け」を使ってください。",
            "获取列表失败(%s)。如果你的网络屏蔽了 vpngate.net，请先连接一个已保存的服务器再点击“↻”，或从网站复制 CSV 文本后使用“粘贴 CSV”。"
        ),
        "g_no_csv" to e("در کلیپ‌بورد فهرست VPN Gate پیدا نشد.", "No VPN Gate list found in the clipboard.", "لم يتم العثور على قائمة VPN Gate في الحافظة.", "Panoda VPN Gate listesi bulunamadı.", "Keine VPN-Gate-Liste in der Zwischenablage gefunden.", "Aucune liste VPN Gate dans le presse-papiers.", "No se encontró una lista de VPN Gate en el portapapeles.", "В буфере обмена нет списка VPN Gate.", "クリップボードに VPN Gate のリストが見つかりません。", "剪贴板中未找到 VPN Gate 列表。"),
        "g_from_clip" to e("%d سرور · از کلیپ‌بورد", "%d servers · from clipboard", "%d خادم · من الحافظة", "%d sunucu · panodan", "%d Server · aus Zwischenablage", "%d serveurs · depuis le presse-papiers", "%d servidores · del portapapeles", "Серверов: %d · из буфера обмена", "%d 台のサーバー · クリップボードから", "%d 台服务器 · 来自剪贴板"),
        "g_src_title" to e("آدرس منبع فهرست", "List source address", "عنوان مصدر القائمة", "Liste kaynak adresi", "Quelladresse der Liste", "Adresse source de la liste", "Dirección de origen de la lista", "Адрес источника списка", "リストのソースアドレス", "列表来源地址"),
        "g_src_msg" to e("خروجی باید همان قالب CSV سایت VPN Gate باشد و آدرس با https:// شروع شود.", "The output must be in the VPN Gate CSV format and the address must start with https://.", "يجب أن يكون الإخراج بصيغة CSV الخاصة بـ VPN Gate وأن يبدأ العنوان بـ https://.", "Çıktı VPN Gate CSV biçiminde olmalı ve adres https:// ile başlamalıdır.", "Die Ausgabe muss im VPN-Gate-CSV-Format sein und die Adresse mit https:// beginnen.", "La sortie doit être au format CSV de VPN Gate et l’adresse doit commencer par https://.", "La salida debe tener el formato CSV de VPN Gate y la dirección debe empezar por https://.", "Вывод должен быть в формате CSV VPN Gate, а адрес должен начинаться с https://.", "出力は VPN Gate の CSV 形式で、アドレスは https:// で始まる必要があります。", "输出必须是 VPN Gate 的 CSV 格式，且地址须以 https:// 开头。"),
        "g_default" to e("پیش‌فرض", "Default", "الافتراضي", "Varsayılan", "Standard", "Par défaut", "Predeterminado", "По умолчанию", "既定値", "默认"),
        "g_paste_csv" to e("چسباندن CSV از کلیپ‌بورد", "Paste CSV from clipboard", "لصق CSV من الحافظة", "Panodan CSV yapıştır", "CSV aus Zwischenablage einfügen", "Coller le CSV du presse-papiers", "Pegar CSV del portapapeles", "Вставить CSV из буфера", "クリップボードから CSV を貼り付け", "从剪贴板粘贴 CSV"),
        "g_set_src" to e("تنظیم آدرس منبع", "Set source address", "تعيين عنوان المصدر", "Kaynak adresini ayarla", "Quelladresse festlegen", "Définir l’adresse source", "Definir dirección de origen", "Задать адрес источника", "ソースアドレスを設定", "设置来源地址"),
        "g_refresh" to e("به‌روزرسانی فهرست", "Refresh list", "تحديث القائمة", "Listeyi yenile", "Liste aktualisieren", "Actualiser la liste", "Actualizar lista", "Обновить список", "リストを更新", "刷新列表"),
        "close" to e("بستن", "Close", "إغلاق", "Kapat", "Schließen", "Fermer", "Cerrar", "Закрыть", "閉じる", "关闭"),
        "g_search" to e("جستجوی کشور یا نام سرور…", "Search country or server name…", "ابحث عن دولة أو اسم خادم…", "Ülke veya sunucu adı ara…", "Land oder Servername suchen…", "Rechercher un pays ou un serveur…", "Buscar país o nombre de servidor…", "Поиск страны или сервера…", "国またはサーバー名を検索…", "搜索国家或服务器名称…"),
        "g_sort_score" to e("بهترین امتیاز", "Best score", "أفضل تقييم", "En iyi puan", "Beste Bewertung", "Meilleur score", "Mejor puntuación", "Лучший рейтинг", "スコア順", "评分最高"),
        "g_sort_ping" to e("کمترین پینگ", "Lowest ping", "أقل بينغ", "En düşük ping", "Niedrigster Ping", "Ping le plus bas", "Menor ping", "Минимальный пинг", "Ping が低い順", "延迟最低"),
        "g_sort_speed" to e("سریع‌ترین", "Fastest", "الأسرع", "En hızlı", "Am schnellsten", "Le plus rapide", "Más rápido", "Самые быстрые", "高速順", "速度最快"),
        "g_none" to e("موردی پیدا نشد.", "Nothing found.", "لا توجد نتائج.", "Sonuç bulunamadı.", "Nichts gefunden.", "Aucun résultat.", "No se encontró nada.", "Ничего не найдено.", "見つかりませんでした。", "未找到结果。"),
        "g_more" to e("برای دیدن بقیه، جستجو کنید (%d مورد دیگر)", "Search to see the rest (%d more)", "ابحث لرؤية الباقي (%d أخرى)", "Gerisini görmek için arayın (%d tane daha)", "Suchen, um den Rest zu sehen (%d weitere)", "Recherchez pour voir le reste (%d de plus)", "Busca para ver el resto (%d más)", "Используйте поиск, чтобы увидеть остальные (ещё %d)", "残りは検索して表示(あと %d 件)", "搜索以查看其余结果(还有 %d 项)"),
        "g_save_desc" to e("ذخیرهٔ کانفیگ", "Save config", "حفظ الإعداد", "Yapılandırmayı kaydet", "Konfiguration speichern", "Enregistrer la config", "Guardar configuración", "Сохранить конфигурацию", "設定を保存", "保存配置")
    )
}
