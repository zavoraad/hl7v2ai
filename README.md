# hl7v2ai

Scala library for parsing HL7 v2.x wire messages into a structured model, with the goal of supporting **LLM-assisted Spark pipelines** grounded on your API, HL7 version, and site-specific implementation guide (IG).

## Overview

`hl7v2ai` splits an ER7 message into:

- **`HL7v2AI`** — message header (`MSH`) plus body segments, field separator, and MSH-12 version id
- **`Segment`** — segment type (e.g. `PID`) and HL7 fields
- **`Field`** — components (e.g. `^`-separated)
- **`Component`** — subcomponents (e.g. `&`-separated)

Delimiters are read from the **MSH** segment when parsing raw text:

| HL7 | Source | Role |
|-----|--------|------|
| MSH-1 | 4th character of `MSH` line | Field separator (often `\|`) |
| MSH-2 | Encoding characters field | Component, repetition, escape, subcomponent (default `^~\&`) |

Body segment lookup uses `HL7v2AI.segment(name)`, which returns a **`Vector[Segment]`** (zero or more matches) from **body `segments` only** — not `messageHeader`. Use `messageHeader` for `MSH`.

## Requirements

- Scala **2.12.8**
- sbt **1.9+**
- Java 8+

Spark version is referenced in `build.sbt` via `SPARK_VERSION` (default `3.5.1`) for future integration; this artifact is currently a **standalone parsing library** without Spark dependencies.

## Build and test

```bash
sbt compile
sbt test
```

Run a single suite:

```bash
sbt "testOnly *DelimiterSuite"
sbt "testOnly *SegmentLookupSuite"
```

### IDE note (Metals / Bloop)

If sbt fails resolving `sbt-bloop`, that comes from auto-generated `project/metals.sbt` (Metals BSP), not from this project’s `build.sbt`. Remove those files if you are not using Metals, or fix network access to Maven so Metals can download the plugin.

## Quick start

### Parse a raw message

```scala
import com.databricks.industry.solutions.hl7v2ai.HL7v2AI

val raw =
  """MSH|^~\&|SENDING|RECV|20250101120000||ADT^A01|MSG001|P|2.5.1
    |PID|1||12345^MR||DOE^JOHN"""

val msg = HL7v2AI.fromRawString(raw).get

msg.hl7Version                    // "2.5.1" (from MSH-12)
msg.delim                         // '|'
msg.messageHeader.segmentType     // "MSH"

val pids = msg.segment("PID")     // Vector of body PID segments (empty if none)
pids.head.field(3).map(_.toString) // HL7 PID-3, if present
```

### Inspect MSH delimiters

```scala
HL7v2AI.delimitersFromMshLine("MSH|^~\\&|APP") match {
  case Some(d) =>
    // d.field, d.component, d.repetition, d.escape, d.subcomponent
  case None => // not a valid MSH line
}
```

### Parse a single segment line

```scala
import com.databricks.industry.solutions.hl7v2ai.Segment

Segment.parse("PID|12345^MR", '|', '^', '&')
```

Pass explicit separators when building segments by hand; when using `fromRawString`, separators for all lines come from the first `MSH` line.

## API summary

| Type / method | Description |
|---------------|-------------|
| `HL7v2AI.fromRawString(raw)` | Full message: split lines → parse segments → `HL7v2AI` |
| `HL7v2AI.fromOrderedSegments(segs)` | Head = MSH, tail = body |
| `HL7v2AI.fromParts(msh, body)` | Construct from already-parsed parts |
| `HL7v2AI.delimitersFromMshLine(line)` | MSH-1 + MSH-2 without full parse |
| `msg.segment("OBX")` | `Vector[Segment]` from body only, case-insensitive |
| `msg.messageHeader` | Parsed `MSH` segment |
| `seg.field(n)` | HL7 **1-based** field index → `Option[Field]` |
| `comp.subcomponent(n)` | HL7 **1-based** subcomponent index → `Option[String]` |

### `toString` behavior

- `Component` / `Field` — rejoin with component / subcomponent delimiters stored on the value
- `Segment` — rejoin **fields** with the segment field separator (`delim`)
- `HL7v2AI` — rejoin **body** `segments` only (not `MSH`), separated by `delim`

## Bundled configuration (classpath / JAR)

Dummy configs ship under `src/main/resources/hl7v2ai/config/` and are available at runtime via **`ConfigResources`**:

| Directory | Purpose |
|-----------|---------|
| `hl7-version/` | HL7 v2.x version profiles (`2.5.1.json`, …) |
| `ehr/` | EHR / site profiles (`example-hospital.json`, …) |
| `implementation-guides/` | Message IGs (`example-hospital-adt-a01.json`, …) |

Each folder has `index.txt` (one id per line) and `<id>.json`. Load from code or Spark:

