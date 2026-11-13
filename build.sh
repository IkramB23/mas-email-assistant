#!/usr/bin/env sh
# Compiles the project and builds mail-assistant.jar (JDK 21+ only, no other dependency).
set -e
cd "$(dirname "$0")"
rm -rf build/classes && mkdir -p build/classes
javac -encoding UTF-8 -d build/classes $(find src/main/java -name "*.java")
jar --create --file mail-assistant.jar --main-class mas.mail.Main -C build/classes .
echo "Built mail-assistant.jar - run: java -jar mail-assistant.jar   (or --cli, --eval, --mock)"
