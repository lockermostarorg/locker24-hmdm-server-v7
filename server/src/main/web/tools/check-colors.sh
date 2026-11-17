#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
named='aliceblue|antiquewhite|aqua|aquamarine|azure|beige|bisque|black|blanchedalmond|blue|blueviolet|brown|burlywood|cadetblue|chartreuse|chocolate|coral|cornflowerblue|cornsilk|crimson|cyan|darkblue|darkcyan|darkgoldenrod|darkgr[ae]y|darkgreen|darkkhaki|darkmagenta|darkolivegreen|darkorange|darkorchid|darkred|darksalmon|darkseagreen|darkslateblue|darkslategr[ae]y|darkturquoise|darkviolet|deeppink|deepskyblue|dimgr[ae]y|dodgerblue|firebrick|floralwhite|forestgreen|fuchsia|gainsboro|ghostwhite|gold|goldenrod|gr[ae]y|green|greenyellow|honeydew|hotpink|indianred|indigo|ivory|khaki|lavender|lavenderblush|lawngreen|lemonchiffon|lightblue|lightcoral|lightcyan|lightgoldenrodyellow|lightgr[ae]y|lightgreen|lightpink|lightsalmon|lightseagreen|lightskyblue|lightslategr[ae]y|lightsteelblue|lightyellow|lime|limegreen|linen|magenta|maroon|mediumaquamarine|mediumblue|mediumorchid|mediumpurple|mediumseagreen|mediumslateblue|mediumspringgreen|mediumturquoise|mediumvioletred|midnightblue|mintcream|mistyrose|moccasin|navajowhite|navy|oldlace|olive|olivedrab|orange|orangered|orchid|palegoldenrod|palegreen|paleturquoise|palevioletred|papayawhip|peachpuff|peru|pink|plum|powderblue|purple|rebeccapurple|red|rosybrown|royalblue|saddlebrown|salmon|sandybrown|seagreen|seashell|sienna|silver|skyblue|slateblue|slategr[ae]y|snow|springgreen|steelblue|tan|teal|thistle|tomato|turquoise|violet|wheat|white|whitesmoke|yellow|yellowgreen'
roots=(projects/core/src projects/hmdm-ui-kit/src)
for plugin in ../../../../plugins/*/src/main/web/projects; do
  case "$plugin" in */plugins/xtra/*) continue ;; esac
  roots+=("$plugin")
done
hits=$(find "${roots[@]}" -type f \
  \( -name '*.scss' -o -name '*.css' -o -name '*.html' -o -name '*.ts' \) \
  ! -name '*.spec.ts' ! -path '*/node_modules/*' ! -path 'projects/core/src/theme-tokens.scss' -print0 \
  | sort -z | xargs -0 perl -ne '
    BEGIN { $named = shift @ARGV }
    $skip = 0 if $. == 1;
    $skip = 1 if /l24-allow-color-start/;
    my $hit = 0;
    unless ($skip || /l24-allow-color/) {
      $hit = 1 if /(?<![&\w])#(?:[0-9a-f]{8}|[0-9a-f]{6}|[0-9a-f]{3,4})(?![\w-])/i;
      $hit = 1 if /(?<![\w-])(?:rgba?|hsla?|hwb|lab|lch|oklab|oklch|color)\(/i;
      if ($ARGV !~ /\.ts$/) {
        while (/[\w-]*(?:color|background|border|fill|stroke|outline|shadow)[\w-]*\s*:\s*([^;{]*)/gi) {
          $hit = 1 if $1 =~ /(?<![\w-])(?:$named)(?![\w-])/i;
        }
      }
    }
    print "$ARGV:$.:$_" if $hit;
    $skip = 0 if /l24-allow-color-end/;
    close ARGV if eof;
  ' "$named" || true)
if [ -n "$hits" ]; then
  echo "Colour literals outside the theme token files ($(printf '%s\n' "$hits" | wc -l | tr -d ' ') lines):"
  echo "$hits"
  exit 1
fi
echo "colour check passed"
