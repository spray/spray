import sbt._
import Keys._
import Utils._


object SphinxSupport {

  val sphinxScript = SettingKey[String]("sphinx-script", "The location of the sphinx-build script")
  val sphinxCompile = TaskKey[Seq[File]]("sphinx-compile", "Compile Sphinx documentation into /site resources")

  val settings = seq(

    sphinxScript := sys.env.getOrElse("SPHINX_PATH", ""),

    sourceDirectory in sphinxCompile <<= baseDirectory { _.getParentFile / "docs" },

    target in sphinxCompile <<= (resourceManaged in Compile) / "sphinx",

    (resourceGenerators in Compile) <+= sphinxCompile,

    sphinxCompile <<= (sphinxScript, sourceDirectory in sphinxCompile,
      target in sphinxCompile, version, state) map compileSphinxSources,

    watchSources <++= (sourceDirectory in sphinxCompile) map { d => (d ***).get.map(_.getAbsoluteFile) }
  )

  def compileSphinxSources(script: String, sourceDir: File, targetDir: File, v: String, state: State) = {
    val log = colorLog(state)
    log("[YELLOW]Recompiling Sphinx sources...")
    if (script.nonEmpty) {
      val cmd = "%1$s -b json -d %3$s/doctrees -D version=%4$s -D release=%4$s %2$s %3$s/json".format(script, sourceDir, targetDir, v)
      log(cmd)
      val exitCode = Process(cmd) ! state.log
      if (exitCode != 0) sys.error("Error compiling sphinx sources")

      (targetDir / "json" ** ("*.fjson" | "*.svg" | "*.png")).get.map(_.getAbsoluteFile)
    } else {
      log("[YELLOW]Environment variable SPHINX_PATH (pointing to sphinx-build script) not set, skipping Sphinx run...")
      Nil
    }
  }

}
