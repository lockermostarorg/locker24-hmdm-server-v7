#!/usr/bin/env bash
# Runs deploy/test/forced-test-inner.sh against deploy/deploy-forced.sh in ubuntu:24.04 with stubs.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
docker run --rm -v "$here/..":/work:ro ubuntu:24.04 bash /work/test/forced-test-inner.sh
