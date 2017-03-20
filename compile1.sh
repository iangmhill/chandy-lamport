javac -d . AccountInterface.java Account.java
java -classpath . -Djava.rmi.server.codebase=file:./ account.Account 8001 localhost:8000 localhost:8002