package party.qwer.iris.features.security.application

/** A bounded SELECT capability, not a general-purpose SQL command interface. */
internal fun requireReadQuery(sql: String) {
    val statement = sql.trimStart()
    require(sql.length in 1..65_536 &&
        statement.takeWhile { it.isLetter() }.equals("SELECT", ignoreCase = true) &&
        ';' !in statement) {
        "Only a single SELECT query is permitted; use bind parameters for values"
    }
}
