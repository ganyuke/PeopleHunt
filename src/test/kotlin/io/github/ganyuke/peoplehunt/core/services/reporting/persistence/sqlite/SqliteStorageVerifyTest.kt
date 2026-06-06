package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertFailsWith

class SqliteStorageVerifyTest {
  @Test
  fun verifyStorage_completesWithoutError() {
    val dir = Files.createTempDirectory("ph-health")
    val storage = SqliteStorage(dir, ReportJson.instance)
    storage.verifyStorage()
  }

  @Test
  fun verifyStorage_throwsWhenHealthDbCannotBeCreated() {
    val blocker = Files.createTempFile("ph-health-block", ".dat")
    val storage = SqliteStorage(blocker, ReportJson.instance)
    assertFailsWith<Exception> { storage.verifyStorage() }
  }

  @Test
  fun requireJsonbHealthProbe_failsWhenTableEmpty() {
    DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
      conn.createStatement().execute("CREATE TABLE t (data JSONB)")
      val error = assertFailsWith<IllegalStateException> {
        SqliteStorage.requireJsonbHealthProbe(conn)
      }
      assert(error.message!!.contains("no rows"))
    }
  }

  @Test
  fun requireJsonbHealthProbe_failsWhenProbeMissingFromPayload() {
    DriverManager.getConnection("jdbc:sqlite::memory:").use { conn ->
      conn.createStatement().execute("CREATE TABLE t (data JSONB)")
      conn.prepareStatement("INSERT INTO t VALUES (jsonb('{\"other\":true}'))").use { it.executeUpdate() }
      val error = assertFailsWith<IllegalStateException> {
        SqliteStorage.requireJsonbHealthProbe(conn)
      }
      assert(error.message!!.contains("JSONB round-trip failed"))
    }
  }
}
