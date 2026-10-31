# تقرير الاختبارات

تاريخ التشغيل: 31 أكتوبر 2026 · بيئة التطوير: Linux، OpenJDK 21، Kotlin 2.1.21.

## الملخص الصادق

| المجموعة | العدد | الحالة |
|---|---|---|
| `core` – اختبارات JVM (المحرك، الجدولة، التكرار، التنبيهات، الصوت، النسخ الاحتياطي، العادات) | **65** | ✅ **شُغّلت فعلياً: 65 ناجحة / 0 فاشلة** – المخرجات الخام في [`core-test-run.txt`](core-test-run.txt) |
| `app` – اختبارات الجهاز (`androidTest`) | **22** | ✍️ مكتوبة وجاهزة، **لم تُشغَّل**: بيئة التطوير محجوبة عن Google Maven و Android SDK |
| فحص نحوي لكل ملفات Kotlin (58 ملف تطبيق + الاختبارات) عبر ktlint | كل الملفات | ✅ لا أخطاء تحليل نحوي (توجد ملاحظات تنسيق فقط) |
| ترجمة وحدة `app` وبناء APK | — | ❌ غير ممكن في بيئة التطوير (لا Android SDK). **يجب** تشغيل `./gradlew assembleDebug` على جهازك؛ إن ظهر خطأ ترجمة فأرسله لي وسأصلحه |
| تغطية `strings.xml` | 490 نصاً | ✅ فُحص آلياً: كل `R.string.*` مستخدم معرَّف، بلا تكرار، وعدد المعاملات `%1$s` مطابق للاستدعاءات |

> نجاح البناء وحده لا يكفي (كما طلبت). لذلك وُضعت كل الخوارزميات الحرجة في `core` حيث تُختبر بحالات طبيعية وغير طبيعية، ووُضعت اختبارات الجهاز لسلوك Android الحقيقي (AlarmManager، Room، إعادة التشغيل، المنطقة الزمنية).

ملاحظة منهجية: تعذّر تنزيل مكتبة JUnit في بيئة التطوير، فشُغّلت اختبارات `core` (المكتوبة بـ JUnit 4 القياسية) عبر مشغّل بسيط ينفّذ كل دالة `@Test` ويعدّ النتائج. على جهازك يشغّلها Gradle بـ JUnit الحقيقية: `./gradlew :core:test`.

## اختبارات core المنفّذة (65)

| المتطلب | الاختبارات |
|---|---|
| التكرار (يومي/كل يومين، أسبوعي بأيام محددة، استثناءات، شهري يوم 31، سنوي، تاريخ انتهاء، ترميز القاعدة) | `codecRoundTrip` `dailyEveryTwoDays` `weeklySpecificDaysWithException` `monthlyOn31stClampsToMonthEnd` `untilStopsRecurrence` `yearlyOccasion` |
| جدولة التنبيهات | `reminderBeforeStart` `pastReminderOfOneOffTaskIsNotScheduled` `completedOrCancelledTaskHasNoAlarm` `recurringReminderMovesToNextOccurrence` |
| إعادة الجدولة بعد تعديل الوقت | `rescheduleAfterTaskTimeChange` |
| اختلاف المنطقة الزمنية والتوقيت الصيفي | `timezoneChangeKeepsWallClockTime` `dstGapMovesForward` `dstOverlapRingsOnceAtEarlierOffset` `dailyAlarmAcrossDstKeepsLocalTime` |
| منع التنبيهات المكررة، التكرار عند عدم الاستجابة، التأجيل | `duplicateRemindersCollapse` `repeatIfUnansweredRespectsLimit` `snoozeAndRequestCodesAreStable` |
| الحالة «متأخرة» | `overdueIsDerived` |
| اكتشاف التعارض | `detectsOverlap` `adjacentBlocksDoNotConflict` `completedTasksIgnoredInConflicts` |
| «ماذا أفعل الآن؟» | `urgentOverdueWinsAndBlockedExcluded` `parentWithOpenSubtasksIsSkipped` `whatNowRespectsRestPeriod` `whatNowPreparesForImminentAppointment` `whatNowFreeTimeStopsAtNextAppointment` |
| أنقذ يومي / إعادة ترتيب اليوم | `plansAroundFixedAndRest` `reportsHonestlyWhenTimeIsNotEnough` `importantTaskThatDoesNotFitIsAtRisk` `splitsLongTasksIntoParts` `dependencyPlacedBeforeDependant` `overdueFromYesterdayIsCarriedIntoToday` `planIsDeterministic` `fullDayStress` (200 مهمة – امتلاء الجدول) |
| المهام المتكررة في الأيام | `recurringOccurrencesAppearOnTheirDays` |
| محرك الذكاء المحلي (الأسئلة العشرة من المواصفات) | `classifiesAllSpecQuestions` `engineAnswersFromRealDataOnly` `planTomorrowNeverChangesDataAndOffersReview` `studyHoursUsesHistory` `mostPostponedFromHistory` `unknownQuestionIsHonest` |
| VOICE TO TASK | `specExample` (جملة المواصفات حرفياً) `digitsAndHalfHour` `wordsWithHalfAndEvening` `quarterTo` `relativeTime` `ambiguousHourIsFlagged` `passedHourTodayRollsToEvening` `durationVariants` `priorityAndRecurrence` `plainTextWithoutTime` `emptyTitleNeedsConfirmation` `numericDate` |
| التصدير والاستيراد والتشفير | `jsonRoundTrip` `exportImportRoundTrip` `plainBackupRoundTrip` `encryptedBackupRoundTripAndWrongPassword` `tamperedFileIsRejected` `encryptionUsesRandomSalt` |
| العادات | `streakCountsBackFromYesterdayWhenTodayPending` `streakSkipsUnscheduledDays` `consistencyIgnoresPendingToday` |
| التنسيق العربي والهجري | `arabicFormatting` `hijriConversionAndAdjustment` |

