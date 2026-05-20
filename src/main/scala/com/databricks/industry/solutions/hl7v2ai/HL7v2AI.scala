package com.databricks.industry.solutions.hl7v2ai

import scala.collection.immutable.Vector

/**
  * HL7 v2.x message: the MSH segment, following segments, the field separator (`delim`),
  * and MSH-12 (Version ID), taken from the header when using `fromParts` / `fromOrderedSegments`.
  */
final case class HL7v2AI(
  messageHeader: Segment,
  segments: Vector[Segment],
  delim: Char,
  /** MSH-12 — HL7 version id (e.g. `2.5.1`), from the header’s 12th field, or `""` if absent. */
  hl7Version: String,
){
  /** Body segments whose type matches `name` (case-insensitive). Does not include `messageHeader`. */
  def segment(name: String): Vector[Segment] = {
    val target = name.trim.toUpperCase
    segments.filter(_.segmentType.equalsIgnoreCase(target))
  }

  override def toString: String =
    segments.map(_.toString).mkString(delim.toString)
}

object HL7v2AI {

  /**
    * Separators declared in the message header: MSH-1 (field) and MSH-2 (encoding characters).
    * MSH-2 positions: component, repetition, escape, subcomponent (default `^~\\&`).
    */
  final case class MshDelimiters(
      field: Char,
      component: Char,
      repetition: Char,
      escape: Char,
      subcomponent: Char,
  )

  /** Reads MSH-1 and MSH-2 from a raw `MSH|...` line. */
  def delimitersFromMshLine(mshLine: String): Option[MshDelimiters] =
    fieldSeparatorFromMshLine(mshLine).map { field =>
      val enc = encodingCharactersFromMshLine(mshLine, field)
      def at(i: Int, default: Char): Char =
        if (i < enc.length) enc.charAt(i) else default
      MshDelimiters(
        field = field,
        component = at(0, '^'),
        repetition = at(1, '~'),
        escape = at(2, '\\'),
        subcomponent = at(3, '&'),
      )
    }

  /** Field separator and MSH-12 are taken from `messageHeader` (must be a parsed MSH segment). */
  def fromParts(messageHeader: Segment, body: Seq[Segment]): HL7v2AI =
    HL7v2AI(
      messageHeader,
      body.toVector,
      messageHeader.delim,
      hl7VersionFromMsh(messageHeader),
    )

  /**
    * Splits a non-empty `Vector` of segments: head is the message header, tail is the body.
    * Field separator and MSH-12 are taken from the first segment.
    */
  def fromOrderedSegments(
    allSegments: Vector[Segment]
  ): Option[HL7v2AI] =
    allSegments match {
      case seq if seq.isEmpty => None
      case seq =>
        val head = seq.head
        Some(
          HL7v2AI(
            head,
            seq.drop(1),
            head.delim,
            hl7VersionFromMsh(head),
          )
        )
    }

  /**
    * Raw wire text (segments separated by `\r`, `\n`, or `\r\n`) to the `Vector` that
    * [[fromOrderedSegments]] expects. MSH-1 (field) and MSH-2 (component / subcomponent, etc.) are
    * taken from the first `MSH` line. Fails if there is no MSH line with a field separator.
    */
  def rawToOrderedSegments(raw: String): Option[Vector[Segment]] =
    nonEmptySegmentLines(raw) match {
      case lines if lines.isEmpty => None
      case lines =>
        delimitersFromMshLine(lines.head).map { d =>
          lines.map(Segment.parse(_, d.field, d.component, d.subcomponent)).toVector
        }
    }

  /**
    * `rawToOrderedSegments` then `fromOrderedSegments` — same as
    * `rawToOrderedSegments(raw).flatMap(fromOrderedSegments)`.
    */
  def fromRawString(raw: String): Option[HL7v2AI] =
    rawToOrderedSegments(raw).flatMap(fromOrderedSegments)

  private def nonEmptySegmentLines(raw: String): Vector[String] = {
    def dropLeadingBom(s: String): String =
      s match {
        case str if str.nonEmpty && str.charAt(0) == '\uFEFF' => str.substring(1)
        case str                                              => str
      }
    val a = dropLeadingBom(raw.replaceFirst("^\\s+", ""))
      .replace("\r\n", "\n")
      .replace("\r", "\n")
      .linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .toArray
    a.toVector
  }

  /** HL7: MSH-1 is the single character immediately following the three-letter "MSH". */
  private def fieldSeparatorFromMshLine(mshLine: String): Option[Char] =
    mshLine match {
      case line if line.length >= 4 && line.startsWith("MSH") => Some(line.charAt(3))
      case _                                                  => None
    }

  /** MSH-2: encoding characters field (first field after MSH-1 on the wire). */
  private def encodingCharactersFromMshLine(mshLine: String, fieldSep: Char): String = {
    val start = 4
    if (mshLine.length <= start) "^~\\&"
    else {
      val rest = mshLine.substring(start)
      val end  = rest.indexOf(fieldSep)
      val enc  = if (end >= 0) rest.substring(0, end) else rest
      if (enc.isEmpty) "^~\\&" else enc
    }
  }

  /**
    * MSH-12 (Version ID). Parsed `fields(0)` is MSH-2, so MSH-12 is at index 10.
    * (Other segments use [[Segment.field]] where HL7 field 1 is `fields(0)`.)
    */
  private def hl7VersionFromMsh(msh: Segment): String =
    msh.fields.lift(10).map(_.toString.trim).getOrElse("")
}
