package com.example.chordlab;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "ChordLab.db";
    private static final String TABLE_NAME = "users";
    private static final String COL_1 = "ID";
    private static final String COL_2 = "USERNAME";
    private static final String COL_3 = "EMAIL";
    private static final String COL_4 = "PASSWORD";

    // Bump version from 2 to 3
    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                "ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "USERNAME TEXT, " +
                "EMAIL TEXT, " +
                "PASSWORD TEXT, " +
                "INSTRUMENT TEXT, " +
                "DAILY_GOAL TEXT, " +
                "EXP INTEGER DEFAULT 0, " +
                "LEVEL INTEGER DEFAULT 1, " +
                "CHORDS_LEARNED INTEGER DEFAULT 0)");   // ← NEW COLUMN
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN EXP INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN LEVEL INTEGER DEFAULT 1");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN CHORDS_LEARNED INTEGER DEFAULT 0");
        }
    }

    public boolean insertUser(String username, String email, String password) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(COL_2, username);
        contentValues.put(COL_3, email);
        contentValues.put(COL_4, password);
        long result = db.insert(TABLE_NAME, null, contentValues);
        return result != -1;
    }

    public boolean checkUser(String username, String password) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM users WHERE USERNAME=? AND PASSWORD=?", new String[]{username, password});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public Cursor getUserData(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE USERNAME=?", new String[]{username});
    }

    public boolean checkUsernameExists(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE USERNAME = ?", new String[]{username});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public boolean checkEmailExists(String email) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_NAME + " WHERE EMAIL = ?", new String[]{email});
        boolean exists = cursor.getCount() > 0;
        cursor.close();
        return exists;
    }

    public boolean updateUserDetails(String username, String instrument, String goal) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues contentValues = new ContentValues();

        contentValues.put("instrument", instrument);
        contentValues.put("daily_goal", goal);

        int result = db.update("users", contentValues, "username = ?", new String[]{username});
        return result > 0;
    }

    public int[] getExpAndLevel(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(
                "SELECT EXP, LEVEL FROM " + TABLE_NAME + " WHERE USERNAME=?",
                new String[]{username}
        );
        int[] result = {0, 1};
        if (cursor != null && cursor.moveToFirst()) {
            result[0] = cursor.getInt(cursor.getColumnIndexOrThrow("EXP"));
            result[1] = cursor.getInt(cursor.getColumnIndexOrThrow("LEVEL"));
            cursor.close();
        }
        return result;
    }

    public int[] addExp(String username, int expToAdd) {
        int[] current = getExpAndLevel(username);
        int exp   = current[0] + expToAdd;
        int level = current[1];
        int didLevelUp = 0;

        while (exp >= 100) {
            exp -= 100;
            level++;
            didLevelUp = 1;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("EXP", exp);
        cv.put("LEVEL", level);
        db.update(TABLE_NAME, cv, "USERNAME=?", new String[]{username});

        return new int[]{exp, level, didLevelUp};
    }

    // ============================================
    // --- NEW: CHORDS LEARNED TRACKER METHODS ---
    // ============================================

    public int getChordsLearned(String username) {
        SQLiteDatabase db = this.getReadableDatabase();
        int chords = 0;
        Cursor cursor = db.rawQuery("SELECT CHORDS_LEARNED FROM " + TABLE_NAME + " WHERE USERNAME=?", new String[]{username});
        if (cursor != null && cursor.moveToFirst()) {
            chords = cursor.getInt(0);
            cursor.close();
        }
        return chords;
    }

    public void incrementChordsLearned(String username) {
        int currentChords = getChordsLearned(username);
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("CHORDS_LEARNED", currentChords + 1);
        db.update(TABLE_NAME, cv, "USERNAME=?", new String[]{username});
    }
}