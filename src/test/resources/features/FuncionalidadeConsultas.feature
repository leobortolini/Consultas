# language: pt

Funcionalidade: Consultas

  Cenario: Criar nova consulta
    Quando criar nova consulta
    Então o sistema deve retornar a consulta criada

  Cenario: Agendar consulta
    Dado que exista uma consulta
    Quando processar as consultas pendentes
    Então a consulta deve ser agendada

  Cenario: Receber confirmação de consulta
    Dado que exista uma consulta agendada
    Quando receber confirmacao
    Então a consulta deve ser confirmada

  Cenario: Receber confirmação negativa de consulta
    Dado que exista uma consulta agendada
    Quando receber confirmacao negativa
    Então a consulta deve ser cancelada