# product-service

Scaffolded bounded context for CatalogStudio microservices extraction.

## Owns (target)
Seller products, AI analysis, templates, profiles, shoots

## Future tables
products, product_images, analysis_jobs, templates, profiles, product_shoots

## Status
Not yet extracted from `catalog-studio-backend`. Traffic remains on platform-api until this service's extract wave.

## Do not
- Copy business logic from platform-api into this module until the extract wave begins
- Create duplicate public APIs or database tables while platform still owns the domain
