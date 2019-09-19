package pxp180031;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

class Node implements Runnable {
  Thread process;
  String name;
  int uid;
  volatile int round;
  Node neighbor;
  volatile boolean hold = true;
  volatile boolean terminate = false;
  volatile int minUID;
  volatile int lastRound;

  CyclicBarrier barrier;
  MasterNode master;

  public Node(int id, MasterNode master, CyclicBarrier barrier) {
    this.name = id + "";
    this.uid = id;
    this.minUID = id;
    this.round = 1;

    this.barrier = barrier;
    this.master = master;
  }

  public void setNeighbor(Node neighbor) {
    this.neighbor = neighbor;
  }

  public void setHold(boolean hold) {
    this.hold = hold;
  }

  public void setTerminate(boolean terminate) { this.terminate = terminate; }

  public void setRound(int round) { this.round = round; }

  /**
   * Pass message to neighboring node
   * Token (UID, round)
   * @param incomingUID
   * @param incomingRound
   */
  public void sendMessage(int incomingUID, int incomingRound) {
    if (this.uid == incomingUID) {
      System.out.printf("Leader %s elected in round %s! \n", uid, round);
      // Inform master thread about election
      this.master.leaderElected();
      return;
    }
    int newMin = Math.min(minUID, incomingUID);
    if (incomingUID < minUID) this.lastRound = incomingRound;

    minUID = newMin;
  }

  /**
   * Helper function to print leader information
   */
  public void checkLeader() {
    if (this.uid == minUID) {
      System.out.printf("UID %s elected in round %s! \n", uid, round);
      return;
    }
    System.out.printf("UID %s not a leader\n", uid);
  }

  /**
   * Checks if the current round is valid to send the message to neighbor
   * @return
   */
  private boolean isValidRound() {
    return round == lastRound + (int) Math.pow(2, minUID);
  }

  @Override
  public void run() {
    try {
      while (!terminate) {
        while (!terminate && hold) continue;

        if (isValidRound()) {
          neighbor.sendMessage(minUID, round);
        }

        hold = true;
        barrier.await();
      }

    } catch (Exception ex) {
      System.err.println(ex);
    }
  }

  public void start() {
    System.out.println("Starting thread for " + name);
    if (process == null) {
      process = new Thread(this, name);
      process.start();
    }
  }
}

class MasterNode implements Runnable {
  Thread process;
  String name;
  // Number of threads to create
  int n;
  volatile boolean isLeaderElected = false;
  volatile boolean terminate = false;
  volatile int round = 0;
  Node[] nodes;
  volatile boolean roundInProgress = false;
  int[] uIds;

  public MasterNode(String name, int n, int[] uIds) {
    this.name = name;
    this.uIds = uIds;
    this.n = n;
  }

  /**
   * Will be called once all threads are done
   */
  private void updateRoundProgress() {
    // if (round % 100000 == 0) System.out.println("All threads done in round " + round);
    for (Node node: nodes) {
      node.setHold(true);
    }
    roundInProgress = false;

    if (isLeaderElected) {
      terminate = true;
      System.out.println("-----Leaders-----");
      for (Node node: nodes) {
        node.checkLeader();
      }
    }
  }

  /**
   * Will be called when master is ready to conduct a new round
   */
  private  void incrementRound() {
    // Stop future rounds if leader is elected
    if (isLeaderElected) return;

    round = round + 1;
    roundInProgress = true;

    // Pass round information to all threads and give go ahead signal
    for (Node node: nodes) {
      node.setRound(round);
      node.setHold(false);
    }
  }

  /**
   * Elected child node notifies master to stop further rounds
   */
  public void leaderElected() {
    this.isLeaderElected = true;
    // Terminate all threads once leader is elected
    for (Node node: nodes) {
      // Terminate all threads by sending a kill signal
      node.setTerminate(true);
    }
  }

  @Override
  public void run() {
    System.out.println("Running " + process.getName() + " thread");
    // Barrier for master to control child nodes
    CyclicBarrier masterBarrier = new CyclicBarrier(1, () -> incrementRound());

    // Barrier for child nodes to inform on completion of round 'x'
    CyclicBarrier nodesBarrier = new CyclicBarrier(n, () -> updateRoundProgress());

    // Create all nodes
    nodes = new Node[n];
    for (int i = 0; i < n; i++) {
      nodes[i] = new Node(uIds[i], this, nodesBarrier);
    }

    // Set neighbor node
    for (int i = 0; i < nodes.length - 1; i++) {
      nodes[i].setNeighbor(nodes[i + 1]);
    }
    nodes[nodes.length - 1].setNeighbor(nodes[0]);

    // Start all nodes
    for (Node node: nodes) {
      node.start();
    }

    try {
      while (!terminate) {
        // Hold master if round is in progress
        if (roundInProgress) continue;
        masterBarrier.await();
      }
    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public void start() {
    System.out.println("Starting thread for " + name);
    if (process == null) {
      process = new Thread(this, name);
      process.start();
    }
  }
}

public class Main {
  public static void main(String[] args) {
    File file = new File("input.dat");
    Scanner in;
    try {
      in = new Scanner(file);
    } catch (FileNotFoundException e) {
      e.printStackTrace();
      return;
    }

    int n = 0;
    if(in.hasNext())
      n = in.nextInt();

    // int[] uIds = new int[] {17, 18, 19, 30, 25};
    int[] uIds = new int[n];
    for( int i = 0; i < n; i++) {
      uIds[i] = in.nextInt();
    }

    for (int num: uIds) System.out.print(num + " ");
    System.out.println();

    MasterNode master = new MasterNode("master", n, uIds);
    master.start();
  }
}
