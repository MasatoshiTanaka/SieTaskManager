package jp.tdu.ics.masatoshi.sietaskmanager.app;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;


public class MySQLiteOpenHelper extends SQLiteOpenHelper {
    static final String DB = "taskDB";
    static final int DB_VERSION = 1;
    static final String CREATE_TABLE = "create table taskTable(task text, placeID text);";
    static final String DROP_TABLE = "drop table taskDB;";
    public MySQLiteOpenHelper(Context context) {
        super(context, DB, null, DB_VERSION);
    }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(DROP_TABLE);
        onCreate(db);
    }
}
