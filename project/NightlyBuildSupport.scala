import java.text.SimpleDateFormat
import java.util.Date
import sbt._
import Keys._
import Utils._


object NightlyBuildSupport {
  val isNightly = sys.props.get("nightly.build") == Some("yes")

  def buildVersion(version: String) =
      if (isNightly) version.takeWhile(_ != '-') + new SimpleDateFormat("-yyyyMMdd").format(new Date)
      else version

  lazy val settings = seq(
    packagedArtifacts <<= (packagedArtifacts, crossTarget) map { (artifacts, dir) =>
      if (isNightly) {
        val file = dir / "commit"
        val commit = git("rev-parse", "HEAD")
        IO.write(file, """<a href="https://github.com/spray/spray/commit/%1$s">%1$s</a>""" format commit)
        artifacts + (Artifact(file.getName, "html", "html") -> file)
      } else artifacts
    }
  )
}
