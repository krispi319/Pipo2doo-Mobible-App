package ai.mwm.client.ui.screens

import androidx.compose.ui.Modifier

import android.content.res.Configuration
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/** Local, offline translations. Stable English keys never alter stored user content. */
data class AppLanguage(val code: String, val name: String)
val appLanguages = listOf(AppLanguage("en", "English"), AppLanguage("es", "Español"), AppLanguage("fr", "Français"), AppLanguage("de", "Deutsch"), AppLanguage("pt", "Português"), AppLanguage("ru", "Русский"), AppLanguage("zh", "中文"), AppLanguage("ja", "日本語"), AppLanguage("hi", "हिन्दी"), AppLanguage("ar", "العربية"))
val LocalAppLanguage = staticCompositionLocalOf { "en" }
val LocalUserContent = staticCompositionLocalOf { emptySet<String>() }
@Composable fun LocalizedApp(code: String, content: @Composable () -> Unit) {
    val language = code.takeIf { value -> appLanguages.any { it.code == value } } ?: "en"
    val base = LocalContext.current
    val config = LocalConfiguration.current
    val localizedConfig = remember(config, language) { Configuration(config).apply { setLocale(Locale.forLanguageTag(language)); setLayoutDirection(Locale.forLanguageTag(language)) } }
    val context = remember(base, localizedConfig) {
        val configured = base.createConfigurationContext(localizedConfig)
        // Keep the original Activity in the wrapper chain for activity-result, back and lifecycle owners.
        object : android.content.ContextWrapper(base) {
            override fun getResources(): android.content.res.Resources = configured.resources
        }
    }
    SideEffect { Locale.setDefault(Locale.forLanguageTag(language)) }
    CompositionLocalProvider(LocalAppLanguage provides language, LocalContext provides context, LocalConfiguration provides localizedConfig, LocalLayoutDirection provides if(language == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, content = content)
}

private val catalog = listOf("""
Today|Hoy|Aujourd'hui|Heute|Hoje|Сегодня|今天|今日|आज|اليوم
Upcoming|Próximas|À venir|Demnächst|Próximas|Предстоящие|即将到来|今後の予定|आगामी|القادمة
Lists|Listas|Listes|Listen|Listas|Списки|清单|リスト|सूचियाँ|القوائم
Progress|Progreso|Progrès|Fortschritt|Progresso|Прогресс|进度|進捗|प्रगति|التقدم
Us|Nosotros|Nous|Wir|Nós|Мы|我们|私たち|हम|نحن
Us & settings|Nosotros y ajustes|Nous et paramètres|Wir & Einstellungen|Nós e configurações|Мы и настройки|我们与设置|私たちと設定|हम और सेटिंग|نحن والإعدادات
Appearance|Apariencia|Apparence|Darstellung|Aparência|Оформление|外观|外観|दिखावट|المظهر
App language|Idioma de la app|Langue de l'app|App-Sprache|Idioma do app|Язык приложения|应用语言|アプリの言語|ऐप की भाषा|لغة التطبيق
Choose language|Elegir idioma|Choisir la langue|Sprache wählen|Escolher idioma|Выбрать язык|选择语言|言語を選択|भाषा चुनें|اختر اللغة
Theme|Tema|Thème|Design|Tema|Тема|主题|テーマ|थीम|السمة
Light mode|Modo claro|Mode clair|Heller Modus|Modo claro|Светлая тема|浅色模式|ライトモード|लाइट मोड|الوضع الفاتح
Night mode|Modo oscuro|Mode sombre|Dunkler Modus|Modo escuro|Тёмная тема|深色模式|ダークモード|डार्क मोड|الوضع الداكن
Use system|Usar sistema|Suivre le système|System verwenden|Usar sistema|Как в системе|跟随系统|システム設定|सिस्टम का उपयोग|اتباع النظام
Empty avatar|Avatar vacío|Avatar vide|Leerer Avatar|Avatar vazio|Пустой аватар|空头像|未設定のアバター|खाली अवतार|صورة رمزية فارغة
Reset avatar|Restablecer avatar|Réinitialiser l'avatar|Avatar zurücksetzen|Redefinir avatar|Сбросить аватар|重置头像|アバターをリセット|अवतार रीसेट करें|إعادة ضبط الصورة
Local mode|Modo local|Mode local|Lokaler Modus|Modo local|Локальный режим|本地模式|ローカルモード|स्थानीय मोड|الوضع المحلي
Not paired|Sin vincular|Non associé|Nicht verbunden|Não pareado|Не подключено|未配对|未接続|जुड़ा नहीं|غير مقترن
My profile|Mi perfil|Mon profil|Mein Profil|Meu perfil|Мой профиль|我的资料|自分のプロフィール|मेरी प्रोफ़ाइल|ملفي الشخصي
Partner profile|Perfil de pareja|Profil du partenaire|Partnerprofil|Perfil do parceiro|Профиль партнёра|伴侣资料|パートナーのプロフィール|साथी की प्रोफ़ाइल|ملف الشريك
Copy code|Copiar código|Copier le code|Code kopieren|Copiar código|Копировать код|复制代码|コードをコピー|कोड कॉपी करें|نسخ الرمز
Share|Compartir|Partager|Teilen|Compartilhar|Поделиться|分享|共有|साझा करें|مشاركة
Code copied|Código copiado|Code copié|Code kopiert|Código copiado|Код скопирован|代码已复制|コピーしました|कोड कॉपी हो गया|تم نسخ الرمز
Reminders|Recordatorios|Rappels|Erinnerungen|Lembretes|Напоминания|提醒|リマインダー|रिमाइंडर|التذكيرات
On this phone|En este teléfono|Sur ce téléphone|Auf diesem Handy|Neste telefone|На этом телефоне|在此手机上|この端末で|इस फ़ोन पर|على هذا الهاتف
Quiet hours|Horas de silencio|Heures calmes|Ruhezeiten|Horário silencioso|Тихие часы|免打扰时段|おやすみ時間|शांत समय|ساعات الهدوء
New reminders wait until you wake|Los avisos esperan a que despiertes|Les rappels attendent votre réveil|Neue Hinweise warten bis zum Aufwachen|Avisos esperam você acordar|Новые напоминания ждут пробуждения|新提醒会等到您醒来|起床まで通知を待機します|नए रिमाइंडर आपके जागने तक रुकेंगे|تنتظر التذكيرات الجديدة حتى تستيقظ
From|Desde|De|Von|De|С|从|開始|से|من
Until|Hasta|À|Bis|Até|До|到|終了|तक|حتى
Reminder style|Estilo de aviso|Style de rappel|Erinnerungsstil|Estilo de lembrete|Стиль напоминания|提醒样式|通知スタイル|रिमाइंडर शैली|نمط التذكير
For your own due tasks|Para tus tareas pendientes|Pour vos tâches à faire|Für deine fälligen Aufgaben|Para suas tarefas pendentes|Для ваших задач|用于您自己的待办事项|自分の期限付きタスク用|आपके अपने कार्यों के लिए|لمهامك المستحقة
Soft purr|Ronroneo suave|Doux ronronnement|Sanftes Schnurren|Ronronar suave|Мягкое мурлыканье|轻柔呼噜|やさしいゴロゴロ|हल्की गुनगुनाहट|خرخرة لطيفة
Sticky note|Nota adhesiva|Note adhésive|Haftnotiz|Nota adesiva|Стикер|便利贴|付箋|चिपकने वाला नोट|ملاحظة لاصقة
Paw nudge|Toque de patita|Petit coup de patte|Pfotenstups|Toque de patinha|Толчок лапкой|爪爪轻推|肉球タッチ|पंजे का इशारा|لمسة مخلب
Partner pokes|Avisos de pareja|Petits rappels du partenaire|Partner-Stupser|Toques do parceiro|Напоминания партнёра|伴侣轻推|パートナーへの合図|साथी के इशारे|تنبيهات الشريك
Preference for future paired use|Preferencia para futura vinculación|Préférence pour une future association|Einstellung für spätere Verbindung|Preferência para uso pareado futuro|Для будущего подключения|用于今后配对|今後の接続用設定|भविष्य में जुड़ने के लिए विकल्प|تفضيل للاستخدام المقترن لاحقًا
Pokes are not available in local mode. Your preference is saved on this phone.|Los avisos no están disponibles en modo local. Tu preferencia se guarda en este teléfono.|Les petits rappels ne sont pas disponibles en mode local. Votre préférence est enregistrée ici.|Stupser sind im lokalen Modus nicht verfügbar. Deine Einstellung wird hier gespeichert.|Toques não estão disponíveis no modo local. Sua preferência fica neste telefone.|В локальном режиме напоминания партнёру недоступны. Настройка сохранена на телефоне.|本地模式不支持轻推。您的偏好已保存在此手机上。|ローカルモードでは合図を送れません。設定はこの端末に保存されます。|स्थानीय मोड में इशारे उपलब्ध नहीं हैं। विकल्प इस फ़ोन पर सहेजा गया है।|تنبيهات الشريك غير متاحة في الوضع المحلي. يُحفظ تفضيلك على هذا الهاتف.
Celebrations|Celebraciones|Célébrations|Feiern|Celebrações|Празднование|庆祝|お祝い|जश्न|الاحتفالات
Sound|Sonido|Son|Ton|Som|Звук|声音|サウンド|ध्वनि|الصوت
Haptics|Vibración|Vibrations|Vibration|Vibração|Вибрация|触感反馈|触覚フィードバック|कंपन|الاهتزاز
Paw-stamp burst|Estallido de patitas|Explosion de pattes|Pfoten-Konfetti|Explosão de patinhas|Конфетти из лапок|爪印庆祝|肉球のお祝い|पंजे का जश्न|احتفال آثار الأقدام
Plays when a task is done|Al completar una tarea|À la fin d'une tâche|Beim Erledigen einer Aufgabe|Ao concluir uma tarefa|При выполнении задачи|完成任务时播放|タスク完了時に再生|कार्य पूरा होने पर|عند إنجاز مهمة
Preview|Vista previa|Aperçu|Vorschau|Prévia|Предпросмотр|预览|プレビュー|पूर्वावलोकन|معاينة
Display name|Nombre visible|Nom affiché|Anzeigename|Nome de exibição|Отображаемое имя|显示名称|表示名|दिखने वाला नाम|اسم العرض
Save name|Guardar nombre|Enregistrer le nom|Name speichern|Salvar nome|Сохранить имя|保存名称|名前を保存|नाम सहेजें|حفظ الاسم
Your name changed to {0}|Tu nombre cambió a {0}|Votre nom est maintenant {0}|Dein Name wurde zu {0} geändert|Seu nome mudou para {0}|Ваше имя изменено на {0}|您的名称已更改为{0}|名前を{0}に変更しました|आपका नाम {0} हो गया|تم تغيير اسمك إلى {0}
Avatar|Avatar|Avatar|Avatar|Avatar|Аватар|头像|アバター|अवतार|الصورة الرمزية
Upload|Subir|Importer|Hochladen|Enviar|Загрузить|上传|アップロード|अपलोड|رفع
Tabby|Atigrado|Tigré|Tigerkatze|Rajado|Полосатый|虎斑|トラ猫|धारीदार|قط مخطط
Kitten|Gatito|Chaton|Kätzchen|Gatinho|Котёнок|小猫|子猫|बिल्ली का बच्चा|هريرة
Wink|Guiño|Clin d'œil|Zwinkern|Piscadela|Подмигивание|眨眼|ウィンク|पलक|غمزة
Loaf|Panecillo|Petit pain|Brotkatze|Pãozinho|Батон|猫面包|香箱座り|लोफ|قط مستريح
Pick a kitty or upload a photo from your gallery.|Elige un gato o sube una foto de tu galería.|Choisissez un chat ou importez une photo de votre galerie.|Wähle eine Katze oder ein Foto aus deiner Galerie.|Escolha um gatinho ou uma foto da galeria.|Выберите котика или фото из галереи.|选择小猫或从图库上传照片。|猫を選ぶかギャラリーから写真を追加してください。|बिल्ली चुनें या गैलरी से फ़ोटो अपलोड करें।|اختر قطًا أو ارفع صورة من معرضك.
""", """
Period & Care|Periodo y cuidado|Cycle et bien-être|Zyklus & Fürsorge|Ciclo e cuidado|Цикл и забота|经期与关怀|生理とケア|मासिक धर्म और देखभाल|الدورة والعناية
Private health logs · Encrypted on this phone|Registros privados · Cifrados en este teléfono|Journal privé · Chiffré sur ce téléphone|Private Gesundheitsdaten · Hier verschlüsselt|Registros privados · Criptografados neste telefone|Личные записи · Зашифрованы на телефоне|私密健康记录 · 在此手机上加密|非公開の健康記録・この端末で暗号化|निजी स्वास्थ्य रिकॉर्ड · इस फ़ोन पर एन्क्रिप्टेड|سجلات صحية خاصة · مشفرة على هذا الهاتف
Open Period & Care|Abrir periodo y cuidado|Ouvrir cycle et bien-être|Zyklus & Fürsorge öffnen|Abrir ciclo e cuidado|Открыть цикл и заботу|打开经期与关怀|生理とケアを開く|मासिक धर्म और देखभाल खोलें|فتح الدورة والعناية
Leave shared space|Salir del espacio compartido|Quitter l'espace partagé|Gemeinsamen Bereich verlassen|Sair do espaço compartilhado|Покинуть общее пространство|离开共享空间|共有スペースを退出|साझा स्थान छोड़ें|مغادرة المساحة المشتركة
You'll be asked to confirm. Your local tasks stay on this phone.|Se pedirá confirmación. Tus tareas locales se conservan.|Une confirmation sera demandée. Vos tâches restent ici.|Du wirst um Bestätigung gebeten. Lokale Aufgaben bleiben hier.|Você confirmará. Suas tarefas locais ficam aqui.|Потребуется подтверждение. Локальные задачи останутся.|需要您确认。本地任务保留在此手机上。|確認が必要です。ローカルのタスクは残ります。|पुष्टि माँगी जाएगी। स्थानीय कार्य इस फ़ोन पर रहेंगे।|ستُطلب منك الموافقة. تبقى مهامك المحلية على هذا الهاتف.
Leave this space?|¿Salir de este espacio?|Quitter cet espace ?|Diesen Bereich verlassen?|Sair deste espaço?|Покинуть пространство?|离开此空间？|このスペースを退出しますか？|यह स्थान छोड़ें?|مغادرة هذه المساحة؟
You'll return to the welcome screen. Your local lists are kept; reminders pause until you start again.|Volverás a la bienvenida. Las listas se conservan; los avisos se pausan hasta que vuelvas.|Vous reviendrez à l'accueil. Les listes restent ; les rappels sont suspendus jusqu'à votre retour.|Du kehrst zur Begrüßung zurück. Listen bleiben; Erinnerungen pausieren bis zum Neustart.|Você volta às boas-vindas. Listas ficam; lembretes pausam até você voltar.|Вы вернётесь на экран приветствия. Списки останутся; напоминания приостановятся до возвращения.|您将返回欢迎页。本地清单会保留，提醒暂停直到您重新开始。|ようこそ画面に戻ります。リストは残り、再開まで通知を停止します。|स्वागत स्क्रीन खुलेगी। सूचियाँ रहेंगी; फिर शुरू करने तक रिमाइंडर रुकेंगे।|ستعود إلى شاشة الترحيب. تُحفظ القوائم وتتوقف التذكيرات حتى تبدأ مجددًا.
Leave space|Salir|Quitter|Verlassen|Sair|Выйти|离开空间|退出|स्थान छोड़ें|مغادرة
Stay|Quedarme|Rester|Bleiben|Ficar|Остаться|留下|残る|रुकें|البقاء
Cancel|Cancelar|Annuler|Abbrechen|Cancelar|Отмена|取消|キャンセル|रद्द करें|إلغاء
Done|Hecho|Terminé|Erledigt|Concluído|Готово|完成|完了|पूरा|تم
Save|Guardar|Enregistrer|Speichern|Salvar|Сохранить|保存|保存|सहेजें|حفظ
Edit|Editar|Modifier|Bearbeiten|Editar|Изменить|编辑|編集|बदलें|تعديل
Delete|Eliminar|Supprimer|Löschen|Excluir|Удалить|删除|削除|हटाएँ|حذف
Back|Atrás|Retour|Zurück|Voltar|Назад|返回|戻る|वापस|رجوع
Close|Cerrar|Fermer|Schließen|Fechar|Закрыть|关闭|閉じる|बंद करें|إغلاق
Remove|Quitar|Retirer|Entfernen|Remover|Убрать|移除|削除|निकालें|إزالة
All|Todo|Tout|Alle|Tudo|Все|全部|すべて|सभी|الكل
Everyone|Todos|Tout le monde|Alle|Todos|Все|所有人|全員|सभी|الجميع
Together|Juntos|Ensemble|Zusammen|Juntos|Вместе|一起|一緒に|साथ में|معًا
Me|Yo|Moi|Ich|Eu|Я|我|自分|मैं|أنا
You|Tú|Toi|Du|Você|Ты|你|あなた|आप|أنت
Both|Ambos|Les deux|Beide|Ambos|Оба|双方|二人|दोनों|كلاكما
Once|Una vez|Une fois|Einmal|Uma vez|Один раз|一次|一度|एक बार|مرة واحدة
Daily|Diario|Chaque jour|Täglich|Diário|Ежедневно|每天|毎日|रोज़|يوميًا
Weekly|Semanal|Chaque semaine|Wöchentlich|Semanal|Еженедельно|每周|毎週|साप्ताहिक|أسبوعيًا
To do|Por hacer|À faire|Offen|A fazer|Сделать|待办|未着手|करना है|للقيام بها
Doing|En curso|En cours|In Arbeit|Em andamento|В работе|进行中|進行中|जारी|قيد التنفيذ
Good morning, {0}|Buenos días, {0}|Bonjour, {0}|Guten Morgen, {0}|Bom dia, {0}|Доброе утро, {0}|早上好，{0}|おはよう、{0}|सुप्रभात, {0}|صباح الخير، {0}
Still to do|Pendiente|Encore à faire|Noch offen|Ainda a fazer|Ещё нужно сделать|待完成|まだやること|अभी करना है|المتبقي
Done today|Hecho hoy|Terminé aujourd'hui|Heute erledigt|Concluído hoje|Выполнено сегодня|今日完成|今日の完了|आज पूरा हुआ|أُنجز اليوم
A clear windowsill. Add a little task when you're ready.|Todo despejado. Añade una tarea cuando quieras.|Tout est dégagé. Ajoutez une tâche quand vous voulez.|Alles frei. Füge eine Aufgabe hinzu, wenn du bereit bist.|Tudo livre. Adicione uma tarefa quando quiser.|Всё свободно. Добавьте задачу, когда захотите.|一切清爽。准备好时添加小任务吧。|すっきりしました。いつでもタスクを追加してください。|सब साफ़ है। तैयार हों तो छोटा कार्य जोड़ें।|كل شيء مرتب. أضف مهمة صغيرة عندما تكون مستعدًا.
Private mood, craving & symptom logs|Registros privados de ánimo, antojos y síntomas|Journal privé : humeur, envies et symptômes|Private Stimmung, Gelüste & Symptome|Registros privados de humor, desejos e sintomas|Личные записи настроения, желаний и симптомов|私密心情、食欲与症状记录|気分・食欲・症状の非公開記録|निजी मनोदशा, इच्छा और लक्षण रिकॉर्ड|سجلات خاصة للمزاج والرغبات والأعراض
Tomorrow|Mañana|Demain|Morgen|Amanhã|Завтра|明天|明日|कल|غدًا
Overdue|Atrasadas|En retard|Überfällig|Atrasadas|Просрочено|逾期|期限超過|समय निकल गया|متأخرة
Nothing planned here yet.|Aún no hay planes.|Rien de prévu ici.|Noch nichts geplant.|Nada planejado aqui.|Здесь пока нет планов.|暂无计划。|まだ予定はありません。|अभी कुछ तय नहीं है।|لا شيء مخطط هنا بعد.
That's everything on the horizon. Mochi says nap time.|Eso es todo por ahora. Mochi dice que toca siesta.|C'est tout pour l'instant. Mochi propose une sieste.|Das war alles. Mochi sagt: Zeit für ein Nickerchen.|É tudo por agora. Mochi sugere um cochilo.|Это всё на горизонте. Моти зовёт поспать.|这就是全部计划。Mochi说该打盹啦。|予定は以上です。モチがお昼寝に誘っています。|अभी इतना ही। मोची कहता है सोने का समय है।|هذا كل ما هو قادم. موتشي يقترح قيلولة.
Shared lists|Listas compartidas|Listes partagées|Gemeinsame Listen|Listas compartilhadas|Общие списки|共享清单|共有リスト|साझा सूचियाँ|القوائم المشتركة
Search lists or items|Buscar listas o elementos|Rechercher des listes ou éléments|Listen oder Einträge suchen|Buscar listas ou itens|Поиск списков и задач|搜索清单或项目|リストや項目を検索|सूचियाँ या आइटम खोजें|البحث عن قوائم أو عناصر
All our lists|Todas nuestras listas|Toutes nos listes|Alle unsere Listen|Todas as nossas listas|Все наши списки|我们的全部清单|すべてのリスト|हमारी सभी सूचियाँ|جميع قوائمنا
+ New list|+ Nueva lista|+ Nouvelle liste|+ Neue Liste|+ Nova lista|+ Новый список|+ 新清单|+ 新しいリスト|+ नई सूची|+ قائمة جديدة
New list|Nueva lista|Nouvelle liste|Neue Liste|Nova lista|Новый список|新清单|新しいリスト|नई सूची|قائمة جديدة
Edit list|Editar lista|Modifier la liste|Liste bearbeiten|Editar lista|Изменить список|编辑清单|リストを編集|सूची बदलें|تعديل القائمة
Create list|Crear lista|Créer la liste|Liste erstellen|Criar lista|Создать список|创建清单|リストを作成|सूची बनाएँ|إنشاء قائمة
List name|Nombre de lista|Nom de la liste|Listenname|Nome da lista|Название списка|清单名称|リスト名|सूची का नाम|اسم القائمة
Save changes|Guardar cambios|Enregistrer les modifications|Änderungen speichern|Salvar alterações|Сохранить изменения|保存更改|変更を保存|बदलाव सहेजें|حفظ التغييرات
Pinned on the fridge|Fijadas en la nevera|Épinglées au frigo|Am Kühlschrank angeheftet|Fixadas na geladeira|На холодильнике|冰箱上置顶|冷蔵庫にピン留め|फ़्रिज पर पिन|مثبتة على الثلاجة
Manage|Gestionar|Gérer|Verwalten|Gerenciar|Управление|管理|管理|प्रबंधित करें|إدارة
Fridge settings|Ajustes de nevera|Paramètres du frigo|Kühlschrank-Einstellungen|Configurações da geladeira|Настройки холодильника|冰箱设置|冷蔵庫の設定|फ़्रिज सेटिंग|إعدادات الثلاجة
Next up|Siguiente|À suivre|Als Nächstes|A seguir|Далее|接下来|次の予定|अगला|التالي
Pin|Fijar|Épingler|Anheften|Fixar|Закрепить|置顶|ピン留め|पिन करें|تثبيت
Pick any emoji|Elige un emoji|Choisissez un emoji|Emoji auswählen|Escolha um emoji|Выберите эмодзи|选择表情|絵文字を選ぶ|इमोजी चुनें|اختر رمزًا تعبيريًا
List|Lista|Liste|Liste|Lista|Список|清单|リスト|सूची|قائمة
Board|Tablero|Tableau|Board|Quadro|Доска|看板|ボード|बोर्ड|لوحة
Delete list|Eliminar lista|Supprimer la liste|Liste löschen|Excluir lista|Удалить список|删除清单|リストを削除|सूची हटाएँ|حذف القائمة
Unpin list|Desfijar lista|Désépingler la liste|Liste lösen|Desafixar lista|Открепить список|取消置顶|ピン留めを解除|सूची अनपिन करें|إلغاء تثبيت القائمة
Pin to fridge|Fijar en nevera|Épingler au frigo|Am Kühlschrank anheften|Fixar na geladeira|Закрепить на холодильнике|置顶到冰箱|冷蔵庫にピン留め|फ़्रिज पर पिन करें|تثبيت على الثلاجة
Add to To do…|Añadir a pendientes…|Ajouter à À faire…|Zu Offen hinzufügen…|Adicionar a A fazer…|Добавить в Сделать…|添加待办…|未着手に追加…|करने के लिए जोड़ें…|إضافة إلى المهام…
+ Add|+ Añadir|+ Ajouter|+ Hinzufügen|+ Adicionar|+ Добавить|+ 添加|+ 追加|+ जोड़ें|+ إضافة
Long-press to drag, or tap Move|Mantén pulsado para arrastrar o toca Mover|Appui long pour glisser, ou touchez Déplacer|Lange drücken zum Ziehen oder Verschieben antippen|Segure para arrastar ou toque em Mover|Удерживайте для переноса или нажмите Переместить|长按拖动，或点击移动|長押しでドラッグ、または移動をタップ|खींचने के लिए दबाएँ या ले जाएँ पर टैप करें|اضغط مطولًا للسحب أو اضغط نقل
""", """
New little task|Nueva tarea|Nouvelle petite tâche|Neue kleine Aufgabe|Nova tarefa|Новая задача|新任务|新しいタスク|नया छोटा कार्य|مهمة صغيرة جديدة
Edit little task|Editar tarea|Modifier la tâche|Aufgabe bearbeiten|Editar tarefa|Изменить задачу|编辑任务|タスクを編集|कार्य बदलें|تعديل المهمة
What needs doing?|¿Qué hay que hacer?|Que faut-il faire ?|Was ist zu tun?|O que precisa ser feito?|Что нужно сделать?|需要做什么？|何をしますか？|क्या करना है?|ما الذي يجب فعله؟
Whose is it?|¿De quién es?|Pour qui ?|Für wen?|De quem é?|Чья задача?|谁的任务？|誰のタスク？|यह किसका है?|لمن هذه المهمة؟
Tap a side or the middle|Toca un lado o el centro|Touchez un côté ou le milieu|Seite oder Mitte antippen|Toque um lado ou o meio|Нажмите сбоку или в центре|点击一侧或中间|左右か中央をタップ|किनारे या बीच में टैप करें|اضغط أحد الجانبين أو الوسط
When?|¿Cuándo?|Quand ?|Wann?|Quando?|Когда?|何时？|いつ？|कब?|متى؟
Pick date|Elegir fecha|Choisir la date|Datum wählen|Escolher data|Выбрать дату|选择日期|日付を選ぶ|तारीख चुनें|اختر التاريخ
Pick time|Elegir hora|Choisir l'heure|Uhrzeit wählen|Escolher hora|Выбрать время|选择时间|時刻を選ぶ|समय चुनें|اختر الوقت
Anytime|Cualquier hora|Sans heure|Jederzeit|Qualquer hora|В любое время|任何时间|時刻指定なし|कभी भी|أي وقت
Repeat|Repetir|Répétition|Wiederholen|Repetir|Повтор|重复|繰り返し|दोहराएँ|تكرار
One task on your selected date.|Una tarea en la fecha elegida.|Une tâche à la date choisie.|Eine Aufgabe am gewählten Datum.|Uma tarefa na data escolhida.|Одна задача на выбранную дату.|在所选日期执行一次。|選んだ日付に一度だけ。|चुनी हुई तारीख पर एक कार्य।|مهمة واحدة في التاريخ المحدد.
Tags|Etiquetas|Étiquettes|Tags|Etiquetas|Метки|标签|タグ|टैग|الوسوم
Set tags|Configurar etiquetas|Gérer les étiquettes|Tags festlegen|Definir etiquetas|Настроить метки|设置标签|タグ設定|टैग तय करें|تعيين الوسوم
Which list?|¿Qué lista?|Quelle liste ?|Welche Liste?|Qual lista?|Какой список?|哪个清单？|どのリスト？|कौन सी सूची?|أي قائمة؟
Create your first list to save this task.|Crea una lista para guardar esta tarea.|Créez une liste pour enregistrer cette tâche.|Erstelle eine Liste, um die Aufgabe zu speichern.|Crie uma lista para salvar esta tarefa.|Создайте список для сохранения задачи.|先创建清单以保存任务。|保存するには最初のリストを作成してください。|कार्य सहेजने के लिए पहली सूची बनाएँ।|أنشئ قائمتك الأولى لحفظ هذه المهمة.
Create a tag to organize your tasks.|Crea una etiqueta para organizar tus tareas.|Créez une étiquette pour organiser vos tâches.|Erstelle einen Tag für deine Aufgaben.|Crie uma etiqueta para organizar tarefas.|Создайте метку для организации задач.|创建标签来整理任务。|タグを作ってタスクを整理しましょう。|कार्यों को व्यवस्थित करने के लिए टैग बनाएँ।|أنشئ وسمًا لتنظيم مهامك.
Gentle reminder|Recordatorio suave|Rappel doux|Sanfte Erinnerung|Lembrete suave|Мягкое напоминание|温柔提醒|やさしい通知|हल्का रिमाइंडर|تذكير لطيف
A soft nudge, no alarms|Un aviso suave, sin alarmas|Un petit rappel, sans alarme|Sanfter Hinweis, kein Alarm|Um toque suave, sem alarmes|Мягкий сигнал без будильника|轻柔提示，无闹铃|アラームなしのやさしい合図|हल्का इशारा, अलार्म नहीं|تنبيه لطيف بلا منبهات
At due time|A la hora prevista|À l'échéance|Zur Fälligkeit|Na hora prevista|В срок|到期时|予定時刻|तय समय पर|عند الموعد
10 min before|10 min antes|10 min avant|10 Min. vorher|10 min antes|За 10 мин|提前10分钟|10分前|10 मिनट पहले|قبل 10 دقائق
30 min before|30 min antes|30 min avant|30 Min. vorher|30 min antes|За 30 мин|提前30分钟|30分前|30 मिनट पहले|قبل 30 دقيقة
1 hour before|1 hora antes|1 heure avant|1 Stunde vorher|1 hora antes|За 1 час|提前1小时|1時間前|1 घंटा पहले|قبل ساعة
Morning of|Esa mañana|Le matin même|Am selben Morgen|Na mesma manhã|Утром этого дня|当天早上|当日の朝|उसी सुबह|صباح ذلك اليوم
Custom…|Personalizado…|Personnalisé…|Benutzerdefiniert…|Personalizado…|Свой вариант…|自定义…|カスタム…|कस्टम…|مخصص…
Enable notifications|Activar notificaciones|Activer les notifications|Benachrichtigungen aktivieren|Ativar notificações|Включить уведомления|启用通知|通知を有効化|सूचनाएँ चालू करें|تفعيل الإشعارات
Little note|Nota pequeña|Petite note|Kleine Notiz|Pequena nota|Заметка|小笔记|ひとことメモ|छोटा नोट|ملاحظة صغيرة
Anything to remember?|¿Algo que recordar?|Quelque chose à retenir ?|Etwas zu merken?|Algo para lembrar?|Что нужно запомнить?|有什么要记住的？|覚えておくことは？|कुछ याद रखना है?|شيء لتذكره؟
Clear note|Borrar nota|Effacer la note|Notiz leeren|Limpar nota|Очистить заметку|清空笔记|メモを消去|नोट साफ़ करें|مسح الملاحظة
Add task|Añadir tarea|Ajouter une tâche|Aufgabe hinzufügen|Adicionar tarefa|Добавить задачу|添加任务|タスクを追加|कार्य जोड़ें|إضافة مهمة
Add for tomorrow|Añadir para mañana|Ajouter pour demain|Für morgen hinzufügen|Adicionar para amanhã|Добавить на завтра|添加到明天|明日に追加|कल के लिए जोड़ें|إضافة للغد
Edit task|Editar tarea|Modifier la tâche|Aufgabe bearbeiten|Editar tarefa|Изменить задачу|编辑任务|タスクを編集|कार्य बदलें|تعديل المهمة
Mark complete|Completar|Marquer terminé|Als erledigt markieren|Marcar como concluída|Отметить выполненной|标记完成|完了にする|पूरा चिह्नित करें|تحديد كمكتملة
Reopen task|Reabrir tarea|Rouvrir la tâche|Aufgabe wieder öffnen|Reabrir tarefa|Открыть снова|重新打开任务|タスクを再開|कार्य फिर खोलें|إعادة فتح المهمة
Delete task|Eliminar tarea|Supprimer la tâche|Aufgabe löschen|Excluir tarefa|Удалить задачу|删除任务|タスクを削除|कार्य हटाएँ|حذف المهمة
Shared task|Tarea compartida|Tâche partagée|Gemeinsame Aufgabe|Tarefa compartilhada|Общая задача|共享任务|共有タスク|साझा कार्य|مهمة مشتركة
Title · Tap to edit|Título · Toca para editar|Titre · Touchez pour modifier|Titel · Zum Bearbeiten tippen|Título · Toque para editar|Название · Нажмите для изменения|标题 · 点击编辑|タイトル・タップして編集|शीर्षक · बदलने के लिए टैप करें|العنوان · اضغط للتعديل
Tap to add a little note|Toca para añadir una nota|Touchez pour ajouter une note|Tippen, um eine Notiz hinzuzufügen|Toque para adicionar uma nota|Нажмите, чтобы добавить заметку|点击添加小笔记|タップしてメモを追加|नोट जोड़ने के लिए टैप करें|اضغط لإضافة ملاحظة
Edit note|Editar nota|Modifier la note|Notiz bearbeiten|Editar nota|Изменить заметку|编辑笔记|メモを編集|नोट बदलें|تعديل الملاحظة
Delete note|Eliminar nota|Supprimer la note|Notiz löschen|Excluir nota|Удалить заметку|删除笔记|メモを削除|नोट हटाएँ|حذف الملاحظة
Save note|Guardar nota|Enregistrer la note|Notiz speichern|Salvar nota|Сохранить заметку|保存笔记|メモを保存|नोट सहेजें|حفظ الملاحظة
Keep note|Conservar nota|Garder la note|Notiz behalten|Manter nota|Оставить заметку|保留笔记|メモを残す|नोट रखें|إبقاء الملاحظة
Edit little note|Editar nota pequeña|Modifier la petite note|Kleine Notiz bearbeiten|Editar pequena nota|Изменить заметку|编辑小笔记|メモを編集|छोटा नोट बदलें|تعديل الملاحظة الصغيرة
When & nudges|Fecha y avisos|Date et rappels|Zeit & Hinweise|Data e toques|Время и напоминания|时间与提醒|日時と通知|समय और इशारे|الوقت والتنبيهات
Due|Vence|Échéance|Fällig|Prazo|Срок|到期|期限|तय तारीख|الموعد
Gentle nudge|Aviso suave|Petit rappel|Sanfter Hinweis|Toque suave|Мягкое напоминание|轻柔提醒|やさしい合図|हल्का इशारा|تنبيه لطيف
Soft ping, no alarm|Aviso suave, sin alarma|Signal doux, sans alarme|Sanfter Ton, kein Alarm|Som suave, sem alarme|Мягкий сигнал без будильника|轻柔提示，无闹铃|アラームなしの軽い音|हल्की ध्वनि, अलार्म नहीं|إشارة لطيفة بلا منبه
Lives in|En la lista|Dans la liste|In der Liste|Na lista|В списке|所属清单|リスト|सूची में|في القائمة
Move|Mover|Déplacer|Verschieben|Mover|Переместить|移动|移動|ले जाएँ|نقل
Move to a list|Mover a una lista|Déplacer vers une liste|In eine Liste verschieben|Mover para uma lista|Переместить в список|移动到清单|リストに移動|सूची में ले जाएँ|نقل إلى قائمة
Choose a cozy spot for this task.|Elige un lugar para esta tarea.|Choisissez une place pour cette tâche.|Wähle einen Platz für diese Aufgabe.|Escolha um lugar para esta tarefa.|Выберите место для задачи.|为此任务选择清单。|このタスクの置き場所を選んでください。|इस कार्य के लिए जगह चुनें।|اختر مكانًا لهذه المهمة.
Current list|Lista actual|Liste actuelle|Aktuelle Liste|Lista atual|Текущий список|当前清单|現在のリスト|वर्तमान सूची|القائمة الحالية
Move here|Mover aquí|Déplacer ici|Hierher verschieben|Mover para cá|Переместить сюда|移动到这里|ここに移動|यहाँ ले जाएँ|نقل هنا
Little history|Historial|Petit historique|Kleine Historie|Histórico|История|历史记录|履歴|छोटा इतिहास|سجل صغير
Our progress|Nuestro progreso|Nos progrès|Unser Fortschritt|Nosso progresso|Наш прогресс|我们的进度|私たちの進捗|हमारी प्रगति|تقدمنا
Week|Semana|Semaine|Woche|Semana|Неделя|周|週|सप्ताह|أسبوع
Month|Mes|Mois|Monat|Mês|Месяц|月|月|महीना|شهر
This week|Esta semana|Cette semaine|Diese Woche|Esta semana|Эта неделя|本周|今週|इस सप्ताह|هذا الأسبوع
This month|Este mes|Ce mois|Dieser Monat|Este mês|Этот месяц|本月|今月|इस महीने|هذا الشهر
Done this week|Hecho esta semana|Terminé cette semaine|Diese Woche erledigt|Concluído nesta semana|Выполнено за неделю|本周完成|今週の完了|इस सप्ताह पूरा|أُنجز هذا الأسبوع
Done this month|Hecho este mes|Terminé ce mois|Diesen Monat erledigt|Concluído neste mês|Выполнено за месяц|本月完成|今月の完了|इस महीने पूरा|أُنجز هذا الشهر
Pipo's mood|Ánimo de Pipo|Humeur de Pipo|Pipos Stimmung|Humor do Pipo|Настроение Пипо|Pipo的心情|ピポの気分|पिपो का मूड|مزاج بيبو
Fully stretched|Bien estirado|Bien étiré|Gut gestreckt|Bem alongado|Хорошо потянулся|舒展自在|のびのび|पूरी तरह तैयार|مسترخٍ تمامًا
Getting cozy|Acomodándose|Bien installé|Gemütlich werden|Ficando confortável|Устраиваемся уютно|渐入舒适|くつろぎ中|आराम से|يستريح
Recently done|Recién completado|Terminé récemment|Kürzlich erledigt|Concluído recentemente|Недавно выполнено|最近完成|最近の完了|हाल में पूरा|أُنجز مؤخرًا
Your first tick starts the story.|Tu primera tarea inicia la historia.|Votre première coche commence l'histoire.|Dein erster Haken beginnt die Geschichte.|Sua primeira tarefa começa a história.|Первая галочка начинает историю.|第一个勾开启故事。|最初のチェックから物語が始まります。|पहला टिक कहानी शुरू करता है।|أول علامة إنجاز تبدأ القصة.
A little kindness goes a long way|Un poco de cariño llega lejos|Un peu de gentillesse fait beaucoup|Ein wenig Freundlichkeit bewirkt viel|Um pouco de carinho faz muito|Немного доброты значит многое|小小善意，温暖长久|小さな優しさが大きな力に|छोटी दया का बड़ा असर|لطف صغير يصنع فرقًا
Send {0} a thank-you|Dar las gracias a {0}|Remercier {0}|{0} Danke sagen|Agradecer a {0}|Поблагодарить {0}|向{0}致谢|{0}にありがとうを伝える|{0} को धन्यवाद दें|إرسال شكر إلى {0}
Appreciation|Agradecimiento|Gratitude|Wertschätzung|Gratidão|Благодарность|感谢|感謝|आभार|التقدير
Thank you|Gracias|Merci|Danke|Obrigado|Спасибо|谢谢|ありがとう|धन्यवाद|شكرًا
You're the best|Eres lo mejor|Tu es formidable|Du bist spitze|Você é incrível|Ты лучше всех|你最棒|最高だよ|आप सबसे अच्छे हैं|أنت الأفضل
Purr-fect|Purr-fecto|Parfait|Schnurrfekt|Perfeito|Мурр-чательно|喵完美|にゃんと素敵|बहुत बढ़िया|رائع
Saved|Guardado|Enregistré|Gespeichert|Salvo|Сохранено|已保存|保存済み|सहेजा गया|محفوظ
Say thanks|Dar las gracias|Dire merci|Danke sagen|Agradecer|Сказать спасибо|说声谢谢|ありがとうを言う|धन्यवाद कहें|قل شكرًا
Later|Luego|Plus tard|Später|Depois|Позже|稍后|後で|बाद में|لاحقًا
Add a little note (optional)|Añade una nota (opcional)|Ajoutez une note (facultatif)|Kleine Notiz (optional)|Adicione uma nota (opcional)|Добавьте заметку (необязательно)|添加小笔记（可选）|メモを追加（任意）|छोटा नोट जोड़ें (वैकल्पिक)|أضف ملاحظة صغيرة (اختياري)
Save a thank-you for {0}|Guardar un gracias para {0}|Enregistrer un merci pour {0}|Ein Danke für {0} speichern|Salvar um obrigado para {0}|Сохранить благодарность для {0}|保存给{0}的感谢|{0}への感謝を保存|{0} के लिए धन्यवाद सहेजें|حفظ شكر لـ {0}
Local mode · Saved here, not delivered to another phone.|Modo local · Guardado aquí, no enviado a otro teléfono.|Mode local · Enregistré ici, non envoyé à un autre téléphone.|Lokaler Modus · Hier gespeichert, nicht an ein anderes Handy gesendet.|Modo local · Salvo aqui, não enviado a outro telefone.|Локальный режим · Сохранено здесь, не отправлено на другой телефон.|本地模式 · 保存在此处，未发送到其他手机。|ローカルモード・ここに保存され、他の端末には送信されません。|स्थानीय मोड · यहीं सहेजा, दूसरे फ़ोन पर नहीं भेजा।|الوضع المحلي · محفوظ هنا ولم يُرسل إلى هاتف آخر.
""", """
Notifications|Notificaciones|Notifications|Benachrichtigungen|Notificações|Уведомления|通知|通知|सूचनाएँ|الإشعارات
Gentle reminders|Recordatorios suaves|Rappels doux|Sanfte Erinnerungen|Lembretes suaves|Мягкие напоминания|温柔提醒|やさしい通知|हल्के रिमाइंडर|تذكيرات لطيفة
Android notification settings|Ajustes de notificaciones Android|Paramètres des notifications Android|Android-Benachrichtigungen|Configurações de notificações Android|Настройки уведомлений Android|Android通知设置|Androidの通知設定|Android सूचना सेटिंग|إعدادات إشعارات Android
Ongoing task notifications|Notificaciones de tareas|Notifications de tâches|Aufgabenbenachrichtigungen|Notificações de tarefas|Уведомления о задачах|持续任务通知|継続的なタスク通知|चल रहे कार्य की सूचनाएँ|إشعارات المهام المستمرة
Hide notification controls|Ocultar controles|Masquer les contrôles|Steuerung ausblenden|Ocultar controles|Скрыть настройки|隐藏通知控制|通知設定を隠す|सूचना नियंत्रण छुपाएँ|إخفاء أدوات الإشعارات
Notifications enabled|Notificaciones activadas|Notifications activées|Benachrichtigungen aktiviert|Notificações ativadas|Уведомления включены|通知已启用|通知が有効です|सूचनाएँ चालू हैं|الإشعارات مفعلة
Notifications are off|Notificaciones desactivadas|Notifications désactivées|Benachrichtigungen deaktiviert|Notificações desativadas|Уведомления выключены|通知已关闭|通知が無効です|सूचनाएँ बंद हैं|الإشعارات متوقفة
Reminder channel settings|Ajustes del canal de avisos|Paramètres du canal de rappels|Erinnerungskanal-Einstellungen|Configurações do canal de lembretes|Настройки канала напоминаний|提醒渠道设置|通知チャンネル設定|रिमाइंडर चैनल सेटिंग|إعدادات قناة التذكيرات
Create a test reminder task|Crear tarea de prueba|Créer une tâche de test|Test-Erinnerung erstellen|Criar tarefa de teste|Создать тестовое напоминание|创建测试提醒任务|テスト通知タスクを作成|परीक्षण रिमाइंडर बनाएँ|إنشاء مهمة تذكير تجريبية
On this phone only|Solo en este teléfono|Uniquement sur ce téléphone|Nur auf diesem Handy|Só neste telefone|Только на этом телефоне|仅在此手机上|この端末のみ|केवल इस फ़ोन पर|على هذا الهاتف فقط
Next period|Próximo periodo|Prochaines règles|Nächste Periode|Próxima menstruação|Следующая менструация|下次经期|次の生理|अगला मासिक धर्म|الدورة القادمة
Estimated|Estimado|Estimé|Geschätzt|Estimado|Прогноз|预计|予測|अनुमानित|تقديري
Log a start to see an estimate|Registra el inicio para ver una estimación|Notez un début pour voir une estimation|Beginn eintragen für eine Schätzung|Registre o início para uma estimativa|Запишите начало для прогноза|记录开始日期以查看预测|開始日を記録すると予測を表示|अनुमान देखने के लिए शुरुआत दर्ज करें|سجل البداية لرؤية تقدير
A guide only — not medical or fertility advice.|Solo una guía, no consejo médico ni de fertilidad.|Simple indication, pas un conseil médical ou de fertilité.|Nur Orientierung, keine medizinische oder Fruchtbarkeitsberatung.|Só orientação, não é conselho médico ou de fertilidade.|Только ориентир, не медицинский совет или совет о фертильности.|仅供参考，不是医疗或生育建议。|目安のみで、医療や妊娠に関する助言ではありません。|केवल मार्गदर्शन, चिकित्सा या प्रजनन सलाह नहीं।|للاسترشاد فقط، وليست نصيحة طبية أو للخصوبة.
How's today?|¿Cómo va hoy?|Comment ça va aujourd'hui ?|Wie geht's heute?|Como está hoje?|Как сегодня?|今天怎么样？|今日はどう？|आज कैसा है?|كيف حالك اليوم؟
Private|Privado|Privé|Privat|Privado|Личное|私密|非公開|निजी|خاص
Very low|Muy bajo|Très bas|Sehr schlecht|Muito baixo|Очень плохо|很低落|とても低い|बहुत उदास|منخفض جدًا
Low|Bajo|Bas|Schlecht|Baixo|Плохо|低落|低い|उदास|منخفض
Okay|Regular|Moyen|Okay|Regular|Нормально|还好|普通|ठीक|عادي
Good|Bien|Bien|Gut|Bom|Хорошо|良好|良い|अच्छा|جيد
Great|Genial|Excellent|Super|Ótimo|Отлично|很好|最高|बहुत अच्छा|رائع
Cravings|Antojos|Envies|Gelüste|Desejos|Желания|食欲|食欲|खाने की इच्छा|الرغبات
Symptoms|Síntomas|Symptômes|Symptome|Sintomas|Симптомы|症状|症状|लक्षण|الأعراض
Private note|Nota privada|Note privée|Private Notiz|Nota privada|Личная заметка|私密笔记|非公開メモ|निजी नोट|ملاحظة خاصة
Mood|Ánimo|Humeur|Stimmung|Humor|Настроение|心情|気分|मनोदशा|المزاج
Flow|Flujo|Flux|Blutung|Fluxo|Выделения|经量|経血量|प्रवाह|التدفق
Cramps|Cólicos|Crampes|Krämpfe|Cólicas|Спазмы|痛经|生理痛|ऐंठन|التقلصات
None|Ninguno|Aucun|Keine|Nenhum|Нет|无|なし|कोई नहीं|لا شيء
Light|Ligero|Léger|Leicht|Leve|Слабый|少量|少ない|हल्का|خفيف
Medium|Medio|Moyen|Mittel|Médio|Средний|中量|普通|मध्यम|متوسط
Heavy|Abundante|Abondant|Stark|Intenso|Обильный|大量|多い|भारी|غزير
Mild|Leve|Léger|Leicht|Leve|Слабые|轻微|軽い|हल्का|بسيط
Moderate|Moderado|Modéré|Mäßig|Moderado|Умеренные|中等|中程度|मध्यम|معتدل
Severe|Intenso|Intense|Stark|Severo|Сильные|严重|強い|गंभीर|شديد
Chocolate|Chocolate|Chocolat|Schokolade|Chocolate|Шоколад|巧克力|チョコレート|चॉकलेट|شوكولاتة
Sweet|Dulce|Sucré|Süß|Doce|Сладкое|甜食|甘いもの|मीठा|حلو
Salty|Salado|Salé|Salzig|Salgado|Солёное|咸食|しょっぱいもの|नमकीन|مالح
Spicy|Picante|Épicé|Scharf|Picante|Острое|辣食|辛いもの|तीखा|حار
Fruit|Fruta|Fruit|Obst|Fruta|Фрукты|水果|果物|फल|فاكهة
Comfort food|Comida reconfortante|Plat réconfortant|Wohlfühlessen|Comida reconfortante|Любимая еда|暖心食物|ほっとする食べ物|पसंदीदा खाना|طعام مريح
No appetite|Sin apetito|Pas d'appétit|Kein Appetit|Sem apetite|Нет аппетита|无食欲|食欲なし|भूख नहीं|لا شهية
Back ache|Dolor de espalda|Mal de dos|Rückenschmerzen|Dor nas costas|Боль в спине|背痛|腰痛|पीठ दर्द|ألم الظهر
Headache|Dolor de cabeza|Mal de tête|Kopfschmerzen|Dor de cabeça|Головная боль|头痛|頭痛|सिरदर्द|صداع
Bloating|Hinchazón|Ballonnements|Blähungen|Inchaço|Вздутие|腹胀|むくみ・膨満感|पेट फूलना|انتفاخ
Fatigue|Cansancio|Fatigue|Müdigkeit|Fadiga|Усталость|疲劳|疲労|थकान|إرهاق
Tender breasts|Sensibilidad mamaria|Seins sensibles|Brustspannen|Seios sensíveis|Чувствительность груди|乳房胀痛|胸の張り|स्तन में दर्द|حساسية الثدي
Acne|Acné|Acné|Akne|Acne|Акне|痤疮|ニキビ|मुहाँसे|حب الشباب
Nausea|Náuseas|Nausée|Übelkeit|Náusea|Тошнота|恶心|吐き気|मतली|غثيان
Poor sleep|Mal sueño|Mauvais sommeil|Schlechter Schlaf|Sono ruim|Плохой сон|睡眠不佳|睡眠不足|खराब नींद|نوم سيئ
Period start|Inicio del periodo|Début des règles|Periodenbeginn|Início da menstruação|Начало менструации|经期开始|生理開始|मासिक धर्म शुरू|بداية الدورة
Period end|Fin del periodo|Fin des règles|Periodenende|Fim da menstruação|Конец менструации|经期结束|生理終了|मासिक धर्म खत्म|نهاية الدورة
Day log|Registro diario|Journal du jour|Tagesprotokoll|Registro diário|Запись дня|每日记录|日々の記録|दिन का रिकॉर्ड|سجل اليوم
Add day log|Añadir registro|Ajouter une entrée|Tag eintragen|Adicionar registro|Добавить запись|添加每日记录|記録を追加|दिन का रिकॉर्ड जोड़ें|إضافة سجل يوم
Edit day log|Editar registro|Modifier l'entrée|Tag bearbeiten|Editar registro|Изменить запись|编辑每日记录|記録を編集|दिन का रिकॉर्ड बदलें|تعديل سجل اليوم
Save day log|Guardar registro|Enregistrer l'entrée|Tag speichern|Salvar registro|Сохранить запись|保存每日记录|記録を保存|दिन का रिकॉर्ड सहेजें|حفظ سجل اليوم
Delete day log|Eliminar registro|Supprimer l'entrée|Tag löschen|Excluir registro|Удалить запись дня|删除每日记录|記録を削除|दिन का रिकॉर्ड हटाएँ|حذف سجل اليوم
No log yet. Add how you're feeling.|Sin registro. Añade cómo te sientes.|Pas d'entrée. Notez votre ressenti.|Noch kein Eintrag. Wie fühlst du dich?|Sem registro. Adicione como se sente.|Записи нет. Добавьте своё самочувствие.|暂无记录。添加您的感受吧。|記録はまだありません。気分を記録しましょう。|रिकॉर्ड नहीं। आप कैसा महसूस करते हैं जोड़ें।|لا سجل بعد. أضف ما تشعر به.
Only you|Solo tú|Vous uniquement|Nur du|Só você|Только вы|仅您可见|自分だけ|केवल आप|أنت فقط
Private health logs|Registros de salud privados|Journal de santé privé|Private Gesundheitsdaten|Registros de saúde privados|Личные записи здоровья|私密健康记录|非公開の健康記録|निजी स्वास्थ्य रिकॉर्ड|سجلات صحية خاصة
Your logs stay encrypted on this phone. They are not sent to your partner or included in shared tasks.|Tus registros están cifrados aquí. No se envían a tu pareja ni a tareas compartidas.|Vos données restent chiffrées ici. Elles ne sont pas envoyées au partenaire ni aux tâches partagées.|Deine Daten bleiben hier verschlüsselt. Sie werden nicht an den Partner oder gemeinsame Aufgaben gesendet.|Seus registros ficam criptografados aqui. Não são enviados ao parceiro nem a tarefas compartilhadas.|Записи зашифрованы на телефоне. Они не отправляются партнёру или в общие задачи.|记录在此手机上加密保存，不会发送给伴侣或加入共享任务。|記録はこの端末で暗号化され、パートナーや共有タスクに送られません。|रिकॉर्ड इस फ़ोन पर एन्क्रिप्टेड हैं। साथी को नहीं भेजे जाते या साझा कार्यों में नहीं जुड़ते।|تبقى سجلاتك مشفرة على هذا الهاتف ولا تُرسل لشريكك أو تُضمّن في المهام المشتركة.
On-device|En el dispositivo|Sur l'appareil|Auf dem Gerät|No dispositivo|На устройстве|本机|端末内|डिवाइस पर|على الجهاز
Evening check-in|Revisión nocturna|Bilan du soir|Abendlicher Check-in|Registro noturno|Вечерняя отметка|晚间记录提醒|夜のチェックイン|शाम की जाँच|تسجيل مسائي
Heads-up|Aviso previo|Préavis|Vorabhinweis|Aviso prévio|Предупреждение|提前提醒|事前のお知らせ|पूर्व सूचना|تنبيه مسبق
Every day|Cada día|Chaque jour|Jeden Tag|Todos os dias|Каждый день|每天|毎日|हर दिन|كل يوم
2 days before estimated start|2 días antes del inicio estimado|2 jours avant le début estimé|2 Tage vor dem geschätzten Beginn|2 dias antes do início estimado|За 2 дня до ожидаемого начала|预计开始前2天|予測開始日の2日前|अनुमानित शुरुआत से 2 दिन पहले|قبل البداية المقدرة بيومين
Locked to this phone|Solo en este teléfono|Lié à ce téléphone|An dieses Handy gebunden|Preso a este telefone|Только на этом телефоне|仅保存在此手机|この端末に限定|इसी फ़ोन तक सीमित|مقيد بهذا الهاتف
Clear all records|Borrar todos los registros|Effacer toutes les données|Alle Einträge löschen|Limpar todos os registros|Удалить все записи|清除所有记录|すべての記録を消去|सभी रिकॉर्ड साफ़ करें|مسح جميع السجلات
Cycle settings|Ajustes del ciclo|Paramètres du cycle|Zykluseinstellungen|Configurações do ciclo|Настройки цикла|周期设置|周期の設定|चक्र सेटिंग|إعدادات الدورة
Cycle length · 10–120 days|Duración del ciclo · 10–120 días|Durée du cycle · 10–120 jours|Zykluslänge · 10–120 Tage|Duração do ciclo · 10–120 dias|Длина цикла · 10–120 дней|周期长度 · 10–120天|周期の長さ・10～120日|चक्र अवधि · 10–120 दिन|طول الدورة · 10–120 يومًا
Period duration · 1–14 days|Duración del periodo · 1–14 días|Durée des règles · 1–14 jours|Periodendauer · 1–14 Tage|Duração da menstruação · 1–14 dias|Длительность · 1–14 дней|经期时长 · 1–14天|生理の長さ・1～14日|मासिक धर्म अवधि · 1–14 दिन|مدة الحيض · 1–14 يومًا
Save settings|Guardar ajustes|Enregistrer les paramètres|Einstellungen speichern|Salvar configurações|Сохранить настройки|保存设置|設定を保存|सेटिंग सहेजें|حفظ الإعدادات
Poke|Avisar|Petit rappel|Stupsen|Toque|Напомнить|轻推|合図|इशारा|تنبيه
Got it|Entendido|Compris|Verstanden|Entendi|Понятно|知道了|了解|समझ गया|فهمت
Photo unavailable|Foto no disponible|Photo indisponible|Foto nicht verfügbar|Foto indisponível|Фото недоступно|照片不可用|写真を開けません|फ़ोटो उपलब्ध नहीं|الصورة غير متاحة
Choose another photo from your gallery.|Elige otra foto de tu galería.|Choisissez une autre photo de la galerie.|Wähle ein anderes Foto aus der Galerie.|Escolha outra foto da galeria.|Выберите другое фото из галереи.|从图库选择其他照片。|ギャラリーから別の写真を選んでください。|गैलरी से दूसरी फ़ोटो चुनें।|اختر صورة أخرى من معرضك.
{0} left|{0} pendientes|{0} restantes|{0} offen|{0} restantes|Осталось {0}|剩余{0}项|残り{0}件|{0} बाकी|{0} متبقية
{0} finished|{0} terminadas|{0} terminées|{0} erledigt|{0} concluídas|Завершено {0}|完成{0}项|{0}件完了|{0} पूरे|{0} مكتملة
{0} tasks ahead|{0} tareas por delante|{0} tâches à venir|{0} anstehende Aufgaben|{0} tarefas futuras|Впереди {0} задач|{0}项待办任务|{0}件の予定|{0} कार्य आगे|{0} مهام قادمة
{0} overdue|{0} atrasadas|{0} en retard|{0} überfällig|{0} atrasadas|Просрочено {0}|{0}项逾期|{0}件期限超過|{0} समय से पीछे|{0} متأخرة
{0} tasks|{0} tareas|{0} tâches|{0} Aufgaben|{0} tarefas|{0} задач|{0}项任务|{0}件のタスク|{0} कार्य|{0} مهام
{0} lists|{0} listas|{0} listes|{0} Listen|{0} listas|{0} списков|{0}个清单|{0}個のリスト|{0} सूचियाँ|{0} قوائم
{0} open|{0} abiertas|{0} ouvertes|{0} offen|{0} abertas|Открыто {0}|{0}项未完成|{0}件未完了|{0} खुले|{0} مفتوحة
{0} done|{0} hechas|{0} terminées|{0} erledigt|{0} concluídas|Готово {0}|{0}项完成|{0}件完了|{0} पूरे|{0} مكتملة
{0} of {1} done|{0} de {1} hechas|{0} sur {1} terminées|{0} von {1} erledigt|{0} de {1} concluídas|Выполнено {0} из {1}|完成{0}/{1}|{1}件中{0}件完了|{1} में से {0} पूरे|{0} من {1} مكتملة
Receive pokes from {0}|Recibir avisos de {0}|Recevoir les rappels de {0}|Stupser von {0} erhalten|Receber toques de {0}|Получать напоминания от {0}|接收{0}的轻推|{0}からの合図を受信|{0} के इशारे पाएँ|استقبال تنبيهات من {0}
Task tags · {0} reusable tags|Etiquetas · {0} reutilizables|Étiquettes · {0} réutilisables|Aufgaben-Tags · {0} wiederverwendbar|Etiquetas · {0} reutilizáveis|Метки задач · {0} многоразовых|任务标签 · {0}个可复用标签|タスクタグ・{0}個|कार्य टैग · {0} दोबारा उपयोग योग्य|وسوم المهام · {0} قابلة لإعادة الاستخدام
{0}-day cycle · {1}-day period|Ciclo de {0} días · Periodo de {1} días|Cycle de {0} jours · Règles de {1} jours|{0}-Tage-Zyklus · {1}-Tage-Periode|Ciclo de {0} dias · Menstruação de {1} dias|Цикл {0} дней · Менструация {1} дней|{0}天周期 · {1}天经期|{0}日周期・生理{1}日|{0} दिन का चक्र · {1} दिन मासिक धर्म|دورة {0} يومًا · حيض {1} يومًا
""").plus(supplementalTranslations).joinToString("\n") { it.trimIndent() }.lineSequence().filter {it.isNotBlank()}.associate { line -> val columns = line.split('|'); columns.first() to columns }
private val templates = catalog.keys.filter { it.contains("{0}") }.map { key ->
    val parts = Regex("\\{\\d+\\}").split(key)
    key to Regex("^" + parts.joinToString("(.+?)") { Regex.escape(it) } + "$")
}
fun localized(text: String, code: String, preserved: Set<String> = emptySet()): String {
    if(text in preserved) return text
    val index = appLanguages.indexOfFirst { it.code == code }
    if(index <= 0 || text.isBlank()) return text
    catalog[text]?.getOrNull(index)?.let { return it }
    templates.forEach { (key, regex) ->
        val match = regex.matchEntire(text)
        if(match != null) {
            var result = catalog.getValue(key).getOrNull(index) ?: return text
            match.groupValues.drop(1).forEachIndexed { i, value -> result = result.replace("{$i}", value) }
            return result
        }
    }
    // Compound metadata uses translated enum labels while preserving names, times and numbers.
    if(text.contains(" · ")) return text.split(" · ").joinToString(" · ") { localized(it, code, preserved) }
    if(text.contains('\n')) return text.split('\n').joinToString("\n") { localized(it, code) }
    return text
}
