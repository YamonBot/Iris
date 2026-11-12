"""Native JVM lock/file regressions; SQLCipher/platform calls are boundary fixtures.

Supply JAVA_HOME, KOTLIN_COMPILER_CLASSPATH and KOTLIN_STDLIB_JAR from an existing
JDK/Kotlin toolchain. No downloads, Android SDK, APK, database or credentials.
"""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SQL = '''package net.zetetic.database.sqlcipher
class Cursor: AutoCloseable { fun moveToFirst() = true; override fun close() {} }
class SQLiteDatabase {
 var closed=false
 var closeFailure=false
 fun close() { if(closeFailure) throw IllegalStateException("fixture close failed"); closed=true }
 fun rawQuery(query:String,args:Array<String>?):Cursor=Cursor()
 companion object {
  const val OPEN_READONLY=1
  const val NO_LOCALIZED_COLLATORS=2
  fun openDatabase(path:String,key:ByteArray,factory:Any?,flags:Int,handler:Any?):SQLiteDatabase=SQLiteDatabase()
 }
}
'''
PLATFORM = '''package party.qwer.iris
import java.io.File
object SqlCipherNative { var loaded=false; fun ensureLoaded()=loaded }
object WorkDirs {
 val root=File(System.getProperty("iris.fixture.root"))
 fun sub(name:String):File?=File(root,name).apply{mkdirs()}
}
object PathUtils { fun getAppPath()=File(WorkDirs.root.parentFile,"app").absolutePath }
'''
CHECK = '''package party.qwer.iris
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.*
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.*
import java.util.concurrent.locks.ReentrantReadWriteLock
fun main(args:Array<String>) {
 val snapshot=CryptoDbSnapshot("fixture","fixture.db","crypto")
 fun set(name:String,value:Any) { snapshot.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(snapshot,value) }
 val lock=snapshot.javaClass.getDeclaredField("rw").apply{isAccessible=true}.get(snapshot) as ReentrantReadWriteLock
 when(args[0]) {
  "recovery" -> {
   set("db",SQLiteDatabase());set("openedAtMs",System.currentTimeMillis())
   val task=FutureTask<Int?> { snapshot.use({byteArrayOf(1)}) { throw IllegalStateException("fixture") } }
   Thread(task).apply{isDaemon=true}.start()
   check(task.get(2,TimeUnit.SECONDS)==null)
   check(lock.readLockCount==0 && !lock.isWriteLocked)
   set("db",SQLiteDatabase());set("openedAtMs",System.currentTimeMillis())
   check(snapshot.use({byteArrayOf(1)}) {42}==42 && lock.readLockCount==0)
  }
  "refresh" -> {
   SqlCipherNative.loaded=true
   val source=File(PathUtils.getAppPath(),"databases/fixture.db").apply{parentFile.mkdirs();writeText("new")}
   val working=File(WorkDirs.sub("crypto"),"fixture.db").apply{writeText("old")}
   val old=SQLiteDatabase();set("db",old);set("openedAtMs",0L)
   val task=FutureTask<Boolean>{snapshot.ensure{byteArrayOf(1)}}
   val thread=Thread(task).apply{isDaemon=true}
   lock.readLock().lock()
   try {
    thread.start()
    val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2)
    while(!lock.hasQueuedThread(thread) && working.readText()=="old" && System.nanoTime()<deadline) Thread.sleep(1)
    check(working.readText()=="old") {"Active reader's snapshot was overwritten"}
    check(!old.closed && lock.hasQueuedThread(thread)) {"Writer did not wait for the active reader"}
   } finally { lock.readLock().unlock();check(task.get(2,TimeUnit.SECONDS)) }
   check(old.closed && working.readText()==source.readText())
   check(lock.readLockCount==0 && !lock.isWriteLocked)
  }
  "short-copy" -> {
   val source=File(WorkDirs.root.parentFile,"source.db").apply{writeBytes(ByteArray(1024*1024){1})}
   val pipe=File(WorkDirs.root.parentFile,"snapshot.pipe")
   val first=CountDownLatch(1);val drain=CountDownLatch(1)
   val reader=FutureTask<Unit>{pipe.inputStream().use{input->check(input.read()==1);first.countDown();check(drain.await(2,TimeUnit.SECONDS));input.copyTo(OutputStream.nullOutputStream())};Unit}
   Thread(reader).apply{isDaemon=true}.start()
   val copy=snapshot.javaClass.getDeclaredMethod("copyInto",File::class.java,File::class.java).apply{isAccessible=true}
   val writer=FutureTask<Throwable?>{try{copy.invoke(snapshot,source,pipe);null}catch(e:InvocationTargetException){e.cause}}
   Thread(writer).apply{isDaemon=true}.start()
   try {
    check(first.await(2,TimeUnit.SECONDS));RandomAccessFile(source,"rw").use{it.setLength(0)}
   } finally {drain.countDown()}
   val failure=writer.get(2,TimeUnit.SECONDS);reader.get(2,TimeUnit.SECONDS)
   check(failure is EOFException) {"Truncated source was accepted as a complete copy"}
  }
  "close-failure" -> {
   SqlCipherNative.loaded=true
   File(PathUtils.getAppPath(),"databases/fixture.db").apply{parentFile.mkdirs();writeText("new")}
   val working=File(WorkDirs.sub("crypto"),"fixture.db").apply{writeText("old")}
   val old=SQLiteDatabase().apply{closeFailure=true};set("db",old);set("openedAtMs",0L)
   check(!snapshot.ensure{byteArrayOf(1)}) {"Failed refresh was reported ready"}
   var queried=false
   check(snapshot.use({byteArrayOf(1)}) {queried=true;42}==null)
   check(!queried && working.readText()=="old" && !old.closed) {"Failed close allowed snapshot mutation or stale read"}
   set("openedAtMs",System.currentTimeMillis())
   var attempts=0
   check(snapshot.use({byteArrayOf(1)}) {attempts++;throw IllegalStateException("fixture query failure")}==null)
   check(attempts==1 && !snapshot.ensure{byteArrayOf(1)} && working.readText()=="old")
   check(lock.readLockCount==0 && !lock.isWriteLocked)
  }
  else -> error("Unknown fixture")
 }
 println("PASS "+args[0])
}
'''


class CryptoDbSnapshotJvmTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        compiler = os.getenv("KOTLIN_COMPILER_CLASSPATH")
        stdlib = os.getenv("KOTLIN_STDLIB_JAR")
        java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.getenv("JAVA_HOME") else shutil.which("java")
        if not (java and compiler and stdlib):
            raise unittest.SkipTest("Requires explicit existing JDK/Kotlin toolchain")
        cls.folder = tempfile.TemporaryDirectory(prefix="iris-snapshot-jvm-")
        cls.root = Path(cls.folder.name)
        cls.addClassCleanup(cls.folder.cleanup)
        source = Path(__file__).with_name("CryptoDbSnapshot.kt").read_bytes()
        (cls.root / "CryptoDbSnapshot.kt").write_bytes(source)
        for name, value in [("Sql.kt", SQL), ("Platform.kt", PLATFORM), ("SnapshotCheck.kt", CHECK)]:
            (cls.root / name).write_text(value)
        cls.env = {k: os.environ[k] for k in ("PATH", "HOME", "TMPDIR") if k in os.environ}
        command = [java, "-Xmx256m", "-cp", compiler, "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler", "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", stdlib, "-d", str(cls.root / "classes"), *map(str, cls.root.glob("*.kt"))]
        result = subprocess.run(command, capture_output=True, text=True, timeout=35, env=cls.env)
        if result.returncode:
            raise AssertionError(result.stderr)
        cls.command = [java, "-Xmx96m", "-cp", os.pathsep.join([str(cls.root / "classes"), stdlib]), "party.qwer.iris.SnapshotCheckKt"]
        print("actual_source_sha256=" + hashlib.sha256(source).hexdigest(), flush=True)

    def check_case(self, name):
        with tempfile.TemporaryDirectory(prefix="fixture-", dir=self.root) as folder:
            root = Path(folder)
            if name == "short-copy":
                os.mkfifo(root / "snapshot.pipe", 0o600)
            result = subprocess.run([*self.command[:1], "-Diris.fixture.root=" + str(root / "work"), *self.command[1:], name], capture_output=True, text=True, timeout=6, env=self.env)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            self.assertIn("PASS " + name, result.stdout)

    def test_failed_query_releases_locks_before_recovery(self):
        self.check_case("recovery")

    def test_refresh_preserves_active_readers_snapshot(self):
        self.check_case("refresh")

    def test_failed_close_preserves_files_and_denies_stale_read(self):
        self.check_case("close-failure")

    @unittest.skipUnless(hasattr(os, "mkfifo"), "Requires a native Unix FIFO")
    def test_truncated_source_is_not_a_successful_copy(self):
        self.check_case("short-copy")


if __name__ == "__main__":
    unittest.main(verbosity=2)
