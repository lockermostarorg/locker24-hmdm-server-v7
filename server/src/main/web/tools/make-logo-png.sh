#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
magick -size 100x100 xc:'#009FD7' \
  \( -background none -density 300 brand/locker-icon-white.svg -resize 64x64 \) \
  -gravity center -composite -strip -depth 8 \
  ../projects/core/public/images/logo.png
