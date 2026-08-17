@rem
@echo off
@if "%DEBUG%"=="" @echo on
@rem Set local directory
set LOCAL_DIR=%~dp0
@rem Find java.exe
set DEFAULT_JAVACMD=java
set JAVA_EXE=%JAVA_HOME%/bin/java.exe
if exist "%JAVA_EXE%" set DEFAULT_JAVACMD=%JAVA_EXE%
@rem Setup the command line
set CLASSPATH=%LOCAL_DIR%gradlewrappergradle-wrapper.jar
@rem Execute Gradle
%DEFAULT_JAVACMD% -cp "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
