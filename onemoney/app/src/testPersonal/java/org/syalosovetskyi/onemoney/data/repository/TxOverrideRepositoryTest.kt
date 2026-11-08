package org.syalosovetskyi.onemoney.data.repository

import android.content.Context
import io.mockk.coEvery
import io.mockk.every
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.syalosovetskyi.onemoney.data.db.dao.TxOverrideDao
import org.syalosovetskyi.onemoney.data.db.entities.TransactionType
import org.syalosovetskyi.onemoney.data.db.entities.TxOverrideEntity
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Отдача правок операций на mono-flow — против настоящего HTTP-сервера, а не мока
 * соединения: проверять надо то, что реально уходит в сеть.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TxOverrideRepositoryTest {

    private val dao: TxOverrideDao = mockk(relaxed = true)
    private val settingsRepo: SettingsRepository = mockk(relaxed = true)
    private val context: Context = mockk(relaxed = true)

    private lateinit var server: ServerSocket
    private var responseCode = 200
    private var lastBody: String? = null
    private var requestCount = 0

    private val row = TxOverrideEntity(
        txId = 718_151_723_918_600_633L,
        note = "заказ підкрилка",
        categoryName = "Авто",
        txType = "EXPENSE",
        updatedAt = 1_700_000_000_000L,
        synced = false
    )

    // Свой мини-сервер на сокете: com.sun.net.httpserver в android-unit-тестах недоступен,
    // а проверять надо именно то, что уходит в сеть.
    @Before
    fun setUp() {
        server = ServerSocket(0)
        thread(isDaemon = true) {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        val input = socket.getInputStream()
                        // Заголовки и тело читаем БАЙТАМИ: Content-Length считает байты,
                        // а в note кириллица — посимвольное чтение ждало бы лишнего и
                        // рвало соединение.
                        val head = StringBuilder()
                        var contentLength = 0
                        while (!head.endsWith("\r\n\r\n")) {
                            val b = input.read()
                            if (b < 0) break
                            head.append(b.toChar())
                        }
                        head.lineSequence()
                            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                            ?.let { contentLength = it.substringAfter(":").trim().toInt() }

                        val body = ByteArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val n = input.read(body, read, contentLength - read)
                            if (n < 0) break
                            read += n
                        }
                        requestCount++
                        lastBody = String(body, 0, read, Charsets.UTF_8)

                        val payload = """{"status":"ok"}""".toByteArray(Charsets.UTF_8)
                        val crlf = "\r\n"
                        val header = ("HTTP/1.1 $responseCode TEST" + crlf +
                            "Content-Type: application/json" + crlf +
                            "Content-Length: " + payload.size + crlf +
                            "Connection: close" + crlf + crlf).toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().write(header + payload)
                        socket.getOutputStream().flush()
                        socket.shutdownOutput()
                    }
                } catch (_: Exception) {
                    // сокет закрыт в tearDown — цикл завершится по isClosed
                }
            }
        }

        val url = "http://127.0.0.1:${server.localPort}"
        every { settingsRepo.settings } returns
            flowOf(AppSettings(monoflowUrl = url, monoflowToken = "test"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun repo() = TxOverrideRepository(context, dao, settingsRepo)

    @Test
    fun `accepted edit is sent with category name and marked as synced`() = runTest {
        responseCode = 200
        coEvery { dao.getPending(any()) } returns listOf(row)
        coEvery { dao.pendingCount(any()) } returns 0

        val result = repo().pushPending()

        assertEquals(1, result.sent)
        assertEquals(0, result.failed)
        assertEquals(0, result.pendingLeft)
        coVerify { dao.markSynced(row.txId, row.updatedAt) }

        val sent = JSONObject(lastBody!!)
        assertEquals(row.txId, sent.getLong("id"))
        assertEquals("заказ підкрилка", sent.getString("note"))
        assertEquals("Авто", sent.getString("category"))   // имя, а не id
        assertEquals("EXPENSE", sent.getString("type"))
    }

    @Test
    fun `server failure does not burn an attempt and keeps the edit pending`() = runTest {
        responseCode = 500                                  // временный отказ
        coEvery { dao.getPending(any()) } returns listOf(row)
        coEvery { dao.pendingCount(any()) } returns 1

        val result = repo().pushPending()

        assertEquals(0, result.sent)
        assertEquals(1, result.failed)
        assertTrue("тяга данных должна быть заблокирована", result.pendingLeft > 0)
        coVerify(exactly = 0) { dao.markSynced(any(), any()) }
        coVerify(exactly = 0) { dao.markRejected(any(), any(), any()) }
    }

    @Test
    fun `bad request counts as an attempt so one broken edit cannot block sync forever`() = runTest {
        responseCode = 400                                  // правка неисправима
        coEvery { dao.getPending(any()) } returns listOf(row)
        coEvery { dao.pendingCount(any()) } returns 1

        val result = repo().pushPending()

        assertEquals(1, result.failed)
        coVerify { dao.markRejected(row.txId, row.updatedAt, any()) }
        coVerify(exactly = 0) { dao.markSynced(any(), any()) }
    }

    @Test
    fun `unconfigured server is reported as skipped, not as success`() = runTest {
        coEvery { dao.getPending(any()) } returns listOf(row)
        every { settingsRepo.settings } returns
            flowOf(AppSettings(monoflowUrl = "", monoflowToken = ""))

        val result = repo().pushPending()

        assertTrue(result.skipped)
        assertEquals(0, result.sent)
        assertEquals(0, requestCount)
    }

    @Test
    fun `locally created transaction is not sent to the server`() = runTest {
        repo().enqueue(
            txId = 42L,                                     // автоинкремент Room, сервер его не знает
            note = "локальна операція",
            categoryName = "Авто",
            type = TransactionType.EXPENSE
        )

        coVerify(exactly = 0) { dao.upsert(any()) }
    }
}
