package com.databricks.industry.solutions.hl7v2ai

import scala.collection.immutable.Vector
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class SegmentLookupSuite extends AnyFunSuite with Matchers {

  private def seg(name: String, value: String): Segment =
    Segment(
      name,
      Vector(Field(Vector(Component(Vector(value), '&')), '^')),
      '|',
    )

  private def msg(body: Segment*): HL7v2AI =
    HL7v2AI(seg("MSH", "hdr"), body.toVector, '|', "2.5.1")

  test("segment returns empty vector when no body segment matches") {
    msg(seg("PID", "1")).segment("OBX") shouldBe Vector.empty
  }

  test("segment returns all matching body segments in order") {
    val obx1 = seg("OBX", "a")
    val pid  = seg("PID", "p")
    val obx2 = seg("OBX", "b")
    msg(obx1, pid, obx2).segment("OBX") shouldBe Vector(obx1, obx2)
  }

  test("segment does not include messageHeader when name is MSH") {
    val msh = seg("MSH", "hdr")
    val pid = seg("PID", "p")
    HL7v2AI(msh, Vector(pid), '|', "2.5.1").segment("MSH") shouldBe Vector.empty
  }

  test("segment match is case-insensitive") {
    val pid = seg("PID", "mrn")
    msg(pid).segment("pid") shouldBe Vector(pid)
  }
}
