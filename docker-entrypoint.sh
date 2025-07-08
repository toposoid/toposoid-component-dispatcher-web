#!/bin/bash
_JAVA_OPTIONS=""
if [ -n "${TOPOSOID_JVM_XMS}" ]; then
  _JAVA_OPTIONS="$_JAVA_OPTIONS -Xms$TOPOSOID_JVM_XMS "
else
  _JAVA_OPTIONS="$_JAVA_OPTIONS -Xms512m "
fi
if [ -n "${TOPOSOID_JVM_XMX}" ]; then
  _JAVA_OPTIONS="$_JAVA_OPTIONS -Xmx$TOPOSOID_JVM_XMX "
else
  _JAVA_OPTIONS="$_JAVA_OPTIONS -Xmx1g "
fi
if [ -n "${TOPOSOID_JVM_XSS}" ]; then
  _JAVA_OPTIONS="$_JAVA_OPTIONS -Xss$TOPOSOID_JVM_XSS "
else
  _JAVA_OPTIONS="$_JAVA_OPTIONS -Xss512k "
fi

export _JAVA_OPTIONS=$_JAVA_OPTIONS
cd /app/toposoid-component-dispatcher-web/target/universal
toposoid-component-dispatcher-web-0.6-SNAPSHOT/bin/toposoid-component-dispatcher-web
