import sbt._
import Keys._
import Utils._


trait DocSupport {
  def site: Project
  def sprayCaching: Project
  def sprayCan: Project
  def sprayClient: Project
  def sprayHttp: Project
  def sprayHttpx: Project
  def sprayIO: Project
  def sprayRouting: Project
  def sprayServlet: Project
  def sprayTestKit: Project
  def sprayUtil: Project

  val moveApiDocs = TaskKey[Unit]("move-api-docs", "Moves the module api documentation into the /site resources")

  val moveApiDocsSettings = seq(
    moveApiDocs <<= (resourceDirectory in (site, Compile), version, state,
      doc in (sprayCaching, Compile),
      doc in (sprayCan, Compile),
      doc in (sprayClient, Compile),
      doc in (sprayHttp, Compile),
      doc in (sprayHttpx, Compile),
      doc in (sprayIO, Compile),
      doc in (sprayRouting, Compile),
      doc in (sprayServlet, Compile),
      doc in (sprayTestKit, Compile),
      doc in (sprayUtil, Compile)
    ) map { (siteResDir, version, state, docCaching, docCan, docClient, docHttp, docHttpx, docIO, docRouting, docServlet, docTestKit, docUtil) =>
      val log = colorLog(state)
      def move(docDir: File): Unit = {
        assert(docDir.getName == "api")
        assert(docDir.getParentFile.getName == "target")
        val projectName = docDir.getParentFile.getParentFile.getName
        val dir = siteResDir / "api" / version / projectName
        log("[YELLOW]Moving '" + docDir + "' to '" + dir + "' ...")
        IO.createDirectory(dir.getParentFile)
        IO.delete(dir)
        if (!docDir.renameTo(dir)) sys.error("Couldn't move '" + docDir + "' to '" + dir + "'")
      }
      List(docCaching, docCan, docClient, docHttp, docHttpx, docIO, docRouting, docServlet, docTestKit, docUtil) foreach move
    }
  )

}
