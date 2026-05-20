package com.databricks.industry.solutions.hl7v2ai

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/** MSH-1 / MSH-2 delimiter extraction and use when parsing wire-format messages. */
class DelimiterSuite extends AnyFunSuite with Matchers {

  test("delimitersFromMshLine reads non-default MSH-1 and MSH-2") {
    val d = HL7v2AI.delimitersFromMshLine("MSH#!@$%#SENDING").get
    d.field shouldBe '#'
    d.component shouldBe '!'
    d.repetition shouldBe '@'
    d.escape shouldBe '$'
    d.subcomponent shouldBe '%'
  }

  test("delimitersFromMshLine defaults missing MSH-2 positions to ^~\\&") {
    val d = HL7v2AI.delimitersFromMshLine("MSH|ab|SENDING").get
    d.field shouldBe '|'
    d.component shouldBe 'a'
    d.repetition shouldBe 'b'
    d.escape shouldBe '\\'
    d.subcomponent shouldBe '&'
  }

  test("MSH segment preserves MSH-2 encoding field without splitting on component separator") {
    val msh = Segment.parse("MSH|^~\\&|SENDING", '|', '^', '&')
    msh.field(1).get.toString shouldBe "^~\\&"
    msh.field(1).get.components should have size 1
  }

  test("Segment.parse uses explicit field, component, and subcomponent separators") {
    val seg = Segment.parse("PID|a>b<c", '|', '>', '<')
    seg.delim shouldBe '|'
    seg.field(1).get.toString shouldBe "a>b<c"
    seg.field(1).get.components should have size 2
    seg.field(1).get.components(0).toString shouldBe "a"
    seg.field(1).get.components(1).subcomponent(1).get shouldBe "b"
    seg.field(1).get.components(1).subcomponent(2).get shouldBe "c"
  }

  test("rawToOrderedSegments splits components and subcomponents per MSH-2") {
    val raw = "MSH|^~\\&|\nOBX|1^CMP&sub1&sub2"
    val obx = HL7v2AI.rawToOrderedSegments(raw).get(1)
    obx.segmentType shouldBe "OBX"
    val field = obx.field(1).get
    field.components should have size 2
    field.components(0).toString shouldBe "1"
    field.components(1).subcomponents shouldBe Vector("CMP", "sub1", "sub2")
  }

  test("rawToOrderedSegments applies custom MSH-1 and MSH-2 to body segments") {
    val raw = "MSH#!@$%#\nPID#mrn!type"
    val pid = HL7v2AI.rawToOrderedSegments(raw).get(1)
    pid.segmentType shouldBe "PID"
    pid.delim shouldBe '#'
    pid.field(1).get.toString shouldBe "mrn!type"
    pid.field(1).get.components(0).toString shouldBe "mrn"
    pid.field(1).get.components(1).toString shouldBe "type"
  }

  test("fromRawString carries field separator from MSH-1 on the message") {
    val msg = HL7v2AI.fromRawString("MSH#^~\\&#\nPID#only").get
    msg.delim shouldBe '#'
    msg.messageHeader.delim shouldBe '#'
    msg.toString shouldBe "only"
  }

  test("parsed fields round-trip toString with message delimiters") {
    val raw = "MSH|^~\\&|\nPID|12345^MR"
    val pid = HL7v2AI.rawToOrderedSegments(raw).get(1)
    pid.field(1).get.toString shouldBe "12345^MR"
    pid.toString shouldBe "12345^MR"
  }
}
