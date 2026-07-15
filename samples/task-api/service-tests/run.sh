#!/usr/bin/env bash
# Runs the Ruby/Cucumber service-level test suite against an already-running task-api instance.
# ../deploy.sh must be running first - these are black-box tests hitting the real HTTP API from
# outside, not unit tests replacing the JUnit ones under app/src/test.
set -euo pipefail
cd "$(dirname "$0")"

if ! curl -sf http://localhost:8080/tasks >/dev/null 2>&1; then
  echo "task-api doesn't seem to be running - run ../deploy.sh first." >&2
  exit 1
fi

# macOS ships an old, frozen system Ruby (2.6, EOL) that's too old for current Cucumber gems -
# same class of gotcha as this repo's "Apple's stub java" checks elsewhere. Prefer Homebrew's
# ruby@3.3 specifically (not the plain "ruby" formula): Ruby 3.4+/4.x ships a C23 stdckdint.h
# compat shim in its own headers that self-matches via `__has_include(<stdckdint.h>)` under
# current Apple clang (16.x) - `#include <stdckdint.h>` then fails to find what has_include just
# claimed existed ("file not found with <angled> include; use "quotes" instead"). Breaks *any*
# native gem extension (bigdecimal, pulled in transitively by cucumber-expressions, is just the
# first one you hit), not something specific to this suite. ruby@3.3 predates that shim entirely.
RUBY_BIN="ruby"
BUNDLE_BIN="bundle"
if [ -x /opt/homebrew/opt/ruby@3.3/bin/ruby ]; then
  RUBY_BIN=/opt/homebrew/opt/ruby@3.3/bin/ruby
  BUNDLE_BIN=/opt/homebrew/opt/ruby@3.3/bin/bundle
elif ! ruby -e 'exit(RUBY_VERSION.split(".").first.to_i >= 3 ? 0 : 1)' >/dev/null 2>&1; then
  echo "No usable Ruby found. Install one, e.g. 'brew install ruby@3.3' (plain 'brew install ruby'" >&2
  echo "gets you 4.x, which hits a native-extension header bug on current Apple clang - see" >&2
  echo "the comment above)." >&2
  exit 1
fi

echo "Using $($RUBY_BIN --version)"
"$BUNDLE_BIN" install --quiet
"$BUNDLE_BIN" exec cucumber
