#!/bin/bash
echo "========================================"
echo "Starting JavaFX Client"
echo "========================================"
cd "$(dirname "$0")/.."
# Compile first to ensure classes are available
echo "Compiling client module..."
mvn compile -pl client -am -q
echo "Starting JavaFX application..."
mvn javafx:run -pl client
