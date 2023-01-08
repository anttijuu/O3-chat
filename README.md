# O3-chat-server

A Java chat server implemented for Programming 3 course. The course teachers server side programming.

Implementation is configurable due to the stepwise advancement of the course. This is why code has lots of places where e.g. checking is done if content is either text only or `application/json`.

First, students implement a simple server with HTTP, without encryption and text only content. Then gradually HTTPS is added, along with JSON payload, database for storing the chat messages, etc.

For this, the `chatserver.properties` contains settings one can use to switch these features on or off.

The server uses a self signed certificate. If using a real one, 

## Building

Build the server (in the root source directory):

```console
mvn package
```

## Running 

First copy `chatserver-template.properties` to `chatserver.properties` and then edit the settings to reflect the features active in the server.

Then run the server:

```console
java -jar target/ChatServer-0.0.1-SNAPSHOT-jar-with-dependencies.jar chatserver.properties s3rver-secr3t-d0no7-xp0s3
```

Where the first parameter is the configuration file you wish to use to run the server, and the second parameter is the password to the self-signed certificate server is using for HTTPS.

You can stop the server in a controlled way from the console with `/quit` command.