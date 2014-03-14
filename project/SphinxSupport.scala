import sbt._
import Keys._
import scala.util.matching.Regex
import Utils._


object SphinxSupport {

  val sphinxScript = SettingKey[Option[File]]("sphinx-script", "The location of the sphinx-build script")
  val sphinxCompile = TaskKey[Seq[File]]("sphinx-compile", "Compile Sphinx documentation into /site resources")
  val generateDirectivesMapTarget = SettingKey[File]("generate-directives-map-file")
  val generateDirectivesMap = TaskKey[Seq[File]]("generate-directives-map", "Try to infer directives from source files")

  val settings = seq(

    sphinxScript := tryToLocateSphinx(),

    sourceDirectory in sphinxCompile <<= baseDirectory { _.getParentFile / "docs" },

    target in sphinxCompile <<= (resourceManaged in Compile) / "sphinx",

    (resourceGenerators in Compile) <+= sphinxCompile,

    sphinxCompile <<= (sphinxScript, sourceDirectory in sphinxCompile,
      target in sphinxCompile, version, state) map compileSphinxSources,

    watchSources <++= (sourceDirectory in sphinxCompile) map { d => (d ***).get.map(_.getAbsoluteFile) },

    generateDirectivesMapTarget <<= (resourceManaged in Compile) / "theme/js/directives-map.js",

    generateDirectivesMap <<= (scalaSource in Compile in Build.sprayRouting, generateDirectivesMapTarget).map(generateDirectivesMap),

    (resourceGenerators in Compile) <+= generateDirectivesMap
  )

  def compileSphinxSources(script: Option[File], sourceDir: File, targetDir: File, v: String, state: State) = {
    val log = colorLog(state)
    log("[YELLOW]Recompiling Sphinx sources...")
    if (script.nonEmpty) {
      val cmd = "%1$s -b json -d %3$s/doctrees -D version=%4$s -D release=%4$s %2$s %3$s/json".format(script.get, sourceDir, targetDir, v)
      log(cmd)
      val exitCode = Process(cmd) ! state.log
      if (exitCode != 0) sys.error("Error compiling sphinx sources")

      (targetDir / "json" ** ("*.fjson" | "*.svg" | "*.png")).get.map(_.getAbsoluteFile)
    } else {
      log("[YELLOW]sphinx-script not found, please point SPHINX_PATH to the sphinx-build script. Skipping Sphinx run...")
      Nil
    }
  }

  val wellKnownSphinxLocations =
    Seq(
      "/usr/bin/sphinx-build",               // Ubuntu
      "/usr/local/share/python/sphinx-build", // OS/X
      "/usr/local/bin/sphinx-build" // homebrew python and sphinx installed by pip
    )

  def tryToLocateSphinx(): Option[File] = {
    def existing(f: File): Option[File] = Some(f).filter(f => f.exists && f.isFile)

    val candidates = sys.env.get("SPHINX_PATH").toSeq ++ wellKnownSphinxLocations
    candidates.map(file).flatMap(existing).headOption
  }

  // a pattern for a directive definition of this kind: "def abc(): Directive ="
  val DirectiveDefinition = """(?ms:(?:def|val)\s+(\w+)(?=[:(\[])[^=]*?[:⇒]\s*(?:Directive|\w*Route)[^=\n)]*=)""".r
  // a pattern for directives explicitly marked like this: "/* directive */ def abc() ="
  val ExplicitDirectiveDefinition = """(?ms:/\* directive \*/\s*(?:def|val)\s+(\w+)(?=[:(\[]))""".r

  /**
   * Tries to extract a list of directive names from the source files at the well-known
   * location spray-routing/src/main/scala/spray/routing/directives/???Directives.scala
   */
  def generateDirectivesMap(routingSourceDir: File, targetFile: File): Seq[File] = {
    targetFile.getParentFile().mkdirs()
    val fw = new java.io.FileWriter(targetFile)

    case class DirectivesGroup(group: String, directives: Seq[String])

    def findDirectivesFiles: Seq[File] = {
      val packageDir = new File(routingSourceDir, "spray/routing/directives")
      packageDir.listFiles().filter(_.getName.endsWith("Directives.scala")).toSeq
    }
    def readDirectivesFile(file: File): DirectivesGroup = {
      val name = directivesGroupName(file.getName.dropRight(6) /* ".scala" */)

      val source = IO.read(file)
      def find(regexp: Regex): Seq[String] =
        regexp.findAllIn(source).matchData.map(_.group(1)).toSeq.filterNot(x => x.startsWith("_") || x == "apply")

      val directives = Seq(DirectiveDefinition, ExplicitDirectiveDefinition).flatMap(find).distinct
      DirectivesGroup(name, directives)
    }
    def directivesGroupName(name: String): String =
      name.split("(?=[A-Z])").drop(1).dropRight(1).map(_.toLowerCase).mkString("-")

    def writeGroup(group: DirectivesGroup): Unit = {
      fw.write("{\n\"group\": \"")
      fw.write(group.group)
      fw.write("\",\n\"entries\": \"")
      fw.write(group.directives.mkString(" "))
      fw.write("\"},\n")
    }
    val groups = findDirectivesFiles.map(readDirectivesFile).sortBy(_.group)

    fw.write("window.DirectivesMap = [\n")
    groups.foreach(writeGroup)
    fw.write("];\n")
    fw.close()
    Seq(targetFile)
  }
}
