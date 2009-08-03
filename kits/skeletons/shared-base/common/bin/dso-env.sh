#!/bin/sh

#
# All content copyright Terracotta, Inc., unless otherwise indicated. All rights reserved.


#

# OS specific support.  $var _must_ be set to either true or false.
cygwin=false
case "`uname`" in
CYGWIN*) cygwin=true;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched
if $cygwin; then
  [ -n "$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "$JAVA_HOME"`
  [ -n "$TC_INSTALL_DIR" ] && TC_INSTALL_DIR=`cygpath --unix "$TC_INSTALL_DIR"`
fi

if test \! -d "${JAVA_HOME}"; then
  echo "$0: the JAVA_HOME environment variable is not defined correctly"
  exit 2
fi

if test "$1" = "-q" || test -n "${TC_INSTALL_DIR}"; then
  if test -z "${TC_INSTALL_DIR}"; then
    echo "Error: When the -q option is specified, I expect that"
    echo "the environment variable TC_INSTALL_DIR is set so that I"
    echo "can locate your Terracotta installation."
    exit 1
  fi
  if test "$1" = "-q"; then
    shift
    __DSO_ENV_QUIET="true"
  fi
else
  TC_INSTALL_DIR=`dirname "$0"`/..
fi

if (test "$1" = "-f") || (test "$1" = "--config"); then
  shift
  TC_CONFIG_PATH="$1"
fi

. "${TC_INSTALL_DIR}/bin/boot-jar-path.sh"

TC_JAVA_OPTS="-Xbootclasspath/p:${DSO_BOOT_JAR} -Dtc.install-root=${TC_INSTALL_DIR}"
 
test "${TC_CONFIG_PATH}" && TC_JAVA_OPTS="${TC_JAVA_OPTS} -Dtc.config=${TC_CONFIG_PATH}" 
test "${TC_SERVER}" && TC_JAVA_OPTS="${TC_JAVA_OPTS} -Dtc.server=${TC_SERVER}"
test -z "${__DSO_ENV_QUIET}" && echo "${TC_JAVA_OPTS}"
