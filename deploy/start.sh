#!/usr/bin/bash
set -euo pipefail
app_dir=$(cd -- "$(dirname -- "$0")" && pwd)
java_home=${JAVA_HOME:-/usr/lib/jvm/jre-21}
exec "$java_home/bin/java" -Xms128m -Xmx640m -XX:+UseG1GC \
  -Dfile.encoding=UTF-8 -Duser.timezone=UTC -jar "$app_dir/app.jar"
