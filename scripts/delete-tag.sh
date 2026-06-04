#!/bin/bash
set -e

if [ -z "$1" ]; then
  echo "Usage: ./scripts/delete-tag.sh <version>"
  echo "Example: ./scripts/delete-tag.sh 0.2"
  exit 1
fi

TAG=$1

git tag -d "$TAG"
git push origin ":refs/tags/$TAG"

echo "Tag $TAG deleted locally and from remote."
