# Leader Re-Election with ZooKeeper

이 프로젝트는 Apache ZooKeeper를 사용하여 분산 환경에서 리더 재선출(Leader Re-Election) 프로세스를 구현한 예제입니다.  
프로젝트는 후보 노드를 등록하고, 전체 후보 목록을 비교하여 자신의 순위를 평가한 후, 자신보다 앞선 후보가 사라지면 리더 재선출을 수행하는 기능을 포함합니다.

## 개요

- **리더 후보 등록**: 현재 노드를 "/election" 네임스페이스 아래의 Ephemeral Sequential znode로 생성하여 후보로 등록합니다.
- **리더 재선출**: 전체 후보 목록을 정렬한 후, 자신의 znode보다 바로 앞선 후보를 감시하여 해당 노드가 삭제되면 리더 재선출 알고리즘을 실행합니다.
- **ZooKeeper 감시**: Watcher 인터페이스를 구현하여 연결 상태 및 노드 이벤트(생성, 삭제, 데이터 변경 등)를 감시하고 처리합니다.

## 필수 전제 조건

- **Java Development Kit (JDK)**  
  JDK 8 이상(예: OpenJDK 11 또는 OpenJDK 17 등)을 설치하세요.

- **Apache ZooKeeper**
    - ZooKeeper 서버 설치가 필요합니다.
    - ZooKeeper의 `zookeeper.jar` (또는 관련 라이브러리)이 클래스패스에 포함되어야 합니다.
    - ZooKeeper는 **zoo.cfg** 파일을 사용하여 설정을 관리합니다. 이 파일은 ZooKeeper 설치 디렉토리의 `conf` 폴더에 있습니다.

- **Maven (선택 사항)**  
  Maven을 사용하여 프로젝트를 빌드할 수 있습니다.

## 설치 및 구성

1. **ZooKeeper 다운로드 및 설치**
    - [Apache ZooKeeper 공식 웹사이트](https://zookeeper.apache.org/releases.html)에서 적절한 버전을 다운로드합니다.
    - 다운로드한 ZooKeeper 압축 파일을 풀고, 설치 디렉토리로 이동합니다.

2. **zoo.cfg 설정**
    - ZooKeeper 설치 디렉토리의 `conf` 폴더에 있는 `zoo_sample.cfg` 파일을 복사하여 `zoo.cfg`로 이름을 변경합니다.
    - 기본 설정은 로컬호스트에서 ZooKeeper 서버를 실행하도록 구성되어 있으며, 포트 번호(기본 2181) 및 데이터 디렉토리 경로 등을 확인/수정하세요.

3. **ZooKeeper 서버 실행**
    - ZooKeeper 설치 디렉토리의 `bin` 폴더로 이동합니다.
    - **Unix/Linux/macOS**:
      ```bash
      ./zkServer.sh start
      ```
    - **Windows**:
      ```cmd
      zkServer.cmd
      ```
    - ZooKeeper 서버가 정상적으로 시작되면, 콘솔에 관련 로그가 출력됩니다.

4. **ZooKeeper CLI 사용**
    - ZooKeeper 설치 디렉토리의 `bin` 폴더에서 CLI를 실행하여 ZooKeeper 클러스터 상태를 확인할 수 있습니다.
    - **Unix/Linux/macOS**:
      ```bash
      ./zkCli.sh
      ```
    - **Windows**:
      ```cmd
      zkCli.cmd
      ```

## 프로젝트 빌드 및 실행

1. **클래스패스에 ZooKeeper 라이브러리 추가**
    - 프로젝트를 빌드할 때 `zookeeper.jar`와 기타 필요한 ZooKeeper 관련 JAR 파일을 클래스패스에 포함시켜야 합니다.
    - Maven 프로젝트의 경우, `pom.xml`에 ZooKeeper 의존성을 추가하면 Maven Central Repository에서 자동으로 다운로드됩니다.

2. **빌드**  
   터미널에서 프로젝트 루트 디렉토리로 이동한 후, 다음 명령어로 프로젝트를 빌드합니다.
   ```bash
   mvn clean package

## 테스트 실행 방법 (여러 터미널 사용)

리더 재선출 기능을 테스트하려면 여러 개의 터미널(또는 콘솔 탭)을 사용하여 여러 인스턴스로 애플리케이션을 실행할 수 있습니다.

### 1. 여러 터미널 창 띄우기
최소 2개 이상의 터미널 창을 엽니다.

### 2. 각 터미널에서 애플리케이션 실행
각 터미널 창에서 다음 명령어를 실행합니다.

```bash
java -jar target/your-jar-file-name.jar
```

### 3. Ctrl + C를 누르면서 노드를 강제로 종료
노드를 강제로 종료하면서, 리더를 재선출 하는 것을 눈으로 확인할 수 있습니다.