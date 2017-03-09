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

public class Account implements AccountInterface {
  private int balance;
  private Hashtable<String, AccountInterface> directory;
  private Random random;

  public Account() {
    balance = 200;
    directory = new Hashtable<String, AccountInterface>();
    random = new Random();
  }

  public boolean send(String message, String recipient) {
    balance += Integer.parseInt(message);
    return true;
  }

  public void main(String args[]) {
    try {
      Registry registry = LocateRegistry.getRegistry();
      Account obj = new Account();
      AccountInterface stub = (AccountInterface) UnicastRemoteObject.exportObject(obj, 0);
      // Bind the remote object's stub in the registry
      registry.bind("AccountInterface", stub);
      System.err.println("Account ready");

      try {
        // Wait for us to turn everything else on
        TimeUnit.SECONDS.sleep(10);

        // Connect to peers from ips in args
        for (int i = 0; i < args.length; i++) {
          String peerIP = args[i];
          try{
            Registry peerRegistry = LocateRegistry.getRegistry(peerIP);
            AccountInterface AccountStub = (AccountInterface) peerRegistry.lookup("AccountInterface");
            directory.put(peerIP, AccountStub);
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
          transferDelay = random.nextInt(45000 + 1) + 5000;
          transferValue = random.nextInt(balance) + 1;
          processIndex = random.nextInt(args.length + 1);
          TimeUnit.SECONDS.sleep(transferDelay / 1000);
          directory.get(args[processIndex]).send(Integer.toString(transferValue), args[processIndex]);
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
