package com.databricks.industry.solutions.hl7v2ai

import scala.collection.immutable.ArraySeq

/**
  * HL7 v2.x message: the MSH segment, following segments, the field separator (`delim`),
  * and MSH-12 (Version ID), taken from the header when using `fromParts` / `fromOrderedSegments`.
  */
final case class HL7v2AI(
  messageHeader: Segment,
  segments: ArraySeq[Segment],
  delim: Char,
  /** MSH-12 — HL7 version id (e.g. `2.5.1`), from the header’s 12th field, or `""` if absent. */
  hl7Version: String,
)

object HL7v2AI {

  /** Field separator and MSH-12 are taken from `messageHeader` (must be a parsed MSH segment). */
  def fromParts(messageHeader: Segment, body: Seq[Segment]): HL7v2AI =
    HL7v2AI(
      messageHeader,
      ArraySeq.from(body),
      messageHeader.delim,
      hl7VersionFromMsh(messageHeader),
    )

  /**
    * Splits a non-empty `ArraySeq` of segments: head is the message header, tail is the body.
    * Field separator and MSH-12 are taken from the first segment.
    */
  def fromOrderedSegments(
    allSegments: ArraySeq[Segment]
  ): Option[HL7v2AI] =
    if allSegments.isEmpty then None
    else
      val head = allSegments.head
      Some(
        HL7v2AI(
          head,
          allSegments.drop(1),
          head.delim,
          hl7VersionFromMsh(head),
        )
      )

  /**
    * Raw wire text (segments separated by `\r`, `\n`, or `\r\n`) to the `ArraySeq` that
    * [[fromOrderedSegments]] expects. Field separator is MSH-1: the first character after `MSH` on
    * the first segment line. Fails if there is no MSH line with a field separator.
    */
  def rawToOrderedSegments(raw: String): Option[ArraySeq[Segment]] =
    val lines = nonEmptySegmentLines(raw)
    if lines.isEmpty then None
    else
      fieldSeparatorFromMshLine(lines.head).map: fs =>
        val segs = lines.map(Segment.parse(_, fs))
        ArraySeq.unsafeWrapArray(segs.toArray)

  /**
    * `rawToOrderedSegments` then `fromOrderedSegments` — same as
    * `rawToOrderedSegments(raw).flatMap(fromOrderedSegments)`.
    */
  def fromRawString(raw: String): Option[HL7v2AI] =
    rawToOrderedSegments(raw).flatMap(fromOrderedSegments)

  private def nonEmptySegmentLines(raw: String): ArraySeq[String] =
    def dropLeadingBom(s: String): String =
      if s.nonEmpty && s.charAt(0) == '\uFEFF' then s.substring(1) else s
    val a = dropLeadingBom(raw.replaceFirst("^\\s+", ""))
      .linesIterator
      .map(_.trim)
      .filter(_.nonEmpty)
      .toArray
    ArraySeq.unsafeWrapArray(a)

  /** HL7: MSH-1 is the single character immediately following the three-letter "MSH". */
  private def fieldSeparatorFromMshLine(mshLine: String): Option[Char] =
    if mshLine.length >= 4 && mshLine.startsWith("MSH") then Some(mshLine.charAt(3))
    else None

  /**
    * MSH-12 (Version ID) — 0-based index 11 in the field split (`fields(0)` is the segment id `MSH`,
    * `fields(1)` is MSH-2, …, `fields(11)` is MSH-12).
    */
  private def hl7VersionFromMsh(msh: Segment): String =
    val f = msh.fields
    if f.length > 11 then f(11).trim
    else ""
}
