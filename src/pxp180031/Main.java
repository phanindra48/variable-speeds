package pxp180031;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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
  CyclicBarrier masterBarrier;

  public Node(int id, CyclicBarrier masterBarrier, CyclicBarrier barrier) {
    this.name = id + "";
    this.uid = id;
    this.minUID = id;
    this.round = 1;
    this.barrier = barrier;
    this.masterBarrier = masterBarrier;
  }

  public void setNeighbor(Node neighbor) {
    this.neighbor = neighbor;
  }

  public void setHold(boolean hold) {
    this.hold = hold;
  }

  public void setTerminate(boolean terminate) { this.terminate = terminate; }

  public void setRound(int round) { this.round = round; }

  public void sendMessage(int incomingUID, int incomingRound) {
    if (this.uid == incomingUID) {
      System.out.printf("Leader %s elected in round %s! \n", uid, round);
      return;
    }
    int newMin = Math.min(minUID, incomingUID);
    if (incomingUID < minUID) this.lastRound = incomingRound;

    minUID = newMin;
  }

  public void checkLeader() {
    if (this.uid == minUID) {
      System.out.printf("UID %s elected in round %s! \n", uid, round);
      return;
    }
    System.out.printf("UID %s not a leader\n", uid);
  }

  public boolean isLeader() { return this.uid == minUID; }

  public boolean isValidRound() {
    return round == lastRound + (int) Math.pow(2, minUID);
  }

  @Override
  public void run() {
    // System.out.println("Running thread " + process.getName());
    try {
      while (!terminate) {
        while (!terminate && hold) continue;
        // System.out.printf("Round %s Thread %s \n", round, uid);
        if (isValidRound()) {
          neighbor.sendMessage(minUID, round);
        }

        hold = true;
        // System.out.printf("Done with thread %s in Round %s\n", process.getName(), round);
        barrier.await();
      }

    } catch (Exception ex) {
      System.err.println(ex);
    }
  }

  public void start() {
    System.out.println("Starting " + name + " thread");
    if (process == null) {
      process = new Thread(this, name);
      process.start();
    }
  }
}

class MasterNode implements Runnable {
  Thread process;
  String name;
  int n;
  int maxRounds;
  volatile boolean terminate = false;
  volatile int round = 0;
  Node[] nodes;
  volatile boolean roundInProgress = false;
  int[] uIds;
  private static final Object LOCK = new Object();

  public MasterNode(String name, int n, int[] uIds) {
    this.name = name;
    this.uIds = uIds;
    this.n = n;
    this.maxRounds = maxRounds();
  }

  /**
   * Max rounds in which a leader has to be elected!
   * @return
   */
  private int maxRounds() {
    int[] temp = new int[n];
    System.arraycopy(uIds, 0, temp, 0, n);
    Arrays.sort(temp);
    System.out.println(n * (int)Math.pow(2, temp[0]));
    return n * (int)Math.pow(2, temp[0]);
  }

  /**
   * Will be called once all threads are done
   */
  private void updateRoundProgress() {
    if (round % 100000 == 0) System.out.println("All threads done in round " + round);
    // System.out.println("-----------------------------");
    for (Node node: nodes) {
      node.setHold(true);
    }
    roundInProgress = false;
  }

  /**
   * Will be called when master is ready to conduct a new round
   */
  private  void incrementRound() {
    round = round + 1;
    roundInProgress = true;

    if (round > maxRounds) {
      System.out.println("-----Leaders-----");
      for (Node node: nodes) {
        if (node.isLeader()) {
          this.terminate = true;
        }
        node.checkLeader();
        // Terminate all threads by sending a kill signal
        node.setTerminate(true);
      }
      return;
    }
    for (Node node: nodes) {
      node.setRound(round);
      node.setHold(false);
    }
  }

  @Override
  public void run() {
    System.out.println("Running " + process.getName() + " thread");
    // TODO: Can this be moved to start?
    CyclicBarrier masterBarrier = new CyclicBarrier(1, () -> incrementRound());
    CyclicBarrier nodesBarrier = new CyclicBarrier(n, () -> updateRoundProgress());

    // Create all nodes
    nodes = new Node[n];
    for (int i = 0; i < n; i++) {
      nodes[i] = new Node(uIds[i], masterBarrier, nodesBarrier);
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

        if (roundInProgress) continue;

        masterBarrier.await();
      }

    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }

  public void start() {
    System.out.println("Starting " + name + " thread");
    if (process == null) {
      process = new Thread(this, name);
      process.start();
    }
  }
}

public class Main {

  public static int[] randomArray(int min, int max, int n) {
    Random rand = new Random();
    List<Integer> list = new ArrayList<Integer>();
    int[] randArr = new int[n];
    int counter = 0;
    while (counter < n) {
      int num = min + rand.nextInt(max);
      if (list.contains(num)) continue;
      list.add(num);
      counter++;
    }
    counter = 0;
    for (Integer num: list) randArr[counter++] = num;
    return randArr;
  }

  public static void main(String[] args) {
    System.out.println("Main");
	  int n = 5;

	  // int[] uIds = new int[] {4, 7, 2, 5, 6};
	  int[] uIds = new int[] {18, 17, 14, 15, 32};
	  // int[] uIds = new int[] {7, 11, 9, 10, 12};
	  // int[] uIds = new int[] {2, 7, 5, 6, 4};
	  // int[] uIds = new int[] {2, 7, 5, 6, 4};
	  // for (int i = 0; i < n; i++) uIds[i] = i + 1;

    // uIds = randomArray(20, 30, n);

    for (int num: uIds) System.out.print(num + " ");
    System.out.println();

    MasterNode master = new MasterNode("master", n, uIds);
    master.start();
  }
}
