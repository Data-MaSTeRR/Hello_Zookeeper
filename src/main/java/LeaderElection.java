import org.apache.zookeeper.WatchedEvent; // ZooKeeper에서 발생하는 이벤트(예: 노드 생성, 삭제, 변경 등)를 나타내는 객체
import org.apache.zookeeper.Watcher;      // ZooKeeper에서 이벤트를 감지하기 위해 구현해야 하는 인터페이스
import org.apache.zookeeper.ZooKeeper;      // ZooKeeper 서버와의 연결 및 다양한 메서드를 제공하는 클래스

import java.io.IOException;               // 입출력 관련 예외 처리를 위한 클래스

// LeaderElection 클래스는 ZooKeeper의 이벤트를 처리하기 위해 Watcher 인터페이스를 구현합니다.
public class LeaderElection implements Watcher {

    // ZooKeeper 서버의 주소 (로컬호스트의 기본 포트 2181 사용)
    private static final String Zookeeper_Address = "localhost:2181";

    // ZooKeeper와 연결할 때 사용할 세션 타임아웃 (밀리초 단위, 여기서는 3000ms 즉 3초)
    private static final int Zookeeper_SessionTimeout = 3000;

    // ZooKeeper 서버와의 연결을 나타내는 객체
    private ZooKeeper zooKeeper;

    /**
     * main 메서드는 프로그램의 시작점입니다.
     * 여기서 ZooKeeper에 연결을 시도하고, 연결 대기 후 종료합니다.
     */
    public static void main(String[] args) throws InterruptedException {
        // LeaderElection 클래스의 인스턴스를 생성합니다.
        LeaderElection leaderElection = new LeaderElection();

        // ZooKeeper 서버와 연결을 시도합니다.
        leaderElection.connectToZooKeeper();

        // 연결 상태를 유지하며 대기합니다.
        leaderElection.run();

        // 연결을 정상적으로 종료합니다.
        leaderElection.close();

        // 연결 종료 후 종료 메시지를 출력합니다.
        System.out.println("Disconnected from ZooKeeper, exiting application");
    }

    /**
     * ZooKeeper 서버와 연결을 생성하는 메서드입니다.
     * ZooKeeper 생성자에 서버 주소, 세션 타임아웃, 그리고 이벤트 처리를 위한 Watcher(현재 객체)를 전달합니다.
     */
    public void connectToZooKeeper() {
        try {
            // ZooKeeper 객체 생성 - 이 과정에서 ZooKeeper 서버에 연결 요청을 보냅니다.
            this.zooKeeper = new ZooKeeper(Zookeeper_Address, Zookeeper_SessionTimeout, this);
        } catch (IOException e) {
            // 연결 중 IOException이 발생하면, RuntimeException으로 감싸서 던집니다.
            throw new RuntimeException(e);
        }
    }

    /**
     * run 메서드는 연결 상태가 변경될 때까지 현재 스레드를 대기 상태로 만듭니다.
     * synchronized 블록을 사용하여 zooKeeper 객체에 대해 락을 획득하고 wait()를 호출합니다.
     */
    public void run() throws InterruptedException {
        synchronized (zooKeeper) {  // zooKeeper 객체를 모니터(락)로 사용
            // 다른 스레드에서 notify 또는 notifyAll()이 호출될 때까지 대기합니다.
            zooKeeper.wait();
        }
    }

    /**
     * close 메서드는 ZooKeeper와의 연결을 정상적으로 종료합니다.
     */
    private void close() throws InterruptedException {
        this.zooKeeper.close();
    }

    /**
     * Watcher 인터페이스의 process 메서드를 구현합니다. (인터페이스에 있는 메서드는 상속 시 무조건 구현해야 함)
     * 이 메서드는 ZooKeeper에서 이벤트가 발생할 때마다 호출되며, 이벤트 타입과 상태에 따라 적절한 처리를 합니다.
     *
     * @param watchedEvent 발생한 이벤트 정보를 담고 있는 객체
     */
    @Override
    public void process(WatchedEvent watchedEvent) {
        // 이벤트 타입에 따라 처리 로직을 구분합니다.
        switch (watchedEvent.getType()) {
            // 이벤트 타입이 None인 경우, 주로 연결 상태의 변화를 의미합니다.
            case None:
                // 만약 이벤트 상태가 SyncConnected라면, ZooKeeper와의 연결이 성공적으로 이루어진 것입니다.
                if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                    System.out.println("Successfully connected to ZooKeeper");
                } else {
                    // 연결 상태가 SyncConnected가 아닌 경우 (예: 연결 종료, 세션 만료 등)
                    // synchronized 블록을 통해 대기 중인 스레드를 깨워 run() 메서드의 wait()를 해제합니다.
                    synchronized (zooKeeper) {
                        System.out.println("Disconnected from ZooKeeper event");
                        // zooKeeper 객체에 대해 wait() 중인 모든 스레드를 깨웁니다.
                        zooKeeper.notifyAll();
                    }
                }
                break; // switch 문 종료

            // 추가로 처리해야 할 다른 이벤트 타입에 대해서는 여기에 case 문을 추가할 수 있습니다.
            default:
                break;
        }
    }
}
