import org.apache.zookeeper.*;            // ZooKeeper 관련 모든 클래스를 임포트합니다.
import org.apache.zookeeper.data.Stat;      // ZooKeeper의 상태 정보를 담는 Stat 클래스를 임포트합니다.

import java.io.IOException;                // 입출력 예외 처리를 위한 클래스를 임포트합니다.
import java.util.List;                     // List 인터페이스를 임포트합니다.

public class WatcherMonitoring implements Watcher {

    // ZooKeeper 서버의 주소 (로컬호스트의 기본 포트 2181 사용)
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";

    // ZooKeeper와 연결할 때 사용할 세션 타임아웃 (밀리초 단위, 여기서는 3000ms 즉 3초)
    private static final int SESSION_TIMEOUT = 3000;

    // 감시할 대상 znode의 경로
    private static final String TARGET_ZONE = "/target_znode";

    // ZooKeeper 서버와의 연결을 나타내는 객체
    private ZooKeeper zooKeeper;

    public static void main(String[] args) {
        // WatcherMonitoring 클래스의 인스턴스를 생성합니다.
        WatcherMonitoring watcherMonitoring = new WatcherMonitoring();

        // ZooKeeper 서버와 연결을 시도합니다.
        watcherMonitoring.connectToZooKeeper();

        // TARGET_ZONE znode에 대한 감시를 시작합니다.
        // 이 메서드는 TARGET_ZONE 노드의 존재 여부, 데이터, 자식 노드를 확인하며 Watcher를 등록합니다.
        try {
            watcherMonitoring.watchTargetZnode();
        } catch (KeeperException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 연결 상태를 유지하며 대기합니다.
        // 이 대기는 보통 연결이 종료되거나 세션 만료 등의 이벤트가 발생할 때 해제됩니다.
        watcherMonitoring.run();

        // 연결을 정상적으로 종료합니다.
        watcherMonitoring.close();
    }

    /**
     * connectToZooKeeper() 메서드는 ZooKeeper 서버와 연결을 생성합니다.
     * - ZooKeeper 생성자에 서버 주소, 세션 타임아웃, 그리고 이벤트 처리를 위한 Watcher(현재 객체)를 전달합니다.
     * - 연결이 성공하면 ZooKeeper 객체가 생성되어 서버와 상호작용할 수 있습니다.
     */
    public void connectToZooKeeper() {
        try {
            // ZooKeeper 객체 생성 시 연결 요청을 보내고, 이 객체를 통해 서버와 통신합니다.
            zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
        } catch (IOException e) {
            // 연결 중 IOException이 발생하면, 에러 메시지를 출력하고 프로그램을 종료합니다.
            System.err.println("Failed to connect to ZooKeeper: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * run() 메서드는 연결 상태 변경 이벤트가 발생할 때까지 현재 스레드를 대기 상태로 만듭니다.
     * - synchronized 블록을 사용하여 zooKeeper 객체에 대한 모니터 락을 획득합니다.
     * - zooKeeper.wait()를 호출하여, 다른 스레드에서 notify 또는 notifyAll()이 호출될 때까지 대기합니다.
     */
    public void run() {
        synchronized (zooKeeper) {  // zooKeeper 객체를 모니터(락)로 사용합니다.
            try {
                // 현재 스레드를 대기 상태로 전환하여, 다른 스레드가 notify 또는 notifyAll()을 호출할 때까지 멈춥니다.
                zooKeeper.wait();
            } catch (InterruptedException e) {
                // 대기 도중 인터럽트가 발생하면, 현재 스레드의 인터럽트 상태를 복원하고 에러 메시지를 출력합니다.
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted while waiting: " + e.getMessage());
            }
        }
    }

    /**
     * close() 메서드는 ZooKeeper와의 연결을 안전하게 종료합니다.
     * - 연결이 존재하면 zooKeeper.close()를 호출하여 서버와의 세션을 종료하고 자원을 정리합니다.
     */
    public void close() {
        if (zooKeeper != null) {
            try {
                // ZooKeeper 연결 종료: 서버와의 세션이 종료되고, 관련 자원이 정리됩니다.
                zooKeeper.close();
            } catch (InterruptedException e) {
                // 연결 종료 도중 인터럽트가 발생하면, 인터럽트 상태를 복원하고 에러 메시지를 출력합니다.
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while closing ZooKeeper connection: " + e.getMessage());
            }
        }
    }

    /**
     * watchTargetZnode() 메서드는 TARGET_ZONE znode에 대한 감시를 설정합니다.
     * - ZooKeeper.exists()를 사용하여 TARGET_ZONE 노드의 존재 여부를 확인하고, Watcher를 등록합니다.
     * - 노드가 존재하면, ZooKeeper.getData()와 ZooKeeper.getChildren()를 호출하여
     *   TARGET_ZONE 노드의 데이터와 자식 노드 목록을 가져오고, Watcher를 재등록합니다.
     *
     * @throws KeeperException    ZooKeeper 서버와의 통신 중 발생하는 예외
     * @throws InterruptedException 스레드가 인터럽트되었을 때 발생하는 예외
     */
    public void watchTargetZnode() throws KeeperException, InterruptedException {
        // TARGET_ZONE 노드의 존재 여부를 확인하고, 이 호출 시 Watcher(현재 객체)를 등록합니다.
        Stat stat = zooKeeper.exists(TARGET_ZONE, this);
        // 노드가 존재하지 않으면, stat이 null이 되어 이후 작업을 생략합니다.
        if (stat == null) {
            return;
        }

        // TARGET_ZONE 노드의 데이터를 가져오면서, 동시에 Watcher를 등록합니다.
        byte [] data = zooKeeper.getData(TARGET_ZONE, this, stat);
        // TARGET_ZONE 노드의 자식 노드 목록을 가져오면서, 동시에 Watcher를 등록합니다.
        List<String> children = zooKeeper.getChildren(TARGET_ZONE, this);

        // TARGET_ZONE 노드의 데이터를 문자열로 변환하고, 자식 노드 목록과 함께 출력합니다.
        System.out.println("Data: " + new String(data) + ", Children: " + children);
    }

    /**
     * process() 메서드는 ZooKeeper의 Watcher 인터페이스를 구현한 것으로,
     * ZooKeeper에서 발생한 이벤트를 처리합니다.
     * - 이벤트 타입에 따라 연결 상태 변화나 노드의 생성, 삭제, 데이터 변경, 자식 노드 변경 등의 이벤트를 처리합니다.
     * - 연결 관련 이벤트(None 타입)에서는 연결 성공 시 메시지를 출력하고,
     *   연결 종료나 세션 만료 등의 경우 대기 중인 스레드를 깨워 run() 메서드의 wait()를 해제합니다.
     * - 노드 관련 이벤트(NodeCreated, NodeDeleted, NodeDataChanged, NodeChildrenChanged)에서는
     *   해당 이벤트 발생을 알리는 메시지를 출력합니다.
     * - 마지막에 watchTargetZnode()를 호출하여 TARGET_ZONE에 대한 감시를 지속적으로 유지합니다.
     *
     * @param watchedEvent ZooKeeper에서 발생한 이벤트 정보를 담은 객체
     */
    @Override
    public void process(WatchedEvent watchedEvent) {
        // 이벤트 타입에 따라 처리 로직을 구분합니다.
        switch (watchedEvent.getType()) {
            case None:
                // 연결 상태 변화 이벤트: 연결 성공 혹은 연결 종료/세션 만료 등을 나타냅니다.
                if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                    // ZooKeeper와 성공적으로 연결되었을 때
                    System.out.println("Successfully connected to ZooKeeper");
                } else {
                    // 연결이 끊기거나 세션이 만료되었을 때
                    synchronized (zooKeeper) {  // 대기 중인 스레드를 깨우기 위해 zooKeeper 객체에 대해 락을 획득합니다.
                        System.out.println("Disconnected from ZooKeeper event");
                        // zooKeeper 객체에 대해 wait() 중인 모든 스레드를 깨워 run() 메서드의 대기를 해제합니다.
                        zooKeeper.notifyAll();
                    }
                }
                break;
            case NodeCreated:
                // TARGET_ZONE 노드가 생성되었을 때 발생하는 이벤트
                System.out.println(TARGET_ZONE + " was created");
                break;
            case NodeDeleted:
                // TARGET_ZONE 노드가 삭제되었을 때 발생하는 이벤트
                System.out.println(TARGET_ZONE + " was deleted");
                break;
            case NodeDataChanged:
                // TARGET_ZONE 노드의 데이터가 변경되었을 때 발생하는 이벤트
                System.out.println(TARGET_ZONE + " data changed");
                break;
            case NodeChildrenChanged:
                // TARGET_ZONE 노드의 자식 노드가 변경되었을 때 발생하는 이벤트
                System.out.println(TARGET_ZONE + " children changed");
                break;
            default:
                // 기타 이벤트는 별도로 처리하지 않고 무시합니다.
                break;
        }

        // TARGET_ZONE 노드에 대한 최신 정보를 확인하고 감시를 지속하기 위해 watchTargetZnode()를 호출합니다.
        try {
            watchTargetZnode();
        } catch (KeeperException | InterruptedException e) {
            // 예외가 발생하면 스택 트레이스를 출력합니다.
            e.printStackTrace();
        }
    }
}
