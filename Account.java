package account;

import java.lang.InterruptedException;

import java.rmi.NotBoundException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.util.concurrent.TimeUnit;
import java.util.Hashtable;
import java.util.Set;
import java.util.Random;
import java.util.Arrays;

public class Account implements AccountInterface {
  private static int balance;
  private static Hashtable<String, AccountInterface> directory;
  private static Random random;

  public Account() {
    balance = 200;
    directory = new Hashtable<String, AccountInterface>();
    random = new Random();
  }

  public boolean send(String message, String sender) {
    balance += Integer.parseInt(message);
    System.out.println("GOT: " + message + " from " + sender + " leaving " + Integer.toString(balance));
    return true;
  }

  public static void main(String args[]) {
    int port = Integer.parseInt(args[0]);
    String[] clientIps = Arrays.copyOfRange(args, 1, args.length);
    String localhost = "localhost";
    try {
      Registry registry = LocateRegistry.getRegistry(localhost, port);
      Account obj = new Account();

      AccountInterface stub = (AccountInterface) UnicastRemoteObject.exportObject(obj, 0);
      // Bind the remote object's stub in the registry
      registry.bind("AccountInterface", stub);
      System.out.println("Account ready");

      try {
        // Wait for us to turn everything else on
        TimeUnit.SECONDS.sleep(10);

        // Connect to peers from ips in clientIps
        for (int i = 0; i < clientIps.length; i++) {
          String[] parts = clientIps[i].split(":");
          String peerIp = parts[0];
          int peerPort = Integer.parseInt(parts[1]);
          try{
            Registry peerRegistry = LocateRegistry.getRegistry(peerIp, peerPort);
            AccountInterface AccountStub = (AccountInterface) peerRegistry.lookup("AccountInterface");
            directory.put(clientIps[i], AccountStub);
            System.out.println("Connection made to: " + clientIps[i]);
          } catch (RemoteException e) {
            System.err.println("Connection exception: " + e.toString());
            e.printStackTrace();
          }
        }

        // Loop to transfer $
        int transferDelay;
        int transferValue;
        int processIndex;
        while (true) {
          // transferDelay = random.nextInt(45000 + 1) + 5000;
          transferDelay = random.nextInt(4000 + 1) + 1000;
          transferValue = random.nextInt(balance) + 1;
          processIndex = random.nextInt(clientIps.length);
          TimeUnit.SECONDS.sleep(transferDelay / 1000);

          balance -= transferValue;
          System.out.println("SNT: " + Integer.toString(transferValue) + " to " + clientIps[processIndex] + " leaving " + Integer.toString(balance));

          directory.get(clientIps[processIndex]).send(Integer.toString(transferValue), args[0]);
        }
      } catch (InterruptedException e) {
        registry.unbind("AccountInterface");
        UnicastRemoteObject.unexportObject(obj, true);
      }
    } catch (Exception e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
    }
  }
}
