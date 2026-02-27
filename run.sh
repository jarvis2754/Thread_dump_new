#!/bin/bash
# Compile and start the backend API server
# Frontend: open frontend/index.html directly in your browser

set -e

JAVAC=$(find /usr/lib/jvm -name "javac" 2>/dev/null | head -1)
[ -z "$JAVAC" ] && JAVAC=$(which javac 2>/dev/null)

if [ -z "$JAVAC" ]; then
  echo "ERROR: javac not found. Install JDK: sudo apt-get install openjdk-21-jdk"
  exit 1
fi

JAVA=$(dirname "$JAVAC")/java

echo "Compiling..."
mkdir -p out
$JAVAC -d out src/com/analyzer/*.java
echo "Done. Starting server on :8080 ..."
echo "Open frontend/index.html in your browser."
$JAVA -cp out com.analyzer.ThreadDumpServer
