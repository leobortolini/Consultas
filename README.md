# Microsserviço de Agendamento de Consultas

Um microsserviço completo para agendamento de consultas médicas com priorização inteligente e redistribuição equilibrada entre médicos, desenvolvido em Java 21 com Spring Boot e seguindo os princípios de Clean Architecture e Domain-Driven Design (DDD).

## Cobertura de Testes
![jacoco.svg](badges/jacoco.svg) ![branches.svg](badges/branches.svg) 

## Visão Geral

Este microsserviço é responsável por gerenciar o agendamento de consultas médicas em uma rede de saúde, priorizando consultas urgentes e distribuindo a carga de trabalho de forma equilibrada entre médicos. O sistema interage com outros três microsserviços: cadastro de médicos, cadastro de pacientes e sistema de notificações.

## Funcionalidades Principais

- **Agendamento de consultas** com diferentes níveis de prioridade
- **Priorização automática de consultas urgentes**, reagendando consultas não urgentes quando necessário
- **Balanceamento de carga** entre médicos da mesma especialidade e cidade
- **Notificações automáticas** para pacientes (agendamento, confirmação, remanejo)
- **Confirmação de consultas** através de integração com sistema de notificações
- **Remarcação inteligente** em caso de consultas urgentes ou conflitos de horário

## Arquitetura

O projeto segue os princípios de **Clean Architecture** e **Domain-Driven Design**, organizando o código em camadas distintas:

### Camadas da Arquitetura

1. **Domínio** (Núcleo da aplicação)
   - Entidades (Consulta, Paciente, Médico, HorarioTrabalho)
   - Enumerações (StatusConsulta, PrioridadeConsulta, TipoNotificacao)
   - Regras de negócio e serviços de domínio
   - Interfaces de repositórios

2. **Aplicação** (Casos de uso)
   - Orquestração das regras de negócio
   - Implementação dos fluxos de trabalho
   - DTOs para transferência de dados
   - Portas para serviços externos

3. **Infraestrutura** (Detalhes técnicos)
   - Persistência (JPA, Repositories)
   - Comunicação HTTP (RestTemplate)
   - Mensageria (Spring Cloud Stream com RabbitMQ)
   - Consumers e producers para mensageria
   - Controllers REST (APIs)

## Fluxos de Negócio

### 1. Agendamento Normal

1. O sistema recebe solicitação de agendamento via API REST
2. A consulta é registrada com status `PENDENTE_AGENDAMENTO`
3. Um job periódico processa as consultas pendentes, ordenadas por prioridade
4. O sistema busca informações do paciente via microsserviço de pacientes
5. O sistema busca médicos disponíveis via microsserviço de médicos
6. O sistema encontra o próximo horário disponível, considerando equilíbrio entre médicos
7. A consulta é agendada e o paciente é notificado

### 2. Agendamento Urgente

1. O sistema recebe solicitação de agendamento urgente
2. O sistema verifica se existem consultas não confirmadas que podem ser remarcadas
3. Se houver, seleciona a consulta com horário mais próximo para remarcação
4. A consulta urgente é agendada no horário da consulta remarcada
5. A consulta remarcada recebe novo horário ou é marcada para remanejo manual
6. Os pacientes são notificados das alterações

### 3. Confirmação de Consultas

1. O sistema envia notificação para confirmar a consulta (2 semanas antes)
2. O paciente responde à notificação
3. O sistema recebe a resposta via mensageria (RabbitMQ)
4. Se confirmada, a consulta recebe status `CONFIRMADA`
5. Se recusada, a consulta é cancelada e o horário liberado

### 4. Remanejo de Consultas

1. Uma consulta é marcada para remanejo (status `PENDENTE_AGENDAMENTO`)
2. O sistema agenda um novo horário 
3. O sistema envia notificação para confirmar a consulta remanejada
4. O sistema recebe a resposta via mensageria
5. Se confirmada, a consulta recebe status `CONFIRMADA` 
6. Se recusada, a consulta é cancelada e o horário liberado

## Tecnologias Utilizadas

- **Java 21** - Linguagem de programação
- **Spring Boot 3.2** - Framework principal
- **Spring Data JPA** - Persistência e acesso a dados
- **Spring Cloud Stream** - Mensageria assíncrona
- **RabbitMQ** - Broker de mensagens
- **PostgreSQL** - Banco de dados relacional
- **Lombok** - Redução de boilerplate
- **Docker** - Containerização
- **JUnit & Mockito** - Testes unitários e de integração

## Integração com Outros Microsserviços

### Microsserviço de Pacientes
- **Método:** REST (GET)
- **Parâmetro:** CPF do paciente
- **Retorno:** Dados completos do paciente (nome, e-mail, telefone, etc.)

### Microsserviço de Médicos
- **Método:** REST (GET)
- **Parâmetros:** Especialidade e cidade
- **Retorno:** Lista de médicos com seus horários de trabalho

### Microsserviço de Notificações
- **Método:** Mensageria (RabbitMQ)
- **Envio:** Notificações para pacientes (confirmação, agendamento, etc.)
- **Recebimento:** Confirmações de consultas/remanejos dos pacientes

## Balanceamento de Carga Entre Médicos

O sistema implementa um algoritmo inteligente para distribuir as consultas de forma equilibrada entre os médicos da mesma especialidade e cidade:

1. Coleta todos os horários disponíveis de todos os médicos da especialidade
2. Organiza os horários do mais próximo ao mais distante
3. Para cada horário, verifica quais médicos estão disponíveis
4. Seleciona o médico com menor número de consultas no dia
5. Atualiza o contador de consultas do médico escolhido

Isso garante que:
- O paciente sempre recebe o horário mais próximo possível
- A carga de trabalho é distribuída de forma justa entre os médicos
- Todos os médicos da mesma especialidade são utilizados

## Estrutura do Projeto

```
com.fiap.consultas
├── domain
│   ├── entities
│   ├── enums
│   ├── repositories
│   └── services
├── application
│   ├── dtos
│   ├── ports
│   └── usecases
└── infrastructure
    ├── api
    ├── config
    ├── http
    ├── messaging
    └── persistence
```

## Como Executar

### Pré-requisitos
- Java 21
- Maven
- Docker e Docker Compose

### Configuração

As principais configurações estão no arquivo `application.properties`:

```properties
# Configurações do banco de dados
spring.datasource.url=jdbc:postgresql://localhost:5432/consultas
spring.datasource.username=myuser
spring.datasource.password=mypassword

# Configurações dos microsserviços
microservices.pacientes.url=http://localhost:8081
microservices.medicos.url=http://localhost:8082

# Configurações do RabbitMQ
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

## Possíveis Melhorias Futuras

1. **Implementação de Circuit Breaker** para chamadas a outros microsserviços
2. **Cache distribuído** para reduzir chamadas aos microsserviços de médicos e pacientes
3. **API Gateway** para gerenciamento centralizado de rotas e autenticação
4. **Autenticação e autorização** com OAuth2/JWT
5. **Métricas e monitoramento** com Prometheus e Grafana
6. **Tracing distribuído** com Jaeger ou Zipkin
8. **Interface administrativa** para gestão de agendamentos
9. **Relatórios gerenciais** para análise de ocupação e eficiência
