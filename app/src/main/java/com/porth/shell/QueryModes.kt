package com.porth.shell

internal enum class QueryMode(val bridgeValue: String) {
    ALL("all"),
    RUN("run"),
    INSERT("insert"),
    EXEC("exec");

    companion object {
        fun fromBridge(value: String): QueryMode =
            entries.firstOrNull { it.bridgeValue == value }
                ?: throw IllegalArgumentException("Unknown query mode: $value")
    }
}

internal enum class DrizzleMethod(val bridgeValue: String) {
    RUN("run"),
    ALL("all"),
    VALUES("values"),
    GET("get");

    companion object {
        fun fromBridge(value: String): DrizzleMethod =
            entries.firstOrNull { it.bridgeValue == value }
                ?: throw IllegalArgumentException("Unknown Drizzle method: $value")
    }
}

internal enum class DrizzleResultType {
    MULTIPLE_ROWS,
    FIRST_ROW,
}
