
# OneSwarm Community Server
http://oneswarm.cs.washington.edu/

This is the reference implementation of the OneSwarm Community Server, which supports file publishing and friend list exchange among users of the OneSwarm P2P software client. 

Dependencies:
* Sun's Java 6 runtime -- http://java.sun.com/javase/downloads/index.jsp
* MySQL (we've tested with 5.1.41) -- http://dev.mysql.com/downloads/

# Getting started: 
1. Installing MySQL
2. Modifying the sample-communtiy.conf (to include your database info, as well as your SSL certificate if you are using SSL)
3. (Potentially) changing the JAVA_HOME variable in the start-* script for your platform
4. Running the start script with your configuration file as an argument

Questions, concerns, and comments should be posted in the forum:
http://forum.oneswarm.org/

The source code is available from github:
http://github.com/CSEMike/OneSwarm-Community-Server
