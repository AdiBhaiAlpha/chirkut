#!/bin/bash
# clean_build.sh
# This script stops Gradle daemons, cleans the build cache, and removes build directories
# to ensure no stale artifact files interfere with the new signing configuration.

echo "Stopping Gradle daemons..."
./gradlew --stop || true

echo "Cleaning Gradle cache..."
./gradlew clean --no-daemon

echo "Removing local .gradle and build directories..."
rm -rf .gradle
rm -rf build
rm -rf app/build

echo "Build cache cleaned successfully. You can now re-run the build."