```scala
import com.databricks.industry.solutions.hl7v2ai.ConfigResources

ConfigResources.listHl7Versions()                    // Vector("2.5.1", "2.3.1")
ConfigResources.loadHl7Version("2.5.1")                // Option[String] JSON text
ConfigResources.loadEhr("example-hospital")
ConfigResources.loadImplementationGuide("example-hospital-adt-a01")
ConfigResources.catalog()                              // all ResourceRef entries
ConfigResources.hl7VersionRef("2.5.1").classpathPath   // "/hl7v2ai/config/hl7-version/2.5.1.json"
```

Replace JSON files with real profiles; keep ids listed in `index.txt`.

## LLM API reference

For code generation (Scala / Spark), use **[docs/API-FOR-LLM.md](docs/API-FOR-LLM.md)** as the canonical API prompt attachment.

## Project layout

```
src/main/scala/com/databricks/industry/solutions/hl7v2ai/
  HL7v2AI.scala         Message model and raw parsing
  Segment.scala         Segment parse (incl. MSH-2 handling)
  Field.scala
  Component.scala
  ConfigResources.scala Classpath config loader

src/main/resources/hl7v2ai/config/
  hl7-version/          Version profiles + index.txt
  ehr/                  EHR profiles + index.txt
  implementation-guides/  IGs + index.txt

docs/
  API-FOR-LLM.md          LLM-oriented API + Spark patterns

src/test/scala/com/databricks/industry/solutions/hl7v2ai/
  ConfigResourcesSuite.scala
  DelimiterSuite.scala       MSH-1 / MSH-2 delimiter tests
  SegmentLookupSuite.scala   segment() lookup tests
  ToStringSuite.scala        Round-trip toString tests
```

## LLM + Spark direction

The intended stack is not “dump the whole implementation guide into a prompt,” but a **bounded context** the model uses to emit Spark code that calls **this library**:

1. **API** — types and methods above (ground truth for generated Scala)
2. **HL7 version config** — base standard (e.g. 2.5.1), aligned with `hl7Version` / MSH-12
3. **EHR / IG config** — site rules: message type, required segments, field cardinality, code sets (structured YAML/JSON, scoped to the question — not full PDF text)
4. **Task spec** — user question, sample message, desired output columns / schema
5. **Validation** — compile, unit tests, smoke run on sample data

Configs alone are a strong **core** for codegen; dependable pipelines also need an explicit **Spark integration pattern** and **examples**.

## Next steps

Roadmap items to move from parsing library toward LLM-assisted Spark development:

### 1. Structured configuration

- [x] Dummy JSON under `src/main/resources/hl7v2ai/config/` (hl7-version, ehr, implementation-guides)
- [x] `ConfigResources` loader (classpath / JAR)
- [ ] Replace dummy JSON with real version + IG exports
- [ ] Loader slice by message type + user question (avoid sending full IG to an LLM)

### 2. LLM context builder

- [ ] Introduce `Hl7QueryContext` (version, IG id, message type, user question, optional raw/parsed message)
- [ ] `toPrompt(apiReference: String): String` that merges API summary + config slice + task spec
- [ ] Document PHI/redaction rules before sending message content to a model

### 3. Spark integration

- [ ] Add Spark dependency profile in `build.sbt` (optional `spark` module or `provided` deps)
- [ ] Publish canonical pattern: `Dataset[String]` (one HL7 message per row) → `mapPartitions` / UDF using `fromRawString`
- [ ] Example notebook or `docs/spark-integration.md` with desired output schema (flat vs nested, explode `OBX`, etc.)
- [ ] Align Scala 2.12 / Spark 3.5.x with target Databricks runtime

### 4. Parsing enhancements

- [ ] Use MSH-2 **repetition** and **escape** characters during parse (today: component + subcomponent from MSH-2; repetition/escape stored on `MshDelimiters` but not applied)
- [ ] Optional: include `messageHeader` in `segment("MSH")` or add `segmentIncludingHeader` if dual behavior is confusing
- [ ] `HL7v2AI.toString` optionally include `MSH` for full wire round-trip

### 5. Quality and codegen loop

- [ ] Golden-file tests: real or synthetic messages per message type from IG config
- [ ] CI: `sbt test` on every change
- [ ] Optional: scaffold that takes model output → compile → run against fixture messages

### 6. Packaging

- [ ] Publish versioned JAR (sbt-assembly or standard `publish`)
- [ ] README example linking config files + generated Spark snippet end-to-end

## Limitations (current)

- No HL7 escape-sequence processing (`\F\`, `\S\`, etc.) yet
- No repeating-field / `~` repetition handling yet
- `hl7Version` depends on parsed MSH field layout (MSH-12 at index 10 after parse)
- Not a conformance validator against an IG — structural parse and navigation only
