package account;

import java.rmi.Remote;
import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Hashtable;

public interface AccountInterface extends Remote {
  boolean send(String message, String sender) throws RemoteException;
  boolean transferSnapshot(String sender, Hashtable<String, ArrayList<String>> channels, int snapshot) throws RemoteException;
}