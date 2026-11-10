# 서버 배포 (EC2 + Docker)

## 흐름

1. `main` 에 서버 코드가 들어오면 `server-ci` 가 테스트
2. 성공하면 `server-cd` 가 jar → Docker 이미지 → Docker Hub 푸시
   - 태그: `<DOCKERHUB_USERNAME>/drive-rule-server:sha-<커밋 7자리>` + `latest`
3. EC2 에 SSH 로 접속해 `compose.prod.yaml` 갱신 → 새 이미지로 재시작 → `/actuator/health` 가 `UP` 이면 완료
   - 150초 안에 `UP` 이 안 되면 실패 처리하고 앱 로그를 출력 (자동 롤백은 없음)

수동 배포: GitHub → Actions → `server-cd` → Run workflow (main)

## GitHub 설정 (최초 1회)

Settings → Secrets and variables → Actions

| 종류 | 이름 | 값 |
|---|---|---|
| Variable | `DOCKERHUB_USERNAME` | Docker Hub 계정명 |
| Secret | `DOCKERHUB_TOKEN` | Docker Hub → Account settings → Personal access tokens (Read & Write) |
| Secret | `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| Secret | `EC2_USER` | SSH 사용자 (Ubuntu AMI 면 `ubuntu`) |
| Secret | `EC2_SSH_KEY` | EC2 접속용 개인키(.pem) 전체 내용 |

- 배포 job 은 `production` 환경을 쓴다. Settings → Environments → production 에서 승인자를 걸면 배포 전 승인을 받는다
- Docker Hub 저장소 `drive-rule-server` 는 비공개로 만들어 둔다 (첫 푸시 때 자동 생성되면 공개일 수 있음)

## EC2 준비 (최초 1회)

```bash
# Docker + compose 플러그인 (Ubuntu)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 재접속 후 적용

# 배포 폴더와 환경변수
mkdir -p ~/drive-rule && cd ~/drive-rule
vi .env        # server/.env.example 을 복사해 값 채우기

# 비공개 이미지를 받으려면 EC2 에서도 로그인
docker login -u <DOCKERHUB_USERNAME>
```

- 보안 그룹: 22(SSH, 팀 IP만), 8080(또는 `.env` 의 `APP_PORT`) 인바운드
- MySQL 은 외부에 포트를 열지 않는다 (compose 내부 네트워크 전용)
- 인스턴스는 **x86_64(amd64)** 기준. Graviton(arm64)을 쓰면 `server-cd.yml` 의 `platforms` 를 바꾼다

## 로컬에서 이미지 확인

```powershell
cd server
.\gradlew.bat bootJar
docker build -t drive-rule-server .
```
