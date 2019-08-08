package com.derek.billtracker;

import android.content.Context;
import android.util.Log;

import net.sqlcipher.Cursor;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteOpenHelper;

class DataHelper extends SQLiteOpenHelper {

    private static DataHelper instance;

    private final String TAG = MainActivity.class.getSimpleName();

    static final String DAY = "receiptDay";
    static final String MONTH = "receiptMonth";
    static final String YEAR = "receiptYear";
    static final String AMOUNT = "receiptAmount";
    static final String PATH = "filePath";

    private static final int DATABASE_VERSION = 2;
    private static final String DB_NAME = "billCalculatorStorage.db";
    static final String DB_TABLE = "receipts";

    private static final String CREATE_TABLE = "CREATE TABLE " + DB_TABLE +
            "(rule INTEGER PRIMARY KEY, " +
            DAY + " INTEGER, " +
            MONTH + " INTEGER, " +
            YEAR + " INTEGER, " +
            AMOUNT  + " REAL, " +
            PATH + " TEXT)";
    private static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + DB_TABLE;

    DataHelper(Context context) {
        super(context,DB_NAME,null,DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(CREATE_TABLE);
    }

    static synchronized DataHelper getInstance(Context context) {
        if (instance == null) {
            instance = new DataHelper(context);
        }
        return instance;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(SQL_DELETE_ENTRIES);
        // create new table
        onCreate(db);
    }

    String getTableAsString(SQLiteDatabase db) {
        Log.d(TAG, "getTableAsString called");
        String tableString = String.format("Table %s:\n", DataHelper.DB_TABLE);
        Cursor allRows  = db.rawQuery("SELECT * FROM " + DataHelper.DB_TABLE, null);
        if (allRows.moveToFirst() ){
            String[] columnNames = allRows.getColumnNames();
            do {
                for (String name: columnNames) {
                    tableString += String.format("%s: %s\n", name,
                            allRows.getString(allRows.getColumnIndex(name)));
                }
                tableString += "\n";

            } while (allRows.moveToNext());
        }

        return tableString;
    }
}
