package account;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface AccountInterface extends Remote {
  boolean send(String message, String recipient) throws RemoteException;
}