## اختبارات الجهاز المكتوبة (22) – تُشغَّل بـ `./gradlew connectedDebugAndroidTest`

| المتطلب | الاختبار |
|---|---|
| إضافة مهمة (+وسوم +تنبيه +سجل) | `addTaskStoresEverythingAndSchedulesAlarm` |
| رفض المدخلات غير الصالحة | `validationRejectsBadInput` |
| تعديل المهمة وإعادة جدولة التنبيه | `editingTimeReschedulesAlarm` `removingReminderCancelsItsAlarm` |
| حذف مهمة لها تنبيه | `deletingTaskCancelsAlarmsButKeepsHistory` `firedAlarmForDeletedTaskIsIgnored` |
| إكمال / تكرار / تأجيل / إلغاء / أرشفة / استنساخ | `completingOneOffTaskCancelsAlarm` `completingRecurringTaskMovesToNextOccurrence` `postponeCountsAndShifts` `cancelAndArchiveStopAlarms` `duplicateCopiesRemindersAndTags` |
| إعادة التشغيل (محاكاة فقدان منبّهات AlarmManager) | `rebootRescheduleRestoresAlarms` `rescheduleIsIdempotent` |
| تغيير المنطقة الزمنية على الجهاز | `timeZoneChangeKeepsLocalWallClock` |
| منع التسليم المكرر | `duplicateDeliveryIsIgnored` |
| اعتماد خطة «أنقذ يومي» مع تقسيم المهام | `applyingPlanSplitsLongTasksIntoSubtasks` |
| امتلاء جدول المهام (300 مهمة) | `manyTasksStayResponsive` |
| النسخ الاحتياطي والاستعادة والحذف الكامل | `exportImportRoundTripPreservesEverything` `encryptedBackupNeedsPassword` `deleteEverythingWipesAllTables` |
| قاعدة البيانات والترحيل | `MigrationTest.allMigrationsValidate` |
| الواجهات الأساسية | `MainUiTest.dashboardShowsCoreCards` |

## حالات لا يمكن أتمتتها بالكامل (اختبار يدوي)
إيقاف الهاتف فعلياً، رفض الصلاحيات من إعدادات النظام، إغلاق التطبيق قسراً، تغيير التاريخ يدوياً، العمل دون إنترنت (التطبيق لا يملك صلاحية الإنترنت أصلاً)، وسلوك شركات مثل Xiaomi/Huawei. القائمة التفصيلية في [`ALARMS.md`](ALARMS.md#قائمة-اختبار-يدوي-على-جهاز-حقيقي).

## مخاطر معروفة
- لم يُترجم كود `app` في بيئة التطوير؛ كُتب بعناية مقابل واجهات Compose BOM 2024.12 / Room 2.6.1 / core-ktx 1.15 وفُحص نحوياً، لكن خطأ ترجمة بسيط وارد. تصحيحه عادة سطر واحد.
- الأيقونات من `material-icons-extended` المعروفة منذ إصدارات قديمة لتقليل خطر اختلاف الأسماء.
- التعرّف الصوتي دون إنترنت يعتمد على محرك الجهاز وحزمة العربية المنزّلة؛ التطبيق يوضح ذلك ويوفر الكتابة دائماً (تحليل الجملة نفسه محلي 100٪).
