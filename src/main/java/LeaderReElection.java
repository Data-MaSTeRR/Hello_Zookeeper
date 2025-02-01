import org.apache.zookeeper.*;            // ZooKeeper 관련 모든 클래스를 임포트합니다.
import org.apache.zookeeper.data.Stat;

import java.io.IOException;                // 입출력 예외 처리를 위한 클래스를 임포트합니다.
import java.util.Collections;              // List 정렬을 위한 유틸리티 클래스를 임포트합니다.
import java.util.List;                     // List 인터페이스를 임포트합니다.

public class LeaderReElection implements Watcher {

    // ZooKeeper 서버의 주소 (로컬호스트의 기본 포트 2181 사용)
    private static final String ZOOKEEPER_ADDRESS = "localhost:2181";

    // ZooKeeper와 연결할 때 사용할 세션 타임아웃 (밀리초 단위, 여기서는 3000ms 즉 3초)
    private static final int SESSION_TIMEOUT = 3000;

    // 리더 선출에 사용될 네임스페이스 경로
    private static final String ELECTION_NAMESPACE = "/election";

    // ZooKeeper 서버와의 연결을 나타내는 객체
    private ZooKeeper zooKeeper;

    // 이 인스턴스가 생성한 후보 znode의 이름 (전체 경로에서 네임스페이스를 제거한 이름)
    private String currentZnodeName;

    /**
     * main 메서드는 프로그램의 시작점입니다.
     * 여기서는 다음의 순서로 작업을 수행합니다.
     * 1. ZooKeeper 서버와 연결
     * 2. 현재 노드를 리더 선출 후보로 등록 (volunteerForLeadership)
     * 3. 리더 선출 알고리즘 수행 (electLeader)
     * 4. 연결 상태 변경 이벤트를 대기 (run)
     * 5. 연결 종료 (close)
     */
    public static void main(String[] args) {
        LeaderReElection leaderElection = new LeaderReElection();

        // ZooKeeper 서버와 연결을 시도합니다.
        leaderElection.connectToZooKeeper();

        // 리더 선출 후보로 등록합니다.
        leaderElection.volunteerForLeadership();

        // 리더 선출 알고리즘을 실행하여 자신이 리더인지 확인합니다.
        leaderElection.reelectLeader();

        // 연결 상태를 유지하며 대기합니다.
        // 이 대기는 보통 연결이 종료되거나 세션 만료 등의 이벤트가 발생할 때 해제됩니다.
        leaderElection.run();

        // 연결을 정상적으로 종료합니다.
        leaderElection.close();

        // 연결 종료 후 종료 메시지를 출력합니다.
        System.out.println("Disconnected from ZooKeeper, exiting application");
    }

    /** 리더 선출 알고리즘 구현 ************************************************************************************************* */

    /**
     * volunteerForLeadership() 메서드는 현재 노드를 리더 선출 후보로 등록합니다.
     * - "/election" 네임스페이스 아래에 "c_" 접두사가 붙은 Ephemeral Sequential znode를 생성합니다.
     * - Ephemeral znode는 클라이언트 세션이 종료되면 자동으로 삭제되므로, 동적 리더 선출에 유용합니다.
     * - Sequential 옵션을 사용하면 ZooKeeper가 각 후보 znode에 순차적인 번호를 부여하여, 순서를 쉽게 비교할 수 있습니다.
     */
    public void volunteerForLeadership() {
        try {
            // c는 candidate(후보)의 약자입니다.
            String znodePrefix = ELECTION_NAMESPACE + "/c_";
            // zooKeeper.create()는 znode를 생성하며, 생성된 znode의 전체 경로를 반환합니다.
            // OPEN_ACL_UNSAFE는 ACL(접근 제어 목록)을 모두 허용하는 설정이며,
            // CreateMode.EPHEMERAL_SEQUENTIAL는 일시적이고 순차적인 znode를 생성함을 의미합니다.
            String znodeFullPath = zooKeeper.create(
                    znodePrefix,
                    new byte[]{},                // 데이터가 필요 없으므로 빈 바이트 배열 사용
                    ZooDefs.Ids.OPEN_ACL_UNSAFE, // 보안이 필요 없는 경우(테스트용) 사용
                    CreateMode.EPHEMERAL_SEQUENTIAL
            );

            // 생성된 znode의 전체 경로를 출력합니다.
            System.out.println("Znode created: " + znodeFullPath);
            // 전체 경로에서 "/election/" 부분을 제거하여, 후보 노드의 이름만을 저장합니다.
            currentZnodeName = znodeFullPath.replace(ELECTION_NAMESPACE + "/", "");
        } catch (KeeperException | InterruptedException e) {
            // 예외 발생 시, InterruptedException이 발생하면 인터럽트 상태를 복원합니다.
            Thread.currentThread().interrupt();
            System.err.println("Exception while volunteering for leadership: " + e.getMessage());
            // 추가 처리(재시도, 로그 기록 등)를 구현할 수 있습니다.
        }
    }

