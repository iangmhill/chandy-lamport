package account;

import java.lang.InterruptedException;

import java.rmi.NotBoundException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import java.net.InetAddress;
import java.net.UnknownHostException;

import java.util.concurrent.TimeUnit;
import java.util.Hashtable;
import java.util.Set;
import java.util.Random;
import java.util.Arrays;
import java.util.ArrayList;

public class Account implements AccountInterface {
  private static int balance;
  private static Hashtable<String, AccountInterface> directory;
  private static Random random;
  private static String nextPeer;
  private static String selfIp;
  private static String selfPort;
  private static boolean isLeader;
  private static boolean leaderElected;
  private static String selfID;
  private static Hashtable<String, ArrayList<String>> channels;
  private static boolean markerReceived;
  private static Hashtable<String, Boolean> isRecording;
  private static int snapshot;
  private static boolean running;

  public Account() {
    balance = 200;
    directory = new Hashtable<String, AccountInterface>();
    random = new Random();
    channels = new Hashtable<String, ArrayList<String>>();
    markerReceived = false;
    isRecording = new Hashtable<String, Boolean>();
    running = true;
  }

  public boolean send(String message, String sender) {
    try {
      int transferDelay = random.nextInt(4000 + 1) + 1000;
      TimeUnit.SECONDS.sleep(transferDelay / 1000);
    } catch (Exception e) {
      System.err.println(e.toString());
      e.printStackTrace();
    }
    System.out.println("RECEIVED: " + message + " FROM " + sender);
    String[] messageParts = message.split("\\|");
    char operation = messageParts[0].charAt(0);
    switch (operation) {
      case 'e': // ELECTION
        Account.handleElection(messageParts[1], messageParts[2]);
        break;
      case 't': // TRANSACTION
        Account.handleTransaction(messageParts[1]);
        break;
      case 'm': // MARKER
        Account.handleMarker(messageParts[1], sender);
        break;
    }
    if (isRecording.get(sender)) {
      channels.get(sender).add(message);
    }

    return true;
  }

  public static boolean handleElection(String initiator, String winner) {
    try {
      // Handle election message
      if (initiator.equals(selfID)) {
        leaderElected = true;
        if (winner.equals(selfID)) {
          isLeader = true;
        }
      } else {
        if (winner.compareTo(selfID) >= 0) {
          winner = selfID;
        }
        directory.get(nextPeer).send("e|" + initiator + "|" + winner, selfID);
        System.out.println("SENT: " + "e|" + initiator + "|" + winner + " TO " + nextPeer);
      }
    } catch (RemoteException e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public static boolean handleTransaction(String value) {
    balance += Integer.parseInt(value);
    return true;
  }

  public static boolean handleMarker(String initiator, String sender) {
    try {
      System.out.println("markerReceived: " + Boolean.toString(markerReceived));
      if (!markerReceived) {
        Account.takeLocalSnapshot();
        markerReceived = true;
        for (String key : directory.keySet()) {
          if (!key.equals(sender)) {
            isRecording.put(key, true);
          }
        }
        for (String key : directory.keySet()) {
          directory.get(key).send("m|" + initiator, selfID);
          System.out.println("SENT: " + "m|" + initiator + " TO " + key);
        }
        System.out.println("isRecording: " + isRecording.toString());
      } else {
        isRecording.put(sender, false);
        System.out.println("isRecording: " + isRecording.toString());
        boolean noneRecording = true;
        for (String key : isRecording.keySet()) {
          if (isRecording.get(key)) {
            noneRecording = false;
          }
        }
        if (noneRecording) {
          System.out.println("Channels: " + channels.toString());
          System.out.println("Snapshot: " + Integer.toString(snapshot));
          running = false;
        }
      }
    } catch (RemoteException e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public static boolean takeLocalSnapshot() {
    snapshot = balance;
    return true;
  }

  public static void main(String args[]) {
    selfPort = args[0];
    try {
      selfIp = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      System.err.println("Couldn't get the IP address: " + e.toString());
      e.printStackTrace();
    }
    selfID = selfIp + ":" + selfPort;
    String[] clientIds = Arrays.copyOfRange(args, 1, args.length);
    nextPeer = clientIds[0];
    try {
      Registry registry = LocateRegistry.getRegistry(selfIp, Integer.parseInt(selfPort));
      Account obj = new Account();

      AccountInterface stub = (AccountInterface) UnicastRemoteObject.exportObject(obj, 0);
      // Bind the remote object's stub in the registry
      registry.bind("AccountInterface", stub);
      System.out.println("Account ready");

      try {
        // Wait for us to turn everything else on
        TimeUnit.SECONDS.sleep(10);

        // Connect to peers from ips in clientIds
        for (int i = 0; i < clientIds.length; i++) {
          String[] parts = clientIds[i].split(":");
          String peerIp = parts[0];
          int peerPort = Integer.parseInt(parts[1]);
          try{
            isRecording.put(clientIds[i], false);
            channels.put(clientIds[i], new ArrayList<String>());
            Registry peerRegistry = LocateRegistry.getRegistry(peerIp, peerPort);
            AccountInterface AccountStub = (AccountInterface) peerRegistry.lookup("AccountInterface");
            directory.put(clientIds[i], AccountStub);
            System.out.println("Connection made to: " + clientIds[i]);
          } catch (RemoteException e) {
            System.err.println("Connection exception: " + e.toString());
            e.printStackTrace();
          }
        }

        // Conduct the leader election process
        TimeUnit.SECONDS.sleep(10);
        String message = "e|" + selfID + "|" + selfID;
        directory.get(nextPeer).send(message, selfID);
        System.out.println("SENT: " + message + " TO " + nextPeer);

        // If I'm the leader, start the snapshot
        if (isLeader && !markerReceived) {
          Account.takeLocalSnapshot();
          markerReceived = true;
          for (String key : directory.keySet()) {
            isRecording.put(key, true);
          }
          for (String key : directory.keySet()) {
            directory.get(key).send("m|" + selfID, selfID);
            System.out.println("SENT: " + "m|" + selfID + " TO " + key);
          }
        }

        // Loop to transfer $
        int transferValue;
        int processIndex;
        while (running) {
          // transferDelay = random.nextInt(45000 + 1) + 5000;
          if (balance > 0) {
            transferValue = random.nextInt(balance) + 1;
            processIndex = random.nextInt(clientIds.length);
            balance -= transferValue;
            directory.get(clientIds[processIndex]).send("t|" + Integer.toString(transferValue), selfID);
            System.out.println("SENT: " + "t|" + Integer.toString(transferValue) + " TO " + clientIds[processIndex]);
          }
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
