# billing-service

Scaffolded bounded context for CatalogStudio microservices extraction.

## Owns (target)
Plans, subscriptions, payments, entitlements

## Future tables
subscription_plans, subscriptions, payment_transactions, payment_webhook_events

## Status
Not yet extracted from `catalog-studio-backend`. Traffic remains on platform-api until this service's extract wave.

## Do not
- Copy business logic from platform-api into this module until the extract wave begins
- Create duplicate public APIs or database tables while platform still owns the domain
