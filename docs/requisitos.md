# Requisitos do Sistema - Migração Massiva de Dados

### 1. Requisitos Funcionais (RF)
| ID | Requisito | Descrição |
| :--- | :--- | :--- |
| **RF01** | Receção de Lotes | O sistema deve disponibilizar uma API RESTful para receber grandes volumes de dados do sistema legado (MySQL). |
| **RF02** | Geração de Protocolo | O sistema deve gerar e retornar um número de protocolo único no momento em que um lote de dados é recebido. |
| **RF03** | Enfileiramento de Dados | O sistema deve enviar os dados recebidos via API para filas de mensageria (RabbitMQ), garantindo o processamento assíncrono. |
| **RF04** | Análise de Amostras via IA | O sistema deve permitir o envio de uma amostra do esquema ou de dados de origem para um modelo de IA Generativa (LLM). |
| **RF05** | Geração de Contratos | O sistema deve gerar automaticamente, através do LLM, um contrato de mapeamento estrutural em formato JSON ligando a origem ao destino. |
| **RF06** | Processamento em Background | O sistema deve possuir workers que consomem as mensagens da fila de forma contínua e assíncrona. |
| **RF07** | Transformação de Dados | O sistema deve aplicar o contrato de mapeamento JSON aos dados da fila para convertê-los para o formato de destino. |
| **RF08** | Persistência no Destino | O sistema deve inserir os dados já transformados no banco de dados de destino (PostgreSQL). |
| **RF09** | Gestão de Falhas (DLQ) | O sistema deve encaminhar os registros que apresentarem erro para uma Dead Letter Queue (DLQ). |
| **RF10** | Reprocessamento | O sistema deve permitir a inspeção e o reprocessamento manual ou agendado dos itens parados na DLQ. |
| **RF11** | Consulta por Protocolo | O sistema deve permitir consultar o status atual da migração informando o número do protocolo. |
| **RF12** | Geração de Log | O sistema deve permitir gerar LOG durante o processamento atrávés do terminal. |

### 2. Requisitos Não Funcionais (RNF)
| ID | Requisito | Descrição |
| :--- | :--- | :--- |
| **RNF01** | Linguagem e Plataforma | O backend (API e Workers) deve ser construído obrigatoriamente utilizando Java 25 (LTS). |
| **RNF02** | Arquitetura | O projeto deve seguir os princípios de uma Arquitetura Orientada a Eventos (EDA). |
| **RNF03** | Mensageria | O Message Broker utilizado para o enfileiramento das requisições e DLQ deve ser o RabbitMQ. |
| **RNF04** | Alta Concorrência | O processamento dos workers deve utilizar Virtual Threads para maximizar o throughput. |
| **RNF05** | Isolamento da IA | A IA Generativa deve atuar exclusivamente no Design Time (fase de configuração). Não deve haver inferência de IA durante a execução (Runtime) da migração. |
| **RNF06** | Determinismo | O processo de migração (Runtime) de extração, transformação e carga deve ser estritamente determinístico, baseado apenas no JSON gerado. |

### 3. Regras de Negócio (RN)
* **RN01 - Continuidade:** A falha na conversão ou inserção de um registro individual não deve travar o lote. O registro deve ser isolado.
* **RN02 - Validação:** Os contratos JSON gerados pela IA no Design Time devem poder ser validados antes da migração massiva iniciar.
* **RN03 - Conclusão de Lote:** O status de um protocolo só é "Concluído" quando a soma dos registros persistidos e enviados para a DLQ for igual ao número recebido.
* **RN04 - Rastreabilidade:** Toda mensagem enviada ao RabbitMQ deve carregar o número do protocolo.