# Services

Only live extract modules live here.

| Service | Port | Database | Owns |
| --- | --- | --- | --- |
| [`supplier-service`](./supplier-service) | 8081 | `catalog_studio_supplier` | Wholesale / supplier management, goods receipts |

Related runtime (outside this folder):

| Component | Port | Database | Owns |
| --- | --- | --- | --- |
| [`../gateway/api-gateway`](../gateway/api-gateway) | 8080 | — | Public API entry; `/api/v1/wholesale/**` → supplier |
| [`../catalog-studio-backend`](../catalog-studio-backend) (platform-api) | 8082 | `catalog_studio` | Auth, products, inventory, billing, marketplace/OMS, email, tips |

Future domain extracts should be added only when an extract wave starts — do not keep empty scaffold modules in this repository.
