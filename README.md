# Pedidos360

Proyecto de Desarrollo Cloud Native I (DSY1107). Sistema de pedidos con frontend Angular, microservicios Spring Boot en EC2, Amazon Cognito como IDaaS y AWS API Gateway como API Manager.

## Flujo de trabajo

- `main`: versión estable y entregable.
- `dev`: integración de funcionalidades.
- `feature/*`: una rama por unidad de trabajo, que entra a `dev` por Pull Request.

`dev` se promueve a `main` por Pull Request cuando está estable.
