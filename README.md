
## Subir infraestrutura

Na pasta `servicelayer`:

```powershell
docker compose up -d rabbitmq-broker mongodb
```

Ver status:

```powershell
docker compose ps
docker logs -f rabbitmq
docker logs -f mongodb
```

## Limpar tudo e iniciar do zero

Reset completo:

```powershell
docker compose down
docker compose up -d rabbitmq-broker mongodb
```

Limpar apenas o banco:

```powershell
docker exec mongodb mongosh "mongodb://root:root@localhost:27017/lakehouse_db?authSource=admin" --eval "db.dropDatabase()"
```

Limpar apenas as collections principais:

```powershell
docker exec mongodb mongosh "mongodb://root:root@localhost:27017/lakehouse_db?authSource=admin" --eval "db.bronze.deleteMany({}); db.ingestion_protocols.deleteMany({}); db.api_clients.deleteMany({})"
```

## Subir a API no WSL

```powershell
wsl -d Ubuntu -u root -e sh -lc "systemd-run --unit=servicelayer-final --property=WorkingDirectory=/mnt/c/Users/Pichau/OneDrive/Desktop/faculdade/TCC/servicelayer --setenv=HOME=/root --setenv=USER=root /bin/sh -lc './mvnw clean spring-boot:run > /tmp/servicelayer-final.log 2>&1'"
```

Ver status da API:

```powershell
wsl -d Ubuntu -u root -e systemctl status servicelayer-final.service
```

Ver logs da API:

```powershell
wsl -d Ubuntu -u root -e tail -f /tmp/servicelayer-final.log
```

Parar a API:

```powershell
wsl -d Ubuntu -u root -e systemctl stop servicelayer-final.service
```

## Subir a API no Linux

```bash
./mvnw spring-boot:run
```

Para rodar em background e salvar logs:

```bash
./mvnw spring-boot:run > /tmp/servicelayer-final.log 2>&1 &
```

Parar a API:

```bash
kill %1
```

## Modo debug

No WSL (background com debug):

```powershell
wsl -d Ubuntu -u root -e sh -lc "systemd-run --unit=servicelayer-final --property=WorkingDirectory=/mnt/c/Users/Pichau/OneDrive/Desktop/faculdade/TCC/servicelayer --setenv=HOME=/root --setenv=USER=root /bin/sh -lc './mvnw spring-boot:run -Dspring-boot.run.jvmArguments=\"-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005\" > /tmp/servicelayer-final.log 2>&1'"
```

No Linux (foreground com debug):

```bash
./mvnw spring-boot:run -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

A porta `5005` fica acessivel do Windows normalmente (WSL2 faz a ponte de rede automaticamente).

Para conectar no VS Code, adicione em `.vscode/launch.json`:

```json
{
  "type": "java",
  "request": "attach",
  "name": "Attach to WSL",
  "hostName": "localhost",
  "port": 5005
}
```

`suspend=n` — o app sobe sem esperar o debugger conectar.
`suspend=y` — o app pausa ate voce conectar o debugger.

## 1. Criar usuario cliente

Use um `clientId` novo, por exemplo `cliente-demo`.

```powershell
curl.exe -X POST "http://localhost:8080/api/auth/register" ^
  -H "Content-Type: application/json" ^
  -d "{\"clientId\":\"cliente-demo\",\"clientSecret\":\"senha123\",\"tenantId\":\"tenant_demo\"}"
```

## 2. Pedir o JWT

```powershell
curl.exe -X POST "http://localhost:8080/api/auth" ^
  -H "Content-Type: application/json" ^
  -d "{\"clientId\":\"cliente-demo\",\"clientSecret\":\"senha123\"}"
```

## 3. Ingerir registros do dataset

> Envie alguns registros individualmente ou em loop.

```bash
curl -s -X POST http://localhost:8080/api/ingest \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "X-Event-Type: " \
  -d '{}'
```
---