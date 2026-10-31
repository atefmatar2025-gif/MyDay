package com.myday.app.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter fun priorityToInt(p: Priority): Int = p.level
    @TypeConverter fun intToPriority(v: Int): Priority = Priority.fromLevel(v)
    @TypeConverter fun repeatToString(r: RepeatType): String = r.name
    @TypeConverter fun stringToRepeat(v: String): RepeatType = RepeatType.fromName(v)
}
