# Optional AWS delivery — not a free-hosting promise

Default usage is local Compose. No AWS account is required. Do not enable this path if you need guaranteed zero cloud charges.

## Pipeline

Manual workflow → Maven verification → Docker build → ECR push with commit-SHA tag → fetch configured ECS task definition → replace app image → deploy existing ECS service → wait for stability.

The workflow is `.github/workflows/aws-deploy.yml`. It never runs automatically on a push. Its acknowledgement input must exactly equal `DEPLOY_CHARGEABLE_AWS_RESOURCES` before any job can run. Configure a protected GitHub Environment named aws-demo and require a reviewer before enabling it.

## Prerequisites you must provision and budget separately

- An ECR repository, ECS cluster/service, task definition with container name `app`, networking, and task execution/task IAM roles.
- A reachable PostgreSQL instance and observability backends. Compose service names do not resolve in ECS.
- TLS ingress and authentication appropriate to the deployment. The v1 fault-injection demo services should stay private. Do not publish Actuator, Grafana, Prometheus, Loki, Tempo or collector ports as open public services.
- Database and API credentials injected using an approved secret store / ECS secrets, not committed YAML or image layers.
- Resource lifecycle, logs retention, image lifecycle, billing alerts, and a teardown plan.

Set repository/environment variables: AWS_REGION, AWS_ROLE_ARN, ECR_REPOSITORY, ECS_CLUSTER, ECS_SERVICE, ECS_TASK_FAMILY. Set APP_MODE=incident in the preconfigured task and supply DB_URL, DB_USER, DB_PASSWORD, API_PASSWORD, PROMETHEUS_URL, LOKI_URL, TEMPO_URL, GRAFANA_URL and logging configuration.

GitHub uses OIDC to assume the deployment role. No long-lived AWS access keys are required. The role's trust policy must restrict the GitHub OIDC issuer, audience sts.amazonaws.com, and exact repository/environment subject `repo:sach3089/incidentlens:environment:aws-demo`. Grant only the needed ECR push and ECS describe/register/update operations, plus iam:PassRole restricted to the intended task roles. Role setup is a security-sensitive manual administration step; this project does not create roles or broaden access.

ECS stability is not a functional evidence-pipeline test. Run health and investigation checks after deployment. Use the previous known-good task revision for rollback.

## Costs and teardown

Fargate compute is metered; ECR storage, CloudWatch ingestion/retention, database compute/storage, load balancers, public IPv4, NAT gateways, and transfer may add charges. Credits/free-tier eligibility depend on the account and current terms. No monthly amount is guaranteed here.

Teardown must remove or scale down the resources you provisioned, including databases, ingress, networking components and retained logs/images as appropriate. Disabling a GitHub workflow does not stop AWS resources or billing.

References: https://aws.amazon.com/fargate/pricing/ and https://docs.aws.amazon.com/AmazonECS/latest/developerguide/deployment-type-ecs.html
