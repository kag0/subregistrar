// build.sc
import mill._, scalalib._

object subregistrar extends ScalaModule {
  def scalaVersion = "2.13.1"

  def ivyDeps = Agg(
    ivy"com.typesafe.akka::akka-http:10.1.11",
    ivy"com.typesafe.play::play-json:2.8.1",
    ivy"de.heikoseeberger::akka-http-play-json:1.31.0",
    ivy"org.typelevel::cats-core:2.1.1"
  )
}
