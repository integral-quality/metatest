#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Usage: ./scripts/publish-tag.sh <version>"
  echo "Example: ./scripts/publish-tag.sh 0.2"
  exit 1
fi

TAG=$1

git tag "$TAG"
git push origin "$TAG"

echo "Tag $TAG pushed. JitPack will build it at:"
echo "https://jitpack.io/#antigen-framework/antigen/$TAG"
