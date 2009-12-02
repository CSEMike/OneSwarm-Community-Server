
set LIBS=war\WEB-INF\lib

set JAVA_HOME=C:\Program Files\Java\jdk1.6.0_17

echo %JAVA_HOME%

set CLASSPATH=war\WEB-INF\classes;%JAVA_HOME%\lib\tools.jar;%LIBS%\commons-fileupload-1.2.1.jar;%LIBS%\SimpleCaptcha-1.1.1.jar;%LIBS%\gwt-user.jar;%LIBS%\jetty-util-6.1.21.jar;%LIBS%\mysql-connector-java-5.1.10-bin.jar;%LIBS%\standard.jar;%LIBS%\gwt-dev-mac.jar;%LIBS%\jdom.jar;%LIBS%\rome-1.0.jar;%LIBS%\commons-io-1.3.2.jar;%LIBS%\gwt-servlet.jar;%LIBS%\jetty-6.1.21.jar;%LIBS%\jstl.jar;%LIBS%\servlet-api-2.5-20081211.jar;%LIBS%\jasper.jar;%LIBS%\tomcat-juli.jar;%LIBS%\jsp-api.jar;%LIBS%\el-api.jar;%LIBS%\jasper-el.jar;%LIBS%\jasper-jdt.jar;%LIBS%\bonecp-0.5.3.1-rc1.jar;%LIBS%\google-collect-1.0-rc4.jar;%LIBS%\log4j-1.2.15.jar

@java edu.washington.cs.oneswarm.community2.server.EmbeddedServer %*
