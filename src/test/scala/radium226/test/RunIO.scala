package radium226.test

import cats.effect.{ContextShift, IO, Timer}
import org.scalatest.Assertion

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

trait RunIO {

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
    program
      .timeout(timeoutDuration)
      .unsafeRunSync()
  }

}
