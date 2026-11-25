# Services

## Live (deployed)

| Service | Port | Database | Owns |
| --- | --- | --- | --- |
| [`supplier-service`](./supplier-service) | 8081 | `catalog_studio_supplier` | Wholesale / supplier management, goods receipts |
| [`../gateway/api-gateway`](../gateway/api-gateway) | 8080 | — | Public API entry; routes `/api/v1/wholesale/**` → supplier |
| [`../catalog-studio-backend`](../catalog-studio-backend) (platform-api) | 8082 | `catalog_studio` | Auth, products, inventory, billing, marketplace/OMS, email, tips |

## Planned extracts (scaffolds only — not deployed)

These modules exist as bounded-context placeholders. **Do not** treat them as running microservices.

| Service | Future ownership |
| --- | --- |
| `identity-service` | Auth / users / OTP / referrals |
| `product-service` | Products / AI / shoots |
| `marketplace-service` | Marketplace OMS / extension |
| `billing-service` | Subscriptions / payments |
| `notification-service` | Email / campaigns |
| `media-service` | File storage |

Until an extract wave begins, that domain code remains inside **platform-api**.
