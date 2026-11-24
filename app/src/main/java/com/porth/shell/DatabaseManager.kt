package com.porth.shell

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteCursor
import android.database.sqlite.SQLiteCursorDriver
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQuery
import android.database.sqlite.SQLiteStatement
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

class DatabaseManager(context: Context) {
    private sealed class SqlParam {
        data object NullValue : SqlParam()
        data class TextValue(val value: String) : SqlParam()
        data class LongValue(val value: Long) : SqlParam()
        data class DoubleValue(val value: Double) : SqlParam()
        data class BoolValue(val value: Boolean) : SqlParam()
    }

    private val helper = object : SQLiteOpenHelper(context, "porth.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {

        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

        }
    }

    @Synchronized
    fun query(sql: String, paramsJson: String?, mode: String): String =
        executeResponse {
            val params = parseParams(paramsJson)
            when (QueryMode.fromBridge(mode)) {
                QueryMode.ALL -> executeRows(sql, params)
                QueryMode.RUN -> executeUpdateDelete(sql, params)
                QueryMode.INSERT -> executeInsert(sql, params)
                QueryMode.EXEC -> executeExec(sql, params)
            }
        }

    @Synchronized
    fun queryRaw(sql: String, paramsJson: String?, method: String): String =
        executeResponse {
            val params = parseParams(paramsJson)
            when (DrizzleMethod.fromBridge(method)) {
                DrizzleMethod.RUN -> executeRawRun(sql, params)
                DrizzleMethod.ALL, DrizzleMethod.VALUES ->
                    executeRawRows(sql, params, DrizzleResultType.MULTIPLE_ROWS)
                DrizzleMethod.GET -> executeRawRows(sql, params, DrizzleResultType.FIRST_ROW)
            }
        }

    private fun executeResponse(block: () -> String): String =
        try {
            block()
        } catch (e: Exception) {
            JSONObject()
                .put("error", e.message ?: "unknown error")
                .toString()
        }

    private fun executeRows(sql: String, params: List<SqlParam>): String {
        queryWithTypedParams(helper.writableDatabase, sql, params).use { cursor ->
            val rows = JSONArray()
            while (cursor.moveToNext()) {
                val row = JSONObject()
                for (columnIndex in 0 until cursor.columnCount) {
                    val key = cursor.getColumnName(columnIndex)
                    row.put(key, cursorValue(cursor, columnIndex))
                }
                rows.put(row)
            }
            return rows.toString()
        }
    }

    private fun executeRawRows(sql: String, params: List<SqlParam>, resultType: DrizzleResultType): String {
        queryWithTypedParams(helper.writableDatabase, sql, params).use { cursor ->
            val rows = JSONArray()
            while (cursor.moveToNext()) {
                rows.put(cursorRow(cursor))
                if (resultType == DrizzleResultType.FIRST_ROW) {
                    break
                }
            }
            val payload = if (resultType == DrizzleResultType.FIRST_ROW) rows.optJSONArray(0) ?: JSONArray() else rows
            return JSONObject().put("rows", payload).toString()
        }
    }

    private fun executeRawRun(sql: String, params: List<SqlParam>): String {
        executeExec(sql, params)
        return JSONObject().put("rows", JSONArray()).toString()
    }

    private fun executeUpdateDelete(sql: String, params: List<SqlParam>): String =
        withStatement(sql, params) {
            val changes = executeUpdateDelete()
            JSONObject()
                .put("changes", changes)
                .put("lastInsertRowid", 0)
                .toString()
        }

    private fun executeInsert(sql: String, params: List<SqlParam>): String =
        withStatement(sql, params) {
            val rowId = executeInsert()
            val changes = if (rowId >= 0) 1 else 0
            JSONObject()
                .put("changes", changes)
                .put("lastInsertRowid", if (rowId >= 0) rowId else 0)
                .toString()
        }

    private inline fun <T> withStatement(
        sql: String,
        params: List<SqlParam>,
        block: SQLiteStatement.() -> T,
    ): T = helper.writableDatabase.compileStatement(sql).use { statement ->
        bindStatementParams(statement, params)
        statement.block()
    }

    private fun executeExec(sql: String, params: List<SqlParam>): String {
        if (params.isEmpty()) {
            helper.writableDatabase.execSQL(sql)
        } else {
            val bindArgs = params.map { it.toSqlArg() }.toTypedArray()
            helper.writableDatabase.execSQL(sql, bindArgs)
        }
        return JSONObject()
            .put("changes", 0)
            .put("lastInsertRowid", 0)
            .toString()
    }

    private fun parseParams(paramsJson: String?): List<SqlParam> {
        if (paramsJson.isNullOrBlank()) {
            return emptyList()
        }

        val parsed = JSONArray(paramsJson)
        return buildList(parsed.length()) {
            for (i in 0 until parsed.length()) {
                if (parsed.isNull(i)) {
                    add(SqlParam.NullValue)
                } else {
                    add(parseParamValue(parsed.get(i), i))
                }
            }
        }
    }

    private fun parseParamValue(value: Any, index: Int): SqlParam {
        return when (value) {
            is String -> SqlParam.TextValue(value)
            is Boolean -> SqlParam.BoolValue(value)
            is Int -> SqlParam.LongValue(value.toLong())
            is Long -> SqlParam.LongValue(value)
            is Short -> SqlParam.LongValue(value.toLong())
            is Byte -> SqlParam.LongValue(value.toLong())
            is Float -> SqlParam.DoubleValue(value.toDouble())
            is Double -> SqlParam.DoubleValue(value)
            is Number -> SqlParam.DoubleValue(value.toDouble())
            else -> throw IllegalArgumentException("Unsupported SQL parameter type at index $index")
        }
    }

    private fun queryWithTypedParams(
        db: SQLiteDatabase,
        sql: String,
        params: List<SqlParam>
    ): Cursor {
        return db.rawQueryWithFactory(
            { _: SQLiteDatabase, driver: SQLiteCursorDriver, editTable: String?, query: SQLiteQuery ->
                bindQueryParams(query, params)
                SQLiteCursor(driver, editTable, query)
            },
            sql,
            emptyArray(),
            null
        )
    }

    private fun bindStatementParams(
        statement: SQLiteStatement,
        params: List<SqlParam>
    ) {
        params.forEachIndexed { index, param ->
            val bindIndex = index + 1
            when (param) {
                SqlParam.NullValue -> statement.bindNull(bindIndex)
                is SqlParam.TextValue -> statement.bindString(bindIndex, param.value)
                is SqlParam.LongValue -> statement.bindLong(bindIndex, param.value)
                is SqlParam.DoubleValue -> statement.bindDouble(bindIndex, param.value)
                is SqlParam.BoolValue -> statement.bindLong(bindIndex, if (param.value) 1L else 0L)
            }
        }
    }

    private fun bindQueryParams(query: SQLiteQuery, params: List<SqlParam>) {
        params.forEachIndexed { index, param ->
            val bindIndex = index + 1
            when (param) {
                SqlParam.NullValue -> query.bindNull(bindIndex)
                is SqlParam.TextValue -> query.bindString(bindIndex, param.value)
                is SqlParam.LongValue -> query.bindLong(bindIndex, param.value)
                is SqlParam.DoubleValue -> query.bindDouble(bindIndex, param.value)
                is SqlParam.BoolValue -> query.bindLong(bindIndex, if (param.value) 1L else 0L)
            }
        }
    }

    private fun SqlParam.toSqlArg(): Any? {
        return when (this) {
            SqlParam.NullValue -> null
            is SqlParam.TextValue -> value
            is SqlParam.LongValue -> value
            is SqlParam.DoubleValue -> value
            is SqlParam.BoolValue -> if (value) 1L else 0L
        }
    }

    private fun cursorValue(cursor: Cursor, columnIndex: Int): Any {
        return when (cursor.getType(columnIndex)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(columnIndex)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(columnIndex)
            Cursor.FIELD_TYPE_BLOB -> Base64.encodeToString(cursor.getBlob(columnIndex), Base64.NO_WRAP)
            else -> cursor.getString(columnIndex)
        }
    }

    private fun cursorRow(cursor: Cursor): JSONArray {
        val row = JSONArray()
        for (columnIndex in 0 until cursor.columnCount) {
            row.put(cursorValue(cursor, columnIndex))
        }
        return row
    }
}
