#!/usr/bin/env bash
# EC2 에서 운영 compose 를 다루는 래퍼. 마지막으로 배포된 이미지 태그(.image)를 자동으로 넘긴다.
#   ./dc.sh ps
#   ./dc.sh logs -f --tail 200 app
#   ./dc.sh restart app
# 배포 워크플로(server-cd)도 이 스크립트로 pull · up 한다.
set -euo pipefail
cd "$(dirname "$0")"

if [ -z "${IMAGE:-}" ]; then
  [ -f .image ] || { echo ".image 가 없다 — 아직 한 번도 배포되지 않았다"; exit 1; }
  IMAGE="$(cat .image)"
fi
export IMAGE

exec docker compose -f compose.prod.yaml "$@"
