# DAYFLOW – مساعدك الشخصي الذكي

> «تحكّم بيومك، لا تدع يومك يتحكم بك.»

تطبيق Android عربي أصلي (RTL) لإدارة المهام والمنبّهات والعادات والتركيز والإنتاجية، يعمل **بالكامل دون إنترنت**.
لا يطلب التطبيق صلاحية الإنترنت أصلاً، ولا يحتوي حسابات ولا إعلانات ولا أدوات تتبع.

---

## 1. حالة التسليم (بصراحة)

| البند | الحالة |
|---|---|
| مشروع Android Studio كامل (Kotlin + Compose + Room + AlarmManager) | ✅ موجود |
| وحدة `core` (محرك الذكاء، الجدولة، أنقذ يومي، محلل الصوت، التكرار، توقيت التنبيهات، النسخ الاحتياطي) | ✅ تُرجمت وشُغّلت اختباراتها: **65/65 ناجحة** |
| اختبارات Android على الجهاز (`androidTest`) | ✍️ مكتوبة (22 اختباراً) – **لم تُشغَّل** لعدم توفر Android SDK في بيئة التطوير |
| ملف APK | ❌ **لم يُبنَ** في بيئة التطوير لأنها لا تصل إلى Google Maven ولا Android SDK. اتبع القسم 3 لبنائه على جهازك بأمر واحد |
| AAB / APK موقّع | يُبنى على جهازك بعد إعداد مفتاح التوقيع (القسم 4) |

التفاصيل الكاملة في [`docs/TEST_REPORT.md`](docs/TEST_REPORT.md).

---

## 2. المتطلبات

- **Android Studio** Ladybug (2024.2) أو أحدث – يتضمن JDK 17/21.
- اتصال إنترنت **مرة واحدة** لتنزيل Gradle والمكتبات (التطبيق نفسه لا يحتاج الإنترنت).
- Android SDK Platform **35** (يثبّته Android Studio تلقائياً).
- جهاز أو محاكي Android **8.0 (API 26)** فما فوق.

الإصدارات المستخدمة (ملف `gradle/libs.versions.toml`):
AGP 8.7.3 · Gradle 8.11.1 · Kotlin 2.1.0 · KSP 2.1.0-1.0.29 · Compose BOM 2024.12.01 · Room 2.6.1 · WorkManager 2.10.0 · Navigation 2.8.5 · compileSdk/targetSdk 35 · minSdk 26.
إن ظهرت نسخ أحدث مستقرة يمكنك تحديثها من هذا الملف وحده.

---

## 3. بناء APK (Debug)

### من Android Studio
1. `File ▸ Open` واختر مجلد `DAYFLOW`.
2. انتظر انتهاء **Gradle Sync**.
3. `Build ▸ Build App Bundle(s) / APK(s) ▸ Build APK(s)`.

