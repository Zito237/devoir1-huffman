#!/usr/bin/env bash
# Compile tout le projet dans bin/ puis lance les tests.
set -e
cd "$(dirname "$0")"
rm -rf bin
javac -encoding UTF-8 -d bin $(find src test -name '*.java')
java -Dstdout.encoding=UTF-8 -cp bin huffman.HuffmanTests
