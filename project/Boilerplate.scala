import sbt._
import Keys._

object Boilerplate {

  val generateBoilerplate = TaskKey[Seq[File]]("generate-boilerplate", "Generates boilerplate from template files")

  val extraSettings = seq(
    sourceDirectory in generateBoilerplate <<= (sourceDirectory in Compile) / "boilerplate",

    target in generateBoilerplate <<= (sourceManaged in Compile),

    generateBoilerplate <<= (streams, sourceDirectory in generateBoilerplate, target in generateBoilerplate) map generateFromTemplates,

    (sourceGenerators in Compile) <+= generateBoilerplate,
    (managedSourceDirectories in Compile) <+= target in generateBoilerplate,

    // watch sources support
    includeFilter in generateBoilerplate := "*.template",
    excludeFilter in generateBoilerplate <<= excludeFilter in Global,
    watch(sourceDirectory in generateBoilerplate, includeFilter in generateBoilerplate, excludeFilter in generateBoilerplate),

    // add managed sources to the packaged sources
    mappings in (Compile, packageSrc) <++=
      (sourceManaged in Compile, managedSources in Compile) map { (base, srcs) =>
        (srcs x (Path.relativeTo(base) | Path.flat))
      }
  )

 def generateFromTemplates(streams: TaskStreams, sourceDir: File, targetDir: File): Seq[File] = {
   val files = sourceDir ** "*.template"

   def changeExtension(f: File): File = {
     val (ext, name) = f.getName.reverse.span(_ != '.')
     new File(f.getParent, name.reverse.toString+"scala")
   }

   val mapping = (files x rebase(sourceDir, targetDir)).map {
     case (orig, target) => (orig, changeExtension(target))
   }

   mapping foreach {
     case (templateFile, target) =>
       if (templateFile.lastModified > target.lastModified) {
         streams.log.info("Generating '%s'" format target.getName)
         val template = IO.read(templateFile)
         IO.write(target, generateFromTemplate(template))
       } else
         streams.log.debug("Template '%s' older than target. Ignoring." format templateFile.getName)
   }

   mapping.map(_._2)
 }

 def watch(sourceDirKey: SettingKey[File], filterKey: SettingKey[FileFilter], excludeKey: SettingKey[FileFilter]) =
   watchSources <++= (sourceDirKey, filterKey, excludeKey) map descendents
 def descendents(sourceDir: File, filt: FileFilter, excl: FileFilter) =
   sourceDir.descendantsExcept(filt, excl).get

  val ExpandReplacement = """(?s:\*\{(.*)\}\*)""".r
  def generateFromTemplate(template: String): String =
    ExpandReplacement.replaceAllIn(template, { m =>
      val format = m.group(1)
      (1 to 22).map(generate(format)).mkString
    })

  val SimpleReplacement = """\[\{(.*)\}\]""".r
  val EnumerateReplacement = """\{\{([^}]*)\}([^}]+)?\}""".r

  def generate(format: String)(idx: Int): String = {
    val numChars = idx.toString.length
    def formatNum0(num: Int) = num.formatted("%0"+numChars+"d")
    def formatNumSpace(num: Int) = num.formatted("%"+numChars+"d")

    def replaceSimple(pattern: String) =
      SimpleReplacement.replaceAllIn(pattern, { m =>
        val pattern = m.group(1)
        replaceInPattern(pattern)(idx)
      })

    def replaceExpand(pattern: String) =
      EnumerateReplacement.replaceAllIn(pattern, { m =>
        val pattern = m.group(1)
        val separator = m.group(2) match {
          case null =>
            if (pattern.endsWith(", ") || pattern.contains("\n")) "" else ", "
          case sep => sep
        }

        (1 to idx).map(replaceInPattern(pattern)).mkString(separator)
      })
    def replaceInPattern(pattern: String)(idx: Int): String =
             // in likely identifiers replace by '04' etc.
      pattern.replaceAll("(?<!\\d)1(?!\\d)", formatNum0(idx))
             .replaceAll("(?<=\\w)(?<!\\d)0(?!\\d)", formatNum0(idx - 1))

             // in other places replace by ' 4' etc.
             .replaceAll("(?<!\\w)(?<!\\d)1(?!\\d)", formatNumSpace(idx))
             .replaceAll("(?<!\\w)(?<!\\d)0(?!\\d)", formatNumSpace(idx - 1))

    replaceExpand(replaceSimple(format))
  }
}