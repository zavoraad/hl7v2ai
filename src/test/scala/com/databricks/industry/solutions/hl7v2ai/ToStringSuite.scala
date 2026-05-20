package com.databricks.industry.solutions.hl7v2ai

import scala.collection.immutable.Vector
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ToStringSuite extends AnyFunSuite with Matchers {

  test("Component.toString joins subcomponents with delim") {
    val c = Component(Vector("12345", "MR"), '&')
    c.toString shouldBe "12345&MR"
  }

  test("Component.toString preserves empty subcomponents") {
    val c = Component(Vector("a", "", "c"), '&')
    c.toString shouldBe "a&&c"
  }

  test("Field.toString joins components with delim") {
    val f = Field(
      Vector(
        Component(Vector("12345"), '&'),
        Component(Vector("MR"), '&'),
      ),
      '^',
    )
    f.toString shouldBe "12345^MR"
  }

  test("Field.toString preserves empty components") {
    val f = Field(
      Vector(
        Component(Vector("x"), '&'),
        Component(Vector.empty, '&'),
        Component(Vector("z"), '&'),
      ),
      '^',
    )
    f.toString shouldBe "x^^z"
  }

  test("Segment.toString joins fields with delim") {
    val seg = Segment(
      "PID",
      Vector(
        Field(Vector(Component(Vector("1"), '&')), '^'),
        Field(Vector(Component(Vector("DOE"), '&')), '^'),
      ),
      '|',
    )
    seg.toString shouldBe "1|DOE"
  }

  test("Segment.toString preserves empty fields") {
    val seg = Segment(
      "MSH",
      Vector(
        Field(Vector(Component(Vector("a"), '&')), '^'),
        Field(Vector.empty, '^'),
        Field(Vector(Component(Vector("b"), '&')), '^'),
      ),
      '|',
    )
    seg.toString shouldBe "a||b"
  }

  test("HL7v2AI.toString joins body segments with delim") {
    val header = Segment(
      "MSH",
      Vector(Field(Vector(Component(Vector("hdr"), '&')), '^')),
      '|',
    )
    val pid = Segment(
      "PID",
      Vector(Field(Vector(Component(Vector("mrn"), '&')), '^')),
      '|',
    )
    val msg = HL7v2AI(header, Vector(pid), '|', "2.5.1")
    msg.toString shouldBe "mrn"
  }

  test("delimitersFromMshLine reads MSH-1 and MSH-2") {
    val d = HL7v2AI.delimitersFromMshLine("MSH|^~\\&|SENDING").get
    d.field shouldBe '|'
    d.component shouldBe '^'
    d.repetition shouldBe '~'
    d.escape shouldBe '\\'
    d.subcomponent shouldBe '&'
  }

  test("rawToOrderedSegments parses PID components using MSH-2 separators") {
    val raw = "MSH|^~\\&|\rPID|12345^MR"
    val segs = HL7v2AI.rawToOrderedSegments(raw).get
    val pid  = segs(1)
    pid.segmentType shouldBe "PID"
    pid.field(1).get.components should have size 2
    pid.field(1).get.components(0).subcomponent(1).get shouldBe "12345"
    pid.field(1).get.components(1).subcomponent(1).get shouldBe "MR"
  }

  test("HL7v2AI.toString joins multiple body segments") {
    val header = Segment("MSH", Vector.empty, '|')
    val obx = Segment(
      "OBX",
      Vector(Field(Vector(Component(Vector("1"), '&')), '^')),
      '|',
    )
    val pid = Segment(
      "PID",
      Vector(Field(Vector(Component(Vector("x"), '&')), '^')),
      '|',
    )
    val msg = HL7v2AI(header, Vector(obx, pid), '|', "")
    msg.toString shouldBe "1|x"
  }
}
