package account;

import java.rmi.NotBoundException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.util.Hashtable;
import java.util.Set;

public class Account implements AccountInterface {
  private int balance;
  private Hashtable<String, AccountInterface> directory;

  public Account() {
    balance = 200;
    directory = new Hashtable<String, ClientInterface>();
  }

  public boolean send(String message, String recipient) {
    // update account balance
  }

  public static void main(String args[]) {
    try {
      Account obj = new Account();
      AccountInterface stub = (AccountInterface) UnicastRemoteObject.exportObject(obj, 0);

      // Bind the remote object's stub in the registry
      Registry registry = LocateRegistry.getRegistry();
      registry.bind("AccountInterface", stub);

      System.err.println("Account ready");

    } catch (Exception e) {
      System.err.println("Account exception: " + e.toString());
      e.printStackTrace();
    }

    // Connect to peers from ips in args

    // Loop to transfer $

  }
}
