# Patika.dev × n11 Spring Boot Bootcamp — Final Project Requirements

> Pure requirements list. No recommendations, no architecture decisions.
> Use this to decide scope, then build from it.

---

IMPORTANT REQUIRMENT: CODE SHOULD BE CREATED WITH BEST PRACTICES, COMPLYING WITH SOLID PRINCIPLES!

## 1. Bootcamp Brief (Official)

### Backend

- [ ] RESTful web service for product listing, cart, and order operations
- [ ] PostgreSQL database for products, orders, and user data
- [ ] Pagination on product listing endpoint
- [ ] Cart operations: add, remove, update
- [ ] Order management: order creation and order flow
- [ ] Iyzico payment integration
- [ ] JWT-based authentication and authorization
- [ ] Unit and integration tests
- [ ] Swagger / OpenAPI documentation
- [ ] Logging mechanism for error tracking

### Frontend (React.js)

- [ ] User interface: product listing and detail pages
- [ ] React Hooks (`useState`, `useEffect`) for state and data management
- [ ] Pagination UI component
- [ ] Cart UI for cart operations
- [ ] API integration with backend services
- [ ] Error handling: user-friendly error messages and loading states

### DevOps & Deployment

- [ ] Docker: containerize backend application
- [ ] Jib: build images without a Dockerfile
- [ ] CI/CD: GitHub Actions pipeline (build, test, deploy)
- [ ] Jenkins comparison: understanding of pipeline logic
- [ ] AWS Deployment: Elastic Beanstalk + RDS
- [ ] Monitoring: Slack deploy notifications

### Project Expectations

- [ ] Backend services functioning correctly
- [ ] Frontend integrated with backend
- [ ] High code quality (Clean Code & SOLID)
- [ ] Tests written
- [ ] CI/CD process understood
- [ ] Application is deployable

### Additional Evaluation Criteria

- [ ] At least one self-initiated nice-to-have feature
      _(each evaluated separately)_

---

## 2. Bootcamp Additional Requirements (per coordinator)

- [ ] Microservices architecture (at least 10 of them)
- [ ] Eureka service discovery
- [ ] RabbitMQ messaging
- [ ] SAGA pattern _(implementation choice for distributed transactions)_

---

## 3. Self-Imposed Nice-to-Have

- [ ] MCP server exposing the storefront to AI agents,

---

## Open Questions to Resolve Before Building

- [ ] Is **Elastic Beanstalk** a hard requirement, or is "deployed on AWS" acceptable?
- [ ] Java version: **17** or **21**?
- [ ] Notification service in scope, or cut?
- [ ] Embeddings for semantic search: paid API (OpenAI) or local (Ollama on homelab)?
- [ ] MCP server hosted via Cloudflare tunnel from homelab, or alongside the AWS deploy?
