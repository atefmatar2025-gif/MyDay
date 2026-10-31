# Room entities & DAO are handled by Room's own consumer rules.
# Keep receivers/services/activities referenced from the manifest (AGP keeps them automatically).
-keepattributes *Annotation*
# Glance: كائنات الإجراءات تُنشأ بالاسم
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
-keep class com.myday.app.widget.** { *; }
