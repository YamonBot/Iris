# Read query boundary

The shared Android has company user0 and personal user10. A company Iris token
must not authorize opening another profile's database through SQL. The deployed
4fd5666 source and the current profile branch accepted arbitrary `/query` text
and passed it directly to a root-owned, read-write SQLite connection. This is a
source-level capability gap; no production cross-profile query was attempted and
no personal message was read to demonstrate it.

The existing database boundary now admits bounded single SELECT statements only,
before database access, and enables SQLite's native `query_only` after attaching
the three databases selected from the validated startup profile. ATTACH, DETACH,
PRAGMA changes, writes, multi-statements and WITH commands are rejected. Values
must use the existing bind array; a literal semicolon is deliberately outside
this API's contract. This is a restricted read capability, not a SQL parser.
Current product transport queries use SELECT and bind parameters; typed reply
commands continue through Kakao's native sending path, not database writes.

The API guard is necessary in addition to query_only: SQLite query_only does not
by itself prohibit ATTACH. Root/ADB, process replacement and access to the Android
host remain separate administrator powers; this change does not defeat them.
Guest boot, exact-profile data access and rejection must be requalified in the
isolated restore before a new APK is promoted. Do not change the runtime APK pin
or claim that the running company/private Iris has this fix from source tests.

Native engine reference:
https://www.sqlite.org/pragma.html#pragma_query_only