### من سطر الأوامر
```bash
cd DAYFLOW
./gradlew assembleDebug            # Windows: gradlew.bat assembleDebug
```
الناتج:
```
app/build/outputs/apk/debug/app-debug.apk
```
التثبيت على جهاز موصول:
```bash
./gradlew installDebug
# أو
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> نسخة Debug تحمل المعرّف `com.dayflow.app.debug` فيمكن تثبيتها بجانب نسخة Release.

---

## 4. نسخة Release موقّعة و AAB

1. أنشئ مفتاح توقيع (مرة واحدة):
   ```bash
   keytool -genkeypair -v -keystore dayflow-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias dayflow
   ```
2. أنشئ الملف `keystore.properties` في جذر المشروع (مستثنى من Git):
   ```properties
   storeFile=dayflow-release.jks
   storePassword=كلمة_مرور_المخزن
   keyAlias=dayflow
   keyPassword=كلمة_مرور_المفتاح
   ```
3. ابنِ:
   ```bash
   ./gradlew assembleRelease   # app/build/outputs/apk/release/app-release.apk
   ./gradlew bundleRelease     # app/build/outputs/bundle/release/app-release.aab
   ```
بدون `keystore.properties` يُبنى Release غير موقّع (`app-release-unsigned.apk`).

---

## 5. تشغيل الاختبارات

```bash
./gradlew :core:test                    # اختبارات المحرك على JVM (لا تحتاج جهازاً) – 65 اختباراً
./gradlew connectedDebugAndroidTest     # اختبارات الجهاز: Room، AlarmManager، إعادة التشغيل، المنطقة الزمنية، النسخ الاحتياطي، الواجهة
```
تقارير HTML:
`core/build/reports/tests/test/index.html` و `app/build/reports/androidTests/connected/index.html`.

اختبار يدوي مقترح للمنبّه على جهاز حقيقي: [`docs/ALARMS.md`](docs/ALARMS.md#قائمة-اختبار-يدوي-على-جهاز-حقيقي).

---

## 6. هيكل المشروع

```
DAYFLOW/
├── core/                     وحدة Kotlin خالصة (بدون Android) – قابلة للاختبار على JVM
│   └── com/dayflow/core/
│       ├── model/            Task, Habit, RecurrenceRule, DaySettings…
│       ├── intelligence/     DAYFLOW INTELLIGENCE ENGINE (واجهة AssistantEngine + محرك القواعد)
│       ├── scheduling/       DayPlanner – محرك «أنقذ يومي» والجدولة
│       ├── time/             التكرار، توقيت التنبيهات (DST/المناطق الزمنية)، الهجري
│       ├── text/             تطبيع العربية + محلل VOICE TO TASK
│       ├── stats/            إحصائيات العادات والإنتاجية
│       └── backup/           صيغة النسخ الاحتياطي + تشفير AES-GCM
├── app/                      تطبيق Android
│   └── com/dayflow/app/
│       ├── data/             Room (17 جدولاً)، DAOs، Repositories، DataStore، النسخ الاحتياطي
│       ├── domain/           TaskInteractor، AssistantService، FocusTimer، DataManager
│       ├── notification/     AlarmScheduler، ReminderScheduler، Receivers، AlarmRingingService، AlarmActivity، Workers
│       ├── widget/           ودجت الشاشة الرئيسية
│       └── ui/               Compose: theme، components، navigation، screens/*
├── docs/                     البنية، الصلاحيات، التنبيهات، تقرير الاختبارات
└── gradle/libs.versions.toml
```

شرح البنية والقرارات الهندسية: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
الصلاحيات: [`docs/PERMISSIONS.md`](docs/PERMISSIONS.md) · التنبيهات: [`docs/ALARMS.md`](docs/ALARMS.md)

---

## 7. الشاشات والميزات

| الميزة | أين |
|---|---|
| لوحة التحكم: تحية، ميلادي/هجري، الساعة، حلقة الإنجاز، «ماذا أفعل الآن؟» مع زر بدء، المتأخرة، القادمة، العادات، الوقت المتاح، التذكير القادم، اقتراح المساعد | الرئيسية |
| إدارة المهام: إضافة/تعديل/حذف/أرشفة/استنساخ/إكمال/تأجيل/إلغاء، أولويات، حالات، بداية وموعد نهائي، مدة، وصف وملاحظات، مرفقات محلية، تصنيفات، وسوم، مهام فرعية واعتماديات | المهام / تفاصيل المهمة / المحرر |
| طرق العرض: قائمة، اليوم، الأسبوع، الشهر، حسب التصنيف، حسب الأولوية، جدول زمني + بحث وفرز وتصفية | المهام |
| المنبّه الحقيقي: عدة تنبيهات للمهمة، نغمة من النظام أو ملف، مستوى صوت، مدة رنين، تكرار عند عدم الاستجابة، تأجيل 5/10/15/30، شاشة منبّه كاملة، تكرار يومي/أسبوعي/شهري/سنوي، أيام محددة، تواريخ مستثناة، سجل كامل | المحرر / شاشة المنبّه / سجل التنبيهات |
| اسأل DAYFLOW (محرك قواعد محلي) | المزيد |
| أنقذ يومي (معاينة قبل الاعتماد) | الرئيسية / المزيد / خريطة اليوم |
| خريطة اليوم MY DAY TIMELINE مع الفراغات والتداخل والسحب لإعادة الترتيب | من الرئيسية والتقويم |
| العادات: موعد، أيام، تسجيل، سلسلة، نسبة استمرار، سجل شهري، هدف أسبوعي، أمثلة اختيارية | العادات |
| وضع التركيز: بومودورو قابل للتخصيص، استراحات، تسجيل الجلسات، أصوات خلفية مولّدة محلياً | المزيد |
| ذاكرة الإنجاز، الإحصائيات، التوازن الحياتي | المزيد |
| التقويم الشهري/الأسبوعي/اليومي مع الهجري الاختياري | التقويم |
| الإضافة بالصوت أو بجملة مكتوبة | المحرر / الرئيسية |
| ودجت، اختصارات سريعة (ضغط مطول على الأيقونة)، إشعارات المهام والعادات والملخص والإنجاز | النظام |
| الإعدادات الكاملة، صفحة حالة الصلاحيات، نسخ احتياطي مشفّر، استعادة، تصدير/استيراد JSON، حذف كل البيانات | الإعدادات |
