package de.thermo.lueftung

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class HistoryDatabase(context:Context,name:String="thermo-history.db"):SQLiteOpenHelper(context,name,null,3) {
    override fun onCreate(db:SQLiteDatabase) {
        db.execSQL("CREATE TABLE samples(room TEXT NOT NULL,at INTEGER NOT NULL,t REAL NOT NULL,rh REAL NOT NULL,dp REAL NOT NULL,source TEXT NOT NULL,PRIMARY KEY(room,at))")
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY AUTOINCREMENT,room TEXT NOT NULL,type TEXT NOT NULL,start INTEGER NOT NULL,end INTEGER,source TEXT NOT NULL,minutes INTEGER NOT NULL DEFAULT 0,t REAL,rh REAL,dp REAL,note TEXT NOT NULL DEFAULT '',deadline INTEGER,alarm INTEGER NOT NULL DEFAULT 0,elapsedDeadline INTEGER,boot INTEGER,endT REAL,endRh REAL,endDp REAL)")
        db.execSQL("CREATE INDEX event_room_time ON events(room,start)")
        db.execSQL("CREATE TABLE contacts(sensor TEXT PRIMARY KEY,room TEXT NOT NULL,type TEXT NOT NULL,opened INTEGER NOT NULL,at INTEGER NOT NULL,source TEXT NOT NULL)")
    }
    override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int) {
        if(oldVersion<3) { db.execSQL("ALTER TABLE events ADD COLUMN endT REAL");db.execSQL("ALTER TABLE events ADD COLUMN endRh REAL");db.execSQL("ALTER TABLE events ADD COLUMN endDp REAL") }
        if(oldVersion<2) { db.execSQL("ALTER TABLE events ADD COLUMN elapsedDeadline INTEGER");db.execSQL("ALTER TABLE events ADD COLUMN boot INTEGER") }
    }
    fun record(room:String,reading:RoomClimateReading) {
        val values=ContentValues().apply { put("room",room);put("at",reading.measuredAtMillis);put("t",reading.temperatureC);put("rh",reading.relativeHumidityPercent);put("dp",reading.dewPointC);put("source",if(reading.isDemo) "Demo" else "Sensor") }
        writableDatabase.insertWithOnConflict("samples",null,values,SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun samples(room:String,start:Long,end:Long):List<ClimateSample> = readableDatabase.rawQuery(
        "SELECT at,t,rh,dp,source FROM samples WHERE room=? AND at>=? AND at<=? ORDER BY at",arrayOf(room,start.toString(),end.toString())).use { c ->
        buildList { while(c.moveToNext()) add(ClimateSample(c.getLong(0),c.getDouble(1),c.getDouble(2),c.getDouble(3),c.getString(4))) }
    }
    fun latest(room:String):ClimateSample?=readableDatabase.rawQuery("SELECT at,t,rh,dp,source FROM samples WHERE room=? ORDER BY at DESC LIMIT 1",arrayOf(room)).use { c ->
        if(c.moveToFirst()) ClimateSample(c.getLong(0),c.getDouble(1),c.getDouble(2),c.getDouble(3),c.getString(4)) else null
    }
    fun begin(room:String,type:String,source:String,at:Long,minutes:Int=0,reading:RoomClimateReading?=null,note:String="",deadline:Long?=null,elapsedDeadline:Long?=null,boot:Int?=null):Long {
        val values=ContentValues().apply { put("room",room);put("type",type);put("source",source);put("start",at);put("minutes",minutes);put("note",note)
            deadline?.let { put("deadline",it) };elapsedDeadline?.let { put("elapsedDeadline",it) };boot?.let { put("boot",it) };reading?.let { put("t",it.temperatureC);put("rh",it.relativeHumidityPercent);put("dp",it.dewPointC) } }
        return writableDatabase.insertOrThrow("events",null,values)
    }
    fun end(id:Long,at:Long,note:String="",reading:RoomClimateReading?=null) {
        val values=ContentValues().apply { put("end",at); if(note.isNotBlank()) put("note",note);reading?.let { put("endT",it.temperatureC);put("endRh",it.relativeHumidityPercent);put("endDp",it.dewPointC) } }
        if(reading?.isDemo==true) {
            val source=readableDatabase.rawQuery("SELECT source FROM events WHERE id=?",arrayOf(id.toString())).use { if(it.moveToFirst()) it.getString(0) else "" }
            if(!source.contains("Demo")) values.put("source",source+" · Ende Klima Demo")
        }
        writableDatabase.update("events",values,"id=? AND end IS NULL AND start<=?",arrayOf(id.toString(),at.toString()))
    }
    fun activeEvent(room:String,type:String):Long?=readableDatabase.rawQuery("SELECT id FROM events WHERE room=? AND type=? AND end IS NULL ORDER BY start DESC LIMIT 1",arrayOf(room,type)).use { if(it.moveToFirst()) it.getLong(0) else null }
    fun events(room:String,start:Long,end:Long):List<ClimateEvent> = readableDatabase.rawQuery(
        "SELECT id,room,type,start,end,source,minutes,t,rh,dp,note,endT,endRh,endDp FROM events WHERE room=? AND start<=? AND (end IS NULL OR end>=?) ORDER BY start",arrayOf(room,end.toString(),start.toString())).use { c ->
        buildList { while(c.moveToNext()) add(ClimateEvent(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),if(c.isNull(4)) null else c.getLong(4),c.getString(5),c.getInt(6),if(c.isNull(7)) null else c.getDouble(7),if(c.isNull(8)) null else c.getDouble(8),if(c.isNull(9)) null else c.getDouble(9),c.getString(10),if(c.isNull(11)) null else c.getDouble(11),if(c.isNull(12)) null else c.getDouble(12),if(c.isNull(13)) null else c.getDouble(13))) }
    }
    fun activeTimers():List<ActiveVentilationTimer> = readableDatabase.rawQuery("SELECT id,room,start,deadline,source,alarm,elapsedDeadline,boot FROM events WHERE type='VENTILATION' AND end IS NULL AND deadline IS NOT NULL",null).use { c ->
        buildList { while(c.moveToNext()) add(ActiveVentilationTimer(c.getLong(0),c.getString(1),c.getLong(2),c.getLong(3),c.getString(4),c.getInt(5)!=0,if(c.isNull(6)) null else c.getLong(6),if(c.isNull(7)) null else c.getInt(7))) }
    }
    fun markAlarm(id:Long) { writableDatabase.execSQL("UPDATE events SET alarm=1 WHERE id=?",arrayOf(id)) }
    /** Duplicate/late sensor packets cannot reverse a newer contact state. */
    @Synchronized fun contact(sensor:String,room:String,type:String,open:Boolean,at:Long,source:String):Boolean {
        val previous=readableDatabase.rawQuery("SELECT at,opened,room,type FROM contacts WHERE sensor=?",arrayOf(sensor)).use { c -> if(c.moveToFirst()) { require(c.getString(2)==room && c.getString(3)==type) { "Contact identity must keep its assigned room/type" }; c.getLong(0) to (c.getInt(1)==1) } else null }
        if(previous!=null && at<=previous.first) return false
        val values=ContentValues().apply { put("sensor",sensor);put("room",room);put("type",type);put("opened",if(open) 1 else 0);put("at",at);put("source",source) }
        writableDatabase.insertWithOnConflict("contacts",null,values,SQLiteDatabase.CONFLICT_REPLACE)
        return previous==null && open || previous!=null && previous.second!=open
    }
    fun contactOpen(sensor:String):Boolean=readableDatabase.rawQuery("SELECT opened FROM contacts WHERE sensor=?",arrayOf(sensor)).use { it.moveToFirst() && it.getInt(0)==1 }
    fun openDoors(room:String):Int=readableDatabase.rawQuery("SELECT count(*) FROM contacts WHERE room=? AND type='DOOR' AND opened=1",arrayOf(room)).use { it.moveToFirst();it.getInt(0) }
    fun openWindows(room:String):Int=readableDatabase.rawQuery("SELECT count(*) FROM contacts WHERE room=? AND type='WINDOW' AND opened=1",arrayOf(room)).use { it.moveToFirst();it.getInt(0) }
}
