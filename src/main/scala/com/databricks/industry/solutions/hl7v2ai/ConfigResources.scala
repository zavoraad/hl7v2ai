package com.databricks.industry.solutions.hl7v2ai

import java.io.InputStream
import scala.io.Source
import scala.util.control.NonFatal

/**
  * Classpath configuration bundled in the JAR under `/hl7v2ai/config/`.
  *
  *   - `hl7-version/` — HL7 v2.x version profiles (dummy JSON)
  *   - `ehr/` — EHR / site profiles
  *   - `implementation-guides/` — message-level IGs (ADT, ORU, …)
  *
  * Each directory has an `index.txt` (one id per line) and `<id>.json` payloads.
  */
object ConfigResources {

  val RootPath: String = "/hl7v2ai/config"

  val Hl7VersionDir: String             = s"$RootPath/hl7-version"
  val EhrDir: String                    = s"$RootPath/ehr"
  val ImplementationGuideDir: String    = s"$RootPath/implementation-guides"

  /** Kind of bundled config resource. */
  sealed trait Kind {
    def directory: String
    def label: String
  }

  object Kind {
    case object Hl7Version extends Kind {
      val directory = Hl7VersionDir
      val label     = "hl7-version"
    }
    case object Ehr extends Kind {
      val directory = EhrDir
      val label     = "ehr"
    }
    case object ImplementationGuide extends Kind {
      val directory = ImplementationGuideDir
      val label     = "implementation-guide"
    }
  }

  /** Pointer to a config file on the classpath (for logging, prompts, or Spark metadata). */
  final case class ResourceRef(
      kind: Kind,
      id: String,
      classpathPath: String,
  )

  def listHl7Versions(): Vector[String]             = listIds(Kind.Hl7Version)
  def listEhrProfiles(): Vector[String]             = listIds(Kind.Ehr)
  def listImplementationGuides(): Vector[String]    = listIds(Kind.ImplementationGuide)

  def hl7VersionRef(id: String): ResourceRef       = ref(Kind.Hl7Version, id)
  def ehrRef(id: String): ResourceRef               = ref(Kind.Ehr, id)
  def implementationGuideRef(id: String): ResourceRef = ref(Kind.ImplementationGuide, id)

  def loadHl7Version(id: String): Option[String]             = load(Kind.Hl7Version, id)
  def loadEhr(id: String): Option[String]                     = load(Kind.Ehr, id)
  def loadImplementationGuide(id: String): Option[String]   = load(Kind.ImplementationGuide, id)

  def listRefs(kind: Kind): Vector[ResourceRef] =
    listIds(kind).map(id => ref(kind, id))

  def listIds(kind: Kind): Vector[String] =
    readResource(s"${kind.directory}/index.txt")
      .map(_.linesIterator.map(_.trim).filter(_.nonEmpty).toVector)
      .getOrElse(Vector.empty)

  def ref(kind: Kind, id: String): ResourceRef =
    ResourceRef(kind, id, s"${kind.directory}/$id.json")

  /** Raw JSON (or text) for a config id, read from the JAR classpath. */
  def load(kind: Kind, id: String): Option[String] =
    readResource(ref(kind, id).classpathPath)

  def load(ref: ResourceRef): Option[String] =
    readResource(ref.classpathPath)

  /** All bundled config paths surfaced for discovery (e.g. LLM context listing). */
  def catalog(): Vector[ResourceRef] =
    Vector(Kind.Hl7Version, Kind.Ehr, Kind.ImplementationGuide).flatMap(listRefs)

  private def readResource(classpathPath: String): Option[String] =
    withStream(classpathPath) { stream =>
      Source.fromInputStream(stream, "UTF-8").mkString
    }

  private def withStream[A](classpathPath: String)(f: InputStream => A): Option[A] =
    Option(ConfigResources.getClass.getResourceAsStream(classpathPath)).flatMap { stream =>
      try Some(f(stream))
      catch {
        case NonFatal(_) => None
      } finally {
        try stream.close()
        catch { case NonFatal(_) => () }
      }
    }
}
