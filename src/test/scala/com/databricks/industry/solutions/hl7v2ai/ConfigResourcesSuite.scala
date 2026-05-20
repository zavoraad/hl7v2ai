package com.databricks.industry.solutions.hl7v2ai

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ConfigResourcesSuite extends AnyFunSuite with Matchers {

  test("list bundled HL7 version configs from classpath") {
    ConfigResources.listHl7Versions() should contain allOf ("2.5.1", "2.3.1")
  }

  test("load HL7 version JSON from JAR resources") {
    val json = ConfigResources.loadHl7Version("2.5.1").get
    json should include ("\"version\"")
    json should include ("2.5.1")
  }

  test("load EHR and implementation guide configs") {
    ConfigResources.loadEhr("example-hospital").get should include ("example-hospital")
    ConfigResources.loadImplementationGuide("example-hospital-adt-a01").get should include (
      "ADT"
    )
  }

  test("catalog surfaces all ResourceRef entries") {
    val refs = ConfigResources.catalog()
    refs.count(_.kind == ConfigResources.Kind.Hl7Version) shouldBe 2
    refs.count(_.kind == ConfigResources.Kind.Ehr) shouldBe 2
    refs.count(_.kind == ConfigResources.Kind.ImplementationGuide) shouldBe 2
    refs.foreach(ref => ConfigResources.load(ref) shouldBe defined)
  }

  test("ResourceRef paths are under hl7v2ai/config") {
    ConfigResources.hl7VersionRef("2.5.1").classpathPath shouldBe
      "/hl7v2ai/config/hl7-version/2.5.1.json"
  }
}
