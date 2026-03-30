package com.twitter.server

import com.twitter.finagle.filter.OffloadFilter
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.Http
import com.twitter.finagle.Service
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import java.util.logging.StreamHandler
import org.slf4j.bridge.SLF4JBridgeHandler
import scala.collection.mutable
import org.scalatest.funsuite.AnyFunSuite

// Pre-install SLF4JBridgeHandler at class-loading time.
// Slf4jBridgeUtility checks isInstalled before installing; by pre-installing
// here we ensure it skips its install+info() call, which is what triggers the
// SLF4J->JUL->SLF4J infinite loop when slf4j-jdk14 is on the classpath.
// The handler is removed again inside the slf4j-jdk14 Logging trait body.
private object Slf4jBridgeLoopGuard {
  SLF4JBridgeHandler.removeHandlersForRootLogger()
  SLF4JBridgeHandler.install()
}

/** Test TwitterServer which overrides the admin.port to localhost ephemeral port */
class TestTwitterServer extends com.twitter.server.slf4j.jdk14.AbstractTwitterServer {
  override val defaultAdminPort = 0

  val bootstrapSeq: mutable.ArrayBuffer[Symbol] = mutable.ArrayBuffer.empty[Symbol]

  override def main(): Unit = {
    bootstrapSeq += 'Main
  }

  init {
    bootstrapSeq += 'Init
  }

  premain {
    bootstrapSeq += 'PreMain
  }

  onExit {
    bootstrapSeq += 'Exit
  }

  postmain {
    bootstrapSeq += 'PostMain
  }
}

class MockExceptionHandler extends Service[Request, Response] {
  val pattern = "/exception_please.json"
  def apply(req: Request): Future[Response] = {
    throw new Exception("test exception")
  }
}

class HandlersTest extends AnyFunSuite {

  // Force the guard to initialise before any TestTwitterServer is constructed.
  Slf4jBridgeLoopGuard

  test("Exceptions thrown in handlers include stack traces") {
    val twitterServer: TwitterServer = new TestTwitterServer {
      val mockExceptionHandler = new MockExceptionHandler

      override protected def configureAdminHttpServer(server: Http.Server): Http.Server = {
        // TODO: with offload filter there appears to be a race condition in the test.
        super.configureAdminHttpServer(server).configured(OffloadFilter.Param.Disabled)
      }

      override def main(): Unit = {
        addAdminRoute(
          AdminHttpServer.mkRoute(
            path = "/exception_please.json",
            handler = mockExceptionHandler,
            alias = "mockExceptionHandler",
            group = None,
            includeInIndex = false
          )
        )

        val port = adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort

        val log = Logger.getLogger(getClass.getName)
        val stream = new ByteArrayOutputStream
        val handler = new StreamHandler(stream, new SimpleFormatter)
        log.addHandler(handler)

        val client = Http.client.newService(s"localhost:$port")
        stream.reset()
        Await.ready {
          client(Request("/exception_please.json"))
        }
        assert(stream.toString.contains("at com.twitter.server.MockExceptionHandler.apply"))
      }
    }
    twitterServer.main(args = Array.empty[String])
  }
}