    /**
     * electLeader() 메서드는 리더 선출을 수행합니다.
     * - /election 네임스페이스 아래에 있는 모든 후보 znode들을 가져옵니다.
     * - 가져온 znode 리스트를 정렬하여, 가장 작은(순서가 앞선) znode가 리더임을 결정합니다.
     * - 현재 노드의 이름(currentZnodeName)과 비교하여 자신이 리더인지 여부를 출력합니다.
     */
    public void reelectLeader() {
        try {
            Stat predecessorStat = null;
            String predecessorZnodeName = "";

            while (predecessorStat == null) {
                // /election 네임스페이스 아래의 자식 znode 목록을 가져옵니다.
                List<String> children = zooKeeper.getChildren(ELECTION_NAMESPACE, false);
                // 가져온 znode 리스트를 오름차순으로 정렬합니다.
                Collections.sort(children);
                // 정렬된 목록의 첫 번째 요소가 가장 작은 순번의 znode, 즉 리더 후보입니다.
                String smallestChild = children.get(0);

                // 현재 노드가 리더 후보와 동일한지 확인합니다.
                if (smallestChild.equals(currentZnodeName)) {
                    System.out.println("I am the leader");
                } else {
                    System.out.println("I am not the leader");
                    int predecessorIndex = Collections.binarySearch(children, currentZnodeName) - 1;
                    predecessorZnodeName = children.get(predecessorIndex);
                    predecessorStat = zooKeeper.exists(ELECTION_NAMESPACE + "/" + predecessorZnodeName, this);
                }
            }

            System.out.println("I'm znode: " + currentZnodeName);
            System.out.println("Watching znode: " + predecessorZnodeName);
        } catch (KeeperException | InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Exception while electing leader: " + e.getMessage());
            // 추가적인 예외 처리가 필요할 수 있습니다.
        }
    }

    /** Zookeeper 클라이언트의 스레딩 모델과 Java API**************************************************************************** */

    /**
     * connectToZooKeeper() 메서드는 ZooKeeper 서버와 연결을 생성합니다.
     * - ZooKeeper 생성자에 서버 주소, 세션 타임아웃, 그리고 이벤트 처리를 위한 Watcher(현재 객체)를 전달합니다.
     * - 연결이 성공하면 ZooKeeper 객체를 반환하며, 실패 시 IOException을 처리합니다.
     */
    public void connectToZooKeeper() {
        try {
            // ZooKeeper 객체 생성 시 연결 요청을 보내고, 이 객체를 통해 서버와 상호작용합니다.
            zooKeeper = new ZooKeeper(ZOOKEEPER_ADDRESS, SESSION_TIMEOUT, this);
        } catch (IOException e) {
            System.err.println("Failed to connect to ZooKeeper: " + e.getMessage());
            e.printStackTrace();
            System.exit(1); // 연결 실패 시 프로그램을 종료합니다.
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
                // 대기 상태: notify 또는 notifyAll() 호출 시까지 현재 스레드는 여기서 멈춥니다.
                zooKeeper.wait();
            } catch (InterruptedException e) {
                // wait() 도중 인터럽트가 발생하면, 현재 스레드의 인터럽트 상태를 복원하고 에러 메시지를 출력합니다.
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted while waiting: " + e.getMessage());
            }
        }
    }

    /**
     * close() 메서드는 ZooKeeper와의 연결을 안전하게 종료합니다.
     * - 연결이 존재하면 zooKeeper.close()를 호출하여 자원을 정리합니다.
     */
    public void close() {
        if (zooKeeper != null) {
            try {
                // ZooKeeper 연결 종료: 연결이 종료되면 클라이언트와 서버 간의 세션이 종료됩니다.
                zooKeeper.close();
            } catch (InterruptedException e) {
                // 연결 종료 도중 인터럽트가 발생하면, 인터럽트 상태를 복원하고 에러 메시지를 출력합니다.
                Thread.currentThread().interrupt();
                System.err.println("Interrupted while closing ZooKeeper connection: " + e.getMessage());
            }
        }
    }

    /**
     * process() 메서드는 ZooKeeper의 Watcher 인터페이스를 구현한 것으로,
     * ZooKeeper에서 발생한 이벤트를 처리합니다.
     * - 이벤트 타입이 None인 경우, 주로 연결 상태의 변화를 나타냅니다.
     * - 연결이 성공적이면 "Successfully connected" 메시지를 출력하고,
     *   그렇지 않은 경우 대기 중인 스레드를 깨워 run() 메서드의 wait()를 해제합니다.
     *
     * @param watchedEvent ZooKeeper에서 발생한 이벤트 정보를 담은 객체
     */
    @Override
    public void process(WatchedEvent watchedEvent) {
        // 이벤트 타입에 따라 처리할 로직을 구분합니다.
        switch (watchedEvent.getType()) {
            case None:
                // 연결 상태 변화 이벤트: 연결 성공 혹은 연결 종료/세션 만료 등
                if (watchedEvent.getState() == Event.KeeperState.SyncConnected) {
                    // 성공적으로 연결되었을 때
                    System.out.println("Successfully connected to ZooKeeper");
                } else {
                    // 연결이 끊기거나 세션이 만료되었을 때
                    synchronized (zooKeeper) {  // 대기 중인 스레드를 깨우기 위해 zooKeeper 객체에 대해 락을 획득합니다.
                        System.out.println("Disconnected from ZooKeeper event");
                        zooKeeper.notifyAll();  // run() 메서드에서 대기 중인 스레드를 깨웁니다.
                    }
                }
                break;
            case NodeDeleted:
                reelectLeader();
                break;
            default:
                // 추가적인 이벤트(예: 노드 생성, 삭제, 데이터 변경 등)가 발생할 경우, 해당 이벤트에 대한 처리 로직을 추가할 수 있습니다.
                break;
        }
    }
}

