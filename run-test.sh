#!/usr/bin/env sh
set -eu
OUT="${TMPDIR:-/tmp}/legal-matter-agent-test-classes"
mkdir -p "$OUT"
javac -d "$OUT" src/main/java/legaltech/*.java src/test/java/legaltech/*.java
java -cp "$OUT" legaltech.LegalMatterAgentServiceTest
