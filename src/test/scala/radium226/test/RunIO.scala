package radium226.test

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.{Assertion, Assertions}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait RunIO {
  self: Assertions =>

  implicit val executionContext: ExecutionContext = ExecutionContext.global

  implicit val contextShiftForIO: ContextShift[IO] = IO.contextShift(executionContext)

  implicit val timerForIO: Timer[IO] = IO.timer(executionContext)

  def duringIO(backgroundProgram: IO[Unit])(program: IO[Assertion]): IO[Assertion] = {
    for {
      backgroundFiber <- backgroundProgram.start
      assertion <- program
      _ <- backgroundFiber.cancel
    } yield assertion
  }

  def runIO(program: IO[Assertion], timeoutDuration: FiniteDuration = 10 seconds): Assertion = {
    val assertion = program
      .timeout(timeoutDuration)
      .redeem({ throwable =>
        fail(throwable)
      }, identity)
      .unsafeRunSync()

    println(assertion)

    assertion
  }

}
