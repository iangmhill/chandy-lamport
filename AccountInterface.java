package account;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AccountInterface extends Remote {
  boolean send(String message, String sender) throws RemoteException;
}