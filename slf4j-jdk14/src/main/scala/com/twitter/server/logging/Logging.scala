package com.twitter.server.logging

import com.twitter.app.App
import com.twitter.{logging => ctl}
import java.util.logging.Logger
import org.slf4j.bridge.SLF4JBridgeHandler

trait Logging extends ctl.Logging { self: App =>

  /** ensure when this trait is used there is a defined logger */
  override lazy val log: ctl.Logger = ctl.Logger(name)

  /**
   * Note: we are applying the `defaultFormatter` to any configured handlers on
   * the ROOT logger in the constructor to apply the desired formatting as early
   * as possible to logged statements.
   *
   * We also undo any SLF4JBridgeHandler (JUL -> SLF4J) installation here.
   * This module uses `slf4j-jdk14` which routes SLF4J -> JUL. The guard in
   * [[com.twitter.util.logging.Slf4jBridgeUtility]] that is supposed to prevent
   * installing the JUL -> SLF4J bridge when `slf4j-jdk14` is present checks for
   * `org.slf4j.impl.JDK14LoggerFactory`, a class that no longer exists in
   * SLF4J 2.x (replaced by `org.slf4j.jul.JDK14LoggerFactory`). As a result
   * the bridge is installed regardless, and the two bridges together form a
   * SLF4J -> JUL -> SLF4J -> ... infinite loop that causes a
   * [[java.lang.StackOverflowError]].
   *
   * This constructor body runs after [[com.twitter.util.logging.Slf4jBridge]]'s
   * constructor (Scala trait linearisation is left-to-right), so removing the
   * handler here is the earliest safe point.
   */
  {
    // Remove any installed JUL->SLF4J bridge to prevent the infinite loop
    // that arises when slf4j-jdk14 (SLF4J->JUL) and jul-to-slf4j (JUL->SLF4J)
    // are both on the classpath.
    if (SLF4JBridgeHandler.isInstalled) {
      SLF4JBridgeHandler.removeHandlersForRootLogger()
    }

    val formatter = defaultFormatter
    for (h <- Logger.getLogger("").getHandlers)
      h.setFormatter(formatter)
  }

  override def defaultFormatter: ctl.Formatter = new LogFormatter
}
