javac -d . AccountInterface.java Account.java
java -classpath . -Djava.rmi.server.codebase=file:./ account.Account 8003 127.0.1.1:8000 127.0.1.1:8001 127.0.1.1:8002