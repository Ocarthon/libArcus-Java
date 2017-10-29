libArcus is a lightweight, easy to use library to send and receive messages between two programs based on the [Protocol Buffers](https://developers.google.com/protocol-buffers/) library by Google. It was originally developed by Ultimaker and is primarily used to communicate with their CuraEngine. This is a port of the [original library](https://github.com/Ultimaker/libArcus/) to the programming language Java. The API is almost identical to make working with both libraries more easy.

Usage
-----
```java
// Create a new instance
ArcusSocket socket = new ArcusSocket();

// Add a listener
socket.addListener(...);

// Register a message type
socket.registerMessageType(MyType.getDefaultInstanceForType());

// Start listening on the specified port
socket.listen(...);

// Send a message
socket.sendMessage(...);

// Retrieve a received message
Message message = socket.takeNextMessage();

// Check of message type
if (message != null && message instanceof MyType) {
    doStuff((MyType) message);
}

// Close the connection
socket.close();
```

Maven
-----
libArcus-Java has been published on Maven Central and you can add it to your pom.xml as a dependency.
```xml
<dependency>
  <groupId>de.ocarthon</groupId>
  <artifactId>libArcus</artifactId>
  <version>1.0.2</version>
</dependency>
```

More detailed information on how the library works and how to use it can be found in the [Documentation](https://ocarthon.github.io/libArcus-Java/docs/)
