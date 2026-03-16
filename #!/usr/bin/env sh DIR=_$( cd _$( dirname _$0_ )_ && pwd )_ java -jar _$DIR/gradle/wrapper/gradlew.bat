@echo off
set DIRNAME=%~dp0
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS=

"%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" %*
