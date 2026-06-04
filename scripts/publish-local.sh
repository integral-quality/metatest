#!/bin/bash
set -e

./gradlew publishToMavenLocal -x test

echo "Published to mavenLocal (~/.m2)."
echo "In your consumer project, make sure settings.gradle.kts has mavenLocal() in repositories."
