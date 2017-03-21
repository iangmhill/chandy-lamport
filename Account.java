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
  private static Hashtable<String, AccountInterface> directory;
  private static Random random;
  private static String[] clientIds;
  private int balance;
  private String nextPeer;
  private boolean isLeader;
  private boolean leaderElected;
  private String selfID;
  private Hashtable<String, ArrayList<String>> channels;
  private boolean markerReceived;
  private Hashtable<String, Boolean> isRecording;
  private int snapshot;
  private boolean running;
  private ArrayList<String> bufferMessage;
  private ArrayList<String> bufferSender;

  public Account(String selfIp, String selfPort) {
    balance = 200;
    channels = new Hashtable<String, ArrayList<String>>();
    bufferMessage = new ArrayList<String>();
    bufferSender = new ArrayList<String>();
    markerReceived = false;
    isRecording = new Hashtable<String, Boolean>();
    running = true;
    selfID = selfIp + ":" + selfPort;
    leaderElected = false;
    isLeader = false;
    for (int i = 0; i < clientIds.length; i++) {
      isRecording.put(clientIds[i], false);
      channels.put(clientIds[i], new ArrayList<String>());
    }
  }

  public boolean send(String message, String sender) {
    this.bufferMessage.add(message);
    this.bufferSender.add(sender);
    return true;
  }

  public boolean transferSnapshot(String sender, Hashtable<String, ArrayList<String>> channels, int snapshot) {
    System.out.println("LOCAL SNAPSHOT for " + sender);
    System.out.println("  CHANNELS: " + channels.toString());
    System.out.println("  BALANCE: " + Integer.toString(snapshot));
    return true;
  }

  private void handleElection(String initiator, String winner) {
    try {
      // Handle election message
      if (initiator.equals(this.selfID)) {
        this.leaderElected = true;
        if (winner.equals(this.selfID)) {
          this.isLeader = true;
        }
      } else {
        if (winner.compareTo(this.selfID) >= 0) {
          winner = this.selfID;
        }
        directory.get(clientIds[0]).send("e|" + initiator + "|" + winner, this.selfID);
        System.out.println("SENT: " + "e|" + initiator + "|" + winner + " TO " + clientIds[0]);
      }
    } catch (RemoteException e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
    }
  }

  private void handleTransaction(String value) {
    balance += Integer.parseInt(value);
  }

  private void handleMarker(String initiator, String sender) {
    try {
      if (!this.markerReceived) {
        this.takeLocalSnapshot();
        this.markerReceived = true;
        for (String key : directory.keySet()) {
          if (!key.equals(sender)) {
            this.isRecording.put(key, true);
          }
        }
        for (String key : directory.keySet()) {
          directory.get(key).send("m|" + initiator, this.selfID);
          System.out.println("SENT: " + "m|" + initiator + " TO " + key);
        }

      } else {
        this.isRecording.put(sender, false);

        boolean noneRecording = true;
        for (String key : this.isRecording.keySet()) {
          if (this.isRecording.get(key)) {
            noneRecording = false;
          }
        }
        if (noneRecording) {
          System.out.println("LOCAL SNAPSHOT for " + this.selfID);
          System.out.println("  CHANNELS: " + this.channels.toString());
          System.out.println("  BALANCE: " + Integer.toString(this.snapshot));
          this.running = false;
          if (!initiator.equals(this.selfID)) {
            directory.get(initiator).transferSnapshot(this.selfID, this.channels, this.snapshot);
          }
        }
      }
    } catch (RemoteException e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
    }
  }

  private void takeLocalSnapshot() {
    this.snapshot = this.balance;
  }

  private void generateTransaction() {
    try {
      if (this.balance > 0) {
        int transferValue = random.nextInt(balance) + 1;
        int processIndex = random.nextInt(clientIds.length);
        this.balance -= transferValue;
        directory.get(clientIds[processIndex]).send("t|" + Integer.toString(transferValue), this.selfID);
        System.out.println("SENT: " + "t|" + Integer.toString(transferValue) + " TO " + clientIds[processIndex]);
      }
    } catch (RemoteException e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
    }
  }

  private void receive() {
    if (bufferMessage.size() > 0 && bufferSender.size() > 0) {
      String message = this.bufferMessage.remove(0);
      String sender = this.bufferSender.remove(0);
      System.out.println("RECEIVED: " + message + " FROM " + sender);

      String[] messageParts = message.split("\\|");
      char operation = messageParts[0].charAt(0);
      switch (operation) {
        case 'e': // ELECTION
          this.handleElection(messageParts[1], messageParts[2]);
          break;
        case 't': // TRANSACTION
          this.handleTransaction(messageParts[1]);
          break;
        case 'm': // MARKER
          this.handleMarker(messageParts[1], sender);
          break;
      }
      if (this.isRecording.get(sender)) {
        this.channels.get(sender).add(message);
      }
    }
  }

  private void initiateElection() {
    try {

      // Conduct the leader election process
      String message = "e|" + this.selfID + "|" + this.selfID;
      directory.get(this.clientIds[0]).send(message, this.selfID);
      System.out.println("SENT: " + message + " TO " + clientIds[0]);

    } catch (RemoteException e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
    }
  }

  private void initiateSnapshot() {
    try {

      // If I'm the leader, start the snapshot
      if (this.isLeader && !this.markerReceived) {
        this.takeLocalSnapshot();
        this.markerReceived = true;
        for (String key : directory.keySet()) {
          this.isRecording.put(key, true);
        }
        for (String key : directory.keySet()) {
          directory.get(key).send("m|" + this.selfID, this.selfID);
          System.out.println("SENT: " + "m|" + this.selfID + " TO " + key);
        }
      }

    } catch (RemoteException e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
    }
  }


  public void start () {
    // Conduct the leader election process
    this.initiateElection();

    // Main loop
    while (this.running) {
      int thisCycle = random.nextInt(2);
      switch (thisCycle) {
        case 0:
          this.generateTransaction();
          break;
        case 1:
          this.receive();
          break;
      }

      // Check and initiate snapshot
      this.initiateSnapshot();

      // Delay cycle
      // int transferDelay = random.nextInt(45000 + 1) + 5000;
      int transferDelay = random.nextInt(400 + 1) + 100;
      Account.wait(transferDelay);
    }
  }

  private static void wait(int delay) {
    try {
      TimeUnit.MILLISECONDS.sleep(delay);
    } catch (Exception e) {
      System.err.println(e.toString());
      e.printStackTrace();
    }
  }

  public static void main(String args[]) {
    try {
      // Initialize account information
      String selfPort = args[0];
      String selfIp = InetAddress.getLocalHost().getHostAddress();
      clientIds = Arrays.copyOfRange(args, 1, args.length);

      // Get the local registry
      Registry registry = LocateRegistry.getRegistry(selfIp, Integer.parseInt(selfPort));

      // Set up account
      Account account = new Account(selfIp, selfPort);

      // Bind the remote object's stub in the registry
      AccountInterface stub = (AccountInterface) UnicastRemoteObject.exportObject(account, 0);
      registry.bind("AccountInterface", stub);
      System.out.println("Account ready");

      // Wait for us to turn everything else on
      TimeUnit.SECONDS.sleep(10);

      // Connect to peers from ips in clientIds
      directory = new Hashtable<String, AccountInterface>();
      random = new Random();
      for (int i = 0; i < clientIds.length; i++) {
        String[] parts = clientIds[i].split(":");
        String peerIp = parts[0];
        int peerPort = Integer.parseInt(parts[1]);
        try{
          Registry peerRegistry = LocateRegistry.getRegistry(peerIp, peerPort);
          AccountInterface AccountStub = (AccountInterface) peerRegistry.lookup("AccountInterface");
          directory.put(clientIds[i], AccountStub);
          System.out.println("Connection made to: " + clientIds[i]);
        } catch (RemoteException e) {
          System.err.println("Connection exception: " + e.toString());
          e.printStackTrace();
        }
      }

      // Wait for everyone to connect
      TimeUnit.SECONDS.sleep(10);

      // Begin
      account.start();

    } catch (Exception e) {
      System.err.println("Connection exception: " + e.toString());
      e.printStackTrace();
    }
  }
}
