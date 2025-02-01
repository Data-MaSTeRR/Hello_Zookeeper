import org.apache.zookeeper.WatchedEvent; // ZooKeeper에서 발생하는 이벤트(예: 노드 생성, 삭제, 변경 등)를 나타내는 객체
import org.apache.zookeeper.Watcher;      // ZooKeeper에서 이벤트를 감지하기 위해 구현해야 하는 인터페이스
import org.apache.zookeeper.ZooKeeper;      // ZooKeeper 서버와의 연결 및 다양한 메서드를 제공하는 클래스

import java.io.IOException;

public class LeaderElection implements Watcher {

    // ZooKeeper 서버의 주소와 세션 타임아웃 시간을 정의합니다.
    private static final String Zookeeper_Address = "localhost:2181";
    private static final int Zookeeper_SessionTimeout = 3000;

    // ZooKeeper 서버와의 연결을 나타내는 객체입니다.
    private ZooKeeper zooKeeper;

    // main 메서드는 애플리케이션의 시작점입니다.
    public static void main(String [] args) {
        LeaderElection leaderElection = new LeaderElection();
        leaderElection.connectToZooKeeper();
    }

    // ZooKeeper 서버와의 연결을 생성하는 메서드입니다.
    public void connectToZooKeeper() {
        try {
            // ZooKeeper 객체 생성 시 서버 주소, 세션 타임아웃, 그리고 Watcher 인터페이스를 구현한 현재 인스턴스를 인자로 전달합니다.
            this.zooKeeper = new ZooKeeper(Zookeeper_Address, Zookeeper_SessionTimeout, this);
        } catch (IOException e) {
            // 연결 과정에서 발생할 수 있는 IOException을 RuntimeException으로 감싸서 던집니다.
            throw new RuntimeException(e);
        }
    }

    /**
     * Watcher 인터페이스에서 상속받은 process 메서드를 구현합니다.(무조건 구현해야 함 - 인터페이스의 정의)
     * 이 메서드는 ZooKeeper에서 이벤트가 발생할 때 호출됩니다.
     *
     * 예제에서는 서버와의 연결 상태를 확인하여,
     * 연결이 성공적으로 이루어졌을 경우 "Successfully connected to ZooKeeper" 메시지를 출력합니다.
     */
    @Override
    public void process(WatchedEvent watchedEvent) {
        switch (watchedEvent.getType()) {
            // 이벤트 타입이 None인 경우, 이는 주로 연결 상태의 변화를 의미합니다.
            case None:
                // 이벤트 상태가 SyncConnected이면, ZooKeeper와의 연결이 성공한 상태입니다.
                if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                    System.out.println("Successfully connected to ZooKeeper");
                }
                break; // switch-case에서 break를 사용하여 다른 case로 넘어가는 것을 방지합니다.
            // 필요에 따라 다른 이벤트 타입(NodeCreated, NodeDeleted, NodeDataChanged, NodeChildrenChanged 등)에 대한 처리를 추가할 수 있습니다.
            default:
                break;
        }
    }
}
