package app.cove.companion.security

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/** [DatabaseCopier] backed by SQLCipher's `sqlcipher_export`. */
object SqlCipherCopier : DatabaseCopier {
    init {
        System.loadLibrary("sqlcipher")
    }

    override fun copy(src: File, srcKey: String, dst: File, dstKey: String) {
        val db = open(src, srcKey)
        try {
            val version = db.rawQuery("PRAGMA user_version", emptyArray<String>()).use { it.moveToFirst(); it.getInt(0) }
            db.execSQL("ATTACH DATABASE '${dst.path.replace("'", "''")}' AS target KEY '${dstKey.replace("'", "''")}'")
            db.rawQuery("SELECT sqlcipher_export('target')", emptyArray<String>()).use { it.moveToFirst() }
            db.execSQL("PRAGMA target.user_version = $version")
            db.execSQL("DETACH DATABASE target")
        } finally {
            db.close()
        }
    }

    override fun tableCounts(db: File, key: String): Map<String, Long> {
        val conn = open(db, key)
        try {
            val tables = conn.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name", emptyArray<String>(),
            ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
            return tables.associateWith { t ->
                conn.rawQuery("SELECT COUNT(*) FROM `$t`", emptyArray<String>()).use { it.moveToFirst(); it.getLong(0) }
            }
        } finally {
            conn.close()
        }
    }

    private fun open(file: File, key: String): SQLiteDatabase =
        SQLiteDatabase.openDatabase(file.path, key, null, SQLiteDatabase.OPEN_READWRITE, null, null)
}
