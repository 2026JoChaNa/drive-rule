# 서버 배포 (EC2 + Docker)

## 흐름

1. `main` 에 서버 코드가 들어오면 `server-ci` 가 테스트
2. 성공하면 `server-cd` 가 jar → Docker 이미지 → Docker Hub 푸시
   - 태그: `<DOCKERHUB_USERNAME>/drive-rule-server:sha-<커밋 7자리>` + `latest`
   - 계정명이 secret 이라 Actions 로그에는 `***/drive-rule-server:…` 로 가려져 보인다
3. EC2 에 SSH 로 접속해 `compose.prod.yaml` 갱신 → 새 이미지로 재시작 → `/actuator/health` 가 `UP` 이면 완료
   - 150초 안에 `UP` 이 안 되면 실패 처리하고 앱 로그를 출력 (자동 롤백은 없음)

수동 배포: GitHub → Actions → `server-cd` → Run workflow (main)

## GitHub 설정 (최초 1회)

Settings → Secrets and variables → Actions

| 종류 | 이름 | 값 |
|---|---|---|
| Secret | `DOCKERHUB_USERNAME` | Docker Hub 계정명 |
| Secret | `DOCKERHUB_TOKEN` | Docker Hub → Account settings → Personal access tokens (Read & Write) |
| Secret | `EC2_HOST` | EC2 퍼블릭 IP 또는 도메인 |
| Secret | `EC2_USER` | SSH 사용자 (Amazon Linux 면 `ec2-user`) |
| Secret | `EC2_SSH_KEY` | EC2 접속용 개인키(.pem) 전체 내용 |

- 배포 job 은 `production` 환경을 쓴다. Settings → Environments → production 에서 승인자를 걸면 배포 전 승인을 받는다
- Docker Hub 저장소 `drive-rule-server` 는 비공개로 만들어 둔다 (첫 푸시 때 자동 생성되면 공개일 수 있음)
- EC2 의 Docker Hub 로그인은 배포 워크플로가 매번 secret 으로 한다 (수동 로그인 불필요)

## EC2 준비 — Amazon Linux 2023 · x86_64

현재 서버(13.124.175.42)는 2026-11-11 에 아래 상태로 준비되어 있다: Docker 25 · compose v5.7.0 · 스왑 2GB · EBS 16GB · `~/drive-rule/.env`

## 운영 중 조작 (EC2 의 `~/drive-rule`)

배포 워크플로가 마지막 배포 이미지 태그를 `.image` 에 남긴다. compose 를 직접 부르지 말고 `dc.sh` 를 쓴다.

```bash
cd ~/drive-rule
./dc.sh ps                      # 상태
./dc.sh logs -f --tail 200 app  # 앱 로그
./dc.sh restart app             # 재시작
docker stats --no-stream        # 메모리 (한도: 앱 768MB, MySQL 512MB)
```

- 이전 버전으로 되돌리기: Actions → `server-cd` → 되돌릴 커밋의 실행 → Re-run (그 커밋 이미지로 다시 배포)
- 디스크: VS Code Remote-SSH 로 접속하면 업데이트마다 `~/.vscode-server/cli/servers` 에 버전이 쌓인다 (버전당 약 650MB). 가끔 최신 1개만 남기고 지운다

## EC2 처음부터 다시 준비할 때

```bash
# Docker (AL2023 기본 저장소) + 자동 시작 + 그룹 (재접속 후 적용)
sudo dnf install -y docker
sudo systemctl enable --now docker
sudo usermod -aG docker ec2-user

# compose 플러그인 — AL2023 저장소에 없어 공식 바이너리 설치
VER=$(curl -fsSL https://api.github.com/repos/docker/compose/releases/latest | grep '"tag_name"' | head -1 | cut -d'"' -f4)
sudo mkdir -p /usr/local/lib/docker/cli-plugins
sudo curl -fsSL "https://github.com/docker/compose/releases/download/${VER}/docker-compose-linux-x86_64" \
  -o /usr/local/lib/docker/cli-plugins/docker-compose
sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

# 스왑 2GB — 메모리 1.9GB 에 MySQL + Spring 을 같이 띄우므로
sudo dd if=/dev/zero of=/swapfile bs=1M count=2048
sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 배포 폴더와 환경변수 (권한 600)
mkdir -p ~/drive-rule && cd ~/drive-rule
vi .env        # server/.env.example 을 복사해 값 채우기. DB 비밀번호는 무작위로
chmod 600 .env
```

- 보안 그룹 인바운드
  - **22 (SSH): GitHub Actions 러너가 배포하려고 접속한다** — 팀 IP 로만 막으면 배포가 실패한다. 러너 IP 는 고정되지 않으므로 0.0.0.0/0 + 키 인증만 허용이 현실적이다
  - 8080 (또는 `.env` 의 `APP_PORT`)
  - MySQL(3306)은 열지 않는다 (compose 내부 네트워크 전용)
- 메모리 한도 (`compose.prod.yaml`): 앱 768MB, MySQL 512MB. 인스턴스를 키우면 함께 올린다
- **x86_64(amd64)** 기준. Graviton(arm64)으로 바꾸면 `server-cd.yml` 의 `platforms` 와 compose 바이너리 이름을 바꾼다

## 로컬에서 이미지 확인

```powershell
cd server
.\gradlew.bat bootJar
docker build -t drive-rule-server .
```
