## Simple File Cloud
File synchronization solution. Written in java.

Client-server multithreaded application, works via TCP, supports many user accounts.
Lets (in almost all cases) keep user files synchronized.

###Server
Stores the files of registered users and synchronizes them.

Files:

Server.java, User.java, sfc.conf

Compilation:

javac Server.java

Configuration:

Uses sfc.conf for settings parameters. Right now has just two: server root directory and port number


Users:

Each user has its own directory in "server root" and the record of username and password in special file "users".

Please note: server stores passwords in non encrypted form. This is not secure, keep it in mind.

Run:

java Server [/path/to/sfc.conf]

by default sfc.conf searching in current directory ("./")

Server has a simple shell that supports the following commands:

* adduser

create new user with it's directory in server root directory

* rmuser

remove user (not implemented yet)

* start

use this command if previously server was manually stopped

* stop

This command stops serving but not shuts down the server. Can be useful if you want reread the configuration file and restart the server.

* status

Running or not

* quit

Finally shuts down the server


###Client
Starts the session with cloud server and synchronizes user files in specified directory.

Files:

SfcClient.java

Compilation:

javac SfcClient.java

Configuration:

Client needs host address and port number to connect. These parameters usually changes not often so it was hardcoded in source file just for simplicity. Make changes if it needs and recompile the source.

Also client must be configured with path to working directory. First argument uses for it. By default it is current directory concatenated with user name.

Running client prompts to enter username and password.

Run:

javac SfcClient [/path/to/working/directory]